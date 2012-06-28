package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.transform.OutputKeys;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.XMLSerializer;

import org.xml.sax.SAXException;

import txtfnnl.uima.Views;

/**
 * A CAS consumer that serializes to XMI and writes the XML data to a file
 * using the platform-specific encoding by default.
 * 
 * This CAS consumer has the following parameters:
 * <ul>
 * <li><code>String {@link #PARAM_OUTPUT_DIRECTORY}</code> (required)</li>
 * <li><code>Boolean {@link #PARAM_FORMAT_XMI}</code> (optional)</li>
 * <li><code>Boolean {@link #PARAM_USE_XML_11}</code> (optional)</li>
 * <li><code>Boolean {@link #PARAM_OVERWRITE_FILES}</code> (optional)</li>
 * <li><code>String {@link #PARAM_ENCODING}</code> (optional)</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class FileSystemXmiWriter extends CasAnnotator_ImplBase {

	/**
	 * Required configuration parameter String that defines the path to a
	 * directory where the output files will be written.
	 * 
	 * Note that the directory will be created if it does not exist.
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";

	/**
	 * Optional Boolean parameter that indicates the output should use
	 * standard XMI formatting.
	 * 
	 * Defaults to <code>false</code>, using the platform's encoding and no
	 * indented XML. If <code>true</code>, the output is indented XML encoded
	 * to UTF-8.
	 * 
	 * @see org.apache.uima.util.XMLSerializer
	 */
	public static final String PARAM_FORMAT_XMI = "FormatXmi";

	/** Serialize to XML 1.1 (all Unicode characters allowed). */
	public static final String PARAM_USE_XML_11 = "UseXml11";

	/**
	 * Optional Boolean to signal the overwriting of existing files.
	 * 
	 * Instead of inserting ".<i>n</i>" between the file name and its new
	 * ".xmi" suffix to make a file unique, the existing file is replaced
	 * (where <i>n</i> is some integer that would make the file name
	 * "unique").
	 */
	public static final String PARAM_OVERWRITE_FILES = "OverwriteFiles";

	/**
	 * Serialize the XML to a specific encoding (default: platform-dependent).
	 */
	public static final String PARAM_ENCODING = "Encoding";

	private boolean formatXmi = false;
	private boolean overwriteFiles = false;
	private boolean useXml11 = false;
	private int counter = 0; // to create "unique" output file names
	private File outputDir = null;
	private String encoding = null;

	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		counter = 0;

		try {
			outputDir = new File(
			    (String) ctx.getConfigParameterValue(PARAM_OUTPUT_DIRECTORY));
		} catch (NullPointerException e) {
			throw new ResourceInitializationException(
			    ResourceInitializationException.CONFIG_SETTING_ABSENT,
			    new Object[] { PARAM_OUTPUT_DIRECTORY });
		}

		encoding = (String) ctx.getConfigParameterValue(PARAM_ENCODING);

		Boolean tmp = (Boolean) ctx.getConfigParameterValue(PARAM_FORMAT_XMI);
		formatXmi = (tmp == null) ? false : tmp.booleanValue();

		tmp = (Boolean) ctx.getConfigParameterValue(PARAM_OVERWRITE_FILES);
		overwriteFiles = (tmp == null) ? false : tmp.booleanValue();

		tmp = (Boolean) ctx.getConfigParameterValue(PARAM_USE_XML_11);
		useXml11 = (tmp == null) ? false : tmp.booleanValue();

		if (!outputDir.exists())
			outputDir.mkdirs();

		if (!(outputDir.isDirectory() && outputDir.canWrite()))
			throw new ResourceInitializationException(new IOException(
			    PARAM_OUTPUT_DIRECTORY + "='" + outputDir +
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
		String uri = cas.getView(Views.CONTENT_RAW.toString())
		    .getSofaDataURI();
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
				outFile = new File(outputDir, outFileBaseName + "." + idx++ +
				                              ".xmi");
		}

		try {
			writeXmi(cas.getView(Views.CONTENT_TEXT.toString()), outFile);
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
