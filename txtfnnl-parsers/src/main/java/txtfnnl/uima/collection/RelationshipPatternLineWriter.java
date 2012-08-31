package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.cas.text.AnnotationTreeNode;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Offset;
import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.utils.IOUtils;

/**
 * A CAS consumer that writes plain-text lines, adding line separators after
 * the character(s)
 * {@link txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator} detected
 * as sentence terminals.
 * 
 * Mandatory parameters:
 * <ul>
 * <li>{@link opennlp.uima.util.UimaUtil#SENTENCE_TYPE_PARAMETER} the sentence
 * annotation type to use (usually, "txtfnnl.uima.SyntaxAnnotation")</li>
 * </ul>
 * This consumer has several optional configuration possibilities. With no
 * option chosen at all, output is written to <b>STDOUT</b>. But if
 * {@link #PARAM_PRINT_TO_STDOUT} was set to <code>False</code> and no output
 * directory was set either, this consumer would go silent.
 * <ul>
 * <li>{@link #PARAM_OUTPUT_DIRECTORY} defines an output directory</li>
 * <li>{@link #PARAM_PRINT_TO_STDOUT} defines <b>STDOUT</b> as output</li>
 * <li>{@link #PARAM_OVERWRITE_FILES} allows overwriting of existing files</li>
 * <li>{@link #PARAM_ENCODING} sets a particular output encoding</li>
 * </ul>
 * All written line-breaks are the character sequence defined by the system
 * property "line.separator".
 * 
 * <b>Note</b> that on <b>Apple OSX</b> the default encoding would be
 * MacRoman; however, this consumer uses either the encoding defined by the
 * <b>LANG</b> environment variable or otherwise defaults to <b>UTF-8</b> as a
 * far more sensible encoding on OSX instead.
 * 
 * @author Florian Leitner
 */
public final class RelationshipPatternLineWriter extends CasAnnotator_ImplBase {

	/** The namespace for the Relationship annotations. */
	public static final String PARAM_RELATIONSHIP_NAMESPACE = KnownRelationshipAnnotator.PARAM_RELATIONSHIP_NAMESPACE;

	/**
	 * A String representing the path of the output directory.
	 * 
	 * With the use of this option, a file named just as the basename of the
	 * CAS URI with ".txt" attached is produced for each SOFA, and these SOFAs
	 * are all written to this output directory.
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";

	/**
	 * <code>True</code> if the output should also go to <b>STDOUT</b>.
	 * 
	 * With the use of this option, all lines are written to <b>STDOUT</b>;
	 * this may be set in <i>addition</i> to any file output.
	 */
	public static final String PARAM_PRINT_TO_STDOUT = "PrintToStdout";

	/**
	 * <code>True</code> if all files written to the
	 * {@link #PARAM_OUTPUT_DIRECTORY} should replace any already existing
	 * ones.
	 * 
	 * By default, if a file exits, ".<i>n</i>" is inserted between the file
	 * name and the ".txt" suffix, where <i>n</i> is an integer chosen as to
	 * make the file name unique wrt. the directory.
	 */
	public static final String PARAM_OVERWRITE_FILES = "OverwriteFiles";

	/** Define a particular output encoding String. */
	public static final String PARAM_ENCODING = "Encoding";

	static final Pattern REGEX_SPACES = Pattern.compile("[ \\t\\v\\f]+");
	static final Pattern REGEX_LINEBREAK_SPACE = Pattern.compile("(\\r?\\n) ");
	static final Pattern REGEX_SINGLE_LINEBREAK = Pattern
	    .compile("(?<!\\r?\\n)\\r?\\n(?!\\r?\\n)");
	static final Pattern REGEX_EMPTY_MODIFIER = Pattern
	    .compile(" ?[\\-,] [\\-,] ");

	static final String LINEBREAK = System.getProperty("line.separator");

	// parameter settings
	File outputDirectory = null;
	boolean printToStdout = false;
	private boolean overwriteFiles = false;
	private String encoding = null;
	private Writer outputWriter;

	// annotator state (thread-independent)
	private Logger logger;
	private int counter = 0;
	private String relationshipNamespace;

	/**
	 * Load the sentence detector model resource and initialize the model
	 * evaluator.
	 */
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();

