/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import txtfnnl.uima.UIMAUtils;
import txtfnnl.uima.Views;
import txtfnnl.uima.pattern.SyntaxPattern;
import txtfnnl.uima.resource.LineBasedStringArrayResource;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;
import es.fnl.fsm.Matcher;
import es.fnl.fsm.Pattern;

/**
 * Annotate SOFAs based on pattern matching of token sequences using a finite state machine.
 * <p>
 * See {@link txtfnnl.uima.pattern.SyntaxPattern} for a description of the pattern language. This
 * AE provides a regular expression language for UIMA that can be used to annotate pattern-based
 * {@link RelationshipAnntation relationships} between {@link SemanticAnnotation semantic entities}.
 * <p>
 * Matching patterns and any capture groups within the patterns are {@link SemanticAnnotation
 * semantically annotated} using the namespace and identifier values defined in the
 * {@link LineBasedStringArrayResource pattern resource}. A pattern resource line first contains
 * the pattern itself, then a namespace, identifier pair for the semantic or relationship
 * annotation of the entire matched pattern and finally namespace, identifier pairs for all
 * (optional) semantic annotations of capture groups. If only the capture groups should be
 * annotated, the two namespace, identifier pair should be left empty. If the annotations are
 * omitted entirely, default values for the namespace and identifier annotation of the whole
 * matched pattern are used. Patterns, namespaces, and identifiers should all be separated with a
 * (default: tab) separator defined via the {@link #MODEL_KEY_PATTERN_RESOURCE pattern resource
 * model}.
 * 
 * @author Florian Leitner
 */
