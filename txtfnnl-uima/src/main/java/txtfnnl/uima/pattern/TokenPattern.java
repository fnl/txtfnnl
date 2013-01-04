/* Created on Jan 3, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.pattern;

import java.util.regex.PatternSyntaxException;

import txtfnnl.pattern.Pattern;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * A pattern compiler for a sequence of {@link TokenAnnotation TokenAnnotations}.
 * 
 * @author Florian Leitner
 */
public class TokenPattern {
  private static final TokenLambdaTransition lambda = new TokenLambdaTransition();

  private TokenPattern() {
    throw new RuntimeException("n/a");
  }

  /**
   * Compile a token expression into a pattern.
   * 
   * @param expression to be compiled
   * @throws PatternSyntaxException if the expression is illegal
   * @return a compiled pattern
   */
  public static Pattern<TokenAnnotation> compile(String expression) {
    TokenLexer scanner = new TokenLexer(expression);
    Pattern<TokenAnnotation> pattern;
    pattern = parse(scanner, new Pattern<TokenAnnotation>(), null);
    if (scanner.hasNext()) { throw new PatternSyntaxException("unused pattern tokens at " +
        scanner.peek(), scanner.toString(), scanner.offset()); }
    return pattern.minimize();
  }

  /**
   * Parse a token expression stream into a pattern.
   * 
   * @param scanner with the token expression stream
   * @param pattern to expand
   * @param chunk to be matched or <code>null</code>
   * @return the expanded pattern
   */
  private static Pattern<TokenAnnotation> parse(TokenLexer scanner,
      Pattern<TokenAnnotation> pattern, String chunk) {
    if (chunk != null) {
      return parseWithChunk(scanner, pattern, chunk);
    } else {
      return parse(scanner, pattern);
    }
  }

  /**
   * Handle the simple case when no chunk needs to be matched.
   * 
   * @param scanner with the token expression stream
   * @param pattern to expand
   * @return the expanded pattern
   */
  private static Pattern<TokenAnnotation> parse(TokenLexer scanner,
      Pattern<TokenAnnotation> pattern) {
    Pattern<TokenAnnotation> sub;
    String subexp;
    while (scanner.hasNext()) {
      subexp = scanner.next();
      if (subexp.length() == 0) {
        continue;
      } else if ("[".equals(subexp)) {
        sub = parseChunk(scanner);
      } else if ("(".equals(subexp)) {
        sub = parseGroup(scanner, null);
      } else if ("*".equals(subexp)) {
        sub = Pattern.match(lambda).optional().repeat();
      } else if ("?".equals(subexp)) {
        sub = Pattern.match(lambda).optional();
      } else if ("+".equals(subexp)) {
        sub = Pattern.match(lambda).repeat();
      } else if ("]".equals(subexp) || ")".equals(subexp)) {
        break;
      } else if ("*_*".equals(subexp) || "*_*_*".equals(subexp)) {
        sub = Pattern.match(lambda);
      } else {
        String[] items = splitTokenExpression(scanner, subexp);
        TokenTransition t = new TokenTransition(items[0], items[1], items[2], "*");
        sub = Pattern.match(t);
      }
      pattern = Pattern.chain(pattern, sub);
    }
    return pattern;
  }

