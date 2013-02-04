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

  public static final String join(String... strings) {
    final StringBuffer b = new StringBuffer();
    for (String s : strings)
      b.append(s);
    return b.toString();
  }

  public static final String join(Iterator<String> stringIt) {
    final StringBuffer b = new StringBuffer();
    while (stringIt.hasNext())
      b.append(stringIt.next());
    return b.toString();
  }

  public static final String join(String using, String... strings) {
    final StringBuffer b = new StringBuffer();
    final int last = strings.length - 1;
    for (int i = 0; i < last; ++i) {
      b.append(strings[i]);
      b.append(using);
    }
    if (last != -1) b.append(strings[last]);
    return b.toString();
  }

  public static final String join(String using, Iterator<String> stringIt) {
    final StringBuffer b = new StringBuffer();
    while (stringIt.hasNext()) {
      b.append(stringIt.next());
      if (stringIt.hasNext()) b.append(using);
    }
    return b.toString();
  }

  public static final String join(char using, String... strings) {
    final StringBuffer b = new StringBuffer();
    final int last = strings.length - 1;
    for (int i = 0; i < last; ++i) {
      b.append(strings[i]);
      b.append(using);
    }
    if (last != -1) b.append(strings[last]);
    return b.toString();
  }
  
  public static final String join(char using, Iterator<String> stringIt) {
    final StringBuffer b = new StringBuffer();
    while (stringIt.hasNext()) {
      b.append(stringIt.next());
      b.append(using);
    }
    return b.length() == 0 ? "" : b.replace(b.length() - 1, b.length(), "").toString();
  }


}
