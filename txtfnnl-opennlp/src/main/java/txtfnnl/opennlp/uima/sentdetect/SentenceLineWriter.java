package txtfnnl.opennlp.uima.sentdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An CPE LineWriter for sentences detected by the SentenceAnnotator.
 * 
 * <p>
 * Mandatory parameters (same as original parameters)
 * <table border=1>
 * <tr>
 * <th>Type</th>
 * <th>Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>String</td>
 * <td>opennlp.uima.ModelName</td>
 * <td>The name (key) of the sentence model resource (e.g.,
 * "EnglishSentenceModelResource").</td>
 * </tr>
 * <tr>
 * <td>String</td>
 * <td>opennlp.uima.SentenceType</td>
 * <td>The full name of the sentence annotation type (usually,
 * "txtfnnl.uima.SyntaxAnnotation"). Note that this AE assumes the chosen
 * annotation type has the features "annotator", "confidence", "identifier",
 * and "namespace".</td>
 * </tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <tr>
 * <th>Type</th>
 * <th>Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>String</td>
 * <td>opennlp.uima.ContainerType</td>
 * <td>The name of the container type (default: use the entire SOFA).</td>
 * </tr>
 * <tr>
 * <td>Boolean</td>
 * <td>opennlp.uima.IsRemoveExistingAnnotations</td>
 * <td>Remove existing annotations (from the container) before processing the
 * CAS.</td>
 * </tr>
 * </table>
 * 
 * @author Florian Leitner
 */
public final class SentenceLineWriter extends CasAnnotator_ImplBase {

	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";

	public static final String PARAM_PRINT_TO_STDOUT = "PrintToStdout";

	public static final String PARAM_OVERWRITE_FILES = "OverwriteFiles";

	public static final String PARAM_ENCODING = "Encoding";

	private File outputDirectory = null;
	private boolean printToStdout = false;
	private boolean overwriteFiles = false;
	private String encoding = null;
	private Writer outputWriter;

	private Feature identifier;
	private Feature namespace;
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
			logger.log(
			    Level.INFO,
			    "writing to STDOUT using '" +
			            System.getProperty("file.encoding") + "'");

			// fix broken Mac JDK that uses MacRoman instead of the LANG
			// setting as default encoding
			if (encoding == null &&
			    System.getProperty("os.name").toLowerCase().startsWith("mac")) {
				String fixedEncoding = "utf-8";
				String lang = System.getenv("LANG");

				if (lang != null && lang.lastIndexOf('.') > -1) {
					fixedEncoding = lang.substring(lang.lastIndexOf('.') + 1);
				}
				try {
					System.setOut(new PrintStream(System.out, true,
					    fixedEncoding));
					logger.log(Level.INFO, "fixed Mac STDOUT to use '" +
					                       fixedEncoding +
					                       "' instead of MacRoman");
				} catch (UnsupportedEncodingException e) {
					throw new ResourceInitializationException(e);
				}
			} else if (encoding != null) {
				try {
					System.setOut(new PrintStream(System.out, true, encoding));
				} catch (UnsupportedEncodingException e) {
					throw new ResourceInitializationException(e);
				}
				logger.log(Level.INFO, "set STDOUT to use '" + encoding +
				                       "' encoding");
			}
		}

		Boolean overwriteFilesVal = (Boolean) ctx
		    .getConfigParameterValue(PARAM_OVERWRITE_FILES);

		if (overwriteFilesVal != null && overwriteFilesVal)
			overwriteFiles = true;

		counter = 0;
	}

	@Override
	public void typeSystemInit(TypeSystem typeSystem)
	        throws AnalysisEngineProcessException {
		String textAnnotationName = SyntaxAnnotation.class.getName();
		identifier = typeSystem.getFeatureByFullName(textAnnotationName +
		                                             ":identifier");
		namespace = typeSystem.getFeatureByFullName(textAnnotationName +
		                                            ":namespace");

		if (identifier == null)
			throw new AnalysisEngineProcessException(new AssertionError(
			    textAnnotationName + ":identifier feature not found"));

		if (namespace == null)
			throw new AnalysisEngineProcessException(new AssertionError(
			    textAnnotationName + ":namespace feature not found"));
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

		for (Annotation ann : textJCas.getAnnotationIndex(SyntaxAnnotation.type)) {
			if (ann.getStringValue(identifier).equals(
			    SentenceAnnotator.IDENTIFIER) &&
			    ann.getStringValue(namespace).equals(
			        SentenceAnnotator.NAMESPACE)) {
				try {
					for (String line : text.substring(offset, ann.getEnd())
					    .split("\\r?\\n")) {
						write(line.replace('\t', ' ').trim());
						write(System.getProperty("line.separator"));
					}
				} catch (IOException e) {
					throw new AnalysisEngineProcessException(e);
				}
				offset = ann.getEnd();
			}
		}
		
		if (outputDirectory != null) {
	        try {
	            outputWriter.close();
            } catch (IOException e) {
            	throw new AnalysisEngineProcessException(e);
            }
		}
	}

	void setStream(JCas jCas) throws CASException, IOException {
		if (outputDirectory != null) {
			String inputName = (new File(jCas.getSofaDataURI())).getName();

			if (inputName == null || inputName.length() == 0)
				inputName = String.format("doc-%06d", ++counter);

			File outputFile = new File(outputDirectory, inputName + ".txt");

			if (!overwriteFiles) {
				int idx = 2;

				while (outputFile.exists())
					outputFile = new File(outputDirectory, inputName + "." +
					                                       idx++ + ".txt");
			}

			logger.log(
			    Level.INFO,
			    "writing to '" +
			            outputFile.getAbsolutePath() +
			            "' using '" +
			            (encoding == null ? System
			                .getProperty("file.encoding") : encoding) +
			            "' encoding");

			if (encoding == null)
				outputWriter = new OutputStreamWriter(new FileOutputStream(
				    outputFile));
			else
				outputWriter = new OutputStreamWriter(new FileOutputStream(
				    outputFile), encoding);
		}
	}

	void write(String text) throws IOException {
		if (outputDirectory != null)
			outputWriter.write(text);

		if (printToStdout)
			System.out.print(text);
	}
}