  /**
   * Handle the more complex case when matching inside a chunk.
   * 
   * @param scanner with the token expression stream
   * @param pattern to expand
   * @param chunk to be matched
   * @return the expanded pattern
   */
  private static Pattern<TokenAnnotation> parseWithChunk(TokenLexer scanner,
      Pattern<TokenAnnotation> pattern, String chunk) {
    boolean start = true;
    boolean end = false;
    TokenTransition openChunk = new TokenTransition("*", "*", "*", chunk, true, false);
    TokenTransition anyChunk = new TokenTransition("*", "*", "*", chunk, false, false);
    TokenTransition closeChunk = new TokenTransition("*", "*", "*", chunk, false, true);
    TokenTransition singleChunk = new TokenTransition("*", "*", "*", chunk, true, true);
    Pattern<TokenAnnotation> sub;
    String subexp;
    while (scanner.hasNext()) {
      subexp = scanner.next();
      end = false;
      if (subexp.length() == 0) {
        continue;
      } else if ("[".equals(subexp)) {
        sub = parseChunk(scanner);
      } else if ("(".equals(subexp)) {
        sub = parseGroup(scanner, chunk);
        pattern = Pattern.chain(pattern, sub);
        continue; // do not loose the start state information
      } else if ("*".equals(subexp)) {
        end = nextIsChunkEnd(scanner);
        if (start && end) {
          sub = Pattern
              .chain(
                  Pattern.chain(Pattern.match(openChunk), Pattern.match(anyChunk).optional()
                      .repeat()), Pattern.match(closeChunk));
          sub = Pattern.branch(sub, Pattern.match(singleChunk).optional());
        } else if (start) {
          sub = Pattern.chain(Pattern.match(openChunk),
              Pattern.match(anyChunk).optional().repeat()).optional();
        } else if (end) {
          sub = Pattern.chain(Pattern.match(anyChunk).optional().repeat(),
              Pattern.match(closeChunk)).optional();
        } else {
          sub = Pattern.match(anyChunk).optional().repeat();
        }
      } else if ("?".equals(subexp)) {
        end = nextIsChunkEnd(scanner);
        if (start && end) {
          sub = Pattern.match(singleChunk).optional();
        } else if (start) {
          sub = Pattern.match(openChunk).optional();
        } else if (end) {
          sub = Pattern.match(closeChunk).optional();
        } else {
          sub = Pattern.match(anyChunk).optional();
        }
      } else if ("+".equals(subexp)) {
        end = nextIsChunkEnd(scanner);
        if (start && end) {
          sub = Pattern
              .chain(
                  Pattern.chain(Pattern.match(openChunk), Pattern.match(anyChunk).optional()
                      .repeat()), Pattern.match(closeChunk));
          sub = Pattern.branch(sub, Pattern.match(singleChunk));
        } else if (start) {
          sub = Pattern.chain(Pattern.match(openChunk), Pattern.match(anyChunk).optional()
              .repeat());
        } else if (end) {
          sub = Pattern.chain(Pattern.match(anyChunk).optional().repeat(),
              Pattern.match(closeChunk));
        } else {
          sub = Pattern.match(anyChunk).repeat();
        }
      } else if ("]".equals(subexp) || ")".equals(subexp)) {
        break;
      } else {
        String[] items = splitTokenExpression(scanner, subexp);
        if (scanner.hasNext()) {
          String next = scanner.peek();
          if ("*".equals(next) || "?".equals(next)) {
            scanner.next();
            TokenTransition tNoEnd = new TokenTransition(items[0], items[1], items[2], chunk,
                start, false);
            if (nextIsChunkEnd(scanner)) {
              sub = Pattern.match(closeChunk);
              if ("*".equals(next))
                sub = Pattern.chain(Pattern.match(anyChunk).optional().repeat(), sub);
              TokenTransition tEnd = new TokenTransition(items[0], items[1], items[2], chunk,
                  start, true);
              sub = Pattern.branch(Pattern.match(tEnd), Pattern.chain(Pattern.match(tNoEnd), sub));
            } else {
              sub = Pattern.match(anyChunk).optional();
              if ("*".equals(next)) sub.repeat();
              sub = Pattern.chain(Pattern.match(tNoEnd), sub);
            }
          } else {
            if ("]".equals(next)) end = true;
            TokenTransition t = new TokenTransition(items[0], items[1], items[2], chunk, start,
                end);
            sub = Pattern.match(t);
          }
        } else {
          TokenTransition t = new TokenTransition(items[0], items[1], items[2], chunk, start, true);
          sub = Pattern.match(t);
        }
      }
      pattern = Pattern.chain(pattern, sub);
      start = false;
    }
    return pattern;
  }

  /** Split a token subexpression into its three pieces (word, PoS, and stem). */
  private static String[] splitTokenExpression(TokenLexer scanner, String subexpression)
      throws PatternSyntaxException {
    if (subexpression.endsWith("_") && !subexpression.endsWith("\\_"))
      throw new PatternSyntaxException("illegal token expression " + subexpression,
          scanner.toString(), scanner.offset());
    String[] items = subexpression.split("(?<!\\\\)_");
    for (String i : items)
      if (i.length() == 0)
        throw new PatternSyntaxException("empty token component " + subexpression,
            scanner.toString(), scanner.offset());
    if (items.length == 2) items = new String[] { "*", items[0], items[1] };
    else if (items.length != 3)
      throw new PatternSyntaxException("illegal token expression (" + items.length + ") " +
          subexpression, scanner.toString(), scanner.offset());
    return items;
  }

  /** Peek into the scanner to check if a chunk end token is coming up. */
  private static boolean nextIsChunkEnd(TokenLexer scanner) {
    return scanner.hasNext() && "]".equals(scanner.peek());
  }

  /** Chunk parsing recursion. */
  private static Pattern<TokenAnnotation> parseChunk(TokenLexer scanner) {
    String chunk;
    try {
      while ((chunk = scanner.next()).length() == 0);
    } catch (IndexOutOfBoundsException e) {
      throw new PatternSyntaxException("bad terminal chunk", scanner.toString(), scanner.offset());
    }
    return parse(scanner, new Pattern<TokenAnnotation>(), chunk);
  }

  /** Capture group parsing recursion. */
  private static Pattern<TokenAnnotation> parseGroup(TokenLexer scanner, String chunk) {
    return Pattern.capture(parse(scanner, new Pattern<TokenAnnotation>(), chunk));
  }
}
