package txtfnnl.utils.stringsim;

/** A Strategy interface for string similarity measures. */
public interface Similarity {
  /**
   * Calculate the similarity between two strings.
   * <p>
   * This method works with all <b>Unicode</b> characters.
   * 
   * @param strA to compare
   * @param strB to compare
   * @return the similarity between the two strings, in the range [0,1].
   */
  public double similarity(String strA, String strB);
}
