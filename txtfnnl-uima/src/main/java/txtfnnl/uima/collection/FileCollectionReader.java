package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import txtfnnl.uima.Views;

/**
 * A collection reader creating SOFAs/CASes from a (list of) file(s).
 * 
 * It can be configured with the following parameters:
 * <ul>
 * <li><code>InputFiles</code> <i>array</i> - the file names to read</li>
 * <li><code>MimeType</code> <i>optional</i> - the MIME type of the input
 * documents; detected through Tika if not given</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class FileCollectionReader extends CollectionReader_ImplBase {

	/**
	 * Name of configuration parameter that must be set to a space-separated
	 * list of input files.
	 */
	public static final String PARAM_FILES = "InputFiles";

	// public static final String PARAM_LANGUAGE = "Language"; // TODO

	/**
	 * Name of optional configuration parameter that determines the MIME type
	 * all input files should use.
	 * 
	 * If not given, the MIME type is detected by Tika.
	 * 
	 * @see http://www.iana.org/assignments/media-types/index.html
	 */
	public static final String PARAM_MIME_TYPE = "MimeType";

	private int counter = 0;

	private List<File> files;

	// private String language; // TODO

	private String mimeType;

	private Tika tika = new Tika(); // for MIME type detection

	public void initialize() throws ResourceInitializationException {
		String[] fileNames = ((String[]) getConfigParameterValue(PARAM_FILES));

		// TODO: instantiate a Tika language identifier and some profiles
		// language = (String) getConfigParameterValue(PARAM_LANGUAGE);
		mimeType = (String) getConfigParameterValue(PARAM_MIME_TYPE);

		files = new ArrayList<File>(fileNames.length);

		for (String fn : fileNames) {
			File f = new File(fn);
			if (f.exists() && f.canRead()) {
				files.add(f);
			} else {
				throw new ResourceInitializationException(
				    ResourceConfigurationException.RESOURCE_DATA_NOT_VALID,
				    new Object[] { fn, PARAM_FILES });
			}
		}
		
		counter = 0;
	}

	public void getNext(CAS aCAS) throws IOException, CollectionException {
		JCas jcas;

		try {
			jcas = aCAS.createView(Views.CONTENT_RAW.toString()).getJCas();
		} catch (CASException e) {
			throw new CollectionException(e);
		}

		// set the new SOFA's data URI and MIME type
		File file = files.get(counter);
		String uri = file.getAbsoluteFile().toURI().toString();
		String fileMime = mimeType;

		if (mimeType == null)
			fileMime = tika.detect(file);

		jcas.setSofaDataURI(uri, fileMime);

		// set document language
		/* if (language != null) { jcas.setDocumentLanguage(language); } else
		 * { // TODO: detect the language with a //
		 * org.apache.tika.language.LanguageIdentifier } */

		counter++;
	}

	public boolean hasNext() throws IOException, CollectionException {
		return counter < files.size();
	}

	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(counter, files.size(),
		    Progress.ENTITIES) };
	}

	public void close() throws IOException {}

}
