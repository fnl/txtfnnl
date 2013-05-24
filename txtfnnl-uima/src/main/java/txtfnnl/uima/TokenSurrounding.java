package txtfnnl.uima;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.jcas.JCas;

import org.uimafit.util.JCasUtil;

import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * A structure to hold the "surrounding" (before, prefix, suffix, and after) token state relative
 * to any kind of {@link TextAnnotation text annotation}.
 */
public class TokenSurrounding {
  /** The token before the relevant annotation or <code>null</code>. */
  public final TokenAnnotation before;
  /** The (first) token covering the begin of the relevant annotation or <code>null</code>. */
  public final TokenAnnotation prefix;
  /**
   * The (last) token covering the end of the relevant annotation or <code>null</code>.
   * <p>
   * This object may be the same as {@link #prefix} if only one token spans the relevant
   * annotation.
   */
  public final TokenAnnotation suffix;
  /** The token after the relevant annotation or <code>null</code>. */
  public final TokenAnnotation after;

  /**
   * Establish the surrounding in the CAS given the semantic annotation.
   * <p>
   * To make this lookup more efficient, a pre-established mapping of tokens covered by semantic
   * annotations as well as semantic annotations contained within tokens need to be provided as
   * additional arguments.
   */
  public <T extends TextAnnotation> TokenSurrounding(JCas jcas, T ann,
      Map<T, Collection<TokenAnnotation>> tokensCoveredByAnn,
      Map<T, Collection<TokenAnnotation>> tokensContainingAnns) {
    // prefix and suffix
    Collection<TokenAnnotation> tokens = tokensCoveredByAnn.get(ann);
    if (tokens == null) tokens = tokensContainingAnns.get(ann);
    else if (tokensContainingAnns.containsKey(ann)) {
      tokens = new LinkedList<TokenAnnotation>(tokens);
      tokens.addAll(tokensContainingAnns.get(ann));
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
      throw new RuntimeException("before does not end before ann @ " + ann.getOffset().toString() +
          "\nann= '" + ann.getCoveredText() + "'\n" + this.toString());
    if (after != null && after.getBegin() < ann.getEnd())
      throw new RuntimeException("after does not begin after ann @ " + ann.getOffset().toString() +
          "\nann= '" + ann.getCoveredText() + "'\n" + this.toString());
    if (suffix != null && ann.getEnd() > suffix.getEnd())
      throw new RuntimeException("suffix does not overlap with ann end @ " +
          ann.getOffset().toString() + "\nann= '" + ann.getCoveredText() + "'\n" + this.toString());
    if (prefix != null && ann.getBegin() < prefix.getBegin())
      throw new RuntimeException("prefix does not overlap with ann begin @ " +
          ann.getOffset().toString() + "\nann= '" + ann.getCoveredText() + "'\n" + this.toString());
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
