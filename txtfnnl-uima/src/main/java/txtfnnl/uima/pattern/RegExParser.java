package txtfnnl.uima.pattern;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import txtfnnl.uima.tcas.TokenAnnotation;
import es.fnl.fsm.Pattern;

/** Compile a syntax pattern's regex into a pattern. */
class RegExParser {
  RegExLexer scanner;
  static final TokenLambdaTransition LAMBDA = TokenLambdaTransition.INSTANCE;
  static final String[] QUANTIFIERS = { "*", "+", "?" };
  static final Set<String> Q_SET = new HashSet<String>(Arrays.asList(QUANTIFIERS));
  static final String WILD_CARD = ".";

  /**
   * Create a new parser given a regular expression's stream of terminals.
   * 
   * @param lexer an unused scanner producing the stream of terminals
   */
  RegExParser(RegExLexer lexer) {
    reset(lexer);
  }

  /**
   * Copy constructor that will also create a new, unused terminal scanner.
   * 
   * @param copy parser to duplicate
   */
  RegExParser(RegExParser copy) {
    reset(new RegExLexer(copy.scanner.toString()));
  }

  /** Default constructor that requires a {@link #reset(RegExLexer) reset} before it is used. */
  RegExParser() {}

  /**
   * Reset the parser with a (fresh) scanner.
   * 
   * @param lexer an unused scanner producing the stream of terminals
   */
  void reset(RegExLexer lexer) {
    scanner = lexer;
  }

  /**
   * Parse the regular syntax expression's stream of terminals into a FSM pattern.
   * 
   * @return the compiled pattern
   */
  Pattern<TokenAnnotation> parse() {
    // S -> Phrase S? | Capture S? | Token S?
    Pattern<TokenAnnotation> s = new Pattern<TokenAnnotation>();
    String terminal;
    while (scanner.hasNext()) {
      terminal = scanner.next();
      if ("[".equals(terminal)) {
        s = Pattern.chain(s, parsePhrase());
      } else if ("(".equals(terminal)) {
        s = Pattern.chain(s, parseCapture());
      } else if (")".equals(terminal)) {
        break;
      } else {
        s = Pattern.chain(s, parseToken(terminal));
      }
    }
    return s;
  }

  /**
   * Parse a chunk phrase, including an optional quantifier "?".
   * 
   * <pre>
   * Phrase -> "[" Chunk InPhrase "]" "?"?
   * </pre>
   * 
   * @param scanner with the terminals stream
   * @param pattern to expand
   * @return the expanded pattern
   */
  private Pattern<TokenAnnotation> parsePhrase() {
    // NB: "[" has already been consumed from the scanner!
    String chunk = scanner.next();
    Pattern<TokenAnnotation> phrase = parseInPhrase(chunk, true);
    if ("?".equals(scanner.peek())) {
      scanner.next();
      phrase.optional();
    }
    return phrase;
  }

  /**
   * Parse a capture group.
   * 
   * <pre>
   * Capture -> "(" S ")"
   * </pre>
   */
  private Pattern<TokenAnnotation> parseCapture() {
    // NB: "(" has already been consumed from the scanner!
    Pattern<TokenAnnotation> capture = parse();
    // NB: ")" will have been consumed from the scanner!
    return Pattern.capture(capture);
  }

  /**
   * Parse a token, optionally followed by a quantifier.
   * 
   * <pre>
   * Token -> "." Quantifier? | RegEx Quantifier?
   * </pre>
   */
  private Pattern<TokenAnnotation> parseToken(String terminal) {
    Pattern<TokenAnnotation> token;
    if (WILD_CARD.equals(terminal)) {
      token = Pattern.match(LAMBDA);
    } else {
      String[] items = splitToken(terminal);
      try {
        TokenTransition t = new TokenTransition(items[0], items[1], items[2], "*");
        token = Pattern.match(t);
      } catch (PatternSyntaxException e) {
        throw new PatternSyntaxException(e.getDescription(), scanner.toString(), scanner.offset());
      }
    }
    return parseQuantifier(token);
  }

  /**
   * Check if the (current!) token is quantified, and if so, consume the quantifier.
   * 
   * <pre>
   * Quantifier -> "*" | "?" | "+"
   * </pre>
   */
  private Pattern<TokenAnnotation> parseQuantifier(Pattern<TokenAnnotation> token) {
    if (Q_SET.contains(scanner.peek())) {
      if (scanner.isOptional()) token.optional();
      if (scanner.isRepeated()) token.repeat();
      scanner.next();
    }
    return token;
  }

  /**
   * Phrases may contain captures and terminal tokens, but no other phrases.
   * 
   * <pre>
   * InPhrase -> CaptureInPhrase InPhrase? | Token InPhrase?
   * </pre>
   */
  private Pattern<TokenAnnotation> parseInPhrase(String chunk, boolean begin) {
    // NB: the Token non-terminal will use a special "TokenInPhrase" method,
    // not the "Token" method
    Pattern<TokenAnnotation> s = new Pattern<TokenAnnotation>();
    String terminal;
    while (true) {
      terminal = scanner.next();
      if ("]".equals(terminal) || ")".equals(terminal)) {
        break;
      } else if ("[".equals(terminal)) {
        throw new PatternSyntaxException("nested phrases detected", scanner.toString(),
            scanner.offset());
      } else if ("(".equals(terminal)) {
        s = Pattern.chain(s, parseCaptureInPhrase(chunk, begin));
        begin = false;
      } else {
        s = Pattern.chain(s, parseTokenInPhrase(terminal, chunk, begin));
        begin = false;
      }
    }
    return s;
  }

