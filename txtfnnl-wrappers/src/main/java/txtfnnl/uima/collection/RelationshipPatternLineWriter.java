package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.ConstraintFactory;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.FSStringConstraint;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeaturePath;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.analysis_component.LinkGrammarAnnotator;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.utils.UIMAUtils;
import txtfnnl.utils.SetUtils;

/**
 * A CAS consumer that writes plain-text lines, adding line separators after
 * each relationship pattern.
 * 
 * TODO (documentation...)
 * 
 * @author Florian Leitner
 */
public final class RelationshipPatternLineWriter extends TextWriter {

	/** The namespace for the relationship annotations. */
	public static final String PARAM_RELATIONSHIP_NAMESPACE = KnownRelationshipAnnotator.PARAM_RELATIONSHIP_NAMESPACE;
	@ConfigurationParameter(name = PARAM_RELATIONSHIP_NAMESPACE, mandatory = true)
	private String relationshipNamespace;

	/**
	 * Define the maximum number of characters a pattern may contain.
	 * 
	 * If unset, all lengths are printed, which is the same as setting this
	 * parameter to 0.
	 */
	public static final String PARAM_MAX_PATTERN_LENGTH = "MaxPatternLength";
	@ConfigurationParameter(name = PARAM_MAX_PATTERN_LENGTH, defaultValue = "0")
	private int maxPatternLength;

	static final Pattern REGEX_SPACES = Pattern.compile("[ \\t\\v\\f]+");
	static final Pattern REGEX_LINEBREAK_SPACE = Pattern.compile("(\\r?\\n) ");
	static final Pattern REGEX_SINGLE_LINEBREAK = Pattern
	    .compile("(?<!\\r?\\n)\\r?\\n(?!\\r?\\n)");
	static final Pattern REGEX_EMPTY_MODIFIER = Pattern.compile(" ?[\\-,] [\\-,] ");

	static final String LINEBREAK = System.getProperty("line.separator");

	static final Set<String> SKIPPABLE_TAGS = new HashSet<String>() {

		private static final long serialVersionUID = -2536780486389921051L;
		{
			add("ADJP");
			add("ADVP");
			add("CONJP");
			add("INTJ");
			add("LST");
			add("PP");
			add("PRN");
			add("SBAR");
			add("SBARQ");
			add("WHADJP");
			add("WHAVP");
			add("WHNP");
			add("WHPP");
			add("X");
		}
	};

	static final Set<String> SENTENCE_STARTER_TAGS = new HashSet<String>() {

		private static final long serialVersionUID = -6175387438316829958L;
		{
			add("S");
			add("SBAR");
			add("SBARQ");
			add("SINV");
			add("SQ");
		}
	};

	/**
	 * Make a constraint that allows iterating constituent annotations in a
	 * CAS.
	 */
	public static FSMatchConstraint
	        makeConstituentSyntaxAnnotationConstraint(JCas jcas, String constituentNamespace) {
		Feature constituentNamespaceFeat = jcas.getTypeSystem().getFeatureByFullName(
		    SyntaxAnnotation.class.getName() + ":namespace");
		ConstraintFactory cf = jcas.getConstraintFactory();
		FeaturePath namespacePath = jcas.createFeaturePath();
		namespacePath.addFeature(constituentNamespaceFeat);
		FSStringConstraint namespaceCons = cf.createStringConstraint();
		namespaceCons.equals(constituentNamespace);
		return cf.embedConstraint(namespacePath, namespaceCons);
	}

