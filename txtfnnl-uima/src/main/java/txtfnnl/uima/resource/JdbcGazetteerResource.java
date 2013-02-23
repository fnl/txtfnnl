/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

import txtfnnl.utils.Offset;
import txtfnnl.utils.StringUtils;

/**
 * A JdbcGazetteerResource uses a {@link JdbcConnectionResourceImpl#PARAM_DRIVER_CLASS JDBC} to
 * retrieve the ID, name values used to populate the Gazetteer. It can use any user-defined
 * {@link JdbcGazetteerResource#PARAM_QUERY_SQL query} that selects these ID, name values and uses
 * regular expressions matching for those names. The {@link JdbcGazetteerResource#PARAM_SEPARATORS
 * token-separating characters} that should be ignored and the
 * {@link JdbcGazetteerResource#PARAM_SEPARATOR_LENGTH max. length} of these separating spans can
 * be parameterized. Finally, it is possible to specify if the
 * {@link JdbcGazetteerResource#PARAM_ID_MATCHING IDs should be matched} and if only
 * {@link JdbcGazetteerResource#PARAM_CASE_MATCHING exact case matches} should be considered
 * (otherwise, Unicode-based, case-insensitive matching is used).
 * <p>
 * <b>Tokens</b> are separated at the defined separators and at any change of Unicode character
 * {@link Character#getType(int) category} in the entity name, with the only exception of a
 * transition from a single upper-case character to lower-case (i.e., capitalized words). For
 * example, the name "Abc" is a single token, but "ABCdef" are two, just as "AbcDef", "ABC1", or
 * "abc def".
 * <p>
 * The {@link JdbcGazetteerResource#get(String) get} method returns a set of matching DB IDs for
 * any existing key, the {@link JdbcGazetteerResource#size() size} reports the <b>total</b> number
 * of keys (incl. normalized and/or lower-cased versions), while
 * {@link JdbcGazetteerResource#iterator() iterator} only provides the <b>regular</b> keys.
 * Normalized and/or lower-cased keys, although reported by size() and retrievable via get(String
 * key), are never directly exposed.
 * <p>
 * <b>Unicode characters</b>: Entity names may be any characters from all Unicode ranges <i>except
 * the private range</i>. Please make sure no private range characters are present in your names.
 * 
 * @author Florian Leitner
 */
