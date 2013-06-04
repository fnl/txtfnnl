/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import com.googlecode.concurrenttrees.common.KeyValuePair;
import org.apache.uima.util.Level;
import org.uimafit.descriptor.ConfigurationParameter;
import txtfnnl.utils.Offset;
import txtfnnl.utils.stringsim.LeitnerLevenshtein;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The JdbcGazetteerResource uses a {@link JdbcGazetteerResource#PARAM_DRIVER_CLASS JDBC database}
 * to retrieve the ID, name values used to populate the Gazetteer. It can use any user-defined
 * {@link GnamedGazetteerResource#PARAM_QUERY_SQL query} that selects these ID, name values and
 * uses regular expressions matching for those names.
 *
 * @author Florian Leitner
 */
public class GnamedGazetteerResource extends JdbcGazetteerResource {
  public static final String PARAM_NO_GREEK_MAPPING = "DisableGreekMapping";
  @ConfigurationParameter(name = PARAM_NO_GREEK_MAPPING,
      mandatory = false,
      description = "Disable mapping of Latin names of Greek letters ('alpha', 'beta', ...) " + "to their actual characters",
      defaultValue = "false")
  private boolean disableGreekMapping = false;
  /** Mappings of gene IDs to their taxon IDs. */
  private Map<String, String> taxonMap = new HashMap<String, String>();

  public static class Builder extends JdbcGazetteerResource.Builder {
    Builder(String url, String driverClass, String querySql) {
      super(GnamedGazetteerResource.class, url, driverClass, querySql);
    }

    /**
     * Disable the mapping of Latin names of Greek letters to the actual Greek letters.
     * <p/>
     * The mapping replaces the Strings "alpha" and "ALPHA" in any gene name with the lower-case
     * Greek char for alpha, while "Alpha" will be replaced with the upper-case char. Any other,
     * mixed case spelling will not trigger a mapping. Idem for all other Greek letters.
     */
    public Builder disableGreekMapping() {
      setOptionalParameter(PARAM_NO_GREEK_MAPPING, true);
      return this;
    }
  }

  /**
   * Configure a resource for transaction-less, read-write JDBC connections.
   *
   * @param databaseUrl     a JDBC database URL
   * @param driverClassName a fully qualified JDBC driver class name
   * @param query           that will retrieve ID, taxon ID, gene name triplets from the database
   */
  public static Builder configure(String databaseUrl, String driverClassName, String query) {
    return new Builder(databaseUrl, driverClassName, query);
  }

  public static final char[] GREEK_NAMES_FIRST = {'A', 'B', 'C', 'D', 'E', 'G', 'I', 'K', 'L', 'M', 'N', 'O', 'P', 'R', 'S', 'T', 'U', 'X', 'Z', 'a', 'b', 'c', 'd', 'e', 'g', 'i', 'k', 'l', 'm', 'n', 'o', 'p', 'r', 's', 't', 'u', 'x', 'z'};
  public static final String[][] GREEK_NAMES = {{"Alpha"}, {"Beta"}, {"Chi"}, {"Delta"}, {"Epsilon", "Eta"}, {"Gamma"}, {"Iota"}, {"Kappa"}, {"Lambda"}, {"Mu"}, {"Nu"}, {"Omega", "Omicron"}, {"Pi", "Psi", "Phi"}, {"Rho"}, {"Sigma"}, {"Tau", "Theta"}, {"Upsilon"}, {"Xi"}, {"Zeta"}, {"alpha"}, {"beta"}, {"chi"}, {"delta"}, {"epsilon"}, {"eta"}, {"gamma"}, {"iota"}, {"kappa"}, {"lambda"}, {"mu"}, {"nu"}, {"omicron", "omega"}, {"pi", "phi", "psi"}, {"rho"}, {"sigma"}, {"tau", "theta"}, {"upsilon"}, {"xi"}, {"zeta"}};

  /** Generate the keys, the trie and the key-to-ID mappings. */
  @Override
  public void afterResourcesInitialized() {
    initializeJdbc();
    // fetch and process the mappings
    // uses "key = makeKey(name) && if (key != null) processMapping(dbId, name, key)"
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      logger.log(Level.INFO, "running SQL query: ''{0}''", querySql);
      ResultSet result = stmt.executeQuery(querySql);
      while (result.next()) {
        final String geneId = result.getString(1);
        final String taxId = result.getString(2);
        final String name = result.getString(3);
        put(geneId, name);
        if (!disableGreekMapping) {
          final String nameWithGreekLetters = mapLatinNamesOfGreekLetters(name);
          if (nameWithGreekLetters != null) put(geneId, nameWithGreekLetters);
        }
        taxonMap.put(geneId, taxId);
      }
      conn.close();
    } catch (SQLException e) {
      logger.log(Level.SEVERE, "SQL error", e);
      throw new RuntimeException(e);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "unknown error", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Return a String with the Latin names of Greek letters replaced with actual Greek characters.
   *
   * @param str to map Latin Strings of Greek letters to Greek characters
   *
   * @return the mapped String or <code>null</code> if no letter was mapped.
   */
  private String mapLatinNamesOfGreekLetters(String str) {
    int len = str.length();
    int last = 0;
    StringBuilder normal = new StringBuilder();
    for (int offset = 0; offset < len - 1; ++offset) {
      char c = str.charAt(offset);
      if (c <= 'z' && c >= 'A') {
        int idx = Arrays.binarySearch(GREEK_NAMES_FIRST, c);
        if (idx > -1) {
          SCAN:
          for (String latin : GREEK_NAMES[idx]) {
            for (int ext = 1; ext < latin.length(); ++ext)
              if (offset + ext >= len || latin.charAt(ext) != str.charAt(offset + ext)) continue SCAN;
            normal.append(str.subSequence(last, offset));
            normal.appendCodePoint(greekLetterFor(latin));
            last = offset + latin.length();
            offset = last - 1;
            break SCAN;
          }
        }
      }
    }
    if (last > 0) {
      normal.append(str.subSequence(last, len));
      return normal.toString();
    } else {
      return null;
    }
  }

  private static final Pattern NUMERIC_EXPANSION = Pattern.compile("^\\-([0-9]+)");
  private static final Pattern NUMERIC_PREFIX = Pattern.compile("([0-9]+)$");
  private static final Pattern ALPHABETIC_EXPANSION = Pattern.compile("^\\-([B-Z]([^A-Z]|$))");
  private static final Pattern ALPHABETIC_PREFIX = Pattern.compile("([A-Z])$");
  private static final Pattern ALTERNATE_EXPANSION = Pattern.compile("^\\/(\\w+)");

  /** Extended matching using alphanumeric and alternate expansions.
   * <p>
   * This matches, for example, "name5" in "name1-10", or "nameB" in "nameA/B". It does not match
   * more complex linguistic conjunctions, such as "name-1, -2, and -3".
   * </p>
   * @param str the string to match
   * @param start the start where a match may be made
   * @param end the end where a match may be made
   * @return the mappings of the offsets of all matches to their Gazetteer IDs
   */
  public Map<Offset, List<String>> match(final String str, final int start, final int end) {
    // TODO: make this spaghetti-code actually legible...
    Map<Offset, List<String>> hits = super.match(str, start, end);
    for (Offset pos : new LinkedList<Offset>(hits.keySet())) {
      if (pos.end() < end) {
        CharSequence test = str.subSequence(pos.end(), end);
        Matcher m = NUMERIC_EXPANSION.matcher(test);
        if (m.find()) {
          int max = Integer.parseInt(m.group(1)) + 1;
          int expansionEnd = pos.end() + m.end(1);
          m = NUMERIC_PREFIX.matcher(str.subSequence(pos.start(), pos.end()));
          if (m.find()) {
            int min = Integer.parseInt(m.group(1)) + 1;
            String base = str.substring(pos.start(), pos.start() + m.start(1));
            int len = pos.end() - pos.start();
            Offset off = new Offset(pos.start(), expansionEnd);
            for (int i = min; i < max; ++i) {
              String alt = String.format("%s%d", base, i);
              if (!exactCaseMatching) alt = alt.toLowerCase();
              for (KeyValuePair<List<String>> hit : trie.scanForKeyValuePairsAtStartOf(alt))
                if (hit.getKey().length() == len) {
                  if (hits.containsKey(off)) hits.get(off).addAll(hit.getValue());
                  else hits.put(off, hit.getValue());
                }
            }
          }
        } else {
          m = ALPHABETIC_EXPANSION.matcher(test);
          if (m.find()) {
            char max = m.group(1).charAt(0);
            int expansionEnd = pos.end() + m.end(1);
            m = ALPHABETIC_PREFIX.matcher(str.subSequence(pos.start(), pos.end()));
            if (m.find()) {
              char min = (char) (((int)m.group(1).charAt(0)) + 1);
              String base = str.substring(pos.start(), pos.start() + m.start(1));
              Offset off = new Offset(pos.start(), expansionEnd);
              int len = pos.end() - pos.start();
              for (char i = min; i <= max; ++i) {
                String alt = String.format("%s%s", base, String.valueOf(i));
                if (!exactCaseMatching) alt = alt.toLowerCase();
                for (KeyValuePair<List<String>> hit : trie.scanForKeyValuePairsAtStartOf(alt))
                  if (hit.getKey().length() == len) {
                    if (hits.containsKey(off)) hits.get(off).addAll(hit.getValue());
                    else hits.put(off, hit.getValue());
                  }
              }
            }
          } else {
            m = ALTERNATE_EXPANSION.matcher(test);
            if (m.find()) {
              String alt = m.group(1);
              Offset off = new Offset(pos.start(), pos.end() + m.end(1));
              alt = String.format("%s%s", str.substring(pos.start(), pos.end() - alt.length()), alt);
              if (!exactCaseMatching) alt = alt.toLowerCase();
              for (KeyValuePair<List<String>> hit : trie.scanForKeyValuePairsAtStartOf(alt))
                if (hit.getKey().length() == pos.end() - pos.start()) {
                  if (hits.containsKey(off)) hits.get(off).addAll(hit.getValue());
                  else hits.put(off, hit.getValue());
                }
            }
          }
        }
      }
    }
    return hits;
  }

  /**
   * Get the Unicode code-point value for the Latin name of a Greek letter.
   *
   * @param latin the Latin name of a Greek letter
   *
   * @throws IllegalArgumentException if the Latin name is unknown
   */
  private int greekLetterFor(String latin) {
    if (Character.isLowerCase(latin.charAt(0))) {
      for (int i = 0; i < LeitnerLevenshtein.GREEK_LOWER.length; ++i) {
        if (latin.equals(LeitnerLevenshtein.GREEK_LOWER_NAMES[i])) return LeitnerLevenshtein.GREEK_LOWER[i];
      }
    } else {
      for (int i = 0; i < LeitnerLevenshtein.GREEK_UPPER.length; ++i) {
        if (latin.equals(LeitnerLevenshtein.GREEK_UPPER_NAMES[i])) return LeitnerLevenshtein.GREEK_UPPER[i];
      }
    }
    throw new IllegalArgumentException("unknown Greek letter name " + latin);
  }

  /** Return the associated taxon ID for the given gene ID. */
  public String getTaxId(String geneId) {
    return taxonMap.get(geneId);
  }
}
