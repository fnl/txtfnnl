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
    pattern = parse(scanner, new Pattern<TokenAnnotation>(), null, false);
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
   * @param start if chunk was just started
   * @return the expanded pattern
   */
  private static Pattern<TokenAnnotation> parse(TokenLexer scanner,
      Pattern<TokenAnnotation> pattern, String chunk, boolean start) {
    if (chunk != null) {
      return parseWithChunk(scanner, pattern, chunk, start);
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
    String token;
    while (scanner.hasNext()) {
      token = scanner.next();
      if ("[".equals(token)) {
        sub = parseChunk(scanner);
      } else if ("(".equals(token)) {
        sub = parseGroup(scanner, null, false);
      } else if ("*".equals(token)) {
        sub = Pattern.match(lambda).optional().repeat();
      } else if ("+".equals(token)) {
        sub = Pattern.match(lambda).repeat();
      } else if (")".equals(token)) {
        break;
      } else if ("*_*".equals(token) || "*_*_*".equals(token)) {
        sub = Pattern.match(lambda);
        if (nextIsOptional(scanner)) {
          scanner.next();
          sub.optional();
        }
      } else {
        String[] items = splitToken(scanner, token);
        TokenTransition t = new TokenTransition(items[0], items[1], items[2], "*");
        sub = Pattern.match(t);
        if (nextIsOptional(scanner)) {
          scanner.next();
          sub.optional();
        }
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
   * @param start
   * @return the expanded pattern
   */
  private static Pattern<TokenAnnotation> parseWithChunk(TokenLexer scanner,
      Pattern<TokenAnnotation> pattern, String chunk, boolean start) {
    TokenTransition openChunk = new TokenTransition("*", "*", "*", chunk, true, false);
    TokenTransition anyChunk = new TokenTransition("*", "*", "*", chunk, false, false);
    TokenTransition closeChunk = new TokenTransition("*", "*", "*", chunk, false, true);
    TokenTransition singleChunk = new TokenTransition("*", "*", "*", chunk, true, true);
    Pattern<TokenAnnotation> sub;
    String subexp;
    while (scanner.hasNext()) {
      subexp = scanner.next();
      if ("(".equals(subexp)) {
        sub = parseGroup(scanner, chunk, start);
      } else if ("*".equals(subexp)) {
        sub = parseKleeneToken(scanner, start, openChunk, anyChunk, closeChunk, singleChunk, true);
      } else if ("+".equals(subexp)) {
        sub = parseKleeneToken(scanner, start, openChunk, anyChunk, closeChunk, singleChunk, false);
      } else if ("]".equals(subexp) || ")".equals(subexp)) {
        break;
      } else {
        sub = parseChunkToken(splitToken(scanner, subexp), scanner, chunk, start, anyChunk,
            closeChunk);
      }
      pattern = Pattern.chain(pattern, sub);
      start = false;
    }
    return pattern;
  }

  private static Pattern<TokenAnnotation> parseKleeneToken(TokenLexer scanner, boolean start,
      TokenTransition openChunk, TokenTransition anyChunk, TokenTransition closeChunk,
      TokenTransition singleChunk, boolean optional) {
    // "*" => optional == true; "+" => optional == false;
    boolean end = scanner.atChunkEnd();
    Pattern<TokenAnnotation> sub;
    if (start && end) {
      sub = Pattern.chain(
          Pattern.chain(Pattern.match(openChunk), Pattern.match(anyChunk).optional().repeat()),
          Pattern.match(closeChunk));
      sub = Pattern.branch(sub,
          optional ? Pattern.match(singleChunk).optional() : Pattern.match(singleChunk));
    } else if (end) {
      sub = Pattern.chain(Pattern.match(anyChunk).optional().repeat(), Pattern.match(closeChunk));
      if (optional) sub.optional();
    } else {
      if (start) sub = Pattern.chain(Pattern.match(openChunk), Pattern.match(anyChunk).optional()
          .repeat());
      else sub = Pattern.match(anyChunk).repeat();
      if (scanner.maybeAtChunkEnd()) {
        if (start) sub = Pattern.branch(
            sub,
            Pattern.branch(
                Pattern.chain(
                    Pattern.chain(Pattern.match(openChunk), Pattern.match(anyChunk).optional()
                        .repeat()), Pattern.match(closeChunk)), Pattern.match(singleChunk)));
        else sub = Pattern.branch(sub,
            Pattern.chain(Pattern.match(anyChunk).optional().repeat(), Pattern.match(closeChunk)));
      }
      if (optional) sub.optional();
    }
    return sub;
  }

  private static Pattern<TokenAnnotation> parseChunkToken(String[] items, TokenLexer scanner,
      String chunk, boolean start, TokenTransition anyChunk, TokenTransition closeChunk)
      throws PatternSyntaxException {
    boolean end = scanner.atChunkEnd();
    Pattern<TokenAnnotation> sub;
    String next = scanner.peek();
    boolean isOpt = "?".equals(next);
    if (isOpt) scanner.next();
    if (end || scanner.maybeAtChunkEnd()) {
      TokenTransition tEnd = new TokenTransition(items[0], items[1], items[2], chunk, start,
          true);
      sub = Pattern.match(tEnd);
      if (!end) {
        TokenTransition tNoEnd = new TokenTransition(items[0], items[1], items[2], chunk, start,
            false);
        sub = Pattern.branch(sub, Pattern.match(tNoEnd));
      }
    } else {
      TokenTransition t = new TokenTransition(items[0], items[1], items[2], chunk, start,
          false);
      sub = Pattern.match(t);
    }
    if (isOpt) sub.optional();
    return sub;
  }

  /** Split a token into its three pieces (optional word, and the PoS and its stem). */
  private static String[] splitToken(TokenLexer scanner, String token)
      throws PatternSyntaxException {
    if (token.endsWith("_") && !token.endsWith("\\_"))
      throw new PatternSyntaxException("illegal token expression " + token, scanner.toString(),
          scanner.offset());
    String[] items = token.split("(?<!\\\\)_");
    for (String i : items)
      if (i.length() == 0)
        throw new PatternSyntaxException("empty token component " + token, scanner.toString(),
            scanner.offset());
    if (items.length == 2) items = new String[] { "*", items[0], items[1] };
    else if (items.length != 3)
      throw new PatternSyntaxException("illegal token expression (" + items.length + ") " + token,
          scanner.toString(), scanner.offset());
    return items;
  }

  /** Peek into the scanner to check if next is an optional marker (?). */
  private static boolean nextIsOptional(TokenLexer scanner) {
    return scanner.hasNext() && "?".equals(scanner.peek());
  }

  /** Chunk parsing recursion. */
  private static Pattern<TokenAnnotation> parseChunk(TokenLexer scanner) {
    String chunk;
    try {
      while ((chunk = scanner.next()).length() == 0);
    } catch (IndexOutOfBoundsException e) {
      throw new PatternSyntaxException("bad terminal chunk", scanner.toString(), scanner.offset());
    }
    return parseWithChunk(scanner, new Pattern<TokenAnnotation>(), chunk, true);
  }

  /** Capture group parsing recursion. */
  private static Pattern<TokenAnnotation> parseGroup(TokenLexer scanner, String chunk,
      boolean start) {
    return Pattern.capture(parse(scanner, new Pattern<TokenAnnotation>(), chunk, start));
  }
}