public class JdbcGazetteerResource extends JdbcConnectionResourceImpl implements
    GazetteerResource<Set<String>> {
  /** The <b>mandatory</b> SQL query used to fetch the entity names. */
  public static final String PARAM_QUERY_SQL = "QuerySQL";
  @ConfigurationParameter(name = PARAM_QUERY_SQL, mandatory = true)
  private String querySql;
  /** Variants of the whitespace " " character (excl. the whitespace itself). */
  public static final Pattern SPACES = Pattern
      .compile("\u00A0|\u2000|\u2001|\u2002|\u2003|\u2004|\u2005|\u2006|\u2007|\u2008|\u2009|\u200A|\u200B|\u202F|\u205F|\u3000|\uFEFF");
  /** Variants of the dash "-" character (excl. the dash itself). */
  public static final Pattern DASHES = Pattern
      .compile("\u1680|\u2010|\u2011|\u2012|\u2013|\u2014|\u2015|\u2212|\uFE58|\uFE63|\uFF0D");
  /** Default separators: space, dash, slash, dot, comma and the underscore. */
  public static final String SEPARATORS = " _-.,/";
  /** A Pattern to detect the {@link GazetterResource#SEPARATOR separator} character. */
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile("(" + SEPARATOR + ")");
  /**
   * Normalized separator characters between tokens (default: the underscore and all Unicode spaces
   * and dashes).
   */
  public static final String PARAM_SEPARATORS = "Separators";
  @ConfigurationParameter(name = PARAM_SEPARATORS, mandatory = false, defaultValue = SEPARATORS)
  private String separators;
  /**
   * The max. length of matchable consecutive separator characters (default: 3).
   * <p>
   * Should not be less than one.
   */
  public static final String PARAM_SEPARATOR_LENGTH = "SeparatorLength";
  @ConfigurationParameter(name = PARAM_SEPARATOR_LENGTH, mandatory = false, defaultValue = "1")
  private int separatorLength;
  /** Whether to match the DB IDs themselves, too (default: <code>false</code>). */
  public static final String PARAM_ID_MATCHING = "IDMatching";
  @ConfigurationParameter(name = PARAM_ID_MATCHING, mandatory = false, defaultValue = "false")
  private boolean idMatching;
  /** Whether to require exact case-sensitive matching (default: <code>false</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "false")
  private boolean exactCaseMatching;
  // additional private range Unicode characters to identify special key properties:
  static final String LOWERCASE = "\uE3A8";
  static final String NORMAL = "\uE3A9";
  static final Pattern RESERVED_CHARS = Pattern
      .compile("([\\@\\&\\~\\^\\#\\\\\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\<\\>])");
  /** Mappings of regular keys to ID sets and of normalized keys to regular key sets. */
  private Map<String, Set<String>> mappings;
  /** The "meta-pattern" created from all individual names. */
  private RunAutomaton patterns;
  /** The pattern used to find token splits. */
  private Pattern split;

  public static class Builder extends JdbcConnectionResourceImpl.Builder {
    Builder(String url, String driverClass, String querySql) {
      super(JdbcGazetteerResource.class, url, driverClass);
      setRequiredParameter(PARAM_QUERY_SQL, querySql);
    }

    /** Match the DB IDs themselves, too. */
    public Builder idMatching() {
      setOptionalParameter(PARAM_ID_MATCHING, Boolean.TRUE);
      return this;
    }

    /** Only do exact, case-sensitive matching. */
    public Builder caseMatching() {
      setOptionalParameter(PARAM_CASE_MATCHING, Boolean.TRUE);
      return this;
    }

    /**
     * Define name token separator characters (default: spaces, hyphens, and the underscore).
     * <p>
     * Separator characters are not allowed within the matched target tokens, while only characters
     * defined as separators may be matched between tokens. This means that if any of these
     * separator characters are encountered within a token of the entity name, the match will fail.
     * <p>
     * Consecutive character ranges can be grouped as in a regular expression (like "A-Z" for all
     * upper-case ASCII letters), and generally all escaping rules of regular expressions must be
     * obeyed.
     * <p>
     * For example, for gene names we suggest to extend the default separators with commas, dots,
     * and slashes.
     */
    public Builder setSeparators(String separators) {
      setOptionalParameter(PARAM_SEPARATORS, separators);
      return this;
    }

    /** Define max. number of consecutive separator characters (must be > 0). */
    public Builder setSeparatorLengths(int length) {
      if (length < 1) throw new IllegalArgumentException("length must be positive");
      setOptionalParameter(PARAM_SEPARATOR_LENGTH, length);
      return this;
    }
  }

  /**
   * Configure a JDBC Gazetteer Resource.
   * <p>
   * The SQL query to fetch the entity IDs and names should always be a <code>SELECT</code>
   * statement that has as its two first results a ID and a name (String). A very simplistic query
   * could just be:
   * 
   * <pre>
   * SELECT id, name FROM entities;
   * </pre>
   * 
   * @param databaseUrl a JDBC database URL
   * @param driverClassName a fully qualified JDBC driver class name
   * @param querySql a SQL statement that retrieves ID, name pairs
   */
  public static Builder configure(String databaseUrl, String driverClassName, String querySql) {
    return new Builder(databaseUrl, driverClassName, querySql);
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

  /** Replace space and dash characters with their normalized ASCII version. */
  private static String normalize(String input) {
    String tmp = SPACES.matcher(input).replaceAll(" ");
    return DASHES.matcher(tmp).replaceAll("-");
  }

  @Override
  public void load(DataResource dataResource) throws ResourceInitializationException {
    super.load(dataResource);
    mappings = new HashMap<String, Set<String>>(1024);
    if (separatorLength > 1) split = Pattern.compile(String.format("[%s]{1,%d}",
        escapeAll(separators), separatorLength));
    else split = Pattern.compile(String.format("[%s]", escapeAll(separators)));
  }

  /** Escape each character in the String with a backslash. */
  private String escapeAll(String str) {
    StringBuilder sb = new StringBuilder();
    for (char c : str.toCharArray())
      sb.append('\\').append(c);
    return sb.toString();
  }

  /** Generate the keys, the trie and the key-to-ID mappings. */
  @Override
  public void afterResourcesInitialized() {
    // note: "afterResourcesInitialized()" is a sort-of broken uimaFIT API,
    // because it cannot throw a ResourceInitializationException
    // therefore, this code throws a RuntimeException to the same effect...
    super.afterResourcesInitialized();
    List<Automaton> automata = new ArrayList<Automaton>(128);
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      ResultSet result = stmt.executeQuery(querySql);
      while (result.next()) {
        String name = result.getString(2);
        String key = makeKey(name);
        if (key == null) continue;
        final String dbId = result.getString(1);
        if (!hasAutomataFor(key)) automata.add(makeAutomaton(key));
        processMapping(dbId, key); // key-to-ID mapping
        if (idMatching) {
          key = makeKey(dbId);
          if (key == null) continue;
          if (!hasAutomataFor(key)) automata.add(makeAutomaton(key));
          processMapping(dbId, key); // key-to-ID mapping
        }
      }
      conn.close();
    } catch (SQLException e) {
      logger.log(Level.SEVERE, "SQL error", e);
      throw new RuntimeException(e);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "unknown error", e);
      throw new RuntimeException(e);
    }
    logger.log(Level.INFO, "defined {0} keys for {1} unique patterns",
        new Object[] { mappings.size(), automata.size() });
    Map<Character, Set<Character>> map = new HashMap<Character, Set<Character>>();
    Set<Character> cset = new HashSet<Character>();
    for (char c : separators.toCharArray())
      cset.add(c);
    map.put(SEPARATOR.charAt(0), cset);
    Automaton trie = BasicOperations.union(automata);
    // trie.minimize(); // does not help to reduce RAM usage :(
    trie = trie.subst(map);
    patterns = new RunAutomaton(trie);
    logger.log(Level.INFO, "compiled trie for all names");
  }

  /** Return <code>true</code> if the Gazetteer already has a patter to match that key. */
  private boolean hasAutomataFor(String key) {
    if (exactCaseMatching) return mappings.containsKey(key);
    else return mappings.containsKey(makeLower(key));
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
    if (separatorLength > 1) pattern = SEPARATOR_PATTERN.matcher(pattern).replaceAll(
        String.format("$1{0,%d}", separatorLength));
    else pattern = SEPARATOR_PATTERN.matcher(pattern).replaceAll("$1?");
    logger.log(Level.FINE, "{0} -> {1}", new String[] { key, pattern });
    return (new RegExp(pattern)).toAutomaton();
  }

  /** Return the "separated" (regular) key of an entity name. */
  private String makeKey(String name) {
    StringBuilder sb = new StringBuilder();
    for (String sub : split.split(normalize(name))) {
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
        if (!isCapitalized(token, lastSplit, offset, charType)) {
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

  /** Add regular and normal mapping, as well as their case-insensitive versions if requested. */
  private void processMapping(final String dbId, final String key) {
    addMapping(key, dbId); // only the regular key maps to the DB ID
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

  // GazetteerResource Methods
  public Map<Offset, String> match(String input) {
    Map<Offset, String> result = new HashMap<Offset, String>();
    AutomatonMatcher match = patterns.newMatcher(normalize(input));
    while (match.find())
      result.put(new Offset(match.start(), match.end()), makeKey(match.group()));
    return result;
  }

  public Set<String> resolve(String key) {
    Set<String> result = new HashSet<String>();
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
    return result;
  }

  // StringMapResource Methods
  /** Return the Set of known ID mappings for any key. */
  public Set<String> get(String key) {
    return mappings.get(key);
  }

  /** Return the number of <b>all</b> (normalized, regular, and/or case-insensitive) keys. */
  public int size() {
    return mappings.size();
  }

  /** Iterate over the <b>regular</b> keys (only). */
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

  /** Check if the (regular, normalized, and/or case-insensitive) key has a mapping. */
  public boolean containsKey(String key) {
    return mappings.containsKey(key);
  }
}
