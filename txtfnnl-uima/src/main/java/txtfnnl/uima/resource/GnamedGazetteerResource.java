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
 * The <code>gnamed</code> gene name gazetteer uses a {@link JdbcGazetteerResource#PARAM_DRIVER_CLASS
 * JDBC database} to retrieve gene ID, tax ID, gene names/symbols triplets for populating the
 * matcher. It can use any user-defined {@link GnamedGazetteerResource#PARAM_QUERY_SQL SQL query}
 * that selects these values and uses (mostly) {@link ExactGazetteerResource exact matching} to
 * detect the gene names/symbols.
 * <p/>
 * In addition the the inherited matching options, the <code>gnamed</code> gazetteer can map the
 * Latin representations of Greek letter in the gene names/symbols (such as "alpha", "Gamma". or
 * "OMEGA") to their actual Greek characters. Finally, it can detect and expand simple suffixes that
 * are used to indicate two or more genes, such as "gene 1-4". It detects expansions separated by
 * dashes if they are numeric (as in the example before), or of a single character (as in "gene
 * A-C") and simple binary cases separated by slashes (as in "gene A/B").
 *
 * @author Florian Leitner
 */
public
class GnamedGazetteerResource extends JdbcGazetteerResource {
  @ConfigurationParameter(name = PARAM_NO_GREEK_MAPPING, mandatory = false, defaultValue = "false",
                          description = "Disable mapping of Latin names of Greek letters to chars.")
  private boolean disableGreekMapping = false;
  public static final String PARAM_NO_GREEK_MAPPING = "DisableGreekMapping";
  public static final String PARAM_NO_EXPANSIONS = "DisableExpansions";
  @ConfigurationParameter(name = PARAM_NO_EXPANSIONS, mandatory = false, defaultValue = "false",
                          description = "Disable expansions of lists of entities.")
  private boolean disableExpansions = true;
  /** Mappings of gene IDs to their taxon IDs. */
  private Map<String, String> taxonMap = new HashMap<String, String>();

  public static
  class Builder extends JdbcGazetteerResource.Builder {
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
    public
    Builder disableGreekMapping() {
      setOptionalParameter(PARAM_NO_GREEK_MAPPING, true);
      return this;
    }

    /** Disable the detection of expanded lists of entities (such as "root A - Z"). */
    public
    Builder disableExpansions() {
      setOptionalParameter(PARAM_NO_EXPANSIONS, true);
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
  public static
  Builder configure(String databaseUrl, String driverClassName, String query) {
    return new Builder(databaseUrl, driverClassName, query);
  }

  public static final char[] GREEK_NAMES_FIRST = {
      'A', 'B', 'C', 'D', 'E', 'G', 'I', 'K', 'L', 'M', 'N', 'O', 'P', 'R', 'S', 'T', 'U', 'X', 'Z',
      'a', 'b', 'c', 'd', 'e', 'g', 'i', 'k', 'l', 'm', 'n', 'o', 'p', 'r', 's', 't', 'u', 'x', 'z'
  };
  public static final String[][] GREEK_NAMES = {
      {"Alpha"}, {"Beta"}, {"Chi"}, {"Delta"}, {"Epsilon", "Eta"}, {"Gamma"}, {"Iota"}, {"Kappa"},
      {"Lambda"}, {"Mu"}, {"Nu"}, {"Omega", "Omicron"}, {"Pi", "Psi", "Phi"}, {"Rho"}, {"Sigma"},
      {"Tau", "Theta"}, {"Upsilon"}, {"Xi"}, {"Zeta"}, {"alpha"}, {"beta"}, {"chi"}, {"delta"},
      {"epsilon"}, {"eta"}, {"gamma"}, {"iota"}, {"kappa"}, {"lambda"}, {"mu"}, {"nu"},
      {"omicron", "omega"}, {"pi", "phi", "psi"}, {"rho"}, {"sigma"}, {"tau", "theta"}, {"upsilon"},
      {"xi"}, {"zeta"}
  };

  /** Generate the keys, the trie and the key-to-ID mappings. */
  @Override
  public
  void afterResourcesInitialized() {
    initializeJdbc();
    // fetch and process the mappings
    // uses "key = makeKey(name) && if (key != null) processMapping(dbId, name, key)"
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      logger.log(Level.INFO, "running SQL query: ''{0}''", querySql);
      ResultSet result = stmt.executeQuery(querySql);
      Set<String> knownKeys = new HashSet<String>();
      String lastId = null;
      while (result.next()) {
        final String geneId = result.getString(1);
        final String taxId = result.getString(2);
        final String name = result.getString(3);
        if (!geneId.equals(lastId)) {
          knownKeys = new HashSet<String>();
          lastId = geneId;
        }
        put(geneId, name, knownKeys);
        if (!disableGreekMapping) {
          final String nameWithGreekLetters = mapLatinNamesOfGreekLetters(name);
          if (nameWithGreekLetters != null) put(geneId, nameWithGreekLetters, knownKeys);
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

  /** Return the associated taxon ID for the given gene ID. */
  public
  String getTaxId(String geneId) {
    return taxonMap.get(geneId);
  }

  /**
   * Return a String with the Latin names of Greek letters replaced with actual Greek characters.
   *
   * @param str to map Latin Strings of Greek letters to Greek characters
   *
   * @return the mapped String or <code>null</code> if no letter was mapped.
   */
  private
  String mapLatinNamesOfGreekLetters(String str) {
    int len = str.length();
    int last = 0;
    StringBuilder normal = new StringBuilder();
    for (int offset = 0; offset < len - 1; ++offset) {
      char c = str.charAt(offset);
      if (c <= 'z' && c >= 'A') {
        int idx = Arrays.binarySearch(GREEK_NAMES_FIRST, c);
        if (idx > -1) {
          scanning:
          for (String latin : GREEK_NAMES[idx]) {
            for (int ext = 1; ext < latin.length(); ++ext)
              if (offset + ext >= len || latin.charAt(ext) != str.charAt(offset + ext))
                continue scanning;
            normal.append(str.subSequence(last, offset));
            normal.appendCodePoint(greekLetterFor(latin));
            last = offset + latin.length();
            offset = last - 1;
            break;
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

  /**
   * Get the Unicode code-point value for the Latin name of a Greek letter.
   *
   * @param latin the Latin name of a Greek letter
   *
   * @throws IllegalArgumentException if the Latin name is unknown
   */
  private
  int greekLetterFor(String latin) {
    if (Character.isLowerCase(latin.charAt(0))) {
      for (int i = 0; i < LeitnerLevenshtein.GREEK_LOWER.length; ++i) {
        if (latin.equals(LeitnerLevenshtein.GREEK_LOWER_NAMES[i]))
          return LeitnerLevenshtein.GREEK_LOWER[i];
      }
    } else {
      for (int i = 0; i < LeitnerLevenshtein.GREEK_UPPER.length; ++i) {
        if (latin.equals(LeitnerLevenshtein.GREEK_UPPER_NAMES[i]))
          return LeitnerLevenshtein.GREEK_UPPER[i];
      }
    }
    throw new IllegalArgumentException("unknown Greek letter name " + latin);
  }

  private static final Pattern ANY_EXPANSION = Pattern.compile(
      "\\s?\\-\\s?[\\p{Alnum}\\p{InGreek}][0-9]*\\b|\\/[\\p{Alnum}\\p{InGreek}]+"
  );

  /**
   * Extended matching using alphanumeric and alternate expansions. <p> This matches, for example,
   * "name5" in "name1-10", or "nameB" in "nameA/B". It does not match more complex linguistic
   * conjunctions, such as "name-1, -2, and -3". <p> Only applied if expansions are not disabled.
   *
   * @param str   the string to match
   * @param start the start where a match may be made
   * @param end   the end where a match may be made
   *
   * @return the mappings of the offsets of all matches to their Gazetteer IDs
   */
  public
  Map<Offset, List<String>> match(final String str, final int start, final int end) {
    Map<Offset, List<String>> hits = super.match(str, start, end);
    if (!disableExpansions) {
      int strlen = Math.min(str.length(), end);
      Matcher m = ANY_EXPANSION.matcher(str);
      // test every hit in this region for expansions
      for (Offset pos : new LinkedList<Offset>(hits.keySet())) {
        m.region(pos.end(), strlen);
        if (m.lookingAt()) {
          // found a hit that most likely can be expanded; try all expansions
          CharSequence region = str.subSequence(0, strlen);
          if (expandNumberedLists(hits, region, pos) ||
              expandAlphabeticLists(hits, region, pos) ||
              expandAlternateVersion(hits, region, pos)) {
            CharSequence[] params = new CharSequence[] {
                str.subSequence(pos.start(), pos.end()),
                str.subSequence(pos.end(), Math.min(pos.end() + 10, strlen))
            };
            logger.log(Level.FINE, "expanded hit ''{0}'' using ''{1}...''", params);
          }
        }
      }
    }
    return hits;
  }

  private static final Pattern NUMERIC_EXPANSION = Pattern.compile("^\\s?\\-\\s?([0-9]+)");
  private static final Pattern NUMERIC_PREFIX = Pattern.compile(".*([0-9]+)$");

  /**
   * Expand numeric lists of the general form "base 1-10" to detect "base 2" etc..
   *
   * @param hits already found (as Offset - ID [String] mappings)
   * @param span containing the relevant content
   * @param pos  of the initial hit ("base 1" in the above example)
   *
   * @return <code>True</code> if an expansion was made
   */
  private
  boolean expandNumberedLists(Map<Offset, List<String>> hits, CharSequence span, Offset pos) {
    CharSequence entity = span.subSequence(pos.start(), pos.end());
    CharSequence suffix = span.subSequence(pos.end(), span.length());
    String[] expansions = findExpansion(
        span, pos, entity, NUMERIC_PREFIX, suffix, NUMERIC_EXPANSION
    );
    if (expansions != null && expansions[0].length() > 0) {
      List<String> alts = new LinkedList<String>();
      int min = Integer.parseInt(expansions[1]) + 1;
      int max = Integer.parseInt(expansions[2]) + 1;
      for (int i = min; i < max; ++i)
        alts.add(Integer.toString(i));
      Offset off = new Offset(pos.start(), pos.end() + expansions[3].length());
      expandHits(hits, expansions[0], alts, off);
      return true;
    } else {
      return false;
    }
  }

  private static final Pattern ALPHABETIC_EXPANSION = Pattern
      .compile("^\\s?\\-\\s?([\\p{InGreek}\\p{Alpha}])(?:\\w|$)");
  private static final Pattern ALPHABETIC_PREFIX = Pattern.compile(".*([\\p{InGreek}\\p{Alpha}])$");

  /**
   * Expand alphabetic lists of the general form "base A-Z" to detect "base B" etc..
   *
   * @param hits already found (as Offset - ID [String] mappings)
   * @param span containing the relevant content
   * @param pos  of the initial hit ("base A" in the above example)
   *
   * @return <code>True</code> if an expansion was made
   */
  private
  boolean expandAlphabeticLists(Map<Offset, List<String>> hits, CharSequence span, Offset pos) {
    CharSequence entity = span.subSequence(pos.start(), pos.end());
    CharSequence suffix = span.subSequence(pos.end(), span.length());
    String[] expansions = findExpansion(
        span, pos, entity, ALPHABETIC_PREFIX, suffix, ALPHABETIC_EXPANSION
    );
    if (expansions != null && expansions[0].length() > 0) {
      List<String> alts = new LinkedList<String>();
      char min = (char) (((int) expansions[1].charAt(0)) + 1);
      char max = expansions[2].charAt(0);
      for (char i = min; i <= max; ++i)
        alts.add(Character.toString(i));
      Offset off = new Offset(pos.start(), pos.end() + expansions[3].length());
      expandHits(hits, expansions[0], alts, off);
      return true;
    } else {
      return false;
    }
  }

  private static final Pattern ALTERNATE_EXPANSION = Pattern
      .compile("^\\/([\\p{Alnum}\\p{InGreek}]+)");

  /**
   * Expand alternates of the general form "base A/B" to detect "base B".
   *
   * @param hits already found (as Offset - ID [String] mappings)
   * @param span containing the relevant content
   * @param pos  of the initial hit ("base A" in the above example)
   *
   * @return <code>True</code> if an expansion was made
   */
  private
  boolean expandAlternateVersion(Map<Offset, List<String>> hits, CharSequence span, Offset pos) {
    CharSequence suffix = span.subSequence(pos.end(), span.length());
    Matcher m = ALTERNATE_EXPANSION.matcher(suffix);
    if (m.lookingAt() && m.group(1).length() < pos.end() - pos.start()) {
      List<String> alts = Arrays.asList(m.group(1));
      Offset off = new Offset(pos.start(), pos.end() + m.end(1));
      expandHits(
          hits, span.subSequence(pos.start(), pos.end() - m.group(1).length()).toString(), alts, off
      );
      return true;
    } else {
      return false;
    }
  }

  /**
   * Detect an expansion of entity at pos in str by checking if the suffix matches the suffix
   * pattern and the entity contains the entity pattern.
   *
   * @return a 4-element array consisting of the "base" string of the entity, the original value in
   *         the entity, the expansion value in the suffix and the matched suffix string; if no
   *         expansion was found, <code>null</code> is returned.
   */
  private
  String[] findExpansion(CharSequence str, Offset pos, CharSequence entity, Pattern entityPattern,
                         CharSequence suffix, Pattern suffixPattern) {
    Matcher m = suffixPattern.matcher(suffix);
    String[] expansions = null;
    if (m.lookingAt()) {
      expansions = new String[4];
      expansions[2] = m.group(1);
      expansions[3] = m.group();
      m = entityPattern.matcher(entity);
      if (m.matches()) {
        expansions[0] = str.subSequence(pos.start(), pos.start() + m.start(1)).toString();
        expansions[1] = m.group(1);
      } else {
        expansions = null;
      }
    }
    return expansions;
  }

  /**
   * Expand hits with any matching combination of base with a String in expansions, adding the
   * detected Gazetteer IDs to the hits at the given offset.
   *
   * @param hits       already found (as Offset - ID [String] mappings)
   * @param base       to prepend the expansions with
   * @param expansions to append to the base
   * @param offset     key to use for adding any matches to the hits
   */
  private
  void expandHits(Map<Offset, List<String>> hits, String base, List<String> expansions,
                  Offset offset) {
    for (String exp : expansions) {
      String alt = String.format("%s%s", base, exp);
      if (!exactCaseMatching) alt = alt.toLowerCase();
      for (KeyValuePair<List<String>> hit : trie.scanForKeyValuePairsAtStartOf(alt)) {
        if (hit.getKey().length() == alt.length()) {
          logger.log(Level.FINE, "alternate hit ''{0}'' detected by expansion", alt);
          if (hits.containsKey(offset)) {
            List<String> ids = hits.get(offset);
            for (String id : hit.getValue())
              if (!ids.contains(id)) ids.add(id);
          } else hits.put(offset, hit.getValue());
        }
      }
    }
  }
}
