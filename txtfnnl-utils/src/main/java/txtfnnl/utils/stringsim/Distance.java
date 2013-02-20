package txtfnnl.utils.stringsim;

/** A Strategy interface for string distance measures. */
public interface Distance {
  /**
   * Calculate the distance between two strings.
   * <p>
   * This method works with all <b>Unicode</b> characters.
   * 
   * @param strA to compare
   * @param strB to compare
   * @return the distance between the two strings, in the range [0,infinity].
   */
  public int distance(String strA, String strB);
}