public class SyntaxPatternAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator (namespace and ID are defined dynamically). */
  public static final String URI = SyntaxPatternAnnotator.class.getName();
  protected Logger logger;
  // TODO: implement token namespace via global NS settings
  private String tokenNamespace = "http://nlp2rdf.lod2.eu/schema/doc/sso/";
  /** The key used for the LineBasedStringArrayResource. */
  public static final String MODEL_KEY_PATTERN_RESOURCE = "SyntaxPatterns";
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
  public static final String PARAM_DEFAULT_IDENTIFIER = "DefaultIdentifier";
  @ConfigurationParameter(name = PARAM_DEFAULT_IDENTIFIER, defaultValue = "Phrase")
  private String defaultIdentifier;
  /** Fallback if no annotation namespace for a pattern are provided in the resource. */
  public static final String PARAM_DEFAULT_NAMESPACE = "DefaultNamespace";
  @ConfigurationParameter(name = PARAM_DEFAULT_NAMESPACE,
      defaultValue = "http://nlp2rdf.lod2.eu/schema/doc/sso/")
  private String defaultNamespace;

  private class MatchContainer {
    final int[] offsets;
    final List<String[]> nsidPairs;

    MatchContainer(int[] o, List<String[]> nsids) {
      offsets = o;
      nsidPairs = nsids;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || !(other instanceof MatchContainer)) return false;
      MatchContainer o = (MatchContainer) other;
      if (offsets.length != o.offsets.length) return false;
      if (nsidPairs.size() != o.nsidPairs.size()) return false;
      for (int i = offsets.length - 1; i >= 0; i--)
        if (offsets[i] != o.offsets[i]) return false;
      for (int i = nsidPairs.size() - 1; i >= 0; i--)
        if (!nsidPairs.get(i)[0].equals(o.nsidPairs.get(i)[0]) ||
            !nsidPairs.get(i)[1].equals(o.nsidPairs.get(i)[1])) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int code = 17;
      for (int i : offsets)
        code *= 31 + i;
      for (String[] nsid : nsidPairs)
        for (String i : nsid)
          code *= 31 + i.hashCode();
      return code;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(Arrays.toString(offsets));
      sb.append(":{");
      for (String[] nsid : nsidPairs) {
        sb.append(nsid[0]);
        sb.append(':');
        sb.append(nsid[1]);
        sb.append(',');
      }
      sb.setCharAt(sb.length() - 1, '}');
      return sb.toString();
    }
  }

  /**
   * Configure a new descriptor with a pattern file resource.
   * 
   * @param patterns to match
   * @param separator between values in the pattern resource
   * @param removeUnmatched remove sentence annotations where none of the patterns matched
   * @param namespace default namespace for the semantic annotations
   * @param identifier default identifier for the semantic annotations
   * @return a configured AE description
   * @throws UIMAException
   * @throws IOException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(File patterns, String separator,
      final boolean removeUnmatched, final String namespace, final String identifier)
      throws UIMAException, IOException {
    final ExternalResourceDescription patternResource = LineBasedStringArrayResource.configure(
        "file:" + patterns.getAbsolutePath(), separator);
    return AnalysisEngineFactory.createPrimitiveDescription(SyntaxPatternAnnotator.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(MODEL_KEY_PATTERN_RESOURCE, patternResource);
            put(PARAM_REMOVE_UNMATCHED, removeUnmatched);
            put(PARAM_DEFAULT_NAMESPACE, namespace);
            put(PARAM_DEFAULT_IDENTIFIER, identifier);
          }
        }));
  }

  /**
   * Default configuration only requires the pattern resource file.
   * 
   * @throws IOException
   */
  public static AnalysisEngineDescription configure(File patterns) throws UIMAException,
      IOException {
    return SyntaxPatternAnnotator.configure(patterns, null, false, null, null);
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
          patterns.put(pattern[0], SyntaxPattern.compile(pattern[0]));
          List<String[]> anns;
          if (pattern.length > 1) {
            anns = new ArrayList<String[]>((pattern.length - 1) / 2);
            countIdx = (pattern.length == 3) ? 0 : 2;
            for (int i = 1; i < pattern.length; i += 2) {
              if (pattern[i].length() > 0 && pattern[i + 1].length() > 0) {
                // store rel ns:id for the pattern (i==1) or sem ns:id for capture group (i>1)
                anns.add(new String[] { pattern[i], pattern[i + 1] });
              } else if (i == 0 && pattern.length > 4) {
                // if annotating groups only, don't store a ns:id pairs for the whole pattern
                countIdx = 1;
                anns.add(new String[0]);
              } else {
                // empty ns:id pairs for capture groups use the default ns:id values
                anns.add(defaultNsId);
              }
            }
            counts[countIdx]++;
          } else {
            // annotate the entire pattern semantically with the default ns:id
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
    // collect a list of done semantic annotations keyed by the position of the first and last
    // token in the TokenAnnotation list ("tokens") to avoid annotating the same segment twice
    Map<int[], List<SemanticAnnotation>> done = new HashMap<int[], List<SemanticAnnotation>>();
    Set<MatchContainer> annotated = new HashSet<MatchContainer>();
    for (String expr : matchers.keySet()) {
      final Matcher<TokenAnnotation> matcher = matchers.get(expr).reset(tokens);
      int offset = 0; // detect partial overlaps
      int nextOffset = 0;
      while (matcher.find(offset)) {
        for (int two = 0; two < 2; two++) {
          if (two == 1) {
            matcher.greedy = true;
            matcher.find(matcher.start());
          } else {
            nextOffset = matcher.end();
          }
          patternHits.put(expr, patternHits.get(expr) + 1);
          logger.log(Level.FINE, "''{0}'' matched", expr);
          final List<String[]> annList = annotations.get(expr);
          // skip/continue on already made annotations:
          final MatchContainer mc = new MatchContainer(matcher.groups(), annList);
          if (annotated.contains(mc)) continue;
          else annotated.add(mc);
          matched = true;
          if (annList.size() == 1) { // annotate case 1.
            semanticAnnotationOfEntirePattern(annList, matcher, tokens, jcas, done);
          } else if (annList.get(0).length == 0) { // annotate case 2.
            try {
              semanticAnnotationOfCaptureGroups(annList, matcher, tokens, jcas, done);
            } catch (IndexOutOfBoundsException e) {
              logger.log(Level.SEVERE, "less annotations than capture groups in pattern ''{0}''",
                  expr);
              throw new AnalysisEngineProcessException(e);
            }
          } else { // annotate case 3.
            try {
              semanticRelationshipAnnotationOfPattern(annList, matcher, tokens, jcas, done);
            } catch (IndexOutOfBoundsException e) {
              logger.log(Level.SEVERE, "more annotations than capture groups in pattern ''{0}''",
                  expr);
            }
          }
        }
        matcher.greedy = false;
        offset = matcher.start();
        if (tokens.get(offset).getChunk() != null) {
          String chunk = tokens.get(offset).getChunk();
          while (chunk.equals(tokens.get(offset).getChunk()) && offset < matcher.end())
            offset++;
        } else {
          offset = nextOffset;
        }
      }
    }
    return matched;
  }

  private void semanticAnnotationOfCaptureGroups(final List<String[]> annList,
      final Matcher<TokenAnnotation> matcher, final List<TokenAnnotation> tokens, JCas jcas,
      Map<int[], List<SemanticAnnotation>> done) throws AnalysisEngineProcessException {
    for (int i = 1; i <= matcher.groupCount(); i++) {
      if (matcher.start(i) != matcher.end(i)) // skip/ignore unmatched groups
        annotateOrGet(annList.get(i), matcher.start(i), matcher.end(i) - 1, jcas, done, tokens);
    }
  }

  private void semanticAnnotationOfEntirePattern(final List<String[]> annList,
      final Matcher<TokenAnnotation> matcher, final List<TokenAnnotation> tokens, JCas jcas,
      Map<int[], List<SemanticAnnotation>> done) {
    annotateOrGet(annList.get(0), matcher.start(), matcher.end() - 1, jcas, done, tokens);
  }

  private void semanticRelationshipAnnotationOfPattern(final List<String[]> annList,
      final Matcher<TokenAnnotation> matcher, final List<TokenAnnotation> tokens, JCas jcas,
      Map<int[], List<SemanticAnnotation>> done) {
    final RelationshipAnnotation rel = new RelationshipAnnotation(jcas);
    final FSArray groups = new FSArray(jcas, annList.size() - 1);
    rel.setAnnotator(URI);
    rel.setIdentifier(annList.get(0)[0]);
    rel.setNamespace(annList.get(0)[1]);
    rel.setConfidence(1);
    for (int i = 1; i < annList.size(); i++) {
      if (matcher.start(i) != matcher.end(i)) { // skip/ignore unmatched groups
        groups
            .set(
                i - 1,
                annotateOrGet(annList.get(i), matcher.start(i), matcher.end(i) - 1, jcas, done,
                    tokens));
      }
    }
    rel.setSources(groups);
    rel.setTargets(groups); // undirected rel: sources == targets
  }

  private SemanticAnnotation annotateOrGet(String[] ns_id, int first, int last, JCas jcas,
      Map<int[], List<SemanticAnnotation>> done, final List<TokenAnnotation> tokens) {
    int[] positions = { first, last };
    if (done.containsKey(positions)) {
      for (SemanticAnnotation ann : done.get(positions)) {
        if (ann.getNamespace().equals(ns_id[0]) && ann.getIdentifier().equals(ns_id[1]))
          return ann;
      }
    } else {
      done.put(positions, new LinkedList<SemanticAnnotation>());
    }
    SemanticAnnotation ann = annotate(ns_id, tokens.get(positions[0]).getBegin(),
        tokens.get(positions[1]).getEnd(), jcas);
    done.get(positions).add(ann);
    return ann;
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
