package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.HashMap;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.CasConsumer_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.utils.UIMAUtils;
import txtfnnl.utils.IOUtils;

/**
 * A CAS consumer that writes the document text only.
 * 
 * @author Florian Leitner
 */
public class TextWriter extends CasConsumer_ImplBase {

	/**
	 * Optional configuration parameter String that defines the path to a
	 * directory where the output files will be written.
	 * 
	 * Note that the directory will be created if it does not exist.
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";
	@ConfigurationParameter(name = PARAM_OUTPUT_DIRECTORY)
	protected File outputDirectory;

	/**
	 * Optional flag leading to the overwriting any existing files; defaults
	 * to <code>false</code>.
	 * 
	 * Instead of inserting ".<i>n</i>" between the file name and its new
	 * ".xmi" suffix to make a file unique, the existing file is replaced
	 * (where <i>n</i> is some integer that would make the file name
	 * "unique").
	 */
	public static final String PARAM_OVERWRITE_FILES = "OverwriteFiles";
	@ConfigurationParameter(name = PARAM_OVERWRITE_FILES, defaultValue = "false")
	protected boolean overwriteFiles;

	/**
	 * If <code>true</code>, the output will (also) be written to
	 * <b>STDOUT</b>.
	 * 
	 * By default, STDOUT is not written.
	 */
	public static final String PARAM_PRINT_TO_STDOUT = "PrintToStdout";
	@ConfigurationParameter(name = PARAM_PRINT_TO_STDOUT, defaultValue = "false")
	protected Boolean printToStdout;

	/** Force a particular output encoding. */
	public static final String PARAM_ENCODING = "Encoding";
	@ConfigurationParameter(name = PARAM_ENCODING)
	protected String encoding;

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
	 * @return a configured AE description
	 * @throws IOException
	 * @throws UIMAException
	 */
	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final File outputDirectory,
	                                                  final String encoding,
	                                                  final boolean printToStdout,
	                                                  final boolean overwriteFiles)
	        throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(TextWriter.class,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(PARAM_OUTPUT_DIRECTORY, outputDirectory);
				    put(PARAM_ENCODING, encoding);
				    put(PARAM_PRINT_TO_STDOUT, printToStdout);
				    put(PARAM_OVERWRITE_FILES, overwriteFiles);

			    }
		    }));
	}

	public static AnalysisEngineDescription configure(File outputDirectory, String encoding,
	                                                  boolean overwriteFiles)
	        throws UIMAException, IOException {
		return configure(outputDirectory, encoding, false, overwriteFiles);
	}

	public static AnalysisEngineDescription configure(String encoding) throws UIMAException,
	        IOException {
		return configure(null, encoding, true, false);
	}

	public static AnalysisEngineDescription configure() throws UIMAException, IOException {
		return configure(null);
	}

	protected int counter; // to create "new" output file names if necessary
	protected Logger logger;
	protected Writer outputWriter;

	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();

		if (outputDirectory != null &&
		    (!outputDirectory.isDirectory() || !outputDirectory.canWrite()))
			throw new ResourceInitializationException(new IOException(
			    "'" + outputDirectory.getAbsolutePath() + "' not a writeable directory"));

		if (!printToStdout && outputDirectory == null)
			throw new ResourceInitializationException(new AssertionError(
			    "no output defined (neither directory or STDOUT specified)"));

		if (printToStdout) {
			if (encoding == null && IOUtils.isMacOSX()) {
				// fix broken Mac JDK that uses MacRoman instead of the LANG
				// setting as default encoding; if LANG is not set, use UTF-8.
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
				logger.log(Level.INFO, "set STDOUT to use '" + encoding + "' encoding");
		}

		counter = 0;
	}

	public void process(CAS cas) throws AnalysisEngineProcessException {
		// TODO: use default views?
		JCas textJCas;
		CAS rawCas;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			rawCas = cas.getView(Views.CONTENT_RAW.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		try {
			setStream(rawCas);
			String text = textJCas.getDocumentText();

			if (outputDirectory != null)
				try {
					outputWriter.write(text);
				} catch (IOException e) {
					throw new AnalysisEngineProcessException(e);
				}

			if (printToStdout)
				System.out.print(text);

			unsetStream();

		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * Sets the handlers for this CAS used by the call to
	 * {@link #write(String)} according to the initial setup parameter
	 * choices.
	 * 
	 * @param doc the current CAS being processed
	 * @throws CASException
	 * @throws IOException
	 */
	void setStream(CAS doc) throws IOException {
		if (outputDirectory != null) {
			String inputName = (new File(doc.getSofaDataURI())).getName();

			if (inputName == null || inputName.length() == 0)
				inputName = String.format("doc-%06d", ++counter);

			File outputFile = new File(outputDirectory, inputName + ".txt");

			if (!overwriteFiles && outputFile.exists()) {
				int idx = 2;

				while (outputFile.exists())
					outputFile = new File(outputDirectory, inputName + "." + idx++ + ".txt");
			}

			if (encoding == null) {
				logger.log(
				    Level.INFO,
				    String.format("writing to '%s' using '%s' encoding", outputFile,
				        System.getProperty("file.encoding")));
				outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile));
			} else {
				logger.log(Level.INFO,
				    String.format("writing to '%s' using '%s' encoding", outputFile, encoding));
				outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile), encoding);
			}
		}
	}

	/**
	 * Close the currently open output file handle (if any).
	 * 
	 * @throws IOException
	 */
	void unsetStream() throws IOException {
		if (outputDirectory != null)
			outputWriter.close();
	}
	
	/**
	 * Write a string to the output stream(s).
	 * 
	 * @param ch to write
	 * @throws IOException
	 */
	void write(String text) throws IOException {
		if (outputDirectory != null)
			outputWriter.write(text);

		if (printToStdout) {
			System.out.print(text);
		}
	}

	/**
	 * Write a single character to the output stream(s).
	 * 
	 * @param ch to write
	 * @throws IOException
	 */
	void write(char ch) throws IOException {
		if (outputDirectory != null)
			outputWriter.append(ch);

		if (printToStdout)
			System.out.append(ch);
	}
}