  /**
   * Ensure captures in phrases are fully enclosed by it and don't contain another phrase.
   * 
   * <pre>
   * CaptureInPhrase -> "(" InPhrase ")"
   * </pre>
   */
  private Pattern<TokenAnnotation> parseCaptureInPhrase(String chunk, boolean start) {
    // NB: "(" has already been consumed from the scanner!
    Pattern<TokenAnnotation> capture = parseInPhrase(chunk, start);
    // NB: ")" will have been consumed from the scanner!
    return Pattern.capture(capture);
  }

  /**
   * Match a token terminal inside a phrase chunk, possibly at the chunk's begin.
   * 
   * @param terminal token to match
   * @param chunk phrase tag to match
   * @param begin <code>true</code> if the match must be at the beginning of a chunk
   * @return
   */
  private Pattern<TokenAnnotation>
      parseTokenInPhrase(String terminal, String chunk, boolean begin) {
    boolean maybeBegin = begin || scanner.maybeAtChunkBegin();
    boolean end = scanner.atChunkEnd();
    boolean maybeEnd = end || scanner.maybeAtChunkEnd();
    boolean repeated = scanner.isRepeated();
    Pattern<TokenAnnotation> token;
    if (repeated) {
      token = parseRepeatTokenInPhrase(terminal, chunk, begin, end);
      if (maybeBegin && !begin) token = Pattern.branch(token,
          parseRepeatTokenInPhrase(terminal, chunk, true, end));
      else if (maybeEnd && !end)
        token = Pattern.branch(token, parseRepeatTokenInPhrase(terminal, chunk, begin, true));
      if (maybeBegin && !begin && maybeEnd && !end)
        token = Pattern.branch(token, parseRepeatTokenInPhrase(terminal, chunk, true, true));
    } else {
      token = parseTokenInPhrase(terminal, chunk, begin, end);
      if (maybeBegin && !begin) token = Pattern.branch(token,
          parseTokenInPhrase(terminal, chunk, true, end));
      else if (maybeEnd && !end)
        token = Pattern.branch(token, parseTokenInPhrase(terminal, chunk, begin, true));
      if (maybeBegin && !begin && maybeEnd && !end)
        token = Pattern.branch(token, parseTokenInPhrase(terminal, chunk, true, true));
    }
    if (scanner.isOptional()) {
      token.optional();
      scanner.next();
    } else if (repeated) {
      scanner.next();
    }
    return token;
  }

  /**
   * Match a token terminal inside a phrase chunk, possibly at the chunk's begin or end.
   * 
   * @param terminal token to match
   * @param chunk phrase tag to match
   * @param begin <code>true</code> if the match must be at the beginning of a chunk
   * @param end <code>true</code> if the match must be at the end of a chunk
   * @return
   */
  private Pattern<TokenAnnotation> parseTokenInPhrase(String terminal, String chunk,
      boolean begin, boolean end) {
    TokenTransition transition;
    if (WILD_CARD.equals(terminal)) {
      try {
        transition = new TokenTransition("*", "*", "*", chunk, begin, end);
      } catch (PatternSyntaxException e) {
        throw new PatternSyntaxException(e.getDescription(), scanner.toString(), scanner.offset());
      }
    } else {
      String[] items = splitToken(terminal);
      try {
        transition = new TokenTransition(items[0], items[1], items[2], "*", begin, end);
      } catch (PatternSyntaxException e) {
        throw new PatternSyntaxException(e.getDescription(), scanner.toString(), scanner.offset());
      }
    }
    return Pattern.match(transition);
  }

  /**
   * Match a repeated ("*", "+") token terminal inside a phrase chunk, possibly at the chunk's
   * begin or end.
   * 
   * @param terminal token to match
   * @param chunk phrase tag to match
   * @param begin <code>true</code> if the match must be at the beginning of a chunk
   * @param end <code>true</code> if the match must be at the end of a chunk
   * @return
   */
  private Pattern<TokenAnnotation> parseRepeatTokenInPhrase(String terminal, String chunk,
      boolean begin, boolean end) {
    // single case:
    Pattern<TokenAnnotation> token = parseTokenInPhrase(terminal, chunk, begin, end);
    if (begin || end) {
      Pattern<TokenAnnotation> open = parseTokenInPhrase(terminal, chunk, begin, false);
      Pattern<TokenAnnotation> inner = parseTokenInPhrase(terminal, chunk, false, false);
      Pattern<TokenAnnotation> close = parseTokenInPhrase(terminal, chunk, false, end);
      Pattern<TokenAnnotation> repeat = Pattern.chain(open,
          Pattern.chain(inner.repeat().optional(), close));
      token = Pattern.branch(token, repeat);
    } else {
      // may be repeated, but has no borders to consider
      token.repeat();
    }
    return token;
  }

  /** Split a token into its three pieces (optional word, optional PoS and required stem/lemma). */
  private String[] splitToken(String token) throws PatternSyntaxException {
    // RegEx
    if (token.endsWith("_") && !token.endsWith("\\_"))
      throw new PatternSyntaxException("illegal token expression " + token, scanner.toString(),
          scanner.offset());
    String[] items = token.split("(?<!\\\\)_");
    for (String i : items)
      if (i.length() == 0)
        throw new PatternSyntaxException("empty annotation token field in " + token,
            scanner.toString(), scanner.offset());
    if (items.length == 1) items = new String[] { "*", "*", items[0] };
    else if (items.length == 2) items = new String[] { "*", items[0], items[1] };
    else if (items.length != 3)
      throw new PatternSyntaxException("" + items.length + " RegEx fields in token",
          scanner.toString(), scanner.offset());
    return items;
  }
}
