/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ExternalResourceFactory;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

import txtfnnl.uima.SharedResourceBuilder;
import txtfnnl.utils.Offset;
import txtfnnl.utils.StringUtils;

/**
 * The AbstractGazetteerResource implements matching without a defined way retrieve the ID, name
 * value pairs used to populate the Gazetteer. The
 * {@link AbstractGazetteerResource#PARAM_SEPARATORS token-separating characters} that should be
 * ignored and the {@link AbstractGazetteerResource#PARAM_SEPARATOR_LENGTH max. length} of these
 * separating spans can be parameterized. It is possible to specify if the
 * {@link AbstractGazetteerResource#PARAM_ID_MATCHING IDs should be matched} and if only
 * {@link AbstractGazetteerResource#PARAM_CASE_MATCHING exact case matches} should be considered
 * (otherwise, Unicode-based, case-insensitive matching is used).
 * <p>
 * <b>Tokens</b> are separated at the defined separators and at any change of Unicode character
 * {@link Character#getType(int) category} in the entity name, with the only exception of a
 * transition from a single upper-case character to lower-case (i.e., capitalized words). For
 * example, the name "Abc" is a single token, but "ABCdef" are two, just as "AbcDef", "ABC1", or
 * "abc def".
 * <p>
 * The {@link AbstractGazetteerResource#get(String) get} method returns a set of matching DB IDs
 * for any existing key, the {@link AbstractGazetteerResource#size() size} reports the <b>total</b>
 * number of keys (incl. normalized and/or lower-cased versions), while
 * {@link AbstractGazetteerResource#iterator() iterator} only provides the <b>regular</b> keys.
 * Normalized and/or lower-cased keys, although reported by size() and retrievable via get(String
 * key), are never directly exposed.
 * <p>
 * <b>Unicode characters</b>: Entity names may be any characters from all Unicode ranges <i>except
 * the private range</i>. Please make sure no private range characters are present in your names.
 * 
 * @author Florian Leitner
 */
