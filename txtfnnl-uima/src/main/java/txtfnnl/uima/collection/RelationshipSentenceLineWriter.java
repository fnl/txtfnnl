package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
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

		counter = 0;
	}

	/**
	 * Detect sentences in the {@link Views.CONTENT_TEXT} view of a CAS.
	 */
	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		JCas textJCas;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			setStream(cas.getView(Views.CONTENT_RAW.toString()).getJCas());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSIterator<TOP> relationshipIt = KnownRelationshipAnnotator
		    .getRelationshipIterator(textJCas, relationshipNamespace);
		String text = textJCas.getDocumentText();

		try {
			while (relationshipIt.hasNext()) {
				RelationshipAnnotation relAnn = (RelationshipAnnotation) relationshipIt
				    .next();
				SyntaxAnnotation sentAnn = (SyntaxAnnotation) relAnn
				    .getSources(0);
				String sentence = replaceSingleBreaksAndSpaces(replaceAndTrimMultipleSpaces(sentAnn
				    .getCoveredText()));
				
				if (sentence.indexOf('\n') != -1)
					continue;
				
				sentence.replace("<", "&lt;");
				sentence.replace(">", "&gt;");
				FSArray entities = relAnn.getTargets();
				Map<Offset, String> replacements = new HashMap<Offset, String>();
				
				for (int i = entities.size(); i-- > 0;) {
					SemanticAnnotation entityAnn = (SemanticAnnotation) entities.get(i);
					Offset off = entityAnn.getOffset();
					
					if (replacements.containsKey(off)) {
						String repl = replacements.get(off);
						
						if (!repl.contains(entityAnn.getNamespace()))
							replacements.put(off, repl + "+" + entityAnn.getNamespace());
					} else {
						replacements.put(off, entityAnn.getIdentifier());
					}
				}
				
				SortedSet<Offset> sortedOffsets = new TreeSet<Offset>(replacements.keySet());
				StringBuilder masked = new StringBuilder("<sentence>");
				int pos = sentAnn.getBegin();
				
				for (Offset off : sortedOffsets) {
					masked.append(text.substring(pos, off.start()));
					masked.append('<');
					masked.append(replacements.get(off));
					masked.append('>');
					masked.append(text.substring(off.start(), off.end()));
					masked.append("</");
					masked.append(replacements.get(off));
					masked.append('>');
					pos = off.end();
				}
				
				masked.append(text.substring(pos, sentAnn.getEnd()));
				masked.append("</sentence>\n");
				write(masked.toString());
			}

			unsetStream();
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * Replace multiple white-spaces with a single space.
	 * 
	 * @param text to replace spaces in
	 * @return the text without consecutive spaces and trimmed
	 */
	String replaceAndTrimMultipleSpaces(String text) {
		return REGEX_SPACES.matcher(text).replaceAll(" ").trim();
	}

	/**
	 * Replace single line breaks with a white-space.
	 * 
	 * @param text to replace line-breaks in
	 * @return the text with single line-breaks trimmed
	 */
	String replaceSingleBreaksAndSpaces(String text) {
		return REGEX_SINGLE_LINEBREAK
		    .matcher(REGEX_LINEBREAK_SPACE.matcher(text).replaceAll("$1"))
		    .replaceAll(" ").trim();
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
		if (outputDirectory != null)
			outputWriter.close();
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
