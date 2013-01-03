package txtfnnl.uima.analysis_component.opennlp;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;
import opennlp.uima.sentdetect.SentenceModelResource;
import opennlp.uima.sentdetect.SentenceModelResourceImpl;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;

/**
 * A pipeline to tokenize, PoS tag, phrase chunk, and stem/lemmatize input text.
 * <p>
 * This AE segments input text into {@link SentenceAnnotation SentenceAnnotations}, setting each
 * sentences probability on the confidence feature. The detector requires a
 * {@link #RESOURCE_SENTENCE_MODEL}, which can be the default model found in the jar.
 * <p>
 * Detected line separators are Windows (CR-LF) and Unix line-breaks (LF only). Successive line
 * separators may be separated by spaces, including U+00A0 (NBS).
 * 
 * @author Florian Leitner
 */
public final class SentenceAnnotator extends JCasAnnotator_ImplBase {
  /** The annotator's URI (for the annotations) set by this AE. */
  public static final String URI = "http://opennlp.apache.org";
  /** The namespace used for all annotations. */
  public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";
  /** The identifier used for all annotations. */
  public static final String IDENTIFIER = "Sentence";

  public enum Split {
    SINGLE, SUCCESSIVE, DEFAULT;
    public static Split parse(String s) {
      if (s == null) return DEFAULT;
      s = s.toLowerCase();
      if ("successive".equals(s)) return SUCCESSIVE;
      else if ("single".equals(s)) return SINGLE;
      else return DEFAULT;
    }
  };

  /**
   * Optional parameter to split on newline characters; can be "single" or "successive", and
   * defaults to <code>null</code> or any other string.
   * <p>
   * The parameter indicates if sentences should never be split with respect to newlines (
   * <code>null</code>, any other string), always ("single"), or only after successive newlines
   * ("successive"). Note that successive newlines may contain spaces in between line-breaks.
   */
  public static final String PARAM_SPLIT_ON_NEWLINE = "SplitOnNewline";
  @ConfigurationParameter(name = PARAM_SPLIT_ON_NEWLINE)
  private String splitOnNewline;
  private Split splitting;
  /** The name of the sentence model resource. */
  private static final String RESOURCE_SENTENCE_MODEL = "SentenceModelResource";
  /** The default sentence model file, as found in the jar. */
  private static final File DEFAULT_SENTENCE_MODEL = new File("txtfnnl/opennlp/en_sent.bin");
  /** The sentence detector that will be instantiated using the model. */
  private SentenceDetectorME sentenceDetector;
  /** Regular expression to detect multiple/successive line-breaks. */
  private static final Pattern SUCCESSIVE_LINEBREAKS = Pattern
      .compile("(?:\\r?\\n[ \\u00a0\\t\\v\\f]*){2,}");
  private Logger logger;

  /**
   * Create this AE's descriptor for a pipeline.
   * 
   * @param modelFile containing the OpenNLP sentence segmentation model to use; this parameter is
   *        <b>required</b>
   * @param splitSentence "single", "successive", or any other value including <code>null</code>
   *        (the default); see {@link #PARAM_SPLIT_ON_NEWLINE}
   * @throws IOException
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure(File modelFile, String splitSentences)
      throws UIMAException, IOException {
    AnalysisEngineDescription aed;
    if (splitSentences == null) {
      aed = AnalysisEngineFactory.createPrimitiveDescription(SentenceAnnotator.class);
    } else {
      aed = AnalysisEngineFactory.createPrimitiveDescription(SentenceAnnotator.class,
          SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, splitSentences);
    }
    ExternalResourceFactory.createDependencyAndBind(aed, RESOURCE_SENTENCE_MODEL,
        SentenceModelResourceImpl.class, "file:" + modelFile.getPath());
    return aed;
  }

  /**
   * Create this AE's descriptor for a pipeline using the default sentence model.
   * 
   * @param splitSentence "single", "successive", or any other value including <code>null</code>
   *        (the default); see {@link #PARAM_SPLIT_ON_NEWLINE}
   * @see SentenceAnnotator#configure(String, String)
   */
  public static AnalysisEngineDescription configure(String splitSentences) throws UIMAException,
      IOException {
    return SentenceAnnotator.configure(DEFAULT_SENTENCE_MODEL, splitSentences);
  }

  /**
   * Create this AE's descriptor for a pipeline using the default sentence model, splitting
   * sentences at every <i>single</i> newline (see {@link #PARAM_SPLIT_ON_NEWLINE}).
   * 
   * @see SentenceAnnotator#configure(String, String)
   */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return SentenceAnnotator.configure("single");
  }

  /**
   * Load the sentence detector model resource and initialize the model evaluator.
   */
  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    SentenceModel model;
    logger = ctx.getLogger();
    try {
      final SentenceModelResource modelResource = (SentenceModelResource) ctx
          .getResourceObject(RESOURCE_SENTENCE_MODEL);
      model = modelResource.getModel();
    } catch (final ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    } catch (final NullPointerException e) {
      throw new ResourceInitializationException(new AssertionError(
          "sentence model resource not found"));
    }
    sentenceDetector = new SentenceDetectorME(model);
    splitting = Split.parse(splitOnNewline);
    switch (splitting) {
    case SINGLE:
      logger.log(Level.INFO, "splitting on single newlines");
      break;
    case SUCCESSIVE:
      logger.log(Level.INFO, "splitting on successive newlines");
      break;
    default:
      logger.log(Level.INFO, "no newline-based splitting");
    }
  }

  /**
   * Make {@link SentenceAnnotation}s in the {@link Views.CONTENT_TEXT} view of a CAS.
   */
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    try {
      jcas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    String[] chunks;
    final String text = jcas.getDocumentText();
    switch (splitting) {
    case SUCCESSIVE:
      chunks = SUCCESSIVE_LINEBREAKS.split(text);
      break;
    case SINGLE:
      chunks = text.split("\\s*?\\r?\\n\\s*");
      break;
    default:
      chunks = new String[] { text };
    }
    int offset = 0;
    for (final String cnk : chunks) {
      if (cnk.length() == 0) {
        continue;
      }
      offset = text.indexOf(cnk, offset);
      final Span[] spans = sentenceDetector.sentPosDetect(cnk);
      final double[] probs = sentenceDetector.getSentenceProbabilities();
      for (int i = 0; i < spans.length; i++) {
        final SentenceAnnotation sentence = new SentenceAnnotation(jcas, offset +
            spans[i].getStart(), offset + spans[i].getEnd());
        sentence.setConfidence(probs[i]);
        sentence.setAnnotator(URI);
        sentence.setIdentifier(IDENTIFIER);
        sentence.setNamespace(NAMESPACE);
        sentence.addToIndexes();
      }
      offset += cnk.length();
    }
  }

  @Override
  public void destroy() {
    sentenceDetector = null;
  }
}
