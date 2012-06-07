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

/**
 * A simple CAS consumer that serializes to XMI and writes the XML data to a
 * file using UTF-8 encoding by default.
 * 
 * This CAS Consumer takes two parameters:
 * <ul>
 * <li><code>OutputDirectory</code> - path to directory into which the XMI
 * output files will be written</li>
 * <li><code>Encoding</code> - the encoding to use for the output files
 * (default: UTF-8)</li>
 * </ul>
 * 
 * 
 */
public class FileSystemXmiConsumer extends CasConsumer_ImplBase {

	public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";

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
		
		if (encoding == null)
			encoding = "UTF-8";

		cas2xml = new CasToInlineXml();
	}

	public void processCas(CAS aCAS) throws ResourceProcessException {
		String uri = aCAS.getView("contentRaw").getSofaDataURI();
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
			    .getView("contentText"));
			FileOutputStream outStream = new FileOutputStream(outFile);
			// and write to output file in UTF-8
			outStream.write(xmlAnnotations.getBytes(encoding));
			outStream.close();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		} catch (IOException e) {
			throw new ResourceProcessException(e);
		}
	}
}
