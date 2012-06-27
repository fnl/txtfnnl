package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
 * A simple CAS consumer that serializes to XMI and writes the XML data to a
 * file using UTF-8 encoding by default.
 * 
 * This CAS consumer has two parameters:
 * <ul>
 * <li><code>OutputDirectory</code> - path to directory into which the XMI
 * output files will be written; if the directory does not exist, it will be
 * created first</li>
 * <li><code>FormatXMI</code> - let the
 * {@link org.apache.uima.util.XMLSerializer XMLSerializer} format the XMI
 * (indent/line-break the XML, use UTF-8; default: compact text using the
 * platform's encoding)</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class FileSystemXmiWriter extends CasAnnotator_ImplBase {

	/**
	 * Name of configuration parameter that must be set to the path of a
	 * directory where the output files will be written to.
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";

	/**
	 * Name of optional, boolean configuration parameter that indicates the
	 * output should use standard XMI formatting.
	 * 
	 * Defaults to <code>false</code>, using the platform's encoding and no
	 * indented XML. If <code>true</code>, the output is indented XML encoded
	 * to UTF-8.
	 */
	public static final String PARAM_FORMAT_XMI = "FormatXMI";

	public static final String PARAM_OVERWRITE_FILES = "OverwriteFiles"; // TODO

	private File outputDir = null;

	private boolean formatXMI = false;

	private int counter = 0; // to create "unique" output file names

	private boolean overwriteFiles = false; // TODO

	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		counter = 0;
		outputDir = new File(
		    (String) ctx.getConfigParameterValue(PARAM_OUTPUT_DIRECTORY));
		Boolean tmp = (Boolean) ctx.getConfigParameterValue(PARAM_FORMAT_XMI);
		formatXMI = (tmp == null) ? false : tmp.booleanValue();
		tmp = (Boolean) ctx.getConfigParameterValue(PARAM_OVERWRITE_FILES);
		overwriteFiles = (tmp == null) ? false : tmp.booleanValue();

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
	 * @param aCas CAS to serialize
	 */
	public void process(CAS aCas) throws AnalysisEngineProcessException {
		String uri = aCas.getView(Views.CONTENT_RAW.toString())
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

		if (!overwriteFiles) {
			int idx = 2;

			while (outFile.exists())
				outFile = new File(outputDir, outFileBaseName + "." + idx++ +
				                              ".xmi");
		}

		try {
			writeXmi(aCas.getView(Views.CONTENT_TEXT.toString()), outFile);
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
	private void writeXmi(CAS cas, File file) throws IOException,
	        SAXException {
		FileOutputStream out = null;

		try {
			out = new FileOutputStream(file);
			XmiCasSerializer xmi = new XmiCasSerializer(cas.getTypeSystem());
			XMLSerializer xml = new XMLSerializer(out, formatXMI);
			xmi.serialize(cas, xml.getContentHandler());
		} finally {
			if (out != null)
				out.close();
		}
	}

}
