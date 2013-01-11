package txtfnnl.utils;

/**
 * Utility functions for working with Strings.
 * 
 * @author Florian Leitner
 */
public final class StringUtils {
  private StringUtils() {
    throw new AssertionError("n/a");
  }

  public static final String join(String... strings) {
    final StringBuffer b = new StringBuffer();
    for (String s : strings)
      b.append(s);
    return b.toString();
  }

  public static final String join(String using, String... strings) {
    final StringBuffer b = new StringBuffer();
    final int last = strings.length - 1;
    for (int i = 0; i <= last; i++) {
      b.append(strings[i]);
      if (i != last) b.append(using);
    }
    return b.toString();
  }

  public static final String join(char using, String... strings) {
    final StringBuffer b = new StringBuffer();
    final int last = strings.length - 1;
    for (int i = 0; i <= last; i++) {
      b.append(strings[i]);
      if (i != last) b.append(using);
    }
    return b.toString();
  }
}
