/* Created on May 14, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.resource.LineBasedStringMapResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;
import txtfnnl.utils.Offset;

/**
 * Filter (or select/whitelist) {@link SemanticAnnotation semantic annotations} based on the
 * contained and surrounding {@link TokenAnnotation tokens}, including the PoS tag of the semantic
 * annotation's (header) token.
 * <p>
 * The "surrounding" of the semantic annotation consists of the last non-overlapping token before
 * the annotation, the first non-overlapping token after the annotation, and possible affixes. An
 * annotation's prefix is the text span between the begin of the overlapping token and the actual
 * annotation; I.e., a prefix only exists if the annotation starts after the token. The same is the
 * case for a suffix, spanning from the end of the annotation to the end of the overlapping token.
 * 
 * @author Florian Leitner
 */
public class TokenBasedSemanticAnnotationFilter extends JCasAnnotator_ImplBase {
  // public configuration
  public static final String PARAM_ANNOTATOR_URI = "AnnotatorUri";
  @ConfigurationParameter(name = PARAM_ANNOTATOR_URI,
      description = "The TextAnnotation URI to filter or select.")
  private String annotatorUri;
  public static final String PARAM_NAMESPACE = "Namespace";
  @ConfigurationParameter(name = PARAM_NAMESPACE,
      description = "The TextAnnotation NS to filter or select.")
  private String namespace;
  public static final String PARAM_IDENTIFIER = "Identifier";
  @ConfigurationParameter(name = PARAM_IDENTIFIER,
      description = "The TextAnnotation ID to filter or select.")
  private String identifier;
  public static final String PARAM_SELECT_TOKENS = "SelectTokenMatches";
  @ConfigurationParameter(name = PARAM_SELECT_TOKENS,
      description = "Select (by setting this parameter to true) or filter (default) annotations "
          + "based on the token matches.",
      defaultValue = "false")
  private boolean doSelect;
  public static final String PARAM_POS_TAGS = "PosTags";
  @ConfigurationParameter(name = PARAM_POS_TAGS, description = "List of required PoS tag matches.")
  private String[] posTags;
  private Set<String> posTagSet = null;
  /**
   * Surrounding tokens and affixes to compare to the semantic annotation being checked.
   * <p>
   * StringMap keys: "before", "after", "prefix", and "suffix"; Anything else will be ignored.
   */
  public static final String MODEL_KEY_TOKEN_SETS = "TokenSets";
  @ExternalResource(key = MODEL_KEY_TOKEN_SETS, mandatory = false)
  private LineBasedStringMapResource<Set<String>> tokenSets;
  private Set<String> beforeSet = null;
  private Set<String> afterSet = null;
  private Set<String> prefixSet = null;
  private Set<String> suffixSet = null;
  // internal state
  private Logger logger;
  private int count;
  private int total;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public Builder() {
      this(TokenBasedSemanticAnnotationFilter.class);
    }

