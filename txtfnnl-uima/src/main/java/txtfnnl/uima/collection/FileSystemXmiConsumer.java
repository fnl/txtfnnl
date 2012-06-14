package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.CasToInlineXml;

import txtfnnl.uima.Views;

/**
 * A simple CAS consumer that serializes to XMI and writes the XML data to a
 * file using UTF-8 encoding by default.
 * 
 * This CAS Consumer takes two parameters:
 * <ul>
 * <li><code>OutputDirectory</code> - path to directory into which the XMI
 * output files will be written; if the directory does not exist, it will be
 * created first</li>
 * <li><code>Encoding</code> - the encoding to use for the output files
 * (default: the platform's encoding)</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class FileSystemXmiConsumer extends CasConsumer_ImplBase {

	/**
	 * Name of configuration parameter that must be set to the path of a
	 * directory where the output files will be written to.
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";

	/**
	 * Name of optional configuration parameter that indicates the encoding to
	 * use for the output files.
	 * 
	 * Defaults to the platform's encoding.
	 */
	public static final String PARAM_ENCODING = "Encoding";

	private File outputDir;

	private String encoding;

	private CasToInlineXml cas2xml;

	private int counter;

	public void initialize() throws ResourceInitializationException {
		counter = 0;
		outputDir = new File(
		    (String) getConfigParameterValue(PARAM_OUTPUT_DIRECTORY));
		encoding = (String) getConfigParameterValue(PARAM_ENCODING);

		if (!outputDir.exists())
			outputDir.mkdirs();

		if (!(outputDir.isDirectory() && outputDir.canWrite()))
			throw new ResourceInitializationException(new IOException(
			    PARAM_OUTPUT_DIRECTORY + "='" + outputDir +
			            "' not a writeable directory"));

		cas2xml = new CasToInlineXml();
	}

	public void processCas(CAS aCAS) throws ResourceProcessException {
		String uri = aCAS.getView(Views.CONTENT_RAW.toString()).getSofaDataURI();
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
			// serialize CAS to XML Metadata Interchange (XMI) format
			String xmlAnnotations = cas2xml.generateXML(aCAS
			    .getView(Views.CONTENT_TEXT.toString()));
			FileOutputStream outStream = new FileOutputStream(outFile);

			// and write to output file
			if (encoding == null)
				outStream.write(xmlAnnotations.getBytes());
			else
				outStream.write(xmlAnnotations.getBytes(encoding));

			outStream.close();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		} catch (IOException e) {
			throw new ResourceProcessException(e);
		}
	}
}
