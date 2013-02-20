package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.tika.Tika;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import org.uimafit.component.CasCollectionReader_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.CollectionReaderFactory;

import txtfnnl.uima.UIMAUtils;
import txtfnnl.uima.Views;

/**
 * A collection reader for an array of file paths. The MIME type of the input files can be
 * predetermined or automatically detected.
 * 
 * @author Florian Leitner
 */
public class FileReader extends CasCollectionReader_ImplBase {
  /**
   * A list of input files for this reader to process. At least one input file is required.
   */
  public static final String PARAM_INPUT_FILES = "InputFiles";
  @ConfigurationParameter(name = PARAM_INPUT_FILES, mandatory = true)
  private String[] inputFiles;
  private File[] files;
  /**
   * Optional parameter defining the MIME type of all input files. If not given, the MIME type of
   * each file will be detected by Tika.
   * 
   * @see http://www.iana.org/assignments/media-types/index.html
   */
  public static final String PARAM_MIME_TYPE = "MimeType";
  @ConfigurationParameter(name = PARAM_MIME_TYPE)
  private String mimeType;
  /* TODO: set document language
   * Optional parameter defining the language used in the input documents.
   * 
  public static final String PARAM_LANGUAGE = "Language";
  @ConfigurationParameter(name = PARAM_LANGUAGE)
  private String language;
   */
  /** Count processed files. */
  private int counter = 0;
  private final Tika tika = new Tika(); // for the MIME type detection

  /**
   * Configure the CR descriptor.
   * 
   * @param filePaths the list of input files to read
   * @param mimeType the MIME type to set for the input files
   * @throws IOException
   * @throws UIMAException
   */
  @SuppressWarnings("serial")
  public static CollectionReaderDescription configure(final String[] filePaths,
      final String mimeType) throws UIMAException, IOException {
    return CollectionReaderFactory.createDescription(FileReader.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_INPUT_FILES, filePaths);
            put(PARAM_MIME_TYPE, mimeType);
          }
        }));
  }

  /**
   * Configure the CR descriptor to use Tika for detecting the MIME types.
   * 
   * @param filePaths the list of input files to read
   * @throws IOException
   * @throws UIMAException
   */
  public static CollectionReaderDescription configure(String[] filePaths) throws UIMAException,
      IOException {
    return FileReader.configure(filePaths, null);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    // TODO: instantiate a Tika language identifier and some profiles
    files = new File[inputFiles.length];
    int idx = 0;
    for (final String fn : inputFiles) {
      final File f = new File(fn);
      if (f.exists() && f.canRead()) {
        files[idx++] = f;
      } else throw new ResourceInitializationException(
          ResourceConfigurationException.RESOURCE_DATA_NOT_VALID, new Object[] { fn,
              PARAM_INPUT_FILES });
    }
    counter = 0;
  }

  public void getNext(CAS aCAS) throws IOException, CollectionException {
    JCas jcas;
    try {
      jcas = aCAS.createView(Views.CONTENT_RAW.toString()).getJCas();
    } catch (final CASException e) {
      throw new CollectionException(e);
    }
    // set the new SOFA's data URI and MIME type
    final File file = files[counter];
    final String uri = file.getAbsoluteFile().toURI().toString();
    String fileMime = mimeType;
    if (mimeType == null) {
      fileMime = tika.detect(file);
    }
    jcas.setSofaDataURI(uri, fileMime);
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
    return counter < files.length;
  }

  public Progress[] getProgress() {
    return new Progress[] { new ProgressImpl(counter, files.length, Progress.ENTITIES) };
  }

  @Override
  public void close() throws IOException {}
}
