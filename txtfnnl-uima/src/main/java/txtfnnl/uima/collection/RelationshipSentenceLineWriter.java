package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Offset;
import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
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
public final class RelationshipSentenceLineWriter extends
        CasAnnotator_ImplBase {

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
	static final String LINEBREAK = System.getProperty("line.separator");

	// parameter settings
	private File outputDirectory = null;
	private boolean printToStdout = false;
	private boolean overwriteFiles = false;
	private String encoding = null;
	private String sentenceTypeName;
	private Writer outputWriter;

	private Logger logger;

	private int counter = 0;

	private String relationshipNamespace;

	/**
	 * Replace &lt;, &gt; and &amp; with their respective escape sequences.
	 * 
	 * @param raw string to escape
	 * @return a string with the relevant characters escaped
	 */
	public static String escapeHTML(String raw) {
		return raw.replace("<", "&lt;").replace(">", "&gt;")
		    .replace("&", "&amp;");
	}

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

		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(UimaUtil.SENTENCE_TYPE_PARAMETER);

		if (sentenceTypeName == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.CONFIG_SETTING_ABSENT,
			    new Object[] { UimaUtil.SENTENCE_TYPE_PARAMETER });

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
				// always
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

		System.out.println("<!DOCTYPE html>\n<html>\n<head>\n<meta " +
		                   "http-equiv=\"Content-Type\" " +
		                   "content=\"text/html; charset=" +
		                   ((encoding == null)
		                           ? Charset.defaultCharset()
		                           : encoding) +
		                   "\" />\n</head>\n<body>\n<ol>\n");
		counter = 0;
	}

	/**
	 * Detect sentences in the {@link Views.CONTENT_TEXT} view of a CAS.
	 */
	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		JCas textJCas, rawJCas;
		String sofaURI;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			rawJCas = cas.getView(Views.CONTENT_RAW.toString()).getJCas();
			setStream(rawJCas);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}

		sofaURI = rawJCas.getSofaDataURI();

		FSIterator<TOP> relationshipIt = KnownRelationshipAnnotator
		    .getRelationshipIterator(textJCas, relationshipNamespace);
		String text = textJCas.getDocumentText();
		// {sentence_offset: [{entity_type: [entity_offset, ...]}, ...]}
		Map<Offset, List<Map<String, Set<Offset>>>> relationshipMappings = new HashMap<Offset, List<Map<String, Set<Offset>>>>();
		// {entity_offset: {entity_type: [entity_ann, ...]}}
		Map<Offset, Map<String, Set<Entity>>> entityMappings = new HashMap<Offset, Map<String, Set<Entity>>>();

		while (relationshipIt.hasNext()) {
			RelationshipAnnotation relAnn = (RelationshipAnnotation) relationshipIt
			    .next();
			Offset sentOff = ((SyntaxAnnotation) relAnn.getSources(0))
			    .getOffset();
			List<Map<String, Set<Offset>>> relationshipList;
			Map<String, Set<Offset>> relationship = new HashMap<String, Set<Offset>>();
			FSArray entityAnnFS = relAnn.getTargets();

			if (relationshipMappings.containsKey(sentOff)) {
				relationshipList = relationshipMappings.get(sentOff);
			} else {
				relationshipList = new LinkedList<Map<String, Set<Offset>>>();
				relationshipMappings.put(sentOff, relationshipList);
			}

			for (int i = entityAnnFS.size(); i-- > 0;) {
				SemanticAnnotation entityAnn = (SemanticAnnotation) entityAnnFS
				    .get(i);
				Entity entity = new Entity(entityAnn.getIdentifier(),
				    entityAnn.getProperties(0).getValue(), entityAnn
				        .getProperties(1).getValue());
				Offset entityOff = entityAnn.getOffset();
				Set<Entity> entitySet;
				Map<String, Set<Entity>> entityTypeMap;
				Set<Offset> offsets;

				if (relationship.containsKey(entity.getType())) {
					offsets = relationship.get(entity.getType());
				} else {
					offsets = new HashSet<Offset>();
					relationship.put(entity.getType(), offsets);
				}

				offsets.add(entityOff);

				if (entityMappings.containsKey(entityOff)) {
					entityTypeMap = entityMappings.get(entityOff);
				} else {
					entityTypeMap = new HashMap<String, Set<Entity>>();
					entityMappings.put(entityOff, entityTypeMap);
				}

				if (entityTypeMap.containsKey(entity.getType())) {
					entitySet = entityTypeMap.get(entity.getType());
				} else {
					entitySet = new HashSet<Entity>();
					entityTypeMap.put(entity.getType(), entitySet);
				}

				entitySet.add(entity);
			}

			if (!relationshipList.contains(relationship))
				relationshipList.add(relationship);
		}

		nextSent: for (Offset sentOff : relationshipMappings.keySet()) {
			nextRel: for (Map<String, Set<Offset>> relationship : relationshipMappings
			    .get(sentOff)) {
				Queue<Integer> entityEndQueue = new PriorityQueue<Integer>();
				Offset lastOffset = null;
				StringBuilder sentence = new StringBuilder(
				    "<li class='sentence'>");
				int pos = sentOff.start();
				SortedSet<Offset> sortedEntityOffsets = new TreeSet<Offset>();

				for (Set<Offset> offsets : relationship.values())
					sortedEntityOffsets.addAll(offsets);

				for (Offset entityOff : sortedEntityOffsets) {
					if (lastOffset != null &&
					    lastOffset.end() > entityOff.start() &&
					    lastOffset.end() < entityOff.end()) {
						logger.log(Level.WARNING,
						    "ignored relationship with partially overlapping "
						            + "annotations {0} and {1} in ''{2}''",
						    new Object[] { lastOffset, entityOff, sofaURI });
						logger.log(Level.FINE,
						    "ignored relationship was in ''{0}''",
						    text.substring(sentOff.start(), sentOff.end()));
						continue nextRel;
					}

					Map<String, Set<Entity>> entityMap = entityMappings
					    .get(entityOff);

					while (!entityEndQueue.isEmpty() &&
					       entityEndQueue.peek() <= entityOff.start())
						pos = appendClosingTag(sentence, text, pos,
						    entityEndQueue);

					sentence.append(clean(text, pos, entityOff.start()));
					sentence.append("<span class='");

					for (String entityType : entityMap.keySet()) {
						sentence.append(entityType);
						sentence.append(' ');
					}

					sentence.deleteCharAt(sentence.length() - 1);
					sentence.append("' title='");

					for (Set<Entity> entitySet : entityMap.values()) {
						for (Entity e : entitySet) {
							sentence.append(e.toString());
							sentence.append(", ");
						}
					}

					sentence.delete(sentence.length() - 2, sentence.length());
					sentence.append("'>");
					entityEndQueue.add(entityOff.end());
					pos = entityOff.start();
				}

				while (!entityEndQueue.isEmpty())
					pos = appendClosingTag(sentence, text, pos, entityEndQueue);

				sentence.append(clean(text, pos, sentOff.end()));

				// skip sentences that do not fit on a single line
				// because they most likely are not real sentences
				if (sentence.toString().indexOf('\n') != -1) {
					logger.log(Level.FINE,
					    "skipping multi-line sentence in ''{0}''", sofaURI);
					logger.log(Level.FINER, "sentence was ''{0}''",
					    text.substring(sentOff.start(), sentOff.end()));
					continue nextSent;
				} else {
					sentence.append('\n');

					try {
						write(sentence.toString());
					} catch (IOException e) {
						throw new AnalysisEngineProcessException(e);
					}
				}
			}
		}

		try {
			unsetStream();
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	public void destroy() {
		super.destroy();

		if (printToStdout)
			System.out.println("</ol>\n</body>\n</html>\n");
	}

	private int appendClosingTag(StringBuilder sentence, String text, int pos,
	                             Queue<Integer> entityEndQueue) {
		int endPos = entityEndQueue.poll();
		sentence.append(clean(text, pos, endPos));
		sentence.append("</span>");
		pos = endPos;
		return pos;
	}

	private String clean(String text, int begin, int end) {
		return escapeHTML(replaceSingleBreaksAndSpaces(replaceAndTrimMultipleSpaces(text
		    .substring(begin, end))));
	}

	/**
	 * Replace multiple white-spaces with a single space.
	 * 
	 * @param text to replace spaces in
	 * @return the text without consecutive spaces and trimmed
	 */
	String replaceAndTrimMultipleSpaces(String text) {
		return REGEX_SPACES.matcher(text).replaceAll(" ");
	}

	/**
	 * Replace single line breaks with a white-space.
	 * 
	 * @param text to replace line-breaks in
	 * @return the text with single line-breaks trimmed
	 */
	String replaceSingleBreaksAndSpaces(String text) {
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

			outputWriter.write("<!DOCTYPE html>\n<html>\n<head>\n<meta " +
			                   "http-equiv=\"Content-Type\" " +
			                   "content=\"text/html; charset=" +
			                   ((encoding == null) ? System
			                       .getProperty("file.encoding") : encoding) +
			                   "\" />\n</head>\n<body>\n<ol>\n");
		}
	}

	/**
	 * Close the output stream (if necessary).
	 * 
	 * @throws IOException if the stream could not be closed correctly
	 */
	void unsetStream() throws IOException {
		if (outputDirectory != null) {
			outputWriter.write("</ol>\n</body>\n</html>\n");
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
