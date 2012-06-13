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

public class FileSystemCollectionReader extends CollectionReader_ImplBase {

	public static final String PARAM_DIRECTORY = "InputDirectory";
	public static final String PARAM_RECURSIVE = "Recursive";
	public static final String PARAM_LANGUAGE = "Language"; // TODO
	public static final String PARAM_MIME_TYPE = "MimeType";

	private String language;
	private String mimeType;
	private List<File> files;
	private Iterator<File> fileIter;
	private int counter = 0;
	private Tika tika = new Tika();

	/* @see org.apache.uima.collection.CollectionReader_ImplBase#initialize() */
	public void initialize() throws ResourceInitializationException {
		File directory = new File(
		    ((String) getConfigParameterValue(PARAM_DIRECTORY)).trim());
		Boolean recursive = (Boolean) getConfigParameterValue(PARAM_RECURSIVE);

		language = (String) getConfigParameterValue(PARAM_LANGUAGE);
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

		// TODO: instantiate a Tika language identifier and some profiles
	}

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
			jcas = aCAS.createView("contentRaw").getJCas();
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
		if (language != null) {
			jcas.setDocumentLanguage(language);
		} else {
			// TODO: detect the language with a
			// org.apache.tika.language.LanguageIdentifier
		}

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
