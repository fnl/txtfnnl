package txtfnnl.utils;

import java.util.Iterator;

/**
 * Utility functions for working with Strings.
 * 
 * @author Florian Leitner
 */
public final class StringUtils {
  private StringUtils() {
    throw new AssertionError("n/a");
  }

  /** Create a code-point array from a string. */
  public static int[] toCodePointArray(String str) {
    return toCodePointArray(str, str.codePointCount(0, str.length()));
  }

  /** Create a code-point array from a string with a known number of code points. */
  public static int[] toCodePointArray(String str, int numberOfCodePoints) {
    int[] arr = new int[numberOfCodePoints];
    for (int cp = 0, off = 0; cp < numberOfCodePoints; off += charCount(str, cp++))
      arr[cp] = str.codePointAt(off);
    return arr;
  }

  /**
   * Return the Java {@link Character#charCount char count} of a Unicode character ("code point")
   * in a string at a given <code>offset</code>.
   */
  public static int charCount(String str, int offset) {
    return Character.charCount(str.codePointAt(offset));
  }

  public static final String join(String... strings) {
    final StringBuilder b = new StringBuilder();
    for (String s : strings)
      b.append(s);
    return b.toString();
  }

  public static final String join(Iterator<String> stringIt) {
    final StringBuilder b = new StringBuilder();
    while (stringIt.hasNext())
      b.append(stringIt.next());
    return b.toString();
  }

  public static final String join(String using, String... strings) {
    final StringBuilder b = new StringBuilder();
    final int last = strings.length - 1;
    for (int i = 0; i < last; ++i) {
      b.append(strings[i]);
      b.append(using);
    }
    if (last != -1) b.append(strings[last]);
    return b.toString();
  }

  public static final String join(String using, Iterator<String> stringIt) {
    final StringBuilder b = new StringBuilder();
    while (stringIt.hasNext()) {
      b.append(stringIt.next());
      if (stringIt.hasNext()) b.append(using);
    }
    return b.toString();
  }

  public static final String join(char using, String... strings) {
    final StringBuilder b = new StringBuilder();
    final int last = strings.length - 1;
    for (int i = 0; i < last; ++i) {
      b.append(strings[i]);
      b.append(using);
    }
    if (last != -1) b.append(strings[last]);
    return b.toString();
  }

  public static final String join(char using, Iterator<String> stringIt) {
    final StringBuilder b = new StringBuilder();
    while (stringIt.hasNext()) {
      b.append(stringIt.next());
      b.append(using);
    }
    return b.length() == 0 ? "" : b.replace(b.length() - 1, b.length(), "").toString();
  }
}
