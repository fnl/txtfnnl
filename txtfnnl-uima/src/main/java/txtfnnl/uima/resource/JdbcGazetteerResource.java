/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.utils.UIMAUtils;
import txtfnnl.utils.StringUtils;

/**
 * JdbcGazetteerResource fetches a list of id, name values from a DB using a user-defined query and
 * generates regular expression matchers for those names. Only the alphanumeric values (in terms of
 * Unicode super-categories, i.e., N and L) are matches, while any number of non-alphanumerics are
 * allowed between each such Unicode category change except for one single upper-case to lower-case
 * change.
 * <p>
 * For example, the name "[AbcDEFghi 123]-Jkl" will separate into five tokens: "Abc", "DEF", "ghi",
 * "123", and "Jkl"; All tokens may be separated by zero or more non-alphanumerics.
 * <p>
 * After loading the names and generating the pattern matchers at startup, input text can be
 * matched against this gazetteer using the {@link #match(String)} method. The resulting Map
 * contains the offsets (in the input string) and keys of the found matches. For each key the list
 * of matching DB IDs can be fetched with {@link #get(String)}. If case-insensitive matching is
 * configured and the key begins with a '~' (tilde) character, the match only could be made for the
 * case-insensitive version of the key.
 * 
 * @author Florian Leitner
 */