		String outputDirName = (String) ctx
		    .getConfigParameterValue(PARAM_OUTPUT_DIRECTORY);

		relationshipNamespace = (String) ctx
		    .getConfigParameterValue(PARAM_RELATIONSHIP_NAMESPACE);

		if (relationshipNamespace == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.CONFIG_SETTING_ABSENT,
			    new Object[] { PARAM_RELATIONSHIP_NAMESPACE });

		if (outputDirName != null && outputDirName.length() > 0) {
			outputDirectory = new File(outputDirName);

			if (!outputDirectory.isDirectory() || !outputDirectory.canWrite())
				throw new ResourceInitializationException(new IOException(
				    "Parameter " + PARAM_OUTPUT_DIRECTORY + " '" +
				            outputDirName + "' not a writeable directory"));
		}

		Boolean printToStdoutVal = (Boolean) ctx
		    .getConfigParameterValue(PARAM_PRINT_TO_STDOUT);

		encoding = (String) ctx.getConfigParameterValue(PARAM_ENCODING);

		if (printToStdoutVal != null && printToStdoutVal)
			printToStdout = true;
		else if (printToStdoutVal == null && outputDirName == null)
			printToStdout = true;
		else if (printToStdoutVal != null && outputDirName == null)
			throw new ResourceInitializationException(new AssertionError(
			    "no output stream (no directory or STDOUT specified)"));

		if (printToStdout) {
			if (encoding == null && IOUtils.isMacOSX()) {
				// fix broken Mac JDK that uses MacRoman instead of the LANG
				// setting as default encoding; if LANG is not set, use UTF-8.
				// TODO: make this change work across the entire library
				encoding = IOUtils.getLocaleEncoding();

				if (encoding == null)
					encoding = "UTF-8";

				try {
					IOUtils.setOutputEncoding(encoding);
				} catch (UnsupportedEncodingException e) {
					throw new ResourceInitializationException(e);
				}
			} else if (encoding != null) {
				try {
					IOUtils.setOutputEncoding(encoding);
				} catch (UnsupportedEncodingException e) {
					throw new ResourceInitializationException(e);
				}
			}

			if (encoding != null)
				logger.log(Level.INFO, "set STDOUT to use '" + encoding +
				                       "' encoding");
		}

		Boolean overwriteFilesVal = (Boolean) ctx
		    .getConfigParameterValue(PARAM_OVERWRITE_FILES);

		if (overwriteFilesVal != null && overwriteFilesVal)
			overwriteFiles = true;

