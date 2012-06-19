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
 * (indent/line-break the XML, use UTF-8; default: compact text using the platform's
 * encoding)</li>
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

	private File outputDir;

	private boolean formatXMI;

	private int counter; // to create "unique" output file names

	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		counter = 0;
		outputDir = new File(
		    (String) ctx.getConfigParameterValue(PARAM_OUTPUT_DIRECTORY));
		Boolean tmp = (Boolean) ctx.getConfigParameterValue(PARAM_FORMAT_XMI);
		formatXMI = (tmp == null) ? false : tmp.booleanValue();

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
		File outFile;

		try {
			File inFile = new File(new URI(uri));
			outFile = new File(outputDir, inFile.getName() + ".xmi");
		} catch (URISyntaxException e) {
			outFile = new File(outputDir, String.format("doc-%06d.xmi",
			    ++counter));
		} catch (NullPointerException e) {
			outFile = new File(outputDir, String.format("doc-%06d.xmi",
			    ++counter));
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
	 * @param aCas CAS to serialize
	 * @param file output file
	 * 
	 * @throws SAXException
	 * @throws IOException
	 */
	private void writeXmi(CAS aCas, File file) throws IOException,
	        SAXException {
		FileOutputStream out = null;

		try {
			out = new FileOutputStream(file);
			XmiCasSerializer trafo = new XmiCasSerializer(aCas.getTypeSystem());
			XMLSerializer xml = new XMLSerializer(out, formatXMI);
			trafo.serialize(aCas, xml.getContentHandler());
		} finally {
			if (out != null)
				out.close();
		}
	}

}
