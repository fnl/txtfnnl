package txtfnnl.utils.stringsim;

import txtfnnl.utils.StringUtils;
import txtfnnl.utils.stringsim.Distance;
import txtfnnl.utils.stringsim.LeitnerLevenshtein;
import txtfnnl.utils.stringsim.Similarity;

/**
 * Calculate the Damerau-Levenshtein measure between two strings.
 * <p>
 * Damerau-Levenshtein essentially works just as the default Levenshtein measure, but treats
 * transpositions as single operations.
 * <p>
 * Similarity is defined as <code>1 - (D / N)</code> where <code>D</code> is the
 * Damerau-Levenshtein {@link DamerauLevenshtein#distance(String,String) distance} and
 * <code>N</code> is the length of the longer of the two input strings measured in <i>Unicode</i>
 * characters.
 * 
 * @author Florian Leitner
 */
public class DamerauLevenshtein extends LeitnerLevenshtein implements Distance, Similarity {
  @SuppressWarnings("hiding")
  public static final DamerauLevenshtein INSTANCE = new DamerauLevenshtein();

  protected DamerauLevenshtein() {
    super(1);
  }

  @Override
  public int distance(String strA, String strB) {
    // exact matches: distance = 0
    if (strA.equals(strB)) return 0;
    int lenA = strA.length(), cpcA = strA.codePointCount(0, lenA), idxA, offA, charA, lastA;
    int lenB = strB.length(), cpcB = strB.codePointCount(0, lenB), idxB;
    // return the max. distance if at least one string has zero length
    if (lenA == 0 || lenB == 0) return Math.max(cpcA, cpcB);
    int[] cpB = StringUtils.toCodePointArray(strB, cpcB);
    // for transpositions, we need the last two rows - so it is easier to use the whole matrix
    // (compared to a regular Levenshtein distance calculation)
    int[][] matrix = new int[cpcA + 1][cpcB + 1];
    for (idxA = 0; idxA < matrix.length; ++idxA)
      matrix[idxA][0] = idxA * factor;
    for (idxB = 1; idxB <= cpcB; ++idxB)
      matrix[0][idxB] = idxB * factor;
    for (idxA = 1, offA = 0, lastA = -1; offA < lenA; offA += StringUtils.charCount(strA, offA), ++idxA) {
      charA = strA.codePointAt(offA);
      for (idxB = 1; idxB <= cpcB; ++idxB) {
        matrix[idxA][idxB] = minimum(matrix[idxA - 1][idxB] + factor, matrix[idxA][idxB - 1] +
            factor, matrix[idxA - 1][idxB - 1] + (charA == cpB[idxB - 1] ? 0 : factor));
        // additional Damerau transposition rule:
        if (idxB > 1 && charA == cpB[idxB - 2] && lastA == cpB[idxB - 1])
          matrix[idxA][idxB] = Math.min(matrix[idxA][idxB], matrix[idxA - 2][idxB - 2] + factor);
      }
      lastA = charA;
    }
    return matrix[cpcA][cpcB];
  }
}