	/**
	 * Make a constraint that allows iterating constituent annotations in a
	 * CAS.
	 * 
	 * Defaults to using the LinkGrammarAnnotator namespace.
	 */
	public static FSMatchConstraint makeConstituentSyntaxAnnotationConstraint(JCas jcas) {
		return makeConstituentSyntaxAnnotationConstraint(jcas, LinkGrammarAnnotator.NAMESPACE);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * @param relationshipNamespace the namespace of the annotated
	 *        relationships
	 * @param outputDirectory path to the output directory (or null)
	 * @param encoding encoding to use for writing (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param maxPatternLength the max length of patterns to extract (or 0 for
	 *        unlimited)
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final String relationshipNamespace,
	                                                  final File outputDirectory,
	                                                  final String encoding,
	                                                  final boolean printToStdout,
	                                                  final boolean overwriteFiles,
	                                                  final int maxPatternLength)
	        throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(
		    RelationshipPatternLineWriter.class,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(PARAM_RELATIONSHIP_NAMESPACE, relationshipNamespace);
				    put(PARAM_OUTPUT_DIRECTORY, outputDirectory);
				    put(PARAM_ENCODING, encoding);
				    put(PARAM_PRINT_TO_STDOUT, printToStdout);
				    put(PARAM_OVERWRITE_FILES, overwriteFiles);
				    put(PARAM_MAX_PATTERN_LENGTH, maxPatternLength);
			    }
		    }));
	}

	/**
	 * Configure a default CAS consumer descriptor for a pipeline.
	 * 
	 * This consumer will print to STDOUT, using the system's default
	 * encoding, and will print patterns of any size.
	 * 
	 * @param relationshipNamespace the namespace of the annotated
	 *        relationships
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(String relationshipNamespace)
	        throws UIMAException, IOException {
		return configure(relationshipNamespace, null, null, true, false, 0);
	}

	/**
	 * For all annotated relationship sentences in the
	 * {@link Views.CONTENT_TEXT} view of a CAS, extract patterns expressing
	 * the syntactic relationships between the annotated entities using the
	 * annotated syntax tree for that sentence.
	 */
	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		JCas textJCas;
		CAS rawJCas;
		String documentId;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			rawJCas = cas.getView(Views.CONTENT_RAW.toString());
			setStream(rawJCas);
			documentId = new File(new URI(rawJCas.getSofaDataURI()).getPath()).getName();
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (URISyntaxException e) {
			throw new AnalysisEngineProcessException(e);
		}

		if (documentId.indexOf('.') > -1)
			documentId = documentId.substring(0, documentId.lastIndexOf('.'));

		FSIterator<TOP> relationshipIt = KnownRelationshipAnnotator.getRelationshipIterator(
		    textJCas, relationshipNamespace);
		String text = textJCas.getDocumentText();
		AnnotationIndex<Annotation> annIdx = textJCas.getAnnotationIndex(SyntaxAnnotation.type);
		FSMatchConstraint constituentConstraint = makeConstituentSyntaxAnnotationConstraint(textJCas);
		logger.log(Level.FINE, "processing document '" + documentId + "'");

		while (relationshipIt.hasNext()) {
			process((RelationshipAnnotation) relationshipIt.next(), text, annIdx,
			    constituentConstraint, textJCas);
		}

		try {
			unsetStream();
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * Extract all patterns for a given relationship annotation on the SOFA.
	 * 
	 * @param relAnn to process
	 * @param text to extract patterns from
	 * @param annIdx holding all SOFA-wide syntax tree annotations
	 * @param constituentConstraint to create syntax tree iterators
	 * @param jcas
	 * @throws AnalysisEngineProcessException if the extraction fails
	 */
	void process(RelationshipAnnotation relAnn, String text, AnnotationIndex<Annotation> annIdx,
	             FSMatchConstraint constituentConstraint, JCas jcas)
	        throws AnalysisEngineProcessException {
		SentenceAnnotation sentAnn = (SentenceAnnotation) relAnn.getSources(0);
		LinkedList<Annotation> nounPhrases = extractNounPhrases(jcas.createFilteredIterator(
		    annIdx.subiterator(sentAnn, true, true), constituentConstraint));
		FSIterator<Annotation> constituentIt = jcas.createFilteredIterator(
		    annIdx.subiterator(sentAnn, true, true), constituentConstraint);
		List<List<Annotation>> constituentCombinations = listConstituentArrangements(constituentIt);

		if (logger != null)
			logger.log(Level.FINER, "processing sentence '" + sentAnn.getCoveredText() + "'");

		for (TextAnnotation[] entities : listSeparateEntities(relAnn.getTargets())) {
			Set<String> patterns = new HashSet<String>();
			LinkedList<Annotation> sentenceSpan = new LinkedList<Annotation>();
			List<TextAnnotation[]> entityPermutations = combine(findNPReplacements(entities,
			    nounPhrases));

			sentenceSpan.add(sentAnn);
			patterns.add(extractPattern(entities, sentenceSpan));
			patterns.addAll(extractNounPhraseSkippedPatterns(sentenceSpan, entityPermutations));

			for (List<Annotation> spans : constituentCombinations) {
				if (containsAllEntities(spans, entities)) {
					patterns.add(extractPattern(entities, spans));
					patterns.addAll(extractNounPhraseSkippedPatterns(spans, entityPermutations));
				}
			}

			if (logger != null && logger.isLoggable(Level.FINER) && entities.length > 0) {
				StringBuilder sb = new StringBuilder();
				for (TextAnnotation e : entities) {
					sb.append('"');
					sb.append(e.getCoveredText());
					sb.append("\", ");
				}
				sb.delete(sb.length() - 2, sb.length());
				logger.log(Level.FINER,
				    "the entities: " + sb.toString() + " have " + patterns.size() + " patterns");
			}

			try {
				for (String p : patterns) {
					if (maxPatternLength == 0 || p.length() <= maxPatternLength) {
						write(p);
						write("\n");
					}
				}

				write("\n");
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
	}

	/**
	 * Return a list of all NPs in the constituent tree iterator.
	 * 
	 * @param constituents iterating over a constituent tree
	 * @return a list of NPs
	 */
	LinkedList<Annotation> extractNounPhrases(FSIterator<Annotation> constituents) {
		LinkedList<Annotation> nps = new LinkedList<Annotation>();

		while (constituents.hasNext()) {
			SyntaxAnnotation ann = (SyntaxAnnotation) constituents.next();

			if (ann.getIdentifier().equals("NP"))
				nps.add(ann);
		}

		return nps;
	}

	/**
	 * Create all possible constituent arrangements by skipping PP, ADJP,
	 * ADVP, SBAR, SBARQ, and WHNP spans or only creating arrangements
	 * containing inner sentences (S, SBAR, SBARQ, SQ, SINV).
	 * 
	 * @param constituentIt that iterates over all annotated constituents
	 * @return all arrangements possible except for the entire sentence
	 */
	List<List<Annotation>> listConstituentArrangements(FSIterator<Annotation> constituentIt) {
		List<List<Annotation>> arrangements = new ArrayList<List<Annotation>>();
		SyntaxAnnotation ann;

		while (constituentIt.hasNext()) {
			ann = (SyntaxAnnotation) constituentIt.next();

			if (isSentenceStarter(ann)) {
				for (int i = arrangements.size(); i-- > 0;) {
					List<Annotation> spans = arrangements.get(i);

					if (spans.size() > 1) {
						Annotation last = spans.get(spans.size() - 1);

						if (last.getEnd() == last.getBegin()) {
							last = spans.get(spans.size() - 2);

							if (last.getEnd() <= ann.getBegin()) {
								List<Annotation> clone = new LinkedList<Annotation>(spans);
								clone.remove(clone.size() - 1);
								clone.add(ann);
								arrangements.add(clone);
							}
						}
					}
				}
			}

			if (mayBeSkipped(ann)) {
				for (int i = arrangements.size(); i-- > 0;) {
					List<Annotation> spans = arrangements.get(i);
					SyntaxAnnotation last = (SyntaxAnnotation) spans.get(spans.size() - 1);

					if (last.contains(ann)) {
						List<Annotation> clone = new LinkedList<Annotation>(spans);
						clone.remove(clone.size() - 1);
						arrangements.add(clone);

						if (last.getBegin() < ann.getBegin()) {
							Annotation segment = (Annotation) last.clone();
							segment.setEnd(ann.getBegin());
							clone.add(segment);
						}

						if (last.getEnd() > ann.getEnd()) {
							Annotation segment = (Annotation) last.clone();
							segment.setBegin(ann.getEnd());
							clone.add(segment);
						} else if (last.getEnd() == ann.getEnd()) {
							Annotation dummy = (Annotation) last.clone();
							dummy.setBegin(dummy.getEnd());
							clone.add(dummy);
						}
					} else if (last.getEnd() <= ann.getBegin()) {
						expandWithAndWithout(spans, ann, last, arrangements);
					}
				}

				startNewSpans(ann, arrangements);
			} else {
				for (int i = arrangements.size(); i-- > 0;) {
					List<Annotation> spans = arrangements.get(i);
					SyntaxAnnotation last = (SyntaxAnnotation) spans.get(spans.size() - 1);

					if (last.getEnd() <= ann.getBegin()) {
						expandWithAndWithout(spans, ann, last, arrangements);
					}
				}

				startNewSpans(ann, arrangements);
			}
		}

		// remove left-over dummy annotations
		for (List<Annotation> list : arrangements) {
			Annotation last = list.get(list.size() - 1);

			while (last.getBegin() == last.getEnd()) {
				list.remove(list.size() - 1);

				if (list.size() > 0)
					last = list.get(list.size() - 1);
				else
					break;
			}
		}

		return arrangements;
	}

	/**
	 * Return all valid combinations of entity annotations.
	 * 
	 * In a valid annotation group, no entity has a partial overlap any other.
	 * For each annotated entity type (NS-ID-based), only one entity mention
	 * of that type is used to form part of a valid annotation group, even if
	 * that entity type is annotated multiple times on the same sentence.
	 * 
	 * All valid combinations of such groups based on the input array are
	 * returned. In each group, the entities are ordered by increasing
	 * offsets.
	 * 
	 * @param entityArray of TextAnnotations of the entities
	 * @return a List of all valid annotation groups, ordered by increasing
	 *         offsets
	 */
	List<TextAnnotation[]> listSeparateEntities(FSArray entityArray) {
		List<TextAnnotation[]> list = new ArrayList<TextAnnotation[]>();
		Map<String, List<TextAnnotation>> entityMap = groupEntities(entityArray);
		int choices = entityMap.size();

		if (choices > 0) {
			// create all possible combinations of entities choosing one from
			// each type
			int choice = 0;

			for (List<TextAnnotation> entities : entityMap.values()) {
				int numEntities = entities.size();

				if (choice == 0) {
					for (TextAnnotation e : entities) {
						TextAnnotation[] anns = new TextAnnotation[choices];
						anns[choice] = e;
						list.add(anns);
					}
				} else {
					TextAnnotation e;
					int size = list.size();

					for (int i = 1; i < numEntities; i++) {
						e = entities.get(i);

						for (int j = 0; j < size; j++) {
							TextAnnotation[] anns = list.get(j);
							TextAnnotation[] copy = Arrays.copyOf(anns, anns.length);
							copy[choice] = e;
							list.add(copy);
						}
					}

					e = entities.get(0);

					for (int j = 0; j < size; j++) {
						list.get(j)[choice] = e;
					}
				}

				choice += 1;
			}
		}

		// order by increasing offsets and remove invalid groups that have
		// a partial overlap between any entities
		Iterator<TextAnnotation[]> it = list.iterator();

		while (it.hasNext()) {
			if (orderEntities(it.next()) == null)
				it.remove();
		}

		return list;
	}

	/**
	 * Return a list of Annotation sets for each entity, where the inner sets
	 * contain the entity annotation and all noun phrases that may be used to
	 * replace it.
	 * 
	 * @param entities
	 * @param nounPhrases
	 * @return all possible replacement spans for each entity
	 */
	List<Set<Annotation>> findNPReplacements(TextAnnotation[] entities,
	                                         LinkedList<Annotation> nounPhrases) {
		List<Set<Annotation>> replacements = new ArrayList<Set<Annotation>>(entities.length);
		int eb, ee;

		// collect all possible NP replacements for the entities plus the
		// entity itself
		for (TextAnnotation e : entities) {
			Set<Annotation> inner = new HashSet<Annotation>();
			inner.add(e);
			eb = e.getBegin();
			ee = e.getEnd();

			for (Annotation np : nounPhrases) {
				if (np.getBegin() <= eb && np.getEnd() >= ee &&
				    (np.getBegin() != eb || np.getEnd() != ee)) {
					TextAnnotation clone = (TextAnnotation) e.clone();
					clone.setBegin(np.getBegin());
					clone.setEnd(np.getEnd());
					inner.add(clone);
				}
			}

			replacements.add(inner);
		}
		return replacements;
	}

	/**
	 * Extract the pattern from the consecutive spans containing the entities.
	 * 
	 * @param entities contained in the spans
	 * @param spans of SOFA text to extract
	 * @return
	 * @throws AnalysisEngineProcessException if writing fails
	 */
	String extractPattern(TextAnnotation[] entities, List<Annotation> spans)
	        throws AnalysisEngineProcessException {
		int entityIdx = 0;
		int closeTags = 0;
		int lastOffset = spans.get(0).getBegin();
		StringBuilder sb = new StringBuilder();

		for (Annotation a : spans) {
			String text = a.getCoveredText();
			final int begin = a.getBegin();
			final int end = a.getEnd();
			int start = begin;

			// write entities starting before the current annotation span
			while (entityIdx < entities.length && entities[entityIdx].getBegin() <= start) {
				// move the start position if the entity span reaches into
				// the current text
				if (entities[entityIdx].getEnd() > start)
					start = entities[entityIdx].getEnd();

				if (closeTags == 0) {
					// add a space before the first inter-span entity if the
					// SB is not proceeded by one already
					checkSpace(sb);
				} else if (entities[entityIdx - 1].getEnd() <= entities[entityIdx].getBegin()) {
					// close entities before writing the next, non-overlapping
					// entity
					closeTags = writeCloseTags(sb, closeTags);
				}

				// but only write the entity if it ends before the current
				// span
				if (entities[entityIdx].getEnd() <= begin) {
					appendEntity(sb, entities[entityIdx++]);
					closeTags++;
				} else {
					break;
				}
			}

			closeTags = writeCloseTags(sb, closeTags);

			// write entities within the current annotations
			while (entityIdx < entities.length && entities[entityIdx].getBegin() < end) {
				int stop = entities[entityIdx].getBegin();

				if (stop > start) {
					closeTags = writeCloseTags(sb, closeTags);

					// add a space for newly started spans (start===begin) if
					// the next span does not start with a space char itself
					if (start == begin && lastOffset != begin &&
					    !Character.isSpaceChar(text.charAt(start - begin)))
						checkSpace(sb);

					sb.append(text.substring(start - begin, stop - begin));
				}

				stop = entities[entityIdx].getEnd();

				// move the start if necessary
				if (stop > begin && stop > start)
					start = stop;

				// only write the entity if it ends within the current span
				if (start <= end) {
					appendEntity(sb, entities[entityIdx++]);
					closeTags++;
				} else {
					break;
				}
			}
			closeTags = writeCloseTags(sb, closeTags);

			// add a space for newly started spans (start===begin) if
			// the spans are not consecutive (lastOffset != begin) and
			// the next span does not start with a space char itself
			if (start == begin && lastOffset != begin &&
			    !Character.isSpaceChar(text.charAt(start - begin)))
				checkSpace(sb);

			// write (the remainder of) the current annotation span
			// if there is any uncovered text left (end > start)
			if (end > start)
				sb.append(text.substring(start - begin, end - begin));

			// store the last offset to check for consecutive spans next turn
			lastOffset = end;
		}

		// write out all remaining entities
		while (entityIdx < entities.length) {
			appendEntity(sb, entities[entityIdx++]);
			closeTags++;
		}
		// and close tags
		closeTags = writeCloseTags(sb, closeTags);
		return clean(sb.toString().trim());
	}

	/**
	 * Extract a set of patterns from the annotations that condenses any NP
	 * that contain an entity to make the NP represent that entity only.
	 * 
	 * @param entities
	 * @param spans
	 * @param nounPhrases
	 * @return a set of patterns that condense one or more of the NPs
	 *         containing the entities
	 * @throws AnalysisEngineProcessException
	 */
	Set<String> extractNounPhraseSkippedPatterns(List<Annotation> spans,
	                                             List<TextAnnotation[]> permutations)
	        throws AnalysisEngineProcessException {
		Set<String> patterns = new HashSet<String>();

		// iterate over all possible entity permutations
		for (TextAnnotation[] permutation : permutations) {
			// if the permuted entities arrays still is covered by the
			// spans, extract and add the pattern
			if (containsAllEntities(spans, permutation))
				patterns.add(extractPattern(permutation, spans));
		}

		return patterns;
	}

	private boolean mayBeSkipped(SyntaxAnnotation ann) {
		return SKIPPABLE_TAGS.contains(ann.getIdentifier());
	}

	private boolean isSentenceStarter(SyntaxAnnotation ann) {
		return SENTENCE_STARTER_TAGS.contains(ann.getIdentifier());
	}

	private void startNewSpans(SyntaxAnnotation ann, List<List<Annotation>> arrangements) {
		List<Annotation> list = new LinkedList<Annotation>();
		list.add(ann);
		arrangements.add(list);
	}

	/**
	 * Check if all entities are covered by the given spans.
	 * 
	 * @param spans
	 * @param entities
	 * @return true if all entities are covered by at least one of the spans
	 */
	private boolean containsAllEntities(List<Annotation> spans, TextAnnotation[] entities) {
		int idx = 0;

		for (Annotation ann : spans) {
			if (ann.getBegin() <= entities[idx].getBegin() &&
			    ann.getEnd() >= entities[idx].getEnd()) {
				idx++;
			}

			if (idx == entities.length)
				break;
		}

		return entities.length == idx;
	}

	/**
	 * Helper method to expand the arrangements with a cloned version of spans
	 * where the current annotation is skipped and is added to spans.
	 * 
	 * Furthermore, if the last annotation is a dummy (begin == end), it is
	 * removed from both spans and the clone.
	 * 
	 * @param spans
	 * @param currentAnnotation
	 * @param lastAnnotation
	 * @param arrangements
	 * @throws CASRuntimeException
	 */
	private void expandWithAndWithout(List<Annotation> spans, SyntaxAnnotation currentAnnotation,
	                                  SyntaxAnnotation lastAnnotation,
	                                  List<List<Annotation>> arrangements)
	        throws CASRuntimeException {
		if (lastAnnotation.getBegin() == lastAnnotation.getEnd()) {
			spans.remove(spans.size() - 1);
		}
		List<Annotation> clone = new LinkedList<Annotation>(spans);
		Annotation dummy = (Annotation) currentAnnotation.clone();
		dummy.setBegin(dummy.getEnd());
		clone.add(dummy);
		arrangements.add(clone);
		spans.add(currentAnnotation);

		if (spans.size() == 0)
			throw new AssertionError("empty spans");

		if (clone.size() == 0)
			throw new AssertionError("empty clone");

	}

	/**
	 * Create all possible combinations from a list of replacement sets at
	 * each possible position in the array using recursive calls of this
	 * method.
	 * 
	 * @param replacements that can be chosen, at each position
	 */
	private List<TextAnnotation[]> combine(List<Set<Annotation>> replacements) {
		List<List<Annotation>> combinations = SetUtils.combinate(replacements);
		List<TextAnnotation[]> result = new ArrayList<TextAnnotation[]>(combinations.size());
		int len = replacements.size();

		for (List<Annotation> list : combinations) {
			result.add(list.toArray(new TextAnnotation[len]));
		}
		return result;
	}

	/**
	 * Group entities by namespaces plus identifiers.
	 * 
	 * @param entities to group
	 * @return grouped entities or an empty map if no entities are present
	 */
	Map<String, List<TextAnnotation>> groupEntities(FSArray entities) {
		Map<String, List<TextAnnotation>> map = new HashMap<String, List<TextAnnotation>>();

		if (entities != null) {
			for (int i = entities.size(); i-- > 0;) {
				TextAnnotation ann = (TextAnnotation) entities.get(i);
				String key = ann.getNamespace() + ann.getIdentifier();

				if (!map.containsKey(key))
					map.put(key, new LinkedList<TextAnnotation>());

				map.get(key).add(ann);
			}
		}

		return map;
	}

	/**
	 * Sort entities by increasing offsets (increasing start, decresing end)
	 * and all other comparator settings (see
	 * {@link TextAnnotation#TEXT_ANNOTATION_COMPARATOR}).
	 * 
	 * @param entities
	 * @return the same array or <code>null</code> if partial overlaps were
	 *         detected in the array
	 */
	TextAnnotation[] orderEntities(TextAnnotation[] entities) {
		if (entities != null) {
			Arrays.sort(entities, TextAnnotation.TEXT_ANNOTATION_COMPARATOR);
			TextAnnotation last = null;

			for (TextAnnotation e : entities) {
				if (last != null && e.getBegin() < last.getEnd() && e.getEnd() > last.getEnd()) {
					// partially overlapping entities detected
					return null;
				}
				last = e;
			}
		}
		return entities;
	}

	/**
	 * Append an entity mention (but don't close it) to the string builder.
	 * 
	 * @param sb
	 * @param entity
	 */
	private void appendEntity(StringBuilder sb, TextAnnotation entity) {
		checkSpace(sb);
		sb.append("[[");
		sb.append(entity.getNamespace());
		sb.append(entity.getIdentifier());
	}

	/**
	 * Add a space character if the string builder contains characters and the
	 * last character is not a space character.
	 * 
	 * @param sb
	 */
	private void checkSpace(StringBuilder sb) {
		if (sb.length() > 0 && !Character.isSpaceChar(sb.charAt(sb.length() - 1)))
			sb.append(' ');
	}

	/**
	 * Close <code>num</code> entity mentions in the string builder.
	 * 
	 * @param sb
	 * @param num
	 * @return
	 */
	private int writeCloseTags(StringBuilder sb, int num) {
		while (num > 0) {
			sb.append("]]");
			num--;
		}

		return num;
	}

	/**
	 * Remove multiple spaces, replace single line breaks, and escape HTML
	 * from a substring within a text.
	 * 
	 * @param text with substring to extract and clean
	 * @return
	 */
	String clean(String text) {
		if (text.trim().length() == 0)
			return "";

		return replaceEmptyModifierPhrases(replaceAndTrimMultipleSpaces(replaceSingleBreaksAndSpaces(replaceAndTrimMultipleSpaces(text))));
	}

	/**
	 * After all this trimming, sometimes empty phrases might lead to dangling
	 * commas and dots that should be replaced.
	 * 
	 * @param text to replace
	 * @return the text with these "dangling" characters replaced
	 */
	private String replaceEmptyModifierPhrases(String text) {
		String replacement = REGEX_EMPTY_MODIFIER.matcher(text).replaceAll(" ")
		    .replace(" , ", " ").trim();

		if (replacement.endsWith(" ."))
			replacement = replacement.substring(0, replacement.length() - 2).trim();

		if (replacement.endsWith(","))
			replacement = replacement.substring(0, replacement.length() - 1).trim();

		if (!Character.isUpperCase(replacement.charAt(0)) && replacement.endsWith("."))
			replacement = replacement.substring(0, replacement.length() - 1).trim();

		return replacement;
	}

	/**
	 * Replace multiple white-spaces with a single space.
	 * 
	 * @param text to replace spaces in
	 * @return the text without consecutive spaces and trimmed
	 */
	private String replaceAndTrimMultipleSpaces(String text) {
		return REGEX_SPACES.matcher(text).replaceAll(" ");
	}

	/**
	 * Replace single line breaks with a white-space.
	 * 
	 * @param text to replace line-breaks in
	 * @return the text with single line-breaks trimmed
	 */
	private String replaceSingleBreaksAndSpaces(String text) {
		return REGEX_SINGLE_LINEBREAK
		    .matcher(REGEX_LINEBREAK_SPACE.matcher(text).replaceAll("$1")).replaceAll(" ");
	}

}
