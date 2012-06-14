package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
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
 * A collection reader creating SOFAs/CASes from a directory of files.
 * 
 * It can be configured with the following parameters:
 * <ul>
 * <li><code>InputDirectory</code> - path to directory containing files</li>
 * <li><code>Recursive</code> (optional) - <code>true</code> if any
 * sub-directories should be included, too</li>
 * <li><code>MimeType</code> (optional) - the MIME type of the input
 * documents; detected through Tika if not given</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class FileSystemCollectionReader extends CollectionReader_ImplBase {

	/**
	 * Name of configuration parameter that must be set to the path of a
	 * directory containing input files.
	 */
	public static final String PARAM_DIRECTORY = "InputDirectory";

	// public static final String PARAM_LANGUAGE = "Language"; // TODO

	/**
	 * Name of optional configuration parameter that determines the MIME type
	 * all input files should use.
	 * 
	 * @see http://www.iana.org/assignments/media-types/index.html
	 */
	public static final String PARAM_MIME_TYPE = "MimeType";

	/**
	 * Name of optional configuration parameter that (recursively) includes
	 * the sub-directories of the current input directory if <code>true</code>
	 * .
	 */
	public static final String PARAM_RECURSIVE = "Recursive";

	private int counter = 0;
	
	private Iterator<File> fileIter;
	
	private List<File> files;
	
	// private String language; // TODO
	
	private String mimeType;
	
	private Tika tika = new Tika(); // for MIME type detection

	public void initialize() throws ResourceInitializationException {
		File directory = new File(
		    ((String) getConfigParameterValue(PARAM_DIRECTORY)).trim());
		Boolean recursive = (Boolean) getConfigParameterValue(PARAM_RECURSIVE);

		// TODO: instantiate a Tika language identifier and some profiles
		// language = (String) getConfigParameterValue(PARAM_LANGUAGE);
		mimeType = (String) getConfigParameterValue(PARAM_MIME_TYPE);

		if (recursive == null)
			recursive = Boolean.FALSE;

		if (!directory.exists() || !directory.isDirectory()) {
			throw new ResourceInitializationException(
			    ResourceConfigurationException.DIRECTORY_NOT_FOUND,
			    new Object[] {
			        PARAM_DIRECTORY,
			        this.getMetaData().getName(),
			        directory.getPath() });
		}

		files = addFilesFromDir(directory, recursive);
		fileIter = files.iterator();
		counter = 0;
	}

	/**
	 * Starting from a given directory, return all files in it.
	 * 
	 * If recurse is <code>true</code>, include files in sub-directories, too.
	 * 
	 * @param dir to scan for files
	 * @param recurse determines if sub-directories are scanned, too
	 * @return the list of files found in the directory
	 */
	private List<File> addFilesFromDir(File dir, boolean recurse) {
		File[] entries = dir.listFiles();
		List<File> collector = new LinkedList<File>();

		for (int i = 0; i < entries.length; i++) {
			if (entries[i].isFile()) {
				collector.add(entries[i]);
			} else if (recurse && entries[i].isDirectory()) {
				collector.addAll(addFilesFromDir(entries[i], recurse));
			}
		}

		return collector;
	}

	public void getNext(CAS aCAS) throws IOException, CollectionException {
		JCas jcas;

		try {
			jcas = aCAS.createView(Views.CONTENT_RAW.toString()).getJCas();
		} catch (CASException e) {
			throw new CollectionException(e);
		}

		// set the new SOFA's data URI and MIME type
		File file = fileIter.next();
		String uri = file.getAbsoluteFile().toURI().toString();
		String fileMime = mimeType;

		if (mimeType == null)
			mimeType = tika.detect(file);

		jcas.setSofaDataURI(uri, fileMime);

		// set document language
		/* if (language != null) { jcas.setDocumentLanguage(language); } else
		 * { // TODO: detect the language with a //
		 * org.apache.tika.language.LanguageIdentifier } */

		counter++;
	}

	public boolean hasNext() throws IOException, CollectionException {
		return fileIter.hasNext();
	}

	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(counter, files.size(),
		    Progress.ENTITIES) };
	}

	public void close() throws IOException {}

}
