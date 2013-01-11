package txtfnnl.uima.pattern;

import java.util.Iterator;

import txtfnnl.utils.StringUtils;

public class TokenLexer implements Iterable<String>, Iterator<String> {
  private final String[] tokens;
  private int idx;

  public TokenLexer(String expression) {
    tokens = expression.split(" ");
    idx = nextIdx(-1);
  }

  TokenLexer(String[] tokens) {
    this.tokens = tokens;
    idx = nextIdx(-1);
  }

  public boolean hasNext() {
    return idx < tokens.length;
  }

  private int nextIdx(int i) {
    while (++i < tokens.length)
      if (tokens[i] != null && tokens[i].length() > 0) break;
    return i;
  }

  public String next() {
    int i = idx;
    idx = nextIdx(idx);
    int len = tokens[i].length();
    if (len > 0 && tokens[i].charAt(len - 1) == '\\') {
      // last char is an escape: count how many escapes are at the end
      int count = 1;
      for (int j = len - 2; j > 0; j--) {
        if (tokens[i].charAt(j) == '\\') count++;
        else break;
      }
      // if it is an even number of escapes, return the token; otherwise join with the next
      if (count % 2 == 0) {
        return tokens[i];
      } else {
        if (!hasNext() || tokens[1 + i].length() == 0) return tokens[i].substring(0, len - 1) +
            " ";
        else return String.format("%s %s", tokens[i].substring(0, len - 1), next());
      }
    } else {
      // great - just return the token
      return tokens[i];
    }
  }

  public String peek() {
    String next = null;
    if (hasNext()) {
      int tmp = idx;
      next = next();
      idx = tmp;
    }
    return next;
  }

  public Iterator<String> iterator() {
    return this;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return StringUtils.join(' ', tokens);
  }

  public int offset() {
    return idx;
  }

  public boolean atChunkEnd() {
    int offset = idx;
    boolean atEnd = false;
    while (hasNext()) {
      String next = next();
      if ("?".equals(next) || ")".equals(next)) continue;
      if ("]".equals(next)) atEnd = true;
      break;
    }
    idx = offset;
    return atEnd;
  }

  public boolean maybeAtChunkEnd() {
    int offset = idx;
    boolean atEnd = false;
    while (hasNext()) {
      String next = next();
      if ("?".equals(next) || ")".equals(next) || "*".equals(next)) continue;
      else if ("]".equals(next)) atEnd = true;
      else if ("?".equals(peek())) continue;
      break;
    }
    idx = offset;
    return atEnd;
  }
}