public class JdbcGazetteerResource extends JdbcConnectionResourceImpl implements
    StringMapResource<Set<String>> {
  /** The SQL query to fetch the entity names. */
  public static final String PARAM_QUERY_SQL = "QuerySQL";
  @ConfigurationParameter(name = PARAM_QUERY_SQL, mandatory = true)
  private String querySql;
  /** Whether to match the DB IDs themselves, too (default: <code>true</code>). */
  public static final String PARAM_ID_MATCHING = "IDMatching";
  @ConfigurationParameter(name = PARAM_ID_MATCHING, mandatory = false, defaultValue = "true")
  private boolean idMatching;
  /** Whether to do case-sensitive matching only (default: <code>false</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "false")
  private boolean exactCaseMatching;
  // /** Whether to use the fuzzy matcher or not (default: <code>false</code>). */
  /* XXX: fuzzy matching?
  public static final String PARAM_FUZZY_MATCHING = "FuzzyMatching";
  @ConfigurationParameter(name = PARAM_FUZZY_MATCHING, mandatory = false, defaultValue = "false")
  private boolean fuzzyMatching;
  */
  private Map<String, Set<String>> keyIds;
  private Map<String, Set<String>> normalIds;
  private Pattern patterns;
  static final String SEPARATOR = "[ _~\\.\\-\u2010-\u2015\\/\\\\]"; // NB: without a quantifier!
  static final Pattern SPLIT_ON_SEP = Pattern.compile(SEPARATOR + "+");

  /** Simple container for two hashable integers, <code>begin</code> and <code>end</code>. */
  public class Offset {
    public final int begin;
    public final int end;

    Offset(int b, int e) {
      begin = b;
      end = e;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Offset)) return false;
      Offset o = (Offset) other;
      return begin == o.begin && end == o.end;
    }

    @Override
    public int hashCode() {
      return (17 + begin) * (31 + end);
    }
  }

  /**
   * Configure a JdbcGazetterResource.
   * 
   * @param connectionUrl the JDBC URL to connect to ("<code>jdbc:</code> <i>[provider]</i>
   *        <code>://</code><i>[host[:port]]</i> <code>/</code><i>[database]</i>")
   * @param querySql the SQL query returning id, value pairs
   * @param exactCaseMatching flag indicating if case-sensitive (<code>true</code>) or also
   *        case-insensitive (<code>false</code>) matching is used
   * @param driverClass the driver's class name to use (see {@link #PARAM_DRIVER_CLASS})
   * @param username the username to use (optional)
   * @param password the password to use (optional)
   * @param loginTimeout seconds before timing out the connection (optional)
   * @param isolationLevel an isolation level name (see {@link #PARAM_ISOLATION_LEVEL}, optional)
   * @param readOnly if <code>false</code>, the resulting connections can be used to write to the
   *        DB
   * @return a configured descriptor
   * @throws IOException
   */
  @SuppressWarnings("serial")
  public static ExternalResourceDescription configure(String connectionUrl,
      final String querySql, final boolean idMatching,
      final boolean exactCaseMatching, // final boolean fuzzyMatching,
      final String driverClass, final String username, final String password,
      final int loginTimeout, final String isolationLevel, final boolean readOnly)
      throws IOException {
    return ExternalResourceFactory.createExternalResourceDescription(JdbcGazetteerResource.class,
        connectionUrl, UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_QUERY_SQL, querySql);
            put(PARAM_ID_MATCHING, idMatching);
            put(PARAM_CASE_MATCHING, exactCaseMatching);
            // put(PARAM_FUZZY_MATCHING, fuzzyMatching);
            put(PARAM_DRIVER_CLASS, driverClass);
            put(PARAM_USERNAME, username);
            put(PARAM_PASSWORD, password);
            put(PARAM_ISOLATION_LEVEL, isolationLevel);
            put(PARAM_LOGIN_TIMEOUT, loginTimeout);
            put(PARAM_READ_ONLY, readOnly);
          }
        }));
  }

  /**
   * Configure a JdbcGazetterResource using case-insensitive matching with a <b>read-only</b>,
   * non-isolated DB connection that requires neither username or password.
   * 
   * @param connectionUrl the URL to connect to
   * @param querySql the SQL query returning id, value pairs
   * @param driverClass the driver's class name to use (see {@link #PARAM_DRIVER_CLASS})
   * @return a configured descriptor
   * @throws IOException
   */
  public static ExternalResourceDescription configure(String connectionUrl, String querySql,
      String driverClass) throws IOException {
    return JdbcGazetteerResource.configure(connectionUrl, querySql, true, false, // false,
        driverClass, null, null, -1, null, true);
  }

  @Override
  public void load(DataResource dataResource) throws ResourceInitializationException {
    super.load(dataResource);
    keyIds = new HashMap<String, Set<String>>();
    normalIds = new HashMap<String, Set<String>>();
  }

  /** Generate the names' regex patterns and the key-to-ID mappings. */
  @Override
  public void afterResourcesInitialized() {
    // note: "afterResourcesInitialized()" is a sort-of broken uimaFIT API,
    // because it cannot throw a ResourceInitializationException
    // therefore, this code throws a RuntimeException to the same effect...
    super.afterResourcesInitialized();
    List<String> regexes = new LinkedList<String>();
    // (1) retrieve the names
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      ResultSet result = stmt.executeQuery(querySql);
      String regexSep = SEPARATOR + "{0,3}"; // limit separators to a max. of three characters
      String dbId = result.getString(1); 
      while (result.next()) {
        String key = makeKey(result.getString(2));
        if (!keyIds.containsKey(key)) {
          String pattern = key.replace("-", regexSep);
          regexes.add(String.format("\\b%s|%s\\b", pattern, pattern));
          keyIds.put(key, new HashSet<String>());
        }
        if (idMatching) {
          regexes.add(String.format("\\b%s\\b", Pattern.quote(dbId)));
          String idKey = makeKey(dbId);
          if (!keyIds.containsKey(idKey)) keyIds.put(idKey, new HashSet<String>());
          keyIds.get(idKey).add(dbId);
        }
        addMappings(key, result.getString(1));
        if (!exactCaseMatching) addMappings(String.format("~%s", key.toLowerCase()), key);
      }
      conn.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    // (2) sort longest patterns first
    Collections.sort(regexes, new Comparator<String>() {
      public int compare(String a, String b) {
        if (a.length() > b.length()) return -1;
        else if (a.length() < b.length()) return 1;
        else return 0;
      }
    });
    // (3) compile the patterns
    if (exactCaseMatching) patterns = Pattern.compile(StringUtils.join('|', regexes.iterator()));
    else patterns = Pattern.compile(StringUtils.join('|', regexes.iterator()),
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  /** Add the key-to-ID and normalized-key-to-ID mappings. */
  private void addMappings(String key, String id) {
    if (!keyIds.containsKey(key)) keyIds.put(key, new HashSet<String>());
    keyIds.get(key).add(id);
    String normal = key.replace("-", "");
    if (!normalIds.containsKey(normal)) normalIds.put(normal, new HashSet<String>());
    normalIds.get(normal).add(id);
  }

  /** Return the dash-separated key of the entity name. */
  private String makeKey(String name) {
    StringBuilder regex = new StringBuilder();
    for (String sub : SPLIT_ON_SEP.split(name)) {
      if (sub.length() > 0) {
        if (regex.length() > 0) regex.append('-');
        regex.append(makeToken(sub));
      }
    }
    String pattern = regex.toString();
    if (pattern.length() == 0)
      throw new IllegalArgumentException("zero-length pattern from \"" + name + "\"");
    return pattern;
  }

  /** Return the dash-separated key of an alphanumeric token. */
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
          regex.append('-');
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

  /**
   * For an input string, match all content to this gazetteer, returning offset values mapped to
   * entity keys.
   * 
   * @param input to scan for possible mentions of the gazetteer's entities
   * @return The matched entities as offsets mapped to their corresponding keys.
   */
  public Map<Offset, String> match(String input) {
    Matcher match = patterns.matcher(input);
    Map<Offset, String> result = new HashMap<Offset, String>();
    int offset = 0;
    while (match.find(offset)) {
      result.put(new Offset(match.start(), match.end()), makeKey(match.group()));
      offset += Character.charCount(input.codePointAt(match.start()));
    }
    return result;
  }

  /** Return the Set of known mappings for a key. */
  public Set<String> get(String key) {
    return keyIds.get(key);
  }

  /** Return the Set of known mappings for a normalized key. */
  public Set<String> getNormal(String normal) {
    return normalIds.get(normal);
  }

  /** Return the number of mapped entities. */
  public int size() {
    return keyIds.size();
  }

  /** Iterate over all known keys. */
  public Iterator<String> iterator() {
    return keyIds.keySet().iterator();
  }

  public String getResourceUrl() {
    return getUrl();
  }

  /** Check if a key is known. */
  public boolean containsKey(String key) {
    return keyIds.containsKey(key);
  }

  /** Check if a normalized key is known. */
  public boolean containsNormal(String normal) {
    return normalIds.containsKey(normal);
  }

  public boolean usesExactCaseMatching() {
    return exactCaseMatching;
  }
}