    /** Limit the semantic annotations to check to this annotator URI (default: any URI). */
    public Builder setAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_ANNOTATOR_URI, uri);
      return this;
    }

    /** Limit the semantic annotations to check to this namespace (default: any namespace). */
    public Builder setNamespace(String ns) {
      setOptionalParameter(PARAM_NAMESPACE, ns);
      return this;
    }

    /** Limit the semantic annotations to check to this identifier (default: any ID). */
    public Builder setIdentifier(String id) {
      setOptionalParameter(PARAM_IDENTIFIER, id);
      return this;
    }

    /**
     * If set, the PoS tag of the head token of the checked semantic annotation (and that covers
     * one or more tokens) must match to an entry in this list, otherwise the annotation is removed
     * (i.e., whitelisting).
     * <p>
     * If the semantic annotation spans multiple tokens, its <strong>last</strong> ("head") token
     * is used as reference. Unlike other parameters, if a semantic annotation has no PoS, and this
     * parameter is set, it is <em>always</em> filtered. In other words,
     * {@link TokenBasedSemanticAnnotationFilter.Builder#whitelist() whitelisting} has no influence
     * on this parameter, because if a PoS tag list is set, this always indicates "whitelisting" of
     * the relevant tags. <strong>However</strong>, if the semantic annotation has no tokens, it is
     * not not filtered by this parameter, even if a PoS tag list was set!
     * 
     * @param posTags to select/whitelist
     * @return the configuation builder
     */
    public Builder setPosTags(String[] posTags) {
      setOptionalParameter(PARAM_POS_TAGS, posTags);
      return this;
    }

    /**
     * Instead of filtering (removing) matches of tokens, filter (remove) all semantic annotations
     * that have no match to <string>any</strong> of the defined sets, thereby <em>selecting</em>
     * for annotation with matches.
     */
    public Builder whitelist() {
      setOptionalParameter(PARAM_SELECT_TOKENS, true);
      return this;
    }

    /**
     * Filter (or select) annotations where the token <code>before</code> or <code>after</code> the
     * annotation or the annotation's <code>prefix</code> or <code>suffix</code> matches to any
     * String defined in the sets of this resource keyed with any of these names.
     * <p>
     * The resource is assumed to be a {@link LineBasedStringMapResource} mapping to collections of
     * String Sets. The mappings should be keyed by the above codewords. Any mapping using another
     * codeword is simply ignored.
     */
    public Builder setSurroundingTokens(ExternalResourceDescription desc) {
      setOptionalParameter(MODEL_KEY_TOKEN_SETS, desc);
      return this;
    }
  }

  /** Create a new AE configuration builder. */
  public static Builder configure() {
    return new Builder();
  }

  /** Initialize the filter/selection sets. */
  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    posTagSet = makeSetIfProvided(posTags);
    beforeSet = tokenSets.get("before");
    afterSet = tokenSets.get("after");
    prefixSet = tokenSets.get("prefix");
    suffixSet = tokenSets.get("suffix");
    count = 0;
    total = 0;
    logger.log(Level.CONFIG, "received {0} PoS tags and {1}/{2}/{3}/{4} tokens", new Object[] {
        (posTagSet == null) ? -1 : posTagSet.size(), (beforeSet == null) ? -1 : beforeSet.size(),
        (prefixSet == null) ? -1 : prefixSet.size(), (suffixSet == null) ? -1 : suffixSet.size(),
        (afterSet == null) ? -1 : afterSet.size(), });
  }

  /** Initialization helper to create the sets. */
  private Set<String> makeSetIfProvided(String[] items) {
    if (items != null && items.length > 0) {
      Set<String> s = new HashSet<String>();
      for (String i : items)
        s.add(i);
      return s;
    } else {
      return null;
    }
  }

  /**
   * Process the filtering or selection of the semantic annotations.
   * 
   * @see org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(org.apache.uima.jcas.JCas)
   */
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    FSMatchConstraint cons = SemanticAnnotation.makeConstraint(jcas, annotatorUri, namespace,
        identifier);
    FSIterator<Annotation> iter = jcas.createFilteredIterator(
        SemanticAnnotation.getIterator(jcas), cons);
    Map<SemanticAnnotation, Collection<TokenAnnotation>> coveredTokens = JCasUtil.indexCovered(
        jcas, SemanticAnnotation.class, TokenAnnotation.class);
    Map<SemanticAnnotation, Collection<TokenAnnotation>> innerTokens = JCasUtil.indexCovering(
        jcas, SemanticAnnotation.class, TokenAnnotation.class);
    TokenSurrounding surr = null;
    Offset last = null;
    boolean autoRemove = false;
    List<SemanticAnnotation> removalBuffer = new LinkedList<SemanticAnnotation>();
    while (iter.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) iter.next();
      ++total;
      if (!ann.getOffset().equals(last)) {
        surr = new TokenSurrounding(jcas, ann, coveredTokens, innerTokens);
        last = ann.getOffset();
        autoRemove = false;
      } else {
        if (autoRemove) removalBuffer.add(ann);
        continue;
      }
      if (posTagSet != null && surr.prefix != null &&
          remove(surr.prefix.getPos(), posTagSet, true)) {
        removalBuffer.add(ann);
        autoRemove = true;
        continue;
      }
      if (checkVicinity(beforeSet, surr.before) ||
          checkVicinity(afterSet, surr.after) ||
          checkAffix(prefixSet, (surr.prefix == null) ? null : surr.prefix.getCoveredText()
              .substring(0, ann.getBegin() - surr.prefix.getBegin())) ||
          checkAffix(suffixSet, (surr.suffix == null) ? null : surr.suffix.getCoveredText()
              .substring(ann.getEnd() - surr.suffix.getBegin()))) {
        removalBuffer.add(ann);
        autoRemove = true;
      }
    }
    logger.log(Level.FINE, "removing " + removalBuffer.size() + " semantic annotations");
    count += removalBuffer.size();
    for (SemanticAnnotation ann : removalBuffer)
      ann.removeFromIndexes();
  }

  private boolean checkAffix(Set<String> aSet, String text) {
    if (aSet != null) {
      if (text == null) {
        if (doSelect) return true;
      } else if (remove(text, aSet, doSelect)) { return true; }
    }
    return false;
  }

  private boolean checkVicinity(Set<String> aSet, TokenAnnotation aToken) {
    if (aSet != null) {
      if (aToken == null) {
        if (doSelect) return true;
      } else if (remove(aToken.getCoveredText(), aSet, doSelect)) { return true; }
    }
    return false;
  }

  /** Return <code>true</code> if the value for the given set indicates removal of the annotation. */
  private static boolean remove(String value, Set<String> testSet, boolean doSelect) {
    if (doSelect && !testSet.contains(value)) return true;
    else if (!doSelect && testSet.contains(value)) return true;
    else return false;
  }

  /**
   * A structure to hold the "surrounding" (before, at, and after) token state relative to a given
   * semantic annotation.
   */
  private static class TokenSurrounding {
    /** The token before the relevant semantic annotation. */
    final TokenAnnotation before;
    /** The (last) token covering the relevant semantic annotation. */
    final TokenAnnotation prefix;
    /**
     * The first token covering the relevant semantic annotation or <code>null</code>.
     * <p>
     * This value only gets set if there are multiple tokens that span the relevant
     * {@link SemanticAnnotation}.
     */
    final TokenAnnotation suffix;
    /** The token after the relevant semantic annotation. */
    final TokenAnnotation after;

    /**
     * Establish the surrounding in the CAS given the semantic annotation.
     * <p>
     * To make this lookup more efficient, a pre-established mapping of tokens covered by semantic
     * annotations as well as semantic annotations contained within tokens need to be provided as
     * additional arguments.
     */
    public TokenSurrounding(JCas jcas, SemanticAnnotation ann,
        Map<SemanticAnnotation, Collection<TokenAnnotation>> tokensCoveredBySemAnn,
        Map<SemanticAnnotation, Collection<TokenAnnotation>> tokensContainingSemAnns) {
      // prefix and suffix
      Collection<TokenAnnotation> tokens = tokensCoveredBySemAnn.get(ann);
      if (tokens == null) tokens = tokensContainingSemAnns.get(ann);
      else if (tokensContainingSemAnns.containsKey(ann)) {
        tokens = new LinkedList<TokenAnnotation>(tokens);
        tokens.addAll(tokensContainingSemAnns.get(ann));
      }
      if (tokens == null || tokens.size() == 0) {
        prefix = null;
        suffix = null;
      } else {
        if (tokens.size() > 1) {
          TokenAnnotation[] multi = tokens.toArray(new TokenAnnotation[tokens.size()]);
          Arrays.sort(multi, new Comparator<TokenAnnotation>() {
            public int compare(TokenAnnotation a, TokenAnnotation b) {
              return a.getOffset().compareTo(b.getOffset());
            }
          });
          if (multi[0].getBegin() > ann.getBegin()) prefix = null;
          else prefix = multi[0];
          if (multi[multi.length - 1].getEnd() < ann.getEnd()) suffix = null;
          else suffix = multi[multi.length - 1];
        } else {
          TokenAnnotation tmp = tokens.iterator().next();
          prefix = (tmp.getBegin() > ann.getBegin() || tmp.getEnd() < ann.getEnd()) ? null : tmp;
          suffix = prefix;
        }
      }
      // before
      List<TokenAnnotation> r = JCasUtil.selectPreceding(jcas, TokenAnnotation.class, ann, 1);
      if (r.size() == 1 && ann.getBegin() - r.get(0).getEnd() < 10) before = r.get(0);
      else before = null;
      // after
      r = JCasUtil.selectFollowing(jcas, TokenAnnotation.class, ann, 1);
      if (r.size() == 1 && r.get(0).getBegin() - ann.getEnd() < 10) after = r.get(0);
      else after = null;
      // ensure correctness of offsets
      if (before != null && before.getEnd() > ann.getBegin())
        throw new RuntimeException("before does not end before ann @ " +
            ann.getOffset().toString() + "\nann= '" + ann.getCoveredText() + "'\n" +
            this.toString());
      if (after != null && after.getBegin() < ann.getEnd())
        throw new RuntimeException("after does not begin after ann @ " +
            ann.getOffset().toString() + "\nann= '" + ann.getCoveredText() + "'\n" +
            this.toString());
      if (suffix != null && ann.getEnd() > suffix.getEnd())
        throw new RuntimeException("suffix does not overlap with ann end @ " +
            ann.getOffset().toString() + "\nann= '" + ann.getCoveredText() + "'\n" +
            this.toString());
      if (prefix != null && ann.getBegin() < prefix.getBegin())
        throw new RuntimeException("prefix does not overlap with ann begin @ " +
            ann.getOffset().toString() + "\nann= '" + ann.getCoveredText() + "'\n" +
            this.toString());
    }

    @Override
    public String toString() {
      return "surr.before=" + makeString(before) + " surr.first=" + makeString(suffix) +
          " surr.current=" + makeString(prefix) + " surr.after=" + makeString(after);
    }

    private static String makeString(TokenAnnotation tok) {
      if (tok == null) return "N/A";
      else return "'" + tok.getCoveredText() + "' @ " + tok.getOffset().toString();
    }
  }
  
  @Override
  public void destroy() {
    super.destroy();
    logger.log(Level.INFO, "token-filtered " + count + "/" + total + " semantic annotations");
  }
}