		counter = 0;
	}

	/**
	 * For all annotated relationship sentences in the
	 * {@link Views.CONTENT_TEXT} view of a CAS, extract patterns expressing
	 * the syntactic relationships between the annotated entities using the
	 * annotated syntax tree for that sentence.
	 */
	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		JCas textJCas, rawJCas;
		String documentId;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			rawJCas = cas.getView(Views.CONTENT_RAW.toString()).getJCas();
			setStream(rawJCas);
			documentId = new File(new URI(rawJCas.getSofaDataURI()).getPath())
			    .getName();
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (URISyntaxException e) {
			throw new AnalysisEngineProcessException(e);
		}

		if (documentId.indexOf('.') > -1)
			documentId = documentId.substring(0, documentId.lastIndexOf('.'));

		FSIterator<TOP> relationshipIt = KnownRelationshipAnnotator
		    .getRelationshipIterator(textJCas, relationshipNamespace);
		String text = textJCas.getDocumentText();
		// TODO: restrict this index to SyntaxTree annotation types?
		AnnotationIndex<Annotation> annIdx = textJCas
		    .getAnnotationIndex(SyntaxAnnotation.type);

		while (relationshipIt.hasNext()) {
			process((RelationshipAnnotation) relationshipIt.next(), text,
			    annIdx);
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
	 * @throws AnalysisEngineProcessException if the extraction fails
	 */
	void process(RelationshipAnnotation relAnn, String text,
	             AnnotationIndex<Annotation> annIdx)
	        throws AnalysisEngineProcessException {
		SyntaxAnnotation sentAnn = (SyntaxAnnotation) relAnn.getSources(0);
		AnnotationTreeNode<Annotation> root = annIdx.tree(sentAnn).getRoot();

		for (TextAnnotation[] entities : iterateAnnotations(relAnn
		    .getTargets())) {
			List<List<AnnotationTreeNode<Annotation>>> paths = findPaths(
			    entities, root);
			int commonNodeIdx = findCommonRoot(paths);
			AnnotationTreeNode<Annotation> commonNode = (commonNodeIdx < 0)
			        ? root
			        : paths.get(0).get(commonNodeIdx);
			LinkedList<Annotation> longSpans = longestCommonSpans(sentAnn,
			    paths, commonNodeIdx);
			Set<String> patterns = new HashSet<String>();
			List<LinkedList<Annotation>> allSpans = shortestCommonSpans(
			    entities, paths, commonNodeIdx, root);
			allSpans.add(longSpans);
			LinkedList<Annotation> sentSpan = new LinkedList<Annotation>();
			sentSpan.add(sentAnn);
			allSpans.add(sentSpan);

			for (LinkedList<Annotation> span : allSpans)
				patterns.add(extractPattern(entities, span));

			for (LinkedList<Annotation> spans : skippedModifierSpans(entities,
			    new LinkedList<LinkedList<Annotation>>(allSpans), commonNode)) {
				patterns.add(extractPattern(entities, spans));
				allSpans.add(spans);
			}

			removeEntityNounPhrases(patterns, entities, allSpans, paths,
			    commonNodeIdx);

			try {
				for (String p : patterns) {
					write(p);
					write("\n");
				}

				write("\n");
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
	}

	/**
	 * 
	 * @param patterns
	 * @param entities
	 * @param longSpans
	 * @param spans
	 * @param paths
	 * @param commonNodeIdx
	 * @throws AnalysisEngineProcessException
	 */
	        void
	        removeEntityNounPhrases(Set<String> patterns,
	                                TextAnnotation[] entities,
	                                List<LinkedList<Annotation>> spans,
	                                List<List<AnnotationTreeNode<Annotation>>> paths,
	                                int commonNodeIdx)
	                throws AnalysisEngineProcessException {
		TextAnnotation[] clone = new TextAnnotation[entities.length];
		TextAnnotation tmp;

		for (int idx = 0; idx < entities.length; ++idx) {
			List<AnnotationTreeNode<Annotation>> p = paths.get(idx);
			int nodeIdx = -1;

			for (AnnotationTreeNode<Annotation> node : p) {
				if (++nodeIdx < commonNodeIdx)
					continue;

				TextAnnotation ann = (TextAnnotation) node.get();

				if (ann.getIdentifier().equals("NP") &&
				    (ann.getBegin() < entities[idx].getBegin() || ann.getEnd() > entities[idx]
				        .getEnd())) {
					clone[idx] = (TextAnnotation) entities[idx].clone();
					clone[idx].setBegin(ann.getBegin());
					clone[idx].setEnd(ann.getEnd());
					tmp = entities[idx];
					entities[idx] = clone[idx];

					// replace one NP
					for (LinkedList<Annotation> span : spans)
						patterns.add(extractPattern(entities, span));

					entities[idx] = tmp;
				}
			}

			TextAnnotation leaf = (TextAnnotation) p.get(p.size() - 1).get();

			if (leaf.getIdentifier().equals("NP") &&
			    (leaf.getBegin() < entities[idx].getBegin() || leaf.getEnd() > entities[idx]
			        .getEnd())) {
				clone[idx] = (TextAnnotation) entities[idx].clone();
				clone[idx].setBegin(leaf.getBegin());
				clone[idx].setEnd(leaf.getEnd());
			} else {
				clone[idx] = entities[idx];
			}

			tmp = entities[idx];
			entities[idx] = clone[idx];

			// replace one NP
			for (LinkedList<Annotation> span : spans)
				patterns.add(extractPattern(entities, span));

			entities[idx] = tmp;
		}

		// replace all NPs
		for (LinkedList<Annotation> span : spans)
			patterns.add(extractPattern(clone, span));
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
	List<TextAnnotation[]> iterateAnnotations(FSArray entityArray) {
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
							TextAnnotation[] copy = Arrays.copyOf(anns,
							    anns.length);
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
	 *         detected
	 */
	TextAnnotation[] orderEntities(TextAnnotation[] entities) {
		if (entities != null) {
			Arrays.sort(entities, TextAnnotation.TEXT_ANNOTATION_COMPARATOR);
			TextAnnotation last = null;

			for (TextAnnotation e : entities) {
				if (last != null && e.getBegin() < last.getEnd() &&
				    e.getEnd() > last.getEnd()) {
					// partially overlapping entities
					return null;
				}
				last = e;
			}
		}
		return entities;
	}

	/**
	 * Find the shortest paths to each entity in the syntax tree.
	 * 
	 * @param entities to search for
	 * @param root of the syntax tree
	 * @return an array of shortes path arrays
	 */
	List<List<AnnotationTreeNode<Annotation>>>
	        findPaths(TextAnnotation[] entities,
	                  AnnotationTreeNode<Annotation> root) {
		List<List<AnnotationTreeNode<Annotation>>> paths = new ArrayList<List<AnnotationTreeNode<Annotation>>>(
		    entities.length);

		for (int i = 0; i < entities.length; ++i) {
			paths.add(shortestPathTo(entities[i].getBegin(),
			    entities[i].getEnd(), root));
		}

		return paths;
	}

	/**
	 * Find the shortest path in a syntax tree to a particular span.
	 * 
	 * @param begin of the span
	 * @param end of the span
	 * @param root of the syntax tree
	 * @return the shortest path in the tree
	 */
	private List<AnnotationTreeNode<Annotation>>
	        shortestPathTo(int begin, int end,
	                       AnnotationTreeNode<Annotation> root) {
		List<AnnotationTreeNode<Annotation>> path = new LinkedList<AnnotationTreeNode<Annotation>>();
		List<AnnotationTreeNode<Annotation>> children = root.getChildren();
		path.add(root);

		loop: while (children.size() > 0) {
			for (AnnotationTreeNode<Annotation> node : children) {
				Annotation ann = node.get();

				if (ann.getBegin() <= begin && ann.getEnd() >= end) {
					path.add(node);
					children = node.getChildren();
					continue loop;
				}
			}
			break;
		}

		return path;
	}

	/**
	 * Find the index of the last common node of all paths.
	 * 
	 * @param paths in the syntax tree
	 * @return the index valid for all paths or -1 if no such node exists
	 */
	int findCommonRoot(List<List<AnnotationTreeNode<Annotation>>> paths) {
		int pos = 0;

		search: while (pos < paths.get(0).size()) {
			AnnotationTreeNode<Annotation> node = paths.get(0).get(pos);

			for (List<AnnotationTreeNode<Annotation>> p : paths) {
				if (pos >= p.size() || !p.get(pos).equals(node))
					break search;
			}
			pos++;
		}

		return pos - 1;
	}

	/**
	 * For a given sentence and paths (with a common node, including none as
	 * <code>-1</code>), return the greatest spans they have in common.
	 * 
	 * @param sentAnn sentence at the base of this operation
	 * @param paths to all entities
	 * @param commonNode index of all paths (or <code>-1</code> if none)
	 * @return the LinkedList of the greatest common text spans
	 */
	        LinkedList<Annotation>
	        longestCommonSpans(SyntaxAnnotation sentAnn,
	                           List<List<AnnotationTreeNode<Annotation>>> paths,
	                           int commonNode) {
		boolean commonNodeIsLeaf = false;
		LinkedList<Annotation> spans = new LinkedList<Annotation>();

		for (List<AnnotationTreeNode<Annotation>> p : paths) {
			if (p.size() - 1 == commonNode) {
				commonNodeIsLeaf = true;
				break;
			}
		}

		if (commonNode < 0 || commonNodeIsLeaf) {
			if (commonNode < 0)
				spans.add(sentAnn);
			else
				spans.add(paths.get(0).get(commonNode).get());
		} else {
			spans.add(paths.get(0).get(commonNode + 1).get());

			for (List<AnnotationTreeNode<Annotation>> p : paths) {
				Annotation ann = p.get(commonNode + 1).get();

				if (!spans.getLast().equals(ann))
					spans.add(ann);
			}
		}
		return spans;
	}

	List<LinkedList<Annotation>>
	        skippedModifierSpans(TextAnnotation[] entities,
	                             List<LinkedList<Annotation>> allSpans,
	                             AnnotationTreeNode<Annotation> commonNode) {
		// TODO Auto-generated method stub
		Offset[] entityOffsets = new Offset[entities.length];

		for (int i = 0; i < entities.length; ++i)
			entityOffsets[i] = entities[i].getOffset();

		Set<Offset> skippableSpans = iterateSkippableSpans(entityOffsets,
		    commonNode);
		List<LinkedList<Annotation>> extraSpans = new LinkedList<LinkedList<Annotation>>();

		for (LinkedList<Annotation> spans : allSpans) {

			search: for (Offset skippable : skippableSpans) {
				for (Annotation ann : spans) {
					Offset target = ((TextAnnotation) ann).getOffset();

					if (target.contains(skippable)) {
						LinkedList<Annotation> clone = new LinkedList<Annotation>(
						    spans);
						int idx = clone.indexOf(ann);
						clone.remove(idx);

						if (!skippable.contains(target)) {
							if (skippable.end() != target.end()) {
								Annotation after = (Annotation) ann.clone();
								after.setBegin(skippable.end());
								clone.add(idx, after);
							}
							if (skippable.start() != target.start()) {
								Annotation before = (Annotation) ann.clone();
								before.setEnd(skippable.start());
								clone.add(idx, before);
							}
						}

						extraSpans.add(clone);
						continue search;
					}
				}

			}
		}

		return extraSpans;
	}

	private Set<Offset>
	        iterateSkippableSpans(Offset[] entities,
	                              AnnotationTreeNode<Annotation> node) {
		Set<Offset> spans = new HashSet<Offset>();
		int numChildren = node.getChildCount();

		for (int i = 0; i < numChildren; i++) {
			AnnotationTreeNode<Annotation> child = node.getChild(i);
			TextAnnotation span = (TextAnnotation) child.get();
			String id = span.getIdentifier();

			if ("PP".equals(id) || "ADJP".equals(id) || "ADVP".equals(id)) {
				Offset off = span.getOffset();
				boolean hasEntity = false;

				for (Offset e : entities) {
					if (off.contains(e)) {
						hasEntity = true;
						break;
					}
				}

				if (!hasEntity)
					spans.add(off);
			}

			spans.addAll(iterateSkippableSpans(entities, child));
		}
		return spans;
	}

	/**
	 * 
	 * @param sentAnn
	 * @param entities
	 * @param paths
	 * @param commonNode
	 * @param syntaxTree
	 * @param jcas
	 * @return
	 */
	        List<LinkedList<Annotation>>
	        shortestCommonSpans(TextAnnotation[] entities,
	                            List<List<AnnotationTreeNode<Annotation>>> paths,
	                            int commonNode,
	                            AnnotationTreeNode<Annotation> syntaxTree) {
		List<Set<Offset>> spanList = new ArrayList<Set<Offset>>();
		Set<Offset> spans = new LinkedHashSet<Offset>();
		int i = 0;
		boolean incrementCommonNode = true;

		if (entities.length != paths.size())
			throw new AssertionError("" + entities.length + " entities, but " +
			                         paths.size() + " paths");

		for (List<AnnotationTreeNode<Annotation>> p : paths) {
			if (p.size() - 1 == commonNode) {
				commonNode = -1;
				break;
			}
		}

		if (commonNode != -1) {
			for (List<AnnotationTreeNode<Annotation>> p : paths) {
				spanList.add(new LinkedHashSet<Offset>());

				if (p.size() == commonNode + 1)
					incrementCommonNode = false;
			}

			if (incrementCommonNode)
				commonNode++;
		}

		spanList.add(spans);

		loop: for (List<AnnotationTreeNode<Annotation>> p : paths) {
			int j = 0;

			if (commonNode != -1) {
				for (; j < entities.length; ++j) {
					if (j != i) {
						Annotation a = p.get(commonNode).get();
						spanList.get(j).add(
						    new Offset(a.getBegin(), a.getEnd()));
					}
				}

				j = 0;
			}

			for (AnnotationTreeNode<Annotation> n : p) {
				if (j > commonNode &&
				    (((TextAnnotation) n.get()).getIdentifier().equals("SBAR") || ((TextAnnotation) n
				        .get()).getIdentifier().equals("S"))) {
					List<Offset> offsets = shortestSpan(entities[i], n);
					spans.addAll(offsets);

					if (commonNode != -1)
						spanList.get(i).addAll(offsets);

					i++;
					continue loop;
				}
				j++;
			}

			List<Offset> offsets = shortestSpan(entities[i],
			    (commonNode == -1) ? syntaxTree : p.get(commonNode));
			spans.addAll(offsets);

			if (commonNode != -1)
				spanList.get(i).addAll(offsets);

			i++;
		}

		List<LinkedList<Annotation>> annList = new LinkedList<LinkedList<Annotation>>();
		Annotation base = syntaxTree.get();

		for (Set<Offset> aSpan : spanList) {
			LinkedList<Annotation> aList = new LinkedList<Annotation>();

			for (Offset off : aSpan) {
				Annotation ann = (Annotation) base.clone();
				ann.setBegin(off.start());
				ann.setEnd(off.end());
				aList.add(ann);
			}

			annList.add(aList);
		}

		return annList;
	}

	/**
	 * 
	 * @param e
	 * @param syntaxNode
	 * @return
	 */
	List<Offset> shortestSpan(TextAnnotation e,
	                          AnnotationTreeNode<Annotation> syntaxNode) {
		int begin = e.getBegin();
		int end = e.getEnd();
		Annotation span = syntaxNode.get();
		int position = span.getBegin();
		List<Offset> commonSpans = new LinkedList<Offset>();

		if (begin >= span.getBegin() && end <= span.getEnd()) {
			if (syntaxNode.getChildCount() > 0) {
				for (AnnotationTreeNode<Annotation> child : syntaxNode
				    .getChildren()) {
					Annotation inner = child.get();

					if (position < inner.getBegin()) {
						commonSpans
						    .add(new Offset(position, inner.getBegin()));
						position = inner.getBegin();
					}

					if (begin >= span.getBegin() && end <= span.getEnd()) {
						commonSpans.addAll(shortestSpan(e, child));
						position = inner.getEnd();
					}
				}
			} else {
				commonSpans.add(new Offset(span.getBegin(), span.getEnd()));
				position = span.getEnd();
			}

			if (position < span.getEnd())
				commonSpans.add(new Offset(position, span.getEnd()));
		}

		return commonSpans;
	}

	/**
	 * Extract the pattern from the consecutive spans containing the entities.
	 * 
	 * @param entities contained in the spans
	 * @param spans of SOFA text to extract
	 * @return
	 * @throws AnalysisEngineProcessException if writing fails
	 */
	String extractPattern(TextAnnotation[] entities,
	                      LinkedList<Annotation> spans)
	        throws AnalysisEngineProcessException {
		int entityIdx = 0;
		int closeTags = 0;
		int lastOffset = spans.get(0).getBegin();
		StringBuilder sb = new StringBuilder();

		for (Annotation a : spans) {
			String text = a.getCoveredText();
			int start = a.getBegin();
			int begin = start;
			int end = a.getEnd();
			int finish = end;

			// write entities before the current annotation span
			while (entityIdx < entities.length &&
			       entities[entityIdx].getBegin() <= a.getBegin()) {
				if (entities[entityIdx].getEnd() > start) {
					start = entities[entityIdx].getEnd();
				}

				// add a space before the first inter-span entity if it
				// is not proceeded by one itself.
				if (closeTags == 0) {
					checkSpace(sb);
				} else if (entities[entityIdx - 1].getEnd() <= entities[entityIdx]
				    .getBegin()) {
					closeTags = writeCloseTags(sb, closeTags);
				}

				appendEntity(sb, entities[entityIdx++]);
				closeTags++;
			}

			closeTags = writeCloseTags(sb, closeTags);

			// write entities within the current annotations
			while (entityIdx < entities.length &&
			       entities[entityIdx].getBegin() < finish) {
				end = entities[entityIdx].getBegin();

				if (end > start) {
					closeTags = writeCloseTags(sb, closeTags);

					// add a space for newly started spans (start===begin) if
					// the next span does not start with a space char itself
					if (start == begin && lastOffset != begin &&
					    !Character.isSpaceChar(text.charAt(start - begin)))
						checkSpace(sb);

					sb.append(text.substring(start - begin, end - begin));
				}

				start = entities[entityIdx].getEnd();
				end = finish;
				appendEntity(sb, entities[entityIdx++]);
				closeTags++;
			}
			closeTags = writeCloseTags(sb, closeTags);

			// add a space for newly started spans (start===begin) if
			// the next span does not start with a space char itself
			// and the spans are not consecutive (lastOffset != begin)
			if (start == begin && lastOffset != begin &&
			    !Character.isSpaceChar(text.charAt(start - begin)))
				checkSpace(sb);

			// write (the remainder of) the current annotation span
			if (end > start)
				sb.append(text.substring(start - begin, end - begin));

			// store the last offset to check for consecutive spans
			lastOffset = finish;
		}

		while (entityIdx < entities.length) {
			appendEntity(sb, entities[entityIdx++]);
			closeTags++;
		}
		closeTags = writeCloseTags(sb, closeTags);
		return clean(sb.toString().trim());
	}

	/**
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
	 * 
	 * @param sb
	 */
	private void checkSpace(StringBuilder sb) {
		if (sb.length() > 0 &&
		    !Character.isSpaceChar(sb.charAt(sb.length() - 1)))
			sb.append(' ');
	}

	/**
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

	private String replaceEmptyModifierPhrases(String text) {
		String replacement = REGEX_EMPTY_MODIFIER.matcher(text)
		    .replaceAll(" ").replace(" , ", " ").trim();

		if (replacement.endsWith(" ."))
			replacement = replacement.substring(0, replacement.length() - 2)
			    .trim();

		if (replacement.endsWith(","))
			replacement = replacement.substring(0, replacement.length() - 1)
			    .trim();

		if (!Character.isUpperCase(replacement.charAt(0)) &&
		    replacement.endsWith("."))
			replacement = replacement.substring(0, replacement.length() - 1)
			    .trim();

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
		return REGEX_SINGLE_LINEBREAK.matcher(
		    REGEX_LINEBREAK_SPACE.matcher(text).replaceAll("$1")).replaceAll(
		    " ");
	}

	/**
	 * Set the output stream according to the input stream's URL basename.
	 * 
	 * @param jCas of the input stream (raw)
	 * @throws CASException if fetching the stream fails
	 * @throws IOException if creating an output stream fails
	 */
	void setStream(JCas jCas) throws CASException, IOException {
		if (outputDirectory != null) {
			String inputName = (new File(jCas.getSofaDataURI())).getName();

			if (inputName == null || inputName.length() == 0)
				inputName = String.format("doc-%06d", ++counter);

			File outputFile = new File(outputDirectory, inputName + ".txt");

			if (!overwriteFiles && outputFile.exists()) {
				int idx = 2;

				while (outputFile.exists())
					outputFile = new File(outputDirectory, inputName + "." +
					                                       idx++ + ".txt");
			}

			if (encoding == null) {
				logger.log(Level.INFO, String.format(
				    "writing to '%s' using '%s' encoding", outputFile,
				    System.getProperty("file.encoding")));
				outputWriter = new OutputStreamWriter(new FileOutputStream(
				    outputFile));
			} else {
				logger.log(Level.INFO, String.format(
				    "writing to '%s' using '%s' encoding", outputFile,
				    encoding));
				outputWriter = new OutputStreamWriter(new FileOutputStream(
				    outputFile), encoding);
			}
		}
	}

	/**
	 * Close the output stream (if necessary).
	 * 
	 * @throws IOException if the stream could not be closed correctly
	 */
	void unsetStream() throws IOException {
		if (outputDirectory != null) {
			outputWriter.close();
		}
	}

	/**
	 * Write text to the output stream.
	 * 
	 * @param text to write
	 * @throws IOException if writing to the stream fails
	 */
	void write(String text) throws IOException {
		if (outputDirectory != null)
			outputWriter.write(text);

		if (printToStdout)
			System.out.print(text);
	}

}
