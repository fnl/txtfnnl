/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.resource.LineBasedStringArrayResource;
import txtfnnl.uima.tcas.SentenceAnnotation;

/**
 * Retain or remove sentences matching any of a list of regular expressions.
 * 
 * @author Florian Leitner
 */
public class SentenceFilter extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator. */
  public static final String URI = SentenceFilter.class.getName();
  protected Logger logger;
  // TODO: implement token namespace via global NS settings
  /**
   * Remove {@link SentenceAnnotation SentenceAnnotations} from only one particular annotator
   * (default: work with any annotator/all sentences).
   */
  public static final String PARAM_SENTENCE_ANNOTATOR = "SentenceAnnotator";
  @ConfigurationParameter(name = PARAM_SENTENCE_ANNOTATOR)
  private String sentenceAnnotator = null;
  /** The key used for the LineBasedStringArrayResource. */
  public static final String MODEL_KEY_PATTERN_RESOURCE = "StringPatterns";
  @ExternalResource(key = MODEL_KEY_PATTERN_RESOURCE)
  private LineBasedStringArrayResource patternResource;
  // will be populated from the resource
  private Pattern[] patterns;
  /**
   * Remove {@link SentenceAnnotation SentenceAnnotations} that had a match for any of the patterns
   * (default: retain only matching sentences).
   */
  public static final String PARAM_REMOVE_MATCHED = "RemoveMatched";
  @ConfigurationParameter(name = PARAM_REMOVE_MATCHED, defaultValue = "false")
  private boolean removeMatched;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass,
        ExternalResourceDescription patternResource) {
      super(klass);
      setRequiredParameter(MODEL_KEY_PATTERN_RESOURCE, patternResource);
    }

    Builder(ExternalResourceDescription patternResource) {
      this(SentenceFilter.class, patternResource);
    }

    public Builder removeMatches() {
      setOptionalParameter(PARAM_REMOVE_MATCHED, Boolean.TRUE);
      return this;
    }

    public Builder setAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_SENTENCE_ANNOTATOR, uri);
      return this;
    }
  }

  /**
   * Configure a new descriptor builder with a (line-based string) pattern resource.
   * 
   * @param patternResource with the relevant patterns
   */
  public static Builder configure(ExternalResourceDescription patternResource) {
    return new Builder(patternResource);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    patterns = new Pattern[patternResource.size()];
    int idx = 0;
    for (String[] regex : patternResource) {
      assert regex.length == 1 : Arrays.toString(regex);
      patterns[idx++] = Pattern.compile(regex[0]);
    }
    if (patterns.length == 0) logger.log(Level.WARNING, "no patterns from {0}",
        patternResource.getResourceUrl());
    else logger.log(Level.INFO, "initialized with {0} patterns", patterns.length);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
    List<SentenceAnnotation> removeBuffer = new LinkedList<SentenceAnnotation>();
    int count = 0;
    withNextSentence:
    while (sentenceIt.hasNext()) {
      count++;
      SentenceAnnotation sa = (SentenceAnnotation) sentenceIt.next();
      String sentence = sa.getCoveredText();
      // XXX: might implement this more nicely as different strategies...
      if (sentenceAnnotator != null && !sentenceAnnotator.equals(sa.getAnnotator()))
        continue withNextSentence;
      for (Pattern p : patterns) {
        if (removeMatched && p.matcher(sentence).find()) {
          removeBuffer.add(sa);
          continue withNextSentence;
        } else if (!removeMatched && p.matcher(sentence).find()) {
          continue withNextSentence;
        }
      }
      if (!removeMatched) removeBuffer.add(sa);
    }
    logger.log(Level.FINE, "removed {0}/{1} sentence annotations",
        new Object[] { removeBuffer.size(), count });
    for (SentenceAnnotation s : removeBuffer)
      s.removeFromIndexes();
  }
}
