package txtfnnl.uima.pattern;

import java.util.Iterator;

import txtfnnl.utils.StringUtils;

/**
 * Scan a syntax pattern regex String for terminals.
 * 
 * @author Florian Leitner
 */
public class RegExLexer implements Iterable<String>, Iterator<String> {
  private final String[] tokens;
  private int idx;

  public RegExLexer(String expression) {
    tokens = expression.split(" "); // NB: don't split on " +" or "\s+"
    // otherwise, two tokens "t1\ t2" (where t1 ends in a space) could not be discerned from a
    // single token "t1A\ t1B" (with a separating space)
    idx = nextIdx(-1);
  }

  RegExLexer(String[] tokens) {
    this.tokens = tokens;
    idx = nextIdx(-1);
  }

  public boolean hasNext() {
    return idx < tokens.length;
  }

  private int nextIdx(int i) {
    // skip "empty" tokens in the array (stemming from tokens separated by two or more spaces)
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
      // no escape at end - just return the token
      return tokens[i];
    }
  }

  /**
   * Return the token that would be returned by a call to {@link #next()}.
   * 
   * @return the next token or <code>null</null>
   */
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

  /** @throws UnsupportedOperationException */
  public void remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return StringUtils.join(' ', tokens);
  }

  /** Return the offset of the lexer in the <b>regular expression string<b>. */
  public int offset() {
    int offset = (idx == tokens.length && idx > 0) ? -1 : 0;
    for (int i = 0; i < idx; i++)
      offset += tokens[i].length();
    return offset + idx; // add in the token-separating spaces lengths
  }

  /** Return <code>true</code> if the next token is a "?" or "*" quantifier. */
  public boolean isOptional() {
    String next = peek();
    return "?".equals(next) || "*".equals(next);
  }

  /** Return <code>true</code> if the next token is a "*" or "+" quantifier. */
  public boolean isRepeated() {
    String next = peek();
    return "+".equals(next) || "*".equals(next);
  }

  /**
   * Return <code>true</code> if a chunk-end "]" token is coming up before any annotation token
   */
  public boolean atChunkEnd() {
    int tmp = idx;
    boolean atEnd = false;
    while (hasNext()) {
      String next = next();
      if (RegExParser.Q_SET.contains(next)) continue;
      else if (")".equals(next)) continue;
      else if ("]".equals(next)) atEnd = true;
      break;
    }
    idx = tmp;
    return atEnd;
  }

  /**
   * Return <code>true</code> if a chunk-end "]" token is coming up before any annotation token
   * (".", "stem", "PoS_stem", "token_PoS_stem") or if the next annotation tokens before the
   * chunk-end token are all optional ("*", "?" quantifiers).
   */
  public boolean maybeAtChunkEnd() {
    int offset = idx;
    boolean atEnd = false;
    if (isOptional()) next();
    while (hasNext()) {
      String next = next();
      if (isOptional()) {
        next();
        continue;
      }
      if ("(".equals(next) || ")".equals(next)) continue;
      else if ("]".equals(next)) atEnd = true;
      break;
    }
    idx = offset;
    return atEnd;
  }

  /**
   * Return <code>true</code> if a chunk-begin "[" token had come up before this token or if the
   * previous annotation tokens (".", "stem", "PoS_stem", "token_PoS_stem") before the chunk-begin
   * token are all optional ("*", "?" quantifiers) .
   */
  public boolean maybeAtChunkBegin() {
    int offset = idx - 1;
    boolean atBegin = false;
    // check for a chunk-begin token before the current idx, but stop at end-chunk tokens
    while (offset >= 0 && !"[".equals(tokens[offset]) && !"]".equals(tokens[offset]))
      offset--;
    if (offset >= 0 && "[".equals(tokens[offset])) {
      int tmp = idx;
      idx = offset;
      while (idx < tmp) {
        next();
        if (isOptional()) next();
        else if (idx == tmp) atBegin = true;
        else break;
      }
      idx = tmp;
    }
    return atBegin;
  }
}
