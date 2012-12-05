package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * A CAS consumer that writes plain-text lines, adding line separators after
 * the segments annotated as {@link SentenceAnnotation}.
 * 
 * The output can be written to individual files or STDOUT.
 * 
 * <b>Note</b> that on <b>Apple OSX</b> the default encoding would be
 * MacRoman; however, this consumer uses either the encoding defined by the
 * <b>LANG</b> environment variable or otherwise defaults to <b>UTF-8</b> as a
 * far more sensible encoding on OSX instead.
 * 
 * @author Florian Leitner
 */
public class SentenceLineWriter extends TextWriter {

	/**
	 * If <code>true</code> (the default), <i>single</i> (non-consecutive)
	 * line-breaks within sentences will be joined together.
	 * 
	 * I.e., this option only makes sense if multi-line sentence splitting or
	 * no line-base splitting at all was used to annotate the sentences,
	 * because consecutive line-breaks are never joined. Detected line-breaks
	 * are only Windows (CR-LF) and Unix line-breaks (LF only).
	 */
	public static final String PARAM_JOIN_LINES = "JoinLines";
	@ConfigurationParameter(name = PARAM_JOIN_LINES, defaultValue = "true")
	private Boolean joinLines;

	static final Pattern REGEX_SPACES = Pattern.compile("[ \\t\\v\\f]+");
	static final Pattern REGEX_LINEBREAK_SPACE = Pattern.compile("(\\r?\\n) ");
	static final Pattern REGEX_SINGLE_LINEBREAK = Pattern
	    .compile("(?<!\\r?\\n)\\r?\\n(?!\\r?\\n)");
	static final String LINEBREAK = System.getProperty("line.separator");

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param encoding encoding to use for writing (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param joinLines whether to join single line-breaks in multi-line
	 *        sentences or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final File outputDirectory,
	                                                  final String encoding,
	                                                  final boolean printToStdout,
	                                                  final boolean overwriteFiles,
	                                                  final boolean joinLines)
	        throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(SentenceLineWriter.class,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(PARAM_OUTPUT_DIRECTORY, outputDirectory);
				    put(PARAM_ENCODING, encoding);
				    put(PARAM_PRINT_TO_STDOUT, printToStdout);
				    put(PARAM_OVERWRITE_FILES, overwriteFiles);
				    put(PARAM_JOIN_LINES, joinLines);

			    }
		    }));
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param encoding encoding to use for writing (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription
	        configure(File outputDirectory, String encoding, boolean printToStdout,
	                  boolean overwriteFiles) throws UIMAException, IOException {
		return configure(outputDirectory, encoding, printToStdout, overwriteFiles, true);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>overwriteFiles=false</li>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param encoding encoding to use for writing (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding,
	                                                  boolean printToStdout) throws UIMAException,
	        IOException {
		return configure(outputDirectory, encoding, printToStdout, false, true);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>printToStdout=false</li>
	 * <li>overwriteFiles=false</li>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory
	 * @param encoding encoding to use for writing (or null)
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding)
	        throws UIMAException, IOException {
		return configure(outputDirectory, encoding, false, false, true);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>encoding=NULL (use system default)</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param joinLines whether to join single line-breaks in multi-line
	 *        sentences or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, boolean printToStdout,
	                                                  boolean overwriteFiles, boolean joinLines)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, printToStdout, overwriteFiles, joinLines);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 *  
	 * Defaults:
	 * <ul>
	 * <li>encoding=NULL (use system default)</li>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, boolean printToStdout,
	                                                  boolean overwriteFiles)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, printToStdout, overwriteFiles, true);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code> and
	 * {@link #printToStdout} is <code>false</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>encoding=NULL (use system default)</li>
	 * <li>overwriteFiles=false</li>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, boolean printToStdout)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, printToStdout, false, true);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Note that if the {@link #outputDirectory} is <code>null</code>, a
	 * {@link ResourceInitializationException} will occur when creating the
	 * AE.
	 * 
	 * 
	 * Defaults:
	 * <ul>
	 * <li>encoding=NULL (use system default)</li>
	 * <li>printToStdout=false</li>
	 * <li>overwriteFiles=false</li>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory) throws UIMAException,
	        IOException {
		return configure(outputDirectory, null, false, false, true);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>outputDirectory=NULL (print to STDOUT)</li>
	 * <li>printToStdout=true (false makes no sense in this context)</li>
	 * <li>overwriteFiles=false (true makes no sense in this context)</li>
	 * </ul>
	 * 
	 * @param encoding encoding to use for writing (or null)
	 * @param joinLines whether to join single line-breaks in multi-line
	 *        sentences or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(String encoding, boolean joinLines)
	        throws UIMAException, IOException {
		return configure(null, encoding, true, false, joinLines);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>outputDirectory=NULL (print to STDOUT)</li>
	 * <li>encoding=NULL (use system default)</li>
	 * <li>printToStdout=true (false makes no sense in this context)</li>
	 * <li>overwriteFiles=false (true makes no sense in this context)</li>
	 * </ul>
	 * 
	 * @param joinLines whether to join single line-breaks in multi-line
	 *        sentences or not
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(boolean joinLines) throws UIMAException,
	        IOException {
		return configure(null, null, true, false, joinLines);
	}

	/**
	 * Configure an CAS consumer descriptor for a pipeline using all defaults.
	 * 
	 * Defaults:
	 * <ul>
	 * <li>outputDirectory=NULL (print to STDOUT)</li>
	 * <li>encoding=NULL (use system default)</li>
	 * <li>printToStdout=true (false makes no sense in this context)</li>
	 * <li>overwriteFiles=false (true makes no sense in this context)</li>
	 * <li>joinLines=true</li>
	 * </ul>
	 * 
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure() throws UIMAException, IOException {
		return configure(null, null, true, false, true);
	}

	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		JCas textJCas;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			setStream(cas.getView(Views.CONTENT_RAW.toString()));
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}

		String text = textJCas.getDocumentText();
		int offset = 0;

		FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);

		while (sentenceIt.hasNext()) {
			Annotation ann = sentenceIt.next();
			String prefix = replaceAndTrimMultipleSpaces(text.substring(offset, ann.getBegin()));
			String sentence = replaceAndTrimMultipleSpaces(text.substring(ann.getBegin(),
			    ann.getEnd()));

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
		    .matcher(REGEX_LINEBREAK_SPACE.matcher(text).replaceAll("$1")).replaceAll(" ").trim();
	}

}
