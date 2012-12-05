package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import javax.xml.transform.OutputKeys;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.XMLSerializer;

import org.xml.sax.SAXException;

import org.uimafit.component.CasConsumer_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * A CAS consumer that serializes the documents to XMI.
 * 
 * @author Florian Leitner
 */
public class XmiWriter extends CasConsumer_ImplBase {

	/**
	 * Required configuration parameter String that defines the path to a
	 * directory where the output files will be written.
	 * 
	 * Note that the directory will be created if it does not exist.
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";
	@ConfigurationParameter(name = PARAM_OUTPUT_DIRECTORY)
	private File outputDir;

	/**
	 * Optional Boolean parameter that indicates the output should use
	 * standard XMI formatting; defaults to <code>false</code>.
	 * 
	 * @see org.apache.uima.util.XMLSerializer
	 */
	public static final String PARAM_FORMAT_XMI = "FormatXmi";
	@ConfigurationParameter(name = PARAM_FORMAT_XMI, defaultValue = "false")
	private boolean formatXmi;

	/**
	 * Serialize to XML 1.1 (all Unicode characters allowed); defaults to
	 * <code>true</code>.
	 */
	public static final String PARAM_USE_XML_11 = "UseXml11";
	@ConfigurationParameter(name = PARAM_USE_XML_11, defaultValue = "true")
	private boolean useXml11;

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
	private boolean overwriteFiles;

	/**
	 * Serialize the XML to a specific encoding (default: platform-dependent).
	 */
	public static final String PARAM_ENCODING = "Encoding";
	@ConfigurationParameter(name = PARAM_ENCODING)
	private String encoding;

	private int counter = 0; // to create "new" output file names if necessary

	/**
	 * Configure a CAS consumer descriptor for a pipeline.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param encoding encoding to use for writing (or null)
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param formatXmi whether to created formated XMI or not
	 * @param useXml11 whether to use XML 1.1 or 1.0
	 * @throws IOException
	 * @throws UIMAException
	 */
	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final File outputDirectory,
	                                                  final String encoding,
	                                                  final boolean overwriteFiles,
	                                                  final boolean formatXmi,
	                                                  final boolean useXml11)
	        throws UIMAException, IOException {

		return AnalysisEngineFactory.createPrimitiveDescription(XmiWriter.class,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(PARAM_OUTPUT_DIRECTORY, outputDirectory);
				    put(PARAM_ENCODING, encoding);
				    put(PARAM_OVERWRITE_FILES, overwriteFiles);
				    put(PARAM_FORMAT_XMI, formatXmi);
				    put(PARAM_USE_XML_11, useXml11);
			    }
		    }));
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using the
	 * default encoding.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param formatXmi whether to created formated XMI or not
	 * @param useXml11 whether to use XML 1.1 or 1.0
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory,
	                                                  boolean overwriteFiles, boolean formatXmi,
	                                                  boolean useXml11) throws UIMAException,
	        IOException {
		return configure(outputDirectory, null, overwriteFiles, formatXmi, useXml11);
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using XML 1.1.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param encoding encoding to use for writing (or null)
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param formatXmi whether to created formated XMI or not
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding,
	                                                  boolean overwriteFiles, boolean formatXmi)
	        throws UIMAException, IOException {
		return configure(outputDirectory, encoding, overwriteFiles, formatXmi, true);
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using XML 1.1
	 * and the default encoding.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @param formatXmi whether to created formated XMI or not
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory,
	                                                  boolean overwriteFiles, boolean formatXmi)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, overwriteFiles, formatXmi);
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using XML 1.1.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param encoding encoding to use for writing (or null)
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding,
	                                                  boolean overwriteFiles)
	        throws UIMAException, IOException {
		return configure(outputDirectory, encoding, overwriteFiles, false);
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using XML 1.1
	 * and the default system encoding.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory,
	                                                  boolean overwriteFiles)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, overwriteFiles);
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using XML 1.1.
	 * 
	 * @param outputDirectory (mandatory)
	 * @param encoding encoding to use for writing (or null)
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding)
	        throws UIMAException, IOException {
		return configure(outputDirectory, encoding, false);
	}

	/**
	 * Configure a SentenceLineWriter consumer for a pipeline using XML 1.1
	 * and the default system encoding.
	 * 
	 * @param outputDirectory (mandatory)
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null);
	}

	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);

		if (!outputDir.exists())
			outputDir.mkdirs();

		if (!(outputDir.isDirectory() && outputDir.canWrite()))
			throw new ResourceInitializationException(
			    new IOException(PARAM_OUTPUT_DIRECTORY + "='" + outputDir +
			                    "' not a writeable directory"));
	}

	/**
	 * Consume a CAS to produce a file in the XML Metadata Interchange format.
	 * 
	 * This consumer expects the CAS to have both a raw and a text view. The
	 * file URI is fetched from the raw view, while the XMI content is created
	 * from the text view.
	 * 
	 * @param cas CAS to serialize
	 */
	public void process(CAS cas) throws AnalysisEngineProcessException {
		String uri = cas.getView(Views.CONTENT_RAW.toString()).getSofaDataURI();
		String outFileBaseName;
		File outFile;

		try {
			File inFile = new File(new URI(uri));
			outFileBaseName = inFile.getName();
		} catch (URISyntaxException e) {
			outFileBaseName = String.format("doc-%06d", ++counter);
		} catch (NullPointerException e) {
			outFileBaseName = String.format("doc-%06d", ++counter);
		}
		outFile = new File(outputDir, outFileBaseName + ".xmi");

		if (!overwriteFiles && outFile.exists()) {
			int idx = 2;

			while (outFile.exists())
				outFile = new File(outputDir, outFileBaseName + "." + idx++ + ".xmi");
		}

		try {
			writeXmi(cas, outFile);
		} catch (SAXException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * Serialize a CAS to a file in XMI format.
	 * 
	 * @param cas CAS to serialize
	 * @param file output file
	 * 
	 * @throws SAXException
	 * @throws IOException
	 */
	private void writeXmi(CAS cas, File file) throws IOException, SAXException {
		FileOutputStream out = null;

		try {
			out = new FileOutputStream(file);
			XmiCasSerializer xmi = new XmiCasSerializer(cas.getTypeSystem());
			XMLSerializer xml = new XMLSerializer(out, formatXmi);

			if (useXml11)
				xml.setOutputProperty(OutputKeys.VERSION, "1.1");

			if (encoding != null)
				xml.setOutputProperty(OutputKeys.ENCODING, encoding);

			xmi.serialize(cas, xml.getContentHandler());
		} finally {
			if (out != null)
				out.close();
		}
	}

}
