/* Created on Dec 19, 2012 by Florian Leitner.
 * Copyright 2012. All rights reserved. */
package txtfnnl.uima.pattern;

import java.util.regex.Pattern;

import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * TokenAnnotation element matcher for a pattern matching implementation.
 * 
 * @author Florian Leitner
 */
public class TokenTransition extends TokenLambdaTransition {
  final Pattern text;
  final Pattern pos;
  final Pattern stem;
  final Pattern chunk;
  final boolean chunkBegin;
  final boolean chunkEnd;

  @SuppressWarnings("unused")
  private TokenTransition() {
    throw new AssertionError("n/a (use EpsilonTokenTransitions)");
  }

  public TokenTransition(String text, String pos, String stem, String chunk) {
    this(text, pos, stem, chunk, false, false);
  }

  public TokenTransition(String text, String pos, String stem, String chunk, boolean chunkBegin,
      boolean chunkEnd) {
    this.text = "*".equals(text) ? null : Pattern.compile(text);
    this.pos = "*".equals(pos) ? null : Pattern.compile(pos);
    this.stem = "*".equals(stem) ? null : Pattern.compile(stem);
    this.chunk = "*".equals(chunk) ? null : Pattern.compile(chunk);
    this.chunkBegin = chunkBegin;
    this.chunkEnd = chunkEnd;
  }

  @Override
  public boolean matches(TokenAnnotation token) {
    if (chunkBegin && !token.getChunkBegin()) return false;
    else if (chunkEnd && !token.getChunkEnd()) return false;
    else if (chunk != null && !this.chunk.matcher(token.getChunk()).matches()) return false;
    else if (pos != null && !this.pos.matcher(token.getPos()).matches()) return false;
    else if (stem != null && !this.stem.matcher(token.getStem()).matches()) return false;
    else if (text != null && !this.text.matcher(token.getCoveredText()).matches()) return false;
    else return true;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    else if (!(o instanceof TokenTransition)) return false;
    else return ((TokenTransition) o).hashCode() == hashCode();
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (chunkBegin ? 1 : 0);
    result = 31 * result + (chunkEnd ? 1 : 0);
    if (text != null) result = 31 * result + text.hashCode();
    if (pos != null) result = 31 * result + pos.hashCode();
    if (stem != null) result = 31 * result + stem.hashCode();
    if (chunk != null) result = 31 * result + chunk.hashCode();
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    if (chunkBegin) {
      sb.append("[ ");
      sb.append(chunk == null ? '*' : chunk.pattern());
      sb.append(' ');
    }
    sb.append(text == null ? '*' : text.pattern());
    sb.append('_');
    sb.append(pos == null ? '*' : pos.pattern());
    sb.append('_');
    sb.append(stem == null ? '*' : stem.pattern());
    if (chunkEnd) sb.append(" ]");
    return sb.toString();
  }
}
