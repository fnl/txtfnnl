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
  /** Whether to do case-sensitive matching only (default: <code>true</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "true")
  private boolean caseMatching;
  // /** Whether to use the fuzzy matcher or not (default: <code>false</code>). */
  /* XXX: add fuzzy matching, too?
  public static final String PARAM_FUZZY_MATCHING = "FuzzyMatching";
  @ConfigurationParameter(name = PARAM_FUZZY_MATCHING, mandatory = false, defaultValue = "false")
  private boolean fuzzyMatching;
  */
  private Map<String, Set<String>> identifiers;
  private Map<String, Set<String>> caselessIdentifiers;
  private Pattern pattern;
  private Pattern caseInsensitivePattern;
  static final String SEPARATOR = "[^\\p{L}\\p{N}]"; // NB: without a quantifier!
  static final Pattern splitSep = Pattern.compile(SEPARATOR + "+");
  private int entities = 0;

  /** Simple container for two hashable integer values <code>begin</code> and <code>end</code>. */
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
   * @param caseMatching flag indicating if case sensitive (<code>true</code>) or also insensitive
   *        (<code>false</code>) matching is used
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
      final String querySql,
      final boolean caseMatching, // final boolean fuzzyMatching, XXX
      final String driverClass, final String username, final String password,
      final int loginTimeout, final String isolationLevel, final boolean readOnly)
      throws IOException {
    return ExternalResourceFactory.createExternalResourceDescription(JdbcGazetteerResource.class,
        connectionUrl, UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_QUERY_SQL, querySql);
            put(PARAM_CASE_MATCHING, caseMatching);
            // put(PARAM_FUZZY_MATCHING, fuzzyMatching); XXX
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
   * Configure a JdbcGazetterResource using case-sensitive matching with a <b>read-only</b>,
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
    return JdbcGazetteerResource.configure(connectionUrl, querySql, false, // false, XXX
        driverClass, null, null, -1, null, true);
  }

  @Override
  public void load(DataResource dataResource) throws ResourceInitializationException {
    super.load(dataResource);
    identifiers = new HashMap<String, Set<String>>();
    if (!caseMatching) caselessIdentifiers = new HashMap<String, Set<String>>();
    else caselessIdentifiers = null;
  }

  @Override
  public void afterResourcesInitialized() {
    // note: "afterResourcesInitialized()" is a sort-of broken uimaFIT API,
    // because it cannot throw a ResourceInitializationException
    // therefore, this code throws a RuntimeException to the same effect...
    super.afterResourcesInitialized();
    List<String> regexes = new LinkedList<String>();
    // retrieve the names
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      ResultSet result = stmt.executeQuery(querySql);
      while (result.next()) {
        entities++;
        String id = result.getString(1);
        String name = result.getString(2);
        String key = normalizeName(name);
        if (!identifiers.containsKey(key)) regexes.add(buildRegex(name));
        String universalKey = key.indexOf('-') == -1 ? null : key.replace("-", "");
        addMappings(identifiers, id, key, universalKey);
        if (!caseMatching)
          addMappings(caselessIdentifiers, id, String.format("~%s", key.toLowerCase()),
              universalKey == null ? null : String.format("~%s", universalKey.toLowerCase()));
      }
      conn.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    // sort longest first
    Collections.sort(regexes, new Comparator<String>() {
      public int compare(String a, String b) {
        if (a.length() > b.length()) return -1;
        else if (a.length() < b.length()) return 1;
        else return 0;
      }
    });
    // compile the patterns
    pattern = Pattern.compile(StringUtils.join('|', regexes.iterator()));
    if (!caseMatching) caseInsensitivePattern = Pattern.compile(pattern.pattern(),
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    else caseInsensitivePattern = null;
  }

  private void addMappings(Map<String, Set<String>> mappings, String id, String key,
      String universalKey) {
    if (!mappings.containsKey(key)) mappings.put(key, new HashSet<String>());
    mappings.get(key).add(id);
    if (universalKey != null) {
      if (!mappings.containsKey(universalKey)) mappings.put(universalKey, new HashSet<String>());
      mappings.get(universalKey).add(id);
    }
  }

  /** Return the case-sensitive, normalized name key for an input String. */
  private String normalizeName(String input) {
    StringBuilder normal = new StringBuilder();
    for (String sub : splitSep.split(input)) {
      if (normal.length() > 0 && sub.length() > 0) normal.append('-');
      normal.append(sub);
    }
    return normal.toString();
  }

  /** Build a valid regular expression of a name. */
  private String buildRegex(String name) {
    StringBuilder regex = new StringBuilder();
    String[] tokens = splitSep.split(name);
    int length = 0;
    for (String token : tokens) {
      if (length > 0 && token.length() > 0) regex.append(SEPARATOR).append('*');
      length += token.length();
      int offset = 0;
      int lastSplit = 0;
      int charPoint = token.codePointAt(offset);
      int charType = Character.getType(charPoint);
      while (offset < token.length()) {
        charPoint = token.codePointAt(offset);
        if (Character.getType(charPoint) != charType) {
          if (!isCapitalized(token, lastSplit, offset, charType)) {
            regex.append(SEPARATOR).append('*');
            lastSplit = offset;
          }
          charType = Character.getType(charPoint);
        }
        int charLen = Character.charCount(charPoint);
        regex.append(charLen == 1 ? token.charAt(offset) : token.substring(offset, offset +
            charLen));
        offset += charLen;
      }
    }
    if (length == 0) throw new RuntimeException("zero-length name \"" + name + "\"");
    return String.format("\\b%s|%s\\b", regex.toString(), regex.toString());
  }

  private boolean isCapitalized(String token, int lastSplit, int offset, int lastCharType) {
    return (lastCharType == Character.UPPERCASE_LETTER &&
        Character.getType(token.codePointAt(offset)) == Character.LOWERCASE_LETTER && Character
        .charCount(token.codePointAt(lastSplit)) + lastSplit == offset);
  }

  /**
   * For an input string, match all content to this gazetteer, returning offset values mapped to
   * the normalized name keys.
   * 
   * @param input to scan for possible mentions of the gazetteer's entities
   * @return The matched entities as offsets mapped to their corresponding normalized name keys.
   */
  public Map<Offset, String> match(String input) {
    Matcher m = pattern.matcher(input);
    Map<Offset, String> result = new HashMap<Offset, String>();
    while (m.find())
      result.put(new Offset(m.start(), m.end()), normalizeName(m.group()));
    if (!caseMatching) {
      m = caseInsensitivePattern.matcher(input);
      while (m.find()) {
        Offset o = new Offset(m.start(), m.end());
        if (!result.containsKey(o))
          result.put(o, String.format("~%s", normalizeName(m.group()).toLowerCase()));
      }
    }
    return result;
  }

  /** Return the Set of known identifiers for a normalized name key. */
  public Set<String> get(String nameKey) {
    if (identifiers.containsKey(nameKey)) return identifiers.get(nameKey);
    else if (!caseMatching) return caselessIdentifiers.get(nameKey);
    else return null;
  }

  /** Return the number of mapped entities. */
  public int size() {
    return entities;
  }

  /** Iterate over all case-sensitive, normalized name keys. */
  public Iterator<String> iterator() {
    return identifiers.keySet().iterator();
  }

  public String getResourceUrl() {
    return getUrl();
  }
}
