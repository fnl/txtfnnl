package txtfnnl.utils.stringsim;

import txtfnnl.utils.StringUtils;
import txtfnnl.utils.stringsim.Similarity;

/**
 * Calculate the Jaro-Winkler similarity between two strings.
 * <p>
 * Applies Winkler's boost modification on the Jaro score for common prefixes:
 * 
 * <pre>
 * score_JW = score_J + weight * len_commonPrefix * (1 - score_J)
 * </pre>
 * 
 * Where <code>score_J</code> is the Jaro score, <code>weight</code> is the weight of the
 * modification of the score (e.g., 0.1), and <code>len_commonPrefix</code> is the length of the
 * prefix the two strings have in common (if any, and bounded by a maximum allowed length, e.g.,
 * 4).
 * 
 * @param weight of the Jaro score boost modification (0 = no boosing)
 * @param length max. of prefix to allow for the Winkler boosting (0 = no boosing)
 * @author Florian Leitner
 */
public class JaroWinkler implements Similarity {
  public final static JaroWinkler DEFAULT_INSTANCE = new JaroWinkler();
  /** The Winkler boost factor. */
  final double winkler;
  /** The max. prefix length to consider for Winkler boosting. */
  final int prefix;

  /** A default Jaro-Winkler measure with a weight of 1/10th and a max. prefix length of 4. */
  protected JaroWinkler() {
    winkler = 0.1;
    prefix = 4;
  }

  /**
   * @param weight of the Jaro score boost modification (0.0 == no boosing)
   * @param length max. prefix length to consider for the Winkler boosting (0 == no boosing)
   */
  public JaroWinkler(double weight, int length) {
    winkler = weight;
    prefix = length;
  }

  public double similarity(String strA, String strB) {
    // exact matches: similarity = 1
    if (strA.equals(strB)) return 1.0;
    // ensure that B is of shorter or equal length as B
    if (strA.length() > strB.length()) {
      String tmp = strB;
      strB = strA;
      strA = tmp;
    }
    int lenA = strA.length(), cpcA = strA.codePointCount(0, lenA);
    // if A has zero characters: similarity = 0
    if (lenA == 0) return 0.0;
    int lenB = strB.length(), cpcB = strB.codePointCount(0, lenB), idxA, idxB, last;
    // make Unicode-safe and easier to handle the code points
    int[] cpA = StringUtils.toCodePointArray(strA, cpcA);
    boolean[] matchedA = new boolean[cpcA];
    int[] cpB = StringUtils.toCodePointArray(strB, cpcB);
    boolean[] matchedB = new boolean[cpcB];
    int matchWindow = Math.max(0, cpcB / 2 - 1); // half the max length rounded down, minus one
    int windowSize = matchWindow + 1; //cpcB - cpcA + 1; // add the extra length of B to the window
    // count the number of characters the two strings have in common
    int commons = 0;
    for (idxA = 0; idxA < cpcA; ++idxA) {
      // scan for the current character in strB's match window
      last = Math.min(cpcB, idxA + windowSize);
      for (idxB = Math.max(0, idxA - matchWindow); idxB < last; ++idxB) {
        if (!matchedB[idxB] && cpA[idxA] == cpB[idxB]) {
          commons++; // count common character
          matchedA[idxA] = true;
          matchedB[idxB] = true;
          break;
        }
      }
    }
    // no common characters: similarity = 0
    if (commons == 0) return 0.0;
    // count the number of transpositions among the common characters
    int transpositions = 0;
    idxB = 0;
    for (idxA = 0; idxA < cpcA; idxA++) {
      // it should be always safe to increment idxB because cpcA <= cpcB
      if (matchedA[idxA]) {
        while (!matchedB[idxB])
          ++idxB;
        if (cpA[idxA] != cpB[idxB]) ++transpositions;
        ++idxB;
      }
    }
    transpositions /= 2;
    // calculate Jaro score
    double commonsDouble = (double) commons;
    double score = commonsDouble / cpcA + commonsDouble / cpcB;
    score += (commonsDouble - (double) transpositions) / commonsDouble;
    score /= 3;
    if (winkler == 0.0 || prefix == 0) return score;
    // weight score with Winkler common prefix modification
    int common = 0; // common prefix size
    last = Math.min(prefix, cpcA);
    for (; common < last && cpA[common] == cpB[common]; ++common);
    return score + winkler * common * (1.0 - score);
  }
}
