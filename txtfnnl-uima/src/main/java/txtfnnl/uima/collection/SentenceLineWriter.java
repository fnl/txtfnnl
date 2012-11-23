package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.regex.Pattern;

import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.utils.IOUtils;

/**
 * A CAS consumer that writes plain-text lines, adding line separators after
 * the character(s)
 * {@link txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator} detected
 * as sentence terminals.
 * 
 * This consumer has several optional configuration possibilities. With no
 * option chosen at all, output is written to <b>STDOUT</b>. But if
 * {@link #PARAM_PRINT_TO_STDOUT} was set to <code>False</code> and no output
 * directory was set either, this consumer would go silent.
 * <ul>
 * <li>{@link opennlp.uima.util.UimaUtil#SENTENCE_TYPE_PARAMETER} the sentence
 * annotation type to use (usually, "txtfnnl.uima.SyntaxAnnotation")</li>
 * <li>{@link #PARAM_OUTPUT_DIRECTORY} defines an output directory</li>
 * <li>{@link #PARAM_PRINT_TO_STDOUT} defines <b>STDOUT</b> as output</li>
 * <li>{@link #PARAM_OVERWRITE_FILES} allows overwriting of existing files</li>
 * <li>{@link #PARAM_JOIN_LINES} replaces newlines within sentences with
 * spaces</li>
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
public final class SentenceLineWriter extends CasAnnotator_ImplBase {

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

	/**
	 * <code>True</code> if multi-line sentences spanning <i>single</i>
	 * line-breaks should be joined to form one single line.
	 * 
	 * Double or more consecutive line-breaks are never joined. Detected
	 * line-breaks are only Windows (CR-LF) and Unix line-breaks (LF only).
	 */
	public static final String PARAM_JOIN_LINES = "JoinLines";

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
	private boolean joinLines = false;
	private String encoding = null;
	private String sentenceTypeName;
	private Writer outputWriter;

	private Logger logger;

	private int counter = 0;

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
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;
		
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
		Boolean joinLinesVal = (Boolean) ctx
		    .getConfigParameterValue(PARAM_JOIN_LINES);

		if (overwriteFilesVal != null && overwriteFilesVal)
			overwriteFiles = true;

		if (joinLinesVal != null && joinLinesVal)
			joinLines = true;

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

		String text = textJCas.getDocumentText();
		int offset = 0;

		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(textJCas, sentenceTypeName);

		while (sentenceIt.hasNext()) {
			SyntaxAnnotation ann = (SyntaxAnnotation) sentenceIt.next();
			String prefix = replaceAndTrimMultipleSpaces(text.substring(
			    offset, ann.getBegin()));
			String sentence = replaceAndTrimMultipleSpaces(text.substring(
			    ann.getBegin(), ann.getEnd()));

			try {
				if (prefix.length() > 0)
					write(prefix);

				if (joinLines) {
					write(replaceSingleBreaksAndSpaces(sentence));
					write(LINEBREAK);
				} else {
					for (String line : sentence.split("\\r?\\n")) {
						write(line);
						write(LINEBREAK);
					}
				}
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
			offset = ann.getEnd();
		}

		try {
			unsetStream();
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * @param text to replace spaces in
	 * @return the text without consecutive spaces and trimmed
	 */
	String replaceAndTrimMultipleSpaces(String text) {
		return REGEX_SPACES.matcher(text).replaceAll(" ").trim();
	}

	/**
	 * @param text to replace line-breaks in
	 * @return the text with single line-breaks trimmed
	 */
	String replaceSingleBreaksAndSpaces(String text) {
		return REGEX_SINGLE_LINEBREAK
		    .matcher(REGEX_LINEBREAK_SPACE.matcher(text).replaceAll("$1"))
		    .replaceAll(" ").trim();
	}

	/**
	 * Sets the handlers for this CAS used by the call to
	 * {@link #write(String)} according to the initial setup parameter
	 * choices.
	 * 
	 * @param jCas
	 * @throws CASException
	 * @throws IOException
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
	 * @throws AnalysisEngineProcessException
	 */
	void unsetStream() throws IOException {
		if (outputDirectory != null)
			outputWriter.close();
	}

	void write(String text) throws IOException {
		if (outputDirectory != null)
			outputWriter.write(text);

		if (printToStdout)
			System.out.print(text);
	}
}