public abstract class AbstractGazetteerResource implements GazetteerResource,
    ExternalResourceAware {
  /** Variants of the whitespace " " character (excl. the whitespace itself, incl. newline). */
  public static final Pattern SPACES = Pattern
      .compile("\n|\u00A0|\u2000|\u2001|\u2002|\u2003|\u2004|\u2005|\u2006|\u2007|\u2008|\u2009|\u200A|\u200B|\u202F|\u205F|\u3000|\uFEFF");
  /** Variants of the dash "-" character (excl. the dash itself). */
  public static final Pattern DASHES = Pattern
      .compile("\u1680|\u2010|\u2011|\u2012|\u2013|\u2014|\u2015|\u2212|\uFE58|\uFE63|\uFF0D");
  /**
   * Default separators: space, dash, slash, dot, comma and the underscore. Note that the
   * whitespace should always be treated a separator.
   */
  public static final String SEPARATORS = " _-.,/";
  @ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
  protected String resourceName;
  /** The data resource' URI. */
  protected String resourceUri = null;
  /** A Pattern to detect the {@link GazetterResource#SEPARATOR separator}. */
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile("(" + SEPARATOR + ")");
  /**
   * Normalized separator characters between tokens (default:
   * {@link AbstractGazetteerResource#SEPARATORS}) except space (always treated as separator).
   * <p>
   * Note that spaces and dashes are all normalized to their canonical (ASCII) versions, so it is
   * not necessary to list all (Unicode) dashes or spaces as possible, different separators.
   */
  public static final String PARAM_SEPARATORS = "Separators";
  @ConfigurationParameter(name = PARAM_SEPARATORS, mandatory = false, defaultValue = SEPARATORS)
  private String separators;
  /** Whether to match the DB IDs themselves, too (default: <code>false</code>). */
  public static final String PARAM_ID_MATCHING = "IDMatching";
  @ConfigurationParameter(name = PARAM_ID_MATCHING, mandatory = false, defaultValue = "false")
  protected boolean idMatching;
  /** Whether to require exact case-sensitive matching (default: <code>false</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "false")
  private boolean exactCaseMatching;
  /** The size of the initial HashMap for this resource. */
  static final int INIT_MAP_SIZE = 1024;
  /** A special private range Unicode character to mark case-insensitive keys. */
  static final String LOWERCASE = "\uE3A8";
  /** A special private range Unicode character to mark separator-insensitive keys. */
  static final String NORMAL = "\uE3A9";
  /** Special characters reserved for dk.brics.automaton patterns. */
  private static final Pattern RESERVED_CHARS = Pattern
      .compile("([\\@\\&\\~\\^\\#\\\\\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\<\\>])");
  /** Mappings of regular keys to ID sets and of normalized keys to regular key sets. */
  private Map<String, Set<String>> mappings;
  /** List of automata (before being compiled). */
  private List<Automaton> automata;
  /** The "meta-pattern" created from all individual names. */
  private RunAutomaton patterns;
  /** The pattern used to find token splits. */
  private Pattern split;
  protected Logger logger = null;

  public static class Builder extends SharedResourceBuilder {
    /** Protected constructor that must be extended by concrete implementations. */
    protected Builder(Class<? extends SharedResourceObject> klass, String url) {
      super(klass, url);
    }

    /** Match the Gazetteer's IDs themselves, too. */
    public Builder idMatching() {
      setOptionalParameter(PARAM_ID_MATCHING, Boolean.TRUE);
      return this;
    }

    /** Require case-sensitive matches. */
    public Builder caseMatching() {
      setOptionalParameter(PARAM_CASE_MATCHING, Boolean.TRUE);
      return this;
    }

    /**
     * Define other name token separator characters (for the default characters see
     * {@link AbstractGazetteerResource#SEPARATORS}).
     * <p>
     * Separator characters are not allowed within the matched target tokens, while only characters
     * defined as separators may be matched between tokens. This means that if any of these
     * separator characters are encountered within a token of the entity name, the match will fail.
     * <p>
     * Consecutive character ranges <b>cannot</b> be grouped as in a regular expression (like "A-Z"
     * for all upper-case ASCII letters) - each character must be given separately.
     */
    public Builder setSeparators(String separators) {
      if (separators != null && separators.length() == 0)
        throw new IllegalArgumentException("at least one separator char has to be defined");
      setOptionalParameter(PARAM_SEPARATORS, separators);
      return this;
    }
  }

  /** Replace space and dash characters with their normalized ASCII version. */
  private static String normalizeSpaceDash(String input) {
    String tmp = SPACES.matcher(input).replaceAll(" ");
    return DASHES.matcher(tmp).replaceAll("-");
  }

  /** Create a case-insensitive key. */
  private static String makeLower(String key) {
    return String.format("%s%s", LOWERCASE, key.toLowerCase());
  }

  /** Create a separator-agnostic key. */
  private static String makeNormal(String key) {
    return String.format("%s%s", NORMAL, key.replace(SEPARATOR, ""));
  }

  /** Create a key that is both case-insensitive and separator-agnostic. */
  private static String makeNormalLower(String key) {
    return makeNormal(makeLower(key));
  }

  /** {@inheritDoc} */
  public String getResourceName() {
    return resourceName;
  }

  /** {@inheritDoc} */
  public String getUrl() {
    return resourceUri;
  }

  public synchronized void load(DataResource dataResource) throws ResourceInitializationException {
    if (mappings == null) {
      ConfigurationParameterInitializer.initialize(this, dataResource);
      automata = new ArrayList<Automaton>(128);
      mappings = new HashMap<String, Set<String>>(INIT_MAP_SIZE);
      split = Pattern.compile(String.format("[%s]+", escapeAll(separators)));
      logger = UIMAFramework.getLogger(this.getClass());
      resourceUri = dataResource.getUri().toString();
      logger.log(Level.INFO, "{0} resource loaded", resourceUri);
    }
  }

  /** Escape each character in the String with a backslash. */
  private String escapeAll(String str) {
    StringBuilder sb = new StringBuilder();
    for (char c : str.toCharArray())
      sb.append('\\').append(c);
    return sb.toString();
  }

  public abstract void afterResourcesInitialized();

  // methods for building a NFA from the name-id pairs
  /**
   * Generate the "separated" (regularized) key for an entity name.
   * <p>
   * This method needs to be called by any implementing resource to convert a Gazetteer's name to
   * its "normalized" version, aka. "key", that then can be used for
   * {@link #processMapping(String, String, String) processMapping} <b>if the key is non-null</b>.
   * After processing all names/keys, the DFA can be {@link #compile() compiled} (only needs to be
   * called once).
   * <p>
   * A key is non-normalizable if the only characters it consists of are separator chars.
   * 
   * @return a key ("normalized" name) or <code>null</code> if it cannot be normalized
   */
  protected String makeKey(String name) {
    StringBuilder sb = new StringBuilder();
    for (String sub : split.split(normalizeSpaceDash(name))) {
      if (sub.length() > 0) {
        if (sb.length() > 0) sb.append(SEPARATOR);
        sb.append(makeToken(sub));
      }
    }
    String key = sb.toString();
    if (key.length() == 0) {
      logger.log(Level.WARNING, "\"" + name + "\" gives rise to an empty pattern");
      return null;
    }
    return key;
  }

  /** Return the Unicode category-separated (regular) sub-key of an entity token. */
  private String makeToken(String token) {
    StringBuilder sb = new StringBuilder();
    int length = token.length();
    int offset = 0;
    int lastSplit = 0;
    int charPoint = token.codePointAt(offset);
    int charType = Character.getType(charPoint);
    while (offset < length) {
      charPoint = token.codePointAt(offset);
      if (Character.getType(charPoint) != charType) {
        if (sb.length() > 0 && !isCapitalized(token, lastSplit, offset, charType)) {
          sb.append(SEPARATOR);
          lastSplit = offset;
        }
        charType = Character.getType(charPoint);
      }
      int charLen = Character.charCount(charPoint);
      sb.append(charLen == 1 ? token.charAt(offset) : token.substring(offset, offset + charLen));
      offset += charLen;
    }
    return sb.toString();
  }

  /** Return <code>true</code> if the current split is a capitalized word. */
  private boolean isCapitalized(String token, int lastSplit, int offset, int lastCharType) {
    return (lastCharType == Character.UPPERCASE_LETTER &&
        Character.getType(token.codePointAt(offset)) == Character.LOWERCASE_LETTER && Character
        .charCount(token.codePointAt(lastSplit)) + lastSplit == offset);
  }

  /**
   * Add the DB ID mapping and the patterns for the name using the normaized key.
   * <p>
   * Takes care of all patterns given the Annotators configuration. After all names have been
   * processed, the DFA can be {@link #compile() compiled} (only needs to be called once).
   * 
   * @param id of entity to normalize
   * @param name of the entity to detect
   * @param key of the entity to use for generating the pattern
   */
  protected void processMapping(final String id, String key) {
    if (key == null) throw new IllegalArgumentException("NULL key for ID '" + id + "'");
    if (!containsKey(key)) automata.add(makeAutomaton(key));
    addMapping(key, id); // only the regular key maps to the DB ID
    // all other keys map to the "real" key:
    addMapping(makeNormal(key), key);
    if (!exactCaseMatching) {
      addMapping(makeLower(key), key);
      addMapping(makeNormalLower(key), key);
    }
  }

  /** Add a particular key-value mapping. */
  private void addMapping(String key, String target) {
    if (!mappings.containsKey(key)) mappings.put(key, new HashSet<String>());
    mappings.get(key).add(target);
  }

  /** Create a pattern and Automaton for a given key. */
  private Automaton makeAutomaton(String key) {
    String pattern = RESERVED_CHARS.matcher(key).replaceAll("\\\\$1");
    if (!exactCaseMatching) {
      int[] unicode = StringUtils.toCodePointArray(pattern);
      StringBuilder caseInsensitive = new StringBuilder();
      for (int cp : unicode) {
        if (Character.isLetter(cp)) {
          caseInsensitive.append('[');
          caseInsensitive.append(Character.toChars(Character.toLowerCase(cp)));
          caseInsensitive.append(Character.toChars(Character.toUpperCase(cp)));
          caseInsensitive.append(']');
        } else {
          caseInsensitive.append(Character.toChars(cp));
        }
      }
      pattern = caseInsensitive.toString();
    }
    pattern = SEPARATOR_PATTERN.matcher(pattern).replaceAll("$1*");
    logger.log(Level.FINE, "''{0}'' -> /{1}/",
        new String[] { key, pattern.replace(SEPARATOR, "_") });
    // make sure a separator must be at the end or beginning of the pattern
    return (new RegExp(String.format("%s%s|%s%s", pattern, SEPARATOR, SEPARATOR, pattern)))
        .toAutomaton();
  }

  /**
   * Generate the DFA from all processed key-to-ID mappings.
   * <p>
   * This method needs to be called by the implementing resource only once, after all Gazetteer
   * names have been converted to keys and processed.
   */
  protected void compile() {
    logger.log(Level.INFO, "compiling DFA from {0} keys for {1} unique patterns", new Object[] {
        mappings.size(), automata.size() });
    Automaton dfa = BasicOperations.union(automata);
    Map<Character, Set<Character>> map = new HashMap<Character, Set<Character>>();
    Set<Character> cset = new HashSet<Character>();
    for (char c : separators.toCharArray())
      cset.add(c);
    map.put(SEPARATOR.charAt(0), cset);
    // NB: this next step can take quite some time...
    patterns = new RunAutomaton(dfa.subst(map));
    logger.log(Level.INFO, "the DFA has been compiled");
  }

  // GazetteerResource Methods
  /** {@inheritDoc} */
  public Map<Offset, String> match(String input) {
    Map<Offset, String> result = new HashMap<Offset, String>();
    input = String.format(" %s ", normalizeSpaceDash(input));
    AutomatonMatcher match = patterns.newMatcher(input);
    while (match.find()) {
      int startShift = input.charAt(match.start()) == ' ' ? 1 : 0;
      int endShift = (match.end() > 0 && input.charAt(match.end() - 1) == ' ') ? -1 : 0;
      result.put(new Offset(match.start() + startShift - 1, match.end() + endShift - 1),
          makeKey(input.substring(match.start() + startShift, match.end() + endShift)));
    }
    return result;
  }

  /** {@inheritDoc} */
  public Set<String> resolve(String key) {
    Set<String> result = new HashSet<String>(); // resolved (target) keys
    if (mappings.containsKey(key)) {
      result.add(key);
    } else {
      if (mappings.containsKey(makeNormal(key))) {
        for (String target : mappings.get(makeNormal(key)))
          result.add(target);
      }
      if (!exactCaseMatching && mappings.containsKey(makeLower(key))) {
        for (String target : mappings.get(makeLower(key)))
          result.add(target);
      }
      if (!exactCaseMatching && mappings.containsKey(makeNormalLower(key))) {
        for (String target : mappings.get(makeNormalLower(key)))
          result.add(target);
      }
    }
    return result;
  }

  // StringMapResource Methods
  /** Return the Set of known ID mappings for any key. */
  public Set<String> get(String key) {
    return mappings.get(key);
  }

  /** Return the number of <b>all</b> (name, normalized, and/or case-insensitive) keys. */
  public int size() {
    return mappings.size();
  }

  /** Iterate over the regular name keys (only). */
  public Iterator<String> iterator() {
    return new Iterator<String>() {
      private Iterator<String> it = mappings.keySet().iterator();
      private String cache;

      public boolean hasNext() {
        if (cache == null) cache = cacheNext();
        return cache != null;
      }

      public String next() {
        if (cache == null) cache = cacheNext();
        if (cache != null) {
          String tmp = cache;
          cache = null;
          return tmp;
        } else {
          throw new NoSuchElementException();
        }
      }

      private String cacheNext() {
        while (it.hasNext()) {
          String candidate = it.next();
          if (!candidate.startsWith(LOWERCASE) && !candidate.startsWith(NORMAL)) return candidate;
        }
        return null;
      }

      public void remove() {
        it.remove();
      }
    };
  }

  /** Check if the (name, normalized, and/or case-insensitive) key has a mapping. */
  public boolean containsKey(String key) {
    // if (exactCaseMatching)
    return mappings.containsKey(key);
    // else return mappings.containsKey(makeLower(key));
  }
}
