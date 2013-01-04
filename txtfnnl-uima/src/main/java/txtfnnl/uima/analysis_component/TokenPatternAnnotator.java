/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.pattern.Matcher;
import txtfnnl.pattern.Pattern;
import txtfnnl.uima.Views;
import txtfnnl.uima.pattern.TokenPattern;
import txtfnnl.uima.resource.LineBasedStringArrayResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * Annotate SOFAs based on pattern matching of token sequences using a finite state machine.
 * <p>
 * See {@link txtfnnl.uima.pattern.TokenPattern} for a description of the pattern language.
 * <p>
 * Matching patterns and any capture groups within the patterns are {@link SemanticAnnotation
 * semantically annotated} using the namespace and identifier values defined in the
 * {@link LineBasedStringArrayResource pattern resource}. A pattern resource line first contains
 * the pattern itself, and then two values (namespace, identifier) each for (1) the desired
 * semantic annotation of the entire matched pattern and then (2) for each capture group. If the
 * entire pattern should not be annotated, the two first values can be left empty. If the
 * annotations are omitted entirely, default values for the namespace and identifier annotation of
 * the matched patterns are used.
 * 
 * @author Florian Leitner
 */
public class TokenPatternAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator (namespace and ID are defined dynamically). */
  public static final String URI = TokenPatternAnnotator.class.getName();
  protected Logger logger;
  // TODO: implement token namespace via global NS settings
  private String tokenNamespace = "http://nlp2rdf.lod2.eu/schema/doc/sso/";
  /** The key used for the LineBasedStringArrayResource. */
  public static final String MODEL_KEY_PATTERN_RESOURCE = "TokenPatterns";
  @ExternalResource(key = MODEL_KEY_PATTERN_RESOURCE)
  private LineBasedStringArrayResource patternResource;
  // will be populated from the resource
  private Map<String, Pattern<TokenAnnotation>> patterns;
  private Map<String, List<String[]>> annotations;
  /** Fallback if no annotation identifier for a pattern is provided in the resource. */
  private String defaultIdentifier = "Phrase";  // TODO: configuration
  /** Fallback if no annotation namespace for a pattern are provided in the resource. */
  // TODO: configuration
  private String defaultNamespace = "http://nlp2rdf.lod2.eu/schema/doc/sso/";

  /**
   * Configure a new descriptor with a pattern file resource.
   * 
   * @param patterns to match
   * @param separator between values in the patterns file
   * @return a configured AE description
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure(File patterns, String separator)
      throws UIMAException {
    final ExternalResourceDescription patternResource = LineBasedStringArrayResource.configure(
        "file:" + patterns.getAbsolutePath(), separator);
    return AnalysisEngineFactory.createPrimitiveDescription(TokenPatternAnnotator.class,
        MODEL_KEY_PATTERN_RESOURCE, patternResource);
  }

  /** Default configuration only requires the pattern resource file. */
  public static AnalysisEngineDescription configure(File patterns) throws UIMAException {
    return TokenPatternAnnotator.configure(patterns, null);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    if (patternResource.size() == 0) {
      logger.log(Level.WARNING, "no patterns from {0}", patternResource.getResourceUrl());
    } else {
      logger.log(Level.INFO, "initialized with {0} patterns", patternResource.size());
    }
    patterns = new HashMap<String, Pattern<TokenAnnotation>>();
    annotations = new HashMap<String, List<String[]>>();
    for (String[] pattern : patternResource) {
      try {
        if (pattern.length % 2 == 1) {
          patterns.put(pattern[0], TokenPattern.compile(pattern[0]));
          List<String[]> anns;
          if (pattern.length > 1) {
            anns = new ArrayList<String[]>((pattern.length - 1) / 2);
            for (int i = 1; i < pattern.length; i += 2) {
              if (pattern[i].length() > 0 && pattern[i + 1].length() > 0) anns.add(new String[] {
                  pattern[i], pattern[i + 1] });
              else anns.add(new String[0]); // do not annotate this group
            }
          } else {
            anns = new ArrayList<String[]>(1);
            anns.add(new String[] { defaultNamespace, defaultIdentifier });
          }
          annotations.put(pattern[0], anns);
        } else {
          logger.log(Level.WARNING,
              "skipping pattern definition ''{0}'' with a wrong number of fields", pattern[0]);
        }
      } catch (PatternSyntaxException e) {
        logger.log(Level.INFO, e.getLocalizedMessage());
        logger.log(Level.WARNING, "illegal pattern ''{0}'' ignored", pattern[0]);
      }
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    try {
      jcas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
    final AnnotationIndex<Annotation> tokenIdx = jcas.getAnnotationIndex(TokenAnnotation.type);
    final FSMatchConstraint tokenConstraint = TokenAnnotation.makeConstraint(jcas, tokenNamespace);
    // use per-CAS Matchers (avoids concurrency issues that would arise from using the same
    // Matchers in different threads)
    final Map<String, Matcher<TokenAnnotation>> matchers = new HashMap<String, Matcher<TokenAnnotation>>();
    List<TokenAnnotation> tokens = new ArrayList<TokenAnnotation>(20); // assume most sentences
                                                                       // have <= 20 tokens
    for (String expr : patterns.keySet())
      matchers.put(expr, patterns.get(expr).matcher(tokens));
    while (sentenceIt.hasNext()) {
      final Annotation sentence = sentenceIt.next();
      final FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
          tokenIdx.subiterator(sentence, true, true), tokenConstraint);
      tokens.clear();
      while (tokenIt.hasNext())
        tokens.add((TokenAnnotation) tokenIt.next());
      matchOnSequence(matchers, tokens, jcas);
    }
  }

  /** Annotate matches of any of the pattern on the token sequence. */
  private void matchOnSequence(final Map<String, Matcher<TokenAnnotation>> matchers,
      final List<TokenAnnotation> tokens, JCas jcas) {
    for (String expr : matchers.keySet()) {
      final Matcher<TokenAnnotation> matcher = matchers.get(expr).reset(tokens);
      while (matcher.find()) {
        final List<String[]> annList = annotations.get(expr);
        for (int i = 0; i <= matcher.groupCount(); i++) {
          if (annList.get(i).length > 0) {
            annotate(annList.get(i), tokens.get(matcher.start(i)).getBegin(),
                tokens.get(matcher.end(i) - 1).getEnd(), jcas);
          }
        }
      }
    }
  }

  /** {@link SemanticAnnotation Annotate} a particular match with a given namespace and ID. */
  private void annotate(String[] ns_id, int begin, int end, JCas jcas) {
    final SemanticAnnotation ann = new SemanticAnnotation(jcas, begin, end);
    ann.setAnnotator(URI);
    ann.setConfidence(1);
    ann.setIdentifier(ns_id[1]);
    ann.setNamespace(ns_id[0]);
    ann.addToIndexes();
  }
}
