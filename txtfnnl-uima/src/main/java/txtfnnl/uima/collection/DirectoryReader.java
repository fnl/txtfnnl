package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import org.uimafit.component.CasCollectionReader_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.CollectionReaderBuilder;
import txtfnnl.uima.Views;

/**
 * A collection reader from a directory containing the input files, possibly traversing any
 * sub-directories. The MIME type of the input files can be predetermined or automatically
 * detected.
 * 
 * @author Florian Leitner
 */
public class DirectoryReader extends CasCollectionReader_ImplBase {
  /** The (required) path of a directory containing input files. */
  public static final String PARAM_DIRECTORY = "InputDirectory";
  @ConfigurationParameter(name = PARAM_DIRECTORY, mandatory = true)
  private File inputDirectory;
  /** Will be populated with the files found in the directory. */
  private List<File> files;
  /**
   * Optional parameter defining the MIME type of all input files. If not given, the MIME type of
   * each file will be detected by Tika.
   * 
   * @see http://www.iana.org/assignments/media-types/index.html
   */
  public static final String PARAM_MIME_TYPE = "MimeType";
  @ConfigurationParameter(name = PARAM_MIME_TYPE)
  private String mimeType;
  /**
   * Optional parameter indicating whether to (recursively) include all sub-directories of the
   * input directory.
   */
  public static final String PARAM_RECURSIVE = "Recursive";
  @ConfigurationParameter(name = PARAM_RECURSIVE, defaultValue = "false")
  private boolean recursive;
  /* TODO: set document language
   * Optional parameter defining the language used in the input documents.
   * 
  public static final String PARAM_LANGUAGE = "Language";
  @ConfigurationParameter(name = PARAM_LANGUAGE)
  private String language;
   */
  private int counter;
  private Iterator<File> fileIter;
  private final Tika tika = new Tika(); // for MIME type detection

  public static class Builder extends CollectionReaderBuilder {
    protected Builder(Class<? extends CollectionReader> klass, File dir) {
      super(klass);
      setRequiredParameter(PARAM_DIRECTORY, dir);
    }

    public Builder(File dir) {
      this(DirectoryReader.class, dir);
    }

    /**
     * The MIME type of all input files.
     * <p>
     * If not given, it will be detected by Tika.
     * 
     * @see http://www.iana.org/assignments/media-types/index.html
     */
    public Builder setMimeType(String mimeType) {
      setOptionalParameter(PARAM_MIME_TYPE, mimeType);
      return this;
    }

    /** Recursively include all sub-directories of the input directory, too. */
    public Builder recurseSubdirectories() {
      setOptionalParameter(PARAM_RECURSIVE, Boolean.TRUE);
      return this;
    }
  }

  /**
   * Create a new configuration builder for a directory reader.
   * 
   * @param dir the input directory to scan for files
   */
  public static Builder configure(File dir) {
    return new Builder(dir);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    // TODO: instantiate a Tika language identifier and some profiles
    if (!inputDirectory.exists() || !inputDirectory.isDirectory())
      throw new ResourceInitializationException(
          ResourceConfigurationException.DIRECTORY_NOT_FOUND, new Object[] { PARAM_DIRECTORY,
              getMetaData().getName(), inputDirectory.getPath() });
    files = DirectoryReader.addFilesFromDir(inputDirectory, recursive);
    fileIter = files.iterator();
    counter = 0;
  }

  /**
   * Starting from a given directory, return all files in it. If recurse is <code>true</code>,
   * include files in sub-directories, too.
   * 
   * @param dir to scan for files
   * @param recurse determines if sub-directories are scanned, too
   * @return the list of files found in the directory
   */
  private static List<File> addFilesFromDir(File dir, boolean recurse) {
    final File[] entries = dir.listFiles();
    final List<File> collector = new LinkedList<File>();
    for (int i = 0; i < entries.length; i++) {
      if (entries[i].isFile()) {
        collector.add(entries[i]);
      } else if (recurse && entries[i].isDirectory()) {
        collector.addAll(DirectoryReader.addFilesFromDir(entries[i], recurse));
      }
    }
    return collector;
  }

  public void getNext(CAS aCAS) throws IOException, CollectionException {
    final CAS rawDocument = aCAS.createView(Views.CONTENT_RAW.toString());
    // set the new SOFA's data URI and MIME type
    final File file = fileIter.next();
    final String uri = file.getCanonicalFile().toURI().toString();
    rawDocument.setSofaDataURI(uri, mimeType == null ? tika.detect(file) : mimeType);
    /* TODO: set document language
    if (language != null) {
    	jcas.setDocumentLanguage(language);
    }
    else {
    	// detect the language with a
    	// org.apache.tika.language.LanguageIdentifier
    }
     */
    counter++;
  }

  public boolean hasNext() throws IOException, CollectionException {
    return fileIter.hasNext();
  }

  public Progress[] getProgress() {
    return new Progress[] { new ProgressImpl(counter, files.size(), Progress.ENTITIES) };
  }

  @Override
  public void close() throws IOException {}
}
