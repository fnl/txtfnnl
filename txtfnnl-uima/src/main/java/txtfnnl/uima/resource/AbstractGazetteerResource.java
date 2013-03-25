/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
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

import txtfnnl.uima.SharedResourceBuilder;
import txtfnnl.utils.Offset;
import txtfnnl.utils.StringLengthComparator;
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
  // resource-internal configuration
  /** The pattern used to find token splits. */
  private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
  /** The size of the initial HashMap for this resource. */
  static final int INIT_MAP_SIZE = 1024;
  /** A character to mark case-insensitive keys. */
  static final String LOWERCASE = "~";
  /** A character to mark separator-insensitive keys. */
  static final String NORMAL = "_";
  // public configuration fields
  @ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
  protected String resourceName;
  /** Whether to match the DB IDs themselves, too (default: <code>false</code>). */
  public static final String PARAM_ID_MATCHING = "IDMatching";
  @ConfigurationParameter(name = PARAM_ID_MATCHING, mandatory = false, defaultValue = "false")
  protected boolean idMatching;
  /** Whether to require exact case-sensitive matching (default: <code>false</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "false")
  private boolean exactCaseMatching;
  // resource-internal state
  /** The logger for this Resource. */
  protected Logger logger = null;
  /** The data resource' URI. */
  protected String resourceUri = null;
  /**
   * Mappings of (regular, normal, and/or lower-case) keys to sets of IDs or, for normal and/or
   * lower-case keys, to sets of regular keys.
   */
  private Map<String, Set<String>> mappings;
  /** The list of all regular expressions for the pattern (before being compiled). */
  private List<String> regularExpressions;
  /** The final pattern created from all individual names. */
  private Pattern pattern;

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
      logger = UIMAFramework.getLogger(this.getClass());
      resourceUri = dataResource.getUri().toString();
      mappings = new HashMap<String, Set<String>>(INIT_MAP_SIZE);
      regularExpressions = new ArrayList<String>(INIT_MAP_SIZE);
      pattern = null;
      logger.log(Level.INFO, "{0} resource loaded", resourceUri);
    }
  }

  /**
   * This method implements the particular method of generating the pattern given the resource
   * type.
   */
  public abstract void afterResourcesInitialized();

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
  protected String makeKey(String name) {
    StringBuilder sb = new StringBuilder();
    for (String sub : NOT_LETTER_OR_DIGIT.split(name)) {
      if (sub.length() > 0) {
        if (sb.length() > 0) sb.append(SEPARATOR);
        sb.append(makeToken(sub));
      }
    }
    String key = sb.toString();
    if (key.length() == 0) {
      logger.log(Level.WARNING, "\"" + name + "\" has no matching letters or digits");
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
   * Add the ID mapping and the patterns for the given key.
   * <p>
   * Takes care of generating all keys and patterns given the configuration. After all names have
   * been processed, the pattern can be {@link #compile() compiled} (only needs to be called once).
   * 
   * @param id of entity name to normalize
   * @param key of the entity name to normalize
   */
  protected void processMapping(final String id, final String key) {
    if (key == null) throw new IllegalArgumentException("NULL key for ID '" + id + "'");
    if (!containsKey(key)) {
      if (exactCaseMatching || !containsKey(makeLower(key)))
        regularExpressions.add(makeRegularExpression(key));
    }
    addMapping(key, id); // only the regular key maps to the DB ID
    // all other keys only map to the regular key:
    addMapping(makeNormal(key), key);
    if (!exactCaseMatching) {
      addMapping(makeLower(key), key);
      addMapping(makeNormalLower(key), key);
    }
  }

  /** Add a particular key-value mapping. */
  private void addMapping(final String key, final String target) {
    if (!mappings.containsKey(key)) mappings.put(key, new HashSet<String>());
    mappings.get(key).add(target);
  }

  /** Create a pattern and Automaton for a given key. */
  private String makeRegularExpression(final String key) {
    final String regex = key.replace(SEPARATOR, "[^\\p{L}\\p{N}]*");
    logger.log(Level.FINE, "''{0}'' -> /{1}/", new String[] { key, regex });
    // make sure a separator must be at the end or beginning of the pattern
    return String.format("%s[^\\p{L}\\{N}]|[^\\p{L}\\{N}]%s", regex, regex);
  }

  /**
   * Compile the pattern from all processed key-to-ID mappings.
   * <p>
   * This method needs to be called by the implementing resource only once, after all Gazetteer
   * names have been converted to keys and processed.
   */
  protected void compile() {
    logger.log(Level.INFO, "compiling a pattern for {0} keys of {1} regular expressions",
        new Object[] { mappings.size(), regularExpressions.size() });
    Collections.sort(regularExpressions, StringLengthComparator.INSTANCE);
    String regex = StringUtils.join('|', regularExpressions.iterator());
    int flags = exactCaseMatching ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    pattern = Pattern.compile(regex, flags);
    logger.log(Level.INFO, "the pattern has been compiled");
  }

  // GazetteerResource Methods
  /** {@inheritDoc} */
  public Map<Offset, String> match(String input) {
    Map<Offset, String> result = new HashMap<Offset, String>();
    input = String.format(" %s ", input); // pad with spaces (matching border)
    Matcher match = pattern.matcher(input);
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
      // add normal, lower key mappings only if no other mapping has been found
      else if (!exactCaseMatching && result.size() == 0 &&
          mappings.containsKey(makeNormalLower(key))) {
        for (String target : mappings.get(makeNormalLower(key)))
          result.add(target);
      }
    }
    return result;
  }

  // StringMapResource Methods
  /** Return the Set of known mappings for any key. */
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
    return mappings.containsKey(key);
  }
}
