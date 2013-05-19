package txtfnnl.uima.analysis_component.opennlp;

import java.util.regex.Pattern;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.util.Span;
import opennlp.uima.sentdetect.SentenceModelResource;
import opennlp.uima.sentdetect.SentenceModelResourceImpl;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.AnalysisComponentBuilder;
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
  public static final String URI = SentenceAnnotator.class.getName();
  /** The namespace used for all annotations. */
  public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";
  /** The identifier used for all annotations. */
  public static final String IDENTIFIER = "Sentence";
  /** Regular expression to detect multiple/successive line-breaks. */
  private static final Pattern SUCCESSIVE_LINEBREAKS = Pattern
      .compile("(?:\\r?\\n[ \\u00a0\\t\\v\\f]*){2,}");

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
  /** The name of the (required) sentence model resource. */
  public static final String RESOURCE_SENTENCE_MODEL = "SentenceModelResource";
  @ExternalResource(key = RESOURCE_SENTENCE_MODEL)
  private SentenceModelResource sentenceModel;
  /** The sentence detector that will be instantiated using the model. */
  private SentenceDetectorME sentenceDetector;
  /** The default sentence model file, as found in the jar. */
  static final String DEFAULT_SENTENCE_MODEL_URL = "file:txtfnnl/opennlp/en_sent.bin";
  private Logger logger;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
      setModelResourceUrl(DEFAULT_SENTENCE_MODEL_URL);
    }

    Builder() {
      this(SentenceAnnotator.class);
    }

    public Builder setModelResource(ExternalResourceDescription sentenceModelResourceDescription) {
      setRequiredParameter(RESOURCE_SENTENCE_MODEL, sentenceModelResourceDescription);
      return this;
    }

    public Builder setModelResourceUrl(String sentenceModelResourceUrl) {
      setModelResource(ExternalResourceFactory.createExternalResourceDescription(
          SentenceModelResourceImpl.class, sentenceModelResourceUrl));
      return this;
    }

    public Builder splitOnSingleNewlines() {
      setOptionalParameter(PARAM_SPLIT_ON_NEWLINE, Split.SINGLE.toString());
      return this;
    }

    public Builder splitOnSuccessiveNewlines() {
      setOptionalParameter(PARAM_SPLIT_ON_NEWLINE, Split.SUCCESSIVE.toString());
      return this;
    }

    public Builder splitIgnoringNewlines() {
      setOptionalParameter(PARAM_SPLIT_ON_NEWLINE, Split.DEFAULT.toString());
      return this;
    }
  }

  /** Create this AE's builder for a pipeline. */
  public static SentenceAnnotator.Builder configure() {
    return new Builder();
  }

  /**
   * Load the sentence detector model resource and initialize the model evaluator.
   */
  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    sentenceDetector = new SentenceDetectorME(sentenceModel.getModel());
    splitting = Split.parse(splitOnNewline);
    switch (splitting) {
    case SINGLE:
      logger.log(Level.CONFIG, "splitting on single newlines");
      break;
    case SUCCESSIVE:
      logger.log(Level.CONFIG, "splitting on successive newlines");
      break;
    default:
      logger.log(Level.CONFIG, "no newline-based splitting");
    }
  }

  /**
   * Make {@link SentenceAnnotation}s in the {@link Views.CONTENT_TEXT} view of a CAS.
   */
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
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
    super.destroy();
    sentenceDetector = null;
  }
}
