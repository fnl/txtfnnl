package txtfnnl.utils.stringsim;

import txtfnnl.utils.stringsim.JaroWinkler;

/**
 * Calculate the Jaro similarity score between two strings (without Winkler-boosting).
 * 
 * @author Florian Leitner
 */
public class Jaro extends JaroWinkler {
  @SuppressWarnings("hiding")
  public static final Jaro INSTANCE = new Jaro();

  protected Jaro() {
    super(0.0, 0);
  }
}
