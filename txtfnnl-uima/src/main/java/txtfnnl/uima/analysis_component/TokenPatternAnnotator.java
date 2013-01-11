/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.pattern.Matcher;
import txtfnnl.pattern.Pattern;
import txtfnnl.uima.Views;
import txtfnnl.uima.pattern.TokenPattern;
import txtfnnl.uima.resource.LineBasedStringArrayResource;
import txtfnnl.uima.tcas.RelationshipAnnotation;
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
  private Map<String, Integer> patternHits;
  /**
   * Remove {@link SentenceAnnotation SentenceAnnotations} that did not match to any pattern
   * (default: <code>false</code>).
   */
  public static final String PARAM_REMOVE_UNMATCHED = "RemoveUnmatched";
  @ConfigurationParameter(name = PARAM_REMOVE_UNMATCHED, defaultValue = "false")
  private boolean removeUnmatched;
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
   * @param removeUnmatched remove sentence annotations where none of the patterns matched
   * @return a configured AE description
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure(File patterns, String separator,
      boolean removeUnmatched) throws UIMAException {
    final ExternalResourceDescription patternResource = LineBasedStringArrayResource.configure(
        "file:" + patterns.getAbsolutePath(), separator);
    return AnalysisEngineFactory.createPrimitiveDescription(TokenPatternAnnotator.class,
        MODEL_KEY_PATTERN_RESOURCE, patternResource, PARAM_REMOVE_UNMATCHED, removeUnmatched);
  }

  /** Default configuration only requires the pattern resource file. */
  public static AnalysisEngineDescription configure(File patterns) throws UIMAException {
    return TokenPatternAnnotator.configure(patterns, null, false);
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
    patternHits = new HashMap<String, Integer>();
    final String[] defaultNsId = new String[] { defaultNamespace, defaultIdentifier };
    Integer[] counts = { 0, 0, 0 };
    int countIdx = 2;
    for (String[] pattern : patternResource) {
      patternHits.put(pattern[0], 0);
      try {
        if (pattern.length % 2 == 1) {
          patterns.put(pattern[0], TokenPattern.compile(pattern[0]));
          List<String[]> anns;
          if (pattern.length > 1) {
            anns = new ArrayList<String[]>((pattern.length - 1) / 2);
            countIdx = (pattern.length == 3) ? 0 : 2;
            for (int i = 1; i < pattern.length; i += 2) {
              // store rel ns:id for the pattern (i==1) or sem ns:id for capture group (i>2)
              if (pattern[i].length() > 0 && pattern[i + 1].length() > 0) anns.add(new String[] {
                  pattern[i], pattern[i + 1] });
              else if (i == 0 && pattern.length > 4) {
                // if annotating groups only, don't define/store a ns:id for whole pattern
                countIdx = 1;
                anns.add(new String[0]);
              }
              // empty ns:id pairs for capture groups use the default ns:id
              else anns.add(defaultNsId);
            }
            counts[countIdx]++;
          } else { // annotate entire pattern semantically with default ns:id
            counts[0]++;
            anns = new ArrayList<String[]>(1);
            anns.add(defaultNsId);
          }
          annotations.put(pattern[0], anns);
        } else {
          logger.log(Level.WARNING,
              "skipping pattern ''{0}'' with an illegal number of annotations", pattern[0]);
        }
      } catch (PatternSyntaxException e) {
        logger.log(Level.INFO, e.getLocalizedMessage());
        logger.log(Level.WARNING, "illegal pattern ''{0}'' ignored", pattern[0]);
      }
    }
    logger.log(Level.INFO, "created {0} full semantic, {1} group semantic, "
        + "and {2} relationship patterns", counts);
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
    List<Annotation> removeBuffer = (removeUnmatched) ? new LinkedList<Annotation>() : null;
    for (String expr : patterns.keySet())
      matchers.put(expr, patterns.get(expr).matcher(tokens));
    int count = 0;
    while (sentenceIt.hasNext()) {
      count++;
      final Annotation sentence = sentenceIt.next();
      final FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
          tokenIdx.subiterator(sentence, true, true), tokenConstraint);
      tokens.clear();
      while (tokenIt.hasNext())
        tokens.add((TokenAnnotation) tokenIt.next());
      if (!matchOnSequence(matchers, tokens, jcas) && removeUnmatched) removeBuffer.add(sentence);
    }
    if (removeUnmatched) {
      logger.log(Level.FINE, "removing {0}/{1} unmatched sentence annotations", new Object[] {
          removeBuffer.size(), count });
      for (Annotation sentence : removeBuffer)
        sentence.removeFromIndexes();
    }
  }

  /**
   * Annotate matches of any of the pattern on the token sequence.
   * 
   * @throws AnalysisEngineProcessException
   */
  private boolean matchOnSequence(final Map<String, Matcher<TokenAnnotation>> matchers,
      final List<TokenAnnotation> tokens, JCas jcas) throws AnalysisEngineProcessException {
    /* Possible Annotations:
     * 1. match entire pattern and annotate it semantically
     * 2. match pattern and annotate capture groups (only) semantically
     * 3. match and annotate groups semantically; put into one relationship (ns:id)
     */
    boolean matched = false;
    for (String expr : matchers.keySet()) {
      final Matcher<TokenAnnotation> matcher = matchers.get(expr).reset(tokens);
      while (matcher.find()) {
        patternHits.put(expr, patternHits.get(expr) + 1);
        logger.log(Level.FINE, "''{0}'' matched", expr);
        final List<String[]> annList = annotations.get(expr);
        matched = true;
        if (annList.size() == 1) { // annotate case 1.
          semanticAnnotationOfEntirePattern(annList, matcher, tokens, jcas);
        } else if (annList.get(0).length == 0) { // annotate case 2.
          try {
            semanticAnnotationOfCaptureGroups(annList, matcher, tokens, jcas);
          } catch (IndexOutOfBoundsException e) {
            logger.log(Level.SEVERE, "less annotations than capture groups in pattern ''{0}''",
                expr);
            throw new AnalysisEngineProcessException(e);
          }
        } else { // annotate case 3.
          try {
            semanticRelationshipAnnotationOfPattern(annList, matcher, tokens, jcas);
          } catch (IndexOutOfBoundsException e) {
            logger.log(Level.SEVERE, "more annotations than capture groups in pattern ''{0}''",
                expr);
          }
        }
      }
    }
    return matched;
  }

  private void semanticAnnotationOfCaptureGroups(final List<String[]> annList,
      final Matcher<TokenAnnotation> matcher, final List<TokenAnnotation> tokens, JCas jcas)
      throws AnalysisEngineProcessException {
    for (int i = 1; i <= matcher.groupCount(); i++) {
      if (matcher.start(i) != matcher.end(i)) { // skip/ignore unmatched groups
        annotate(annList.get(i), tokens.get(matcher.start(i)).getBegin(),
            tokens.get(matcher.end(i) - 1).getEnd(), jcas);
      }
    }
  }

  private void semanticAnnotationOfEntirePattern(final List<String[]> annList,
      final Matcher<TokenAnnotation> matcher, final List<TokenAnnotation> tokens, JCas jcas) {
    annotate(annList.get(0), tokens.get(matcher.start()).getBegin(), tokens.get(matcher.end() - 1)
        .getEnd(), jcas);
  }

  private void semanticRelationshipAnnotationOfPattern(final List<String[]> annList,
      final Matcher<TokenAnnotation> matcher, final List<TokenAnnotation> tokens, JCas jcas) {
    final RelationshipAnnotation rel = new RelationshipAnnotation(jcas);
    final FSArray groups = new FSArray(jcas, annList.size() - 1);
    rel.setAnnotator(URI);
    rel.setIdentifier(annList.get(0)[0]);
    rel.setNamespace(annList.get(0)[1]);
    rel.setConfidence(1);
    for (int i = 1; i < annList.size(); i++) {
      if (matcher.start(i) != matcher.end(i)) { // skip/ignore unmatched groups
        groups.set(
            i - 1,
            annotate(annList.get(i), tokens.get(matcher.start(i)).getBegin(),
                tokens.get(matcher.end(i) - 1).getEnd(), jcas));
      }
    }
    rel.setSources(groups);
    rel.setTargets(groups); // undirected rel: sources == targets
  }

  /** {@link SemanticAnnotation Annotate} a particular match with a namespace and ID. */
  private SemanticAnnotation annotate(String[] ns_id, int begin, int end, JCas jcas) {
    final SemanticAnnotation ann = new SemanticAnnotation(jcas, begin, end);
    logger.log(Level.FINE, "annotating {0}:{1} at {2}:{3}", new Object[] { ns_id[0], ns_id[1],
        begin, end });
    ann.setAnnotator(URI);
    ann.setConfidence(1);
    ann.setIdentifier(ns_id[1]);
    ann.setNamespace(ns_id[0]);
    ann.addToIndexes();
    return ann;
  }
  
  @Override
  public void destroy() {
    for (String pattern : patternHits.keySet())
      logger.log(Level.INFO, pattern + " =hits=> " + patternHits.get(pattern));
  }
}
