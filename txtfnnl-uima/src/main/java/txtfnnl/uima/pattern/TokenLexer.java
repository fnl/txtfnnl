package txtfnnl.uima.pattern;

import java.util.Iterator;

public class TokenLexer implements Iterable<String>, Iterator<String> {
  private final String[] tokens;
  private int idx;

  public TokenLexer(String expression) {
    tokens = expression.split(" ");
    idx = 0;
  }

  TokenLexer(String[] tokens) {
    this.tokens = tokens;
    idx = 0;
  }

  public boolean hasNext() {
    return idx < tokens.length;
  }

  public String next() {
    int len = tokens[idx].length();
    if (len > 0 && tokens[idx].charAt(len - 1) == '\\') {
      int count = 1;
      for (int i = len - 2; i > 0; i--) {
        if (tokens[idx].charAt(i) == '\\') count++;
        else break;
      }
      if (count % 2 == 0) {
        return tokens[idx++];
      } else {
        int offset = idx++;
        return tokens[offset].substring(0, len - 1) + (hasNext() ? " " + next() : "");
      }
    } else {
      return tokens[idx++];
    }
  }

  public String peek() {
    int offset = idx;
    String next = next();
    idx = offset;
    return next;
  }

  public Iterator<String> iterator() {
    return this;
  }

  public void remove() {}
  
  @Override
  public String toString() {
    StringBuffer b = new StringBuffer();
    for (String s : tokens) {
      b.append(s);
      b.append(' ');
    }
    b.deleteCharAt(b.length() - 1);
    return b.toString();
  }

  public int offset() {
    return idx;
  }
}
