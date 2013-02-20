package txtfnnl.utils.stringsim;

import txtfnnl.utils.StringUtils;
import txtfnnl.utils.stringsim.Distance;
import txtfnnl.utils.stringsim.Similarity;

/**
 * Calculate a Leitner-modified Levenshtein measure between two strings A and B.
 * <p>
 * Special Leitner cost function: for character case mismatches only, the distance cost is halved.
 * For example, the distance between "aa" and "bb" is four (i.e., doubled!), while the distance
 * between "aa" and "AA" is two, not zero; In other words, regular operations count as two, while
 * case mismatches count as distance one in the {@link LeitnerLevenshtein#distance(String, String)
 * distance} result.
 * <p>
 * Similarity is defined as <code>1 - (D / N)</code> where <code>D</code> is the special
 * {@link LeitnerLevenshtein#distance(String,String) distance} and <code>N</code> is twice the
 * length of the longer of the two input strings measured in <i>Unicode</i> characters.
 * 
 * @author Florian Leitner
 */
public class LeitnerLevenshtein implements Distance, Similarity {
  public static final LeitnerLevenshtein INSTANCE = new LeitnerLevenshtein();
  protected final int factor;

  protected LeitnerLevenshtein() {
    factor = 2;
  }

  /** To instantiate another Levenshtein measure with a different cost factor. */
  protected LeitnerLevenshtein(int factor) {
    this.factor = factor;
  }

  public int distance(String strA, String strB) {
    if (strA.equals(strB)) return 0;
    int lenA = strA.length(), cpcA = strA.codePointCount(0, lenA), idxA, offA;
    int lenB = strB.length(), cpcB = strB.codePointCount(0, lenB), idxB;
    // return the max. distance if at least one string has zero length
    if (cpcA == 0 || cpcB == 0) return Math.max(cpcA, cpcB) * factor;
    // otherwise calculate the distance using dynamic programming with the last and current row
    int[] last = new int[cpcB + 1];
    int[] current = new int[cpcB + 1];
    int[] tmp;
    int[] cpB = StringUtils.toCodePointArray(strB, cpcB);
    for (idxB = 0; idxB < last.length; ++idxB)
      last[idxB] = idxB * factor;
    for (idxA = 1, offA = 0; offA < lenA; offA += StringUtils.charCount(strA, offA), ++idxA) {
      current[0] = idxA * factor;
      for (idxB = 0; idxB < cpcB; ++idxB)
        current[idxB + 1] = minimum(last[idxB + 1] + factor, current[idxB] + factor, last[idxB] +
            cost(strA.codePointAt(offA), cpB[idxB]));
      tmp = last;
      last = current;
      current = tmp;
    }
    return last[cpcB];
  }

  public double similarity(String strA, String strB) {
    // exact matches: similarity = 1
    if (strA.equals(strB)) return 1.0;
    int lenA = strA.length();
    int lenB = strB.length();
    // one string has zero length: similarity = 0
    if (lenA == 0 || lenB == 0) return 0.0;
    int cpcA = strA.codePointCount(0, lenA);
    int cpcB = strB.codePointCount(0, lenB);
    // calculate the distance and return the similarity := 1 - distance / length
    return 1.0 - ((double) distance(strA, strB)) / (Math.max(cpcA, cpcB) * factor);
  }

  /**
   * Leitner's case-dependent cost function.
   * <p>
   * 
   * @return 0 for matches, 1 for case-insensitive matches, and 2 for mismatches
   */
  protected int cost(int target, int other) {
    if (target == other) return 0;
    else if (Character.toLowerCase(target) == Character.toLowerCase(other)) return 1;
    else return 2;
  }

  /** Return the smallest of three integers. */
  protected int minimum(int a, int b, int c) {
    return Math.min(Math.min(a, b), c);
  }
}
