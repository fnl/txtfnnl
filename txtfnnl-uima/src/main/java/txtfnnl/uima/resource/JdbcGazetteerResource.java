/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
  /**
   * Normalized separator characters between tokens (default: the underscore and all Unicode spaces
   * and dashes).
   */
  public static final String PARAM_SEPARATORS = "Separators";
  /** Default separators: spaces, dashes, and the underscore. */
  public static final String DEFAULT_SEPARATORS = " \u00A0\u2000-\u200B\u202F\u205F\u3000\uFEFF_\\-\u1680\u2010-\u2015\u2212\uFE58\uFE63\uFF0D";
  @ConfigurationParameter(name = PARAM_SEPARATORS,
      mandatory = false,
      defaultValue = DEFAULT_SEPARATORS)
  private String separators;
  /**
   * The max. length of matchable consecutive separator characters (default: 3).
   * <p>
   * Should not be less than one.
   */
  public static final String PARAM_SEPARATOR_LENGTH = "SeparatorLength";
  @ConfigurationParameter(name = PARAM_SEPARATOR_LENGTH, mandatory = false, defaultValue = "3")
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
  /** Mappings of regular keys to ID sets and of normalized keys to regular key sets. */
  private Map<String, Set<String>> mappings;
  /** The "meta-pattern" created from all individual names. */
  private RunAutomaton patterns;
  private Pattern reservedChars = Pattern
      .compile("([\\@\\&\\~\\^\\#\\\\\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\<\\>])");
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

    /** Define max. number of consecutive separator characters (default: 3; must be > 0). */
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

  @Override
  public void load(DataResource dataResource) throws ResourceInitializationException {
    super.load(dataResource);
    mappings = new HashMap<String, Set<String>>(1024);
    split = Pattern.compile(separatorRegEx(1));
  }

  /** Create a RegEx from the specified separators, valid up to the defined length. */
  private String separatorRegEx(int minLen) {
    return String.format("[%s]{%d,%d}", separators, minLen, separatorLength);
  }

  /** Generate the names' regex patterns and the key-to-ID mappings. */
  @Override
  public void afterResourcesInitialized() {
    // note: "afterResourcesInitialized()" is a sort-of broken uimaFIT API,
    // because it cannot throw a ResourceInitializationException
    // therefore, this code throws a RuntimeException to the same effect...
    super.afterResourcesInitialized();
    Map<Integer, Automaton> regexes = new HashMap<Integer, Automaton>();
    String regexSep = separatorRegEx(0);
    int numPatterns = 0;
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      ResultSet result = stmt.executeQuery(querySql);
      while (result.next()) {
        String key = makeKey(result.getString(2));
        if (key == null) continue;
        final String dbId = result.getString(1);
        if (!mappings.containsKey(key))
          numPatterns += addPattern(key.length(), makePattern(key, regexSep), regexes);
        processMapping(dbId, key);
        if (idMatching) {
          key = makeKey(dbId);
          if (key == null) continue;
          if (!mappings.containsKey(key))
            numPatterns += addPattern(key.length(), makePattern(key, regexSep), regexes);
          processMapping(dbId, key);
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
    logger.log(Level.INFO, "defined {0} keys for {1} unique patterns over {2} length groups",
        new int[] { mappings.size(), numPatterns, regexes.size() });
    if (logger.isLoggable(Level.FINE)) debugPatterns(regexes.values(), regexSep);
    patterns = joinPatterns(regexes);
    logger.log(Level.INFO, "compiled and ready");
  }

  private Automaton makePattern(String key, String sep) {
    key = reservedChars.matcher(key).replaceAll("\\$1");
    if (!exactCaseMatching) {
      int[] unicode = StringUtils.toCodePointArray(key);
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
      key = caseInsensitive.toString();
    }
    key = key.replace(SEPARATOR, sep);
    return new RegExp(key).toAutomaton();
  }

  private int addPattern(Integer key, Automaton pattern, Map<Integer, Automaton> regexes) {
    try {
      regexes.put(key, regexes.get(key).union(pattern));
    } catch (NullPointerException e) {
      regexes.put(key, pattern);
    }
    return 1;
  }

  private void debugPatterns(Collection<Automaton> patternGroups, String regexSep) {
    StringBuilder sb = new StringBuilder();
    for (Automaton pattern : patternGroups)
      sb.append(pattern.toString()).append('\n');
    logger.log(Level.FINE, "patterns:\n{0}", sb.toString().replace(regexSep, "-"));
  }

  private RunAutomaton joinPatterns(Map<Integer, Automaton> regexMap) {
    // (2) sort longest patterns first and count the patterns
    List<Integer> lengths = new ArrayList<Integer>(regexMap.keySet());
    Collections.sort(lengths);
    Collections.reverse(lengths);
    Automaton regex = new Automaton();
    for (Integer key : lengths)
      regex = regex.union(regexMap.get(key));
    return new RunAutomaton(regex);
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
  private void addMapping(String key, String value) {
    if (!mappings.containsKey(key)) mappings.put(key, new HashSet<String>());
    mappings.get(key).add(value);
  }

  /** Return the "separated" (regular) key of an entity name. */
  private String makeKey(String name) {
    StringBuilder regex = new StringBuilder();
    for (String sub : split.split(name)) {
      if (sub.length() > 0) {
        if (regex.length() > 0) regex.append(SEPARATOR);
        regex.append(makeToken(sub));
      }
    }
    String pattern = regex.toString();
    if (pattern.length() == 0) {
      logger.log(Level.WARNING, "\"" + name + "\" gives rise to an empty pattern");
      return null;
    }
    return pattern;
  }

  /** Return the Unicode category-separated (regular) sub-key of an entity token. */
  private String makeToken(String token) {
    StringBuilder regex = new StringBuilder();
    int length = token.length();
    int offset = 0;
    int lastSplit = 0;
    int charPoint = token.codePointAt(offset);
    int charType = Character.getType(charPoint);
    while (offset < length) {
      charPoint = token.codePointAt(offset);
      if (Character.getType(charPoint) != charType) {
        if (!isCapitalized(token, lastSplit, offset, charType)) {
          regex.append(SEPARATOR);
          lastSplit = offset;
        }
        charType = Character.getType(charPoint);
      }
      int charLen = Character.charCount(charPoint);
      regex
          .append(charLen == 1 ? token.charAt(offset) : token.substring(offset, offset + charLen));
      offset += charLen;
    }
    return regex.toString();
  }

  /** Return <code>true</code> if the current split is a capitalized word. */
  private boolean isCapitalized(String token, int lastSplit, int offset, int lastCharType) {
    return (lastCharType == Character.UPPERCASE_LETTER &&
        Character.getType(token.codePointAt(offset)) == Character.LOWERCASE_LETTER && Character
        .charCount(token.codePointAt(lastSplit)) + lastSplit == offset);
  }

  // GazetteerResource Methods
  public Map<Offset, String> match(String input) {
    Map<Offset, String> result = new HashMap<Offset, String>();
    int length = input.length(), offset = 0;
    AutomatonMatcher match = patterns.newMatcher(input, offset, length);
    while (match.find()) {
      Offset target = new Offset(match.start(), match.end());
      result.put(target, makeKey(match.group()));
      offset = match.start() + Character.charCount(input.codePointAt(match.start()));
      match = patterns.newMatcher(input, offset, length);
    }
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
