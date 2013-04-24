/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

import com.googlecode.concurrenttrees.common.KeyValuePair;

import txtfnnl.uima.SharedResourceBuilder;
import txtfnnl.utils.ConcurrentPatriciaTree;
import txtfnnl.utils.Offset;
import txtfnnl.utils.PatriciaTree;
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
  @ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
  protected String resourceName;
  /**
   * A regex that matches consecutive stretches of characters that should be treated as separators.
   * <p>
   * Defaults to everything but Unicode letters, numbers, and symbols.
   */
  public static final String PARAM_CHARSET_REGEX = "CharsetRegex";
  @ConfigurationParameter(name = PARAM_CHARSET_REGEX,
      mandatory = false,
      defaultValue = "[^\\p{L}\\p{N}\\p{S}]+")
  private String charsetRegex;
  private Pattern charset;
  private static final int INIT_MAP_SIZE = 256;
  /** Whether to match the DB IDs themselves, too (default: <code>false</code>). */
  public static final String PARAM_ID_MATCHING = "IDMatching";
  @ConfigurationParameter(name = PARAM_ID_MATCHING, mandatory = false, defaultValue = "false")
  private boolean idMatching;
  /** Whether hits must coincide with token boundaries (default: <code>false</code>). */
  public static final String PARAM_BOUNDARY_MATCH = "BoundaryMatch";
  @ConfigurationParameter(name = PARAM_BOUNDARY_MATCH, mandatory = false, defaultValue = "false")
  private boolean boundaryMatch;
  /** Whether to require exact case-sensitive matching (default: <code>false</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "false")
  private boolean exactCaseMatching;
  // @ConfigurationParameter(name = PARAM_REVERSE_SCANNING, mandatory = false, defaultValue =
  // "false")
  // private boolean reverseScanning;
  // resource-internal state
  /** The logger for this Resource. */
  protected Logger logger = null;
  /** The data resource itself. */
  protected DataResource resource;
  /** The data resource' URI. */
  protected String resourceUri = null;
  /** The compacted prefix tree created from all individual names. */
  private PatriciaTree<Set<String>> trie;
  // /** The reversed compacted prefix tree created from all individual names. */
  // private PatriciaTree<Set<String>> reverseTrie;
  /** The mapping of IDs to their names. */
  private Map<String, Set<String>> names;

  public static class Builder extends SharedResourceBuilder {
    /** Protected constructor that must be extended by concrete implementations. */
    protected Builder(Class<? extends SharedResourceObject> klass, String url) {
      super(klass, url);
    }

    /**
     * Set an optional regular expression to separate relevant characters from ignored characters
     * in the input (default: all but Unicode letters, numbers, and symbols).
     * <p>
     * Any characters matched by the regular expression will be skipped, as if using
     * {@link Pattern#split(CharSequence)}.
     * 
     * @see AbstractGazetteerResource#PARAM_CHARSET_REGEX
     * @see Pattern#split(CharSequence)
     */
    public Builder setCharsetRegex(String regex) {
      setOptionalParameter(PARAM_CHARSET_REGEX, regex);
      return this;
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

    public Builder boundaryMatch() {
      setOptionalParameter(PARAM_BOUNDARY_MATCH, Boolean.TRUE);
      return this;
    }
    // /** Enable reverse scanning for matches. */
    // public Builder reverseScanninig() {
    // setOptionalParameter(PARAM_REVERSE_SCANNING, Boolean.TRUE);
    // return this;
    // }
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
    if (resource == null) {
      ConfigurationParameterInitializer.initialize(this, dataResource);
      resource = dataResource;
      resourceUri = dataResource.getUri().toString();
      logger = UIMAFramework.getLogger(this.getClass());
      // mappings = new HashMap<String, Set<String>>(INIT_MAP_SIZE);
      // regularExpressions = new ArrayList<String>(INIT_MAP_SIZE);
      charset = Pattern.compile(charsetRegex);
      trie = new ConcurrentPatriciaTree<Set<String>>();
      // reverseTrie = reverseScanning ? new ConcurrentPatriciaTree<Set<String>>() : null;
      names = new HashMap<String, Set<String>>(INIT_MAP_SIZE);
      logger.log(Level.INFO, "{0} resource loaded", resourceUri);
    }
  }

  /**
   * This method implements the particular method of generating the pattern given the resource
   * type.
   * 
   * @throws ResourceInitializationException
   */
  public abstract void afterResourcesInitialized();

  /** Fetch the input stream for this resource. */
  protected InputStream getInputStream() throws IOException {
    return resource.getInputStream();
  }

  // methods for building a pattern from the name-id pairs
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
  protected void put(final String id, final String name) {
    if (id == null) throw new IllegalArgumentException("id == null for name '" + name + "'");
    if (name == null) throw new IllegalArgumentException("name == null for ID '" + id + "'");
    Set<String> mapped = names.get(id);
    if (mapped == null) {
      mapped = new HashSet<String>();
      names.put(id, mapped);
    }
    mapped.add(name);
    String key = makeKey(name);
    if (key.length() == 0) {
      logger.log(Level.WARNING, id + "=\"" + name + "\" has no content characters");
      return;
    }
    put(trie, id, key);
    if (idMatching) put(trie, id, makeKey(id));
    // if (reverseScanning) {
    // put(reverseTrie, id, (new StringBuilder(key)).reverse().toString());
    // if (idMatching) put(trie, id, makeKey((new StringBuilder(id)).reverse().toString()));
    // }
  }

  private String makeKey(final String name) {
    String key = StringUtils.join(charset.split(name));
    return exactCaseMatching ? key : key.toLowerCase();
  }

  private static void put(PatriciaTree<Set<String>> tree, final String id, final String key) {
    Set<String> ids = tree.getValueForExactKey(key);
    if (ids == null) {
      ids = new HashSet<String>();
      tree.put(key, ids);
    }
    ids.add(id);
  }

  /**
   * A special data structure to create normalized (separator-less) versions of an input String
   * together with an alignment of the normalized offsets to the offsets in the input.
   * <p>
   * If exact case matching is disabled, a the normalized version is lower-cased, too.
   */
  private class NormalAlignment {
    /** The normalized version of this string (separator-less and lower-cased if configured). */
    String normal;
    /**
     * The alignment with the relative offsets in the input for the normal String.
     * <p>
     * This means, the length of normal and offset are equal.
     */
    int[] offset;
    /** The positions of token breaks (including 0 and last). */
    int[] tokens;

    public NormalAlignment(String seq) {
      String[] items = charset.split(seq);
      List<Integer> tokenList = new LinkedList<Integer>();
      int off = 0;
      tokenList.add(off);
      for (String tok : items) {
        int pos = 0;
        while ((pos = nextBoundary(tok, pos)) < tok.length())
          tokenList.add(off + pos);
        tokenList.add(off + pos);
        off += pos;
      }
      tokens = new int[tokenList.size()];
      off = 0;
      for (int pos : tokenList)
        tokens[off++] = pos;
      normal = StringUtils.join(items);
      if (!exactCaseMatching) normal = normal.toLowerCase();
      offset = new int[normal.length()];
      if (offset.length > 0) {
        int pos = 0;
        for (int i = 0; i < items.length; ++i) {
          int idx = seq.indexOf(items[i], pos);
          for (int j = 0; j < items[i].length(); ++j)
            offset[pos++] = idx + j;
        }
        assert pos == offset.length;
      }
    }
  }

  private int nextBoundary(String token, int offset) {
    int length = token.length();
    if (offset < length) {
      int charPoint = token.codePointAt(offset);
      int lastCharType = Character.getType(charPoint);
      offset += Character.charCount(charPoint);
      while (offset < length) {
        charPoint = token.codePointAt(offset);
        if (Character.getType(charPoint) != lastCharType) {
          if (!isCapitalized(token, offset)) return offset;
          else lastCharType = Character.getType(charPoint); // switch to lower-case
        }
        offset += Character.charCount(charPoint);
      }
    }
    return offset;
  }

  /** Return <code>true</code> if the current split is a capitalized word. */
  private boolean isCapitalized(String token, int offset) {
    return offset < 3 && offset == Character.charCount(token.codePointAt(0)) &&
        Character.getType(token.codePointAt(0)) == Character.UPPERCASE_LETTER &&
        Character.getType(token.codePointAt(offset)) == Character.LOWERCASE_LETTER;
  }

  // GazetteerResource Methods
  /** {@inheritDoc} */
  public Map<Offset, Set<String>> match(String input) {
    return match(input, 0);
  }

  /** {@inheritDoc} */
  public Map<Offset, Set<String>> match(String input, int start) {
    return match(input, start, input.length());
  }

  /** {@inheritDoc} */
  public Map<Offset, Set<String>> match(String input, int start, int end) {
    Map<Offset, Set<String>> results = new HashMap<Offset, Set<String>>();
    NormalAlignment aln = new NormalAlignment(input);
    int len = Math.min(aln.normal.length(), end);
    for (int i = Math.max(0, start); i < len; ++i) {
      if (boundaryMatch && Arrays.binarySearch(aln.tokens, i) < 0) continue;
      CharSequence suffix = aln.normal.subSequence(i, len);
      for (KeyValuePair<Set<String>> hit : trie.scanForKeyValuePairsAtStartOf(suffix)) {
        int j = i + hit.getKey().length();
        if (boundaryMatch && Arrays.binarySearch(aln.tokens, j) < 0) continue;
        results.put(new Offset(aln.offset[i], aln.offset[j - 1] + 1), hit.getValue());
      }
    }
    return results;
  }

  // /** {@inheritDoc} */
  // public Map<Offset, Set<String>> scan(String input) {
  // return scan(input, 0);
  // }
  //
  // public Map<Offset, Set<String>> scan(String input, int baseOffset) {
  // if (input.length() == 0) throw new IllegalArgumentException("zero-length input");
  // Map<Offset, Set<String>> results = new HashMap<Offset, Set<String>>();
  // NormalAlignment aln = new NormalAlignment(input);
  // for (KeyValuePair<Set<String>> hit : trie.scanForKeyValuePairsAtStartOf(aln.normal))
  // results.put(new Offset(baseOffset + aln.offset[0], baseOffset +
  // aln.offset[hit.getKey().length() - 1] + 1), hit.getValue());
  // return results;
  // }
  //
  // /** {@inheritDoc} */
  // public Map<Offset, Set<String>> reverseScan(String suffix) {
  // return reverseScan(suffix, 0);
  // }
  //
  // public Map<Offset, Set<String>> reverseScan(String suffix, int baseOffset) {
  // if (!reverseScanning) throw new IllegalStateException("reverse scanning was not enabled");
  // if (suffix.length() == 0) throw new IllegalArgumentException("zero-length suffix");
  // Map<Offset, Set<String>> results = new HashMap<Offset, Set<String>>();
  // String reverseInput = (new StringBuilder(suffix)).reverse().toString();
  // NormalAlignment aln = new NormalAlignment(reverseInput);
  // if (aln.normal.length() == 0) return results;
  // int len = baseOffset + suffix.length();
  // int end = len - aln.offset[0];
  // for (KeyValuePair<Set<String>> hit : reverseTrie.scanForKeyValuePairsAtStartOf(aln.normal))
  // results
  // .put(new Offset(len - aln.offset[hit.getKey().length() - 1] - 1, end), hit.getValue());
  // return results;
  // }
  //
  // /** {@inheritDoc} */
  // public boolean canScanReverse() {
  // return reverseScanning;
  // }
  // StringMapResource Methods
  /** Return the Set of official names for an ID. */
  public Set<String> get(String id) {
    return names.get(id);
  }

  /** Check if the ID exists (and therefore has a mapping to a Set of official names). */
  public boolean containsKey(String id) {
    return names.containsKey(id);
  }

  /** Return the number of IDs covered by the Gazetteer. */
  public int size() {
    return names.size();
  }

  /** Iterate over all IDs covered by the Gazetteer. */
  public Iterator<String> iterator() {
    return names.keySet().iterator();
  }
}
