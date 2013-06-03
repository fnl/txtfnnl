/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
import txtfnnl.utils.ConcurrentPatriciaTree;
import txtfnnl.utils.Offset;
import txtfnnl.utils.PatriciaTree;

import com.googlecode.concurrenttrees.common.KeyValuePair;

/**
 * The AbstractExactGazetteerResource implements matching without a defined way to do the initial
 * loading of the ID, name value pairs used to populate the Gazetteer. The matches are made
 * independent of (Unicode-) case, although it is possible to optionally enable
 * {@link AbstractExactGazetteerResource#PARAM_CASE_MATCHING exact case matching}. Also, it is
 * possible to define if the {@link AbstractExactGazetteerResource#PARAM_ID_MATCHING IDs should be
 * matched}. Third, matches may be limited to matches if the region begins and ends with a token
 * boundary.
 * <p>
 * <b>Tokens</b> are separated at any change of Unicode character {@link Character#getType(int)
 * category} change in a String, with the only exception of a transition from a single upper-case
 * character to lower-case (i.e., [potentially] capitalized words). For example, the name "Abc" is
 * a single token, while "AbcDef" are two , just as "ABC1", or "abc def". In the case of
 * consecutive upper-case letters followed by lower-case letters, the split is made at the category
 * switch (i.e., "ABCdef" tokenizes to "ABC" and "def", not "AB" and "Cdef").
 * <p>
 * The {@link AbstractExactGazetteerResource#get(String) get} method returns a set of matching DB
 * IDs for any existing key, the {@link AbstractExactGazetteerResource#size() size} reports the
 * <b>total</b> number of keys, while {@link AbstractExactGazetteerResource#iterator() iterator}
 * provides an iterator over those keys. If case-insensitve matching is used, all keys are
 * lower-cased.
 * 
 * @author Florian Leitner
 */
public abstract class AbstractExactGazetteerResource implements GazetteerResource,
    ExternalResourceAware {
  @ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
  protected String resourceName;
  /** Whether to match the DB IDs themselves, too (default: <code>false</code>). */
  public static final String PARAM_ID_MATCHING = "IdMatching";
  @ConfigurationParameter(name = PARAM_ID_MATCHING, mandatory = false, defaultValue = "false")
  private boolean idMatching;
  /** Whether hits must coincide with token boundaries (default: <code>false</code>). */
  public static final String PARAM_BOUNDARY_MATCH = "BoundaryMatch";
  @ConfigurationParameter(name = PARAM_BOUNDARY_MATCH, mandatory = false, defaultValue = "false")
  protected boolean boundaryMatch;
  /** Whether to require exact case-sensitive matching (default: <code>false</code>). */
  public static final String PARAM_CASE_MATCHING = "CaseMatching";
  @ConfigurationParameter(name = PARAM_CASE_MATCHING, mandatory = false, defaultValue = "false")
  protected boolean exactCaseMatching;
  // resource-internal state
  /** The logger for this Resource. */
  protected Logger logger = null;
  /** The data resource itself. */
  protected DataResource resource;
  /** The data resource' URI. */
  protected String resourceUri = null;
  /** The compacted prefix tree created from all individual, normalized names. */
  protected PatriciaTree<Set<String>> trie;
  /** A mapping of all the Gazetteer's IDs to their official names. */
  private Map<String, Set<String>> names;
  private static final int INIT_MAP_SIZE = 256;

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

    /** Require token boundaries to coincide with matches. */
    public Builder boundaryMatch() {
      setOptionalParameter(PARAM_BOUNDARY_MATCH, Boolean.TRUE);
      return this;
    }
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
      trie = new ConcurrentPatriciaTree<Set<String>>();
      names = new HashMap<String, Set<String>>(INIT_MAP_SIZE);
      logger.log(Level.CONFIG, "{0} resource loaded", resourceUri);
    }
  }

  /**
   * This method should implement the particular method of compiling the patterns (i.e., the
   * PATRICIA tree), given the final resource type.
   * 
   * @throws ResourceInitializationException
   */
  public abstract void afterResourcesInitialized();

  /** Fetch the input stream for this resource. */
  protected InputStream getInputStream() throws IOException {
    return resource.getInputStream();
  }

  // methods for building a pattern from the name-id pairs
  /** Add an ID, name mapping to the Gazetteer. */
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
    if (idMatching) {
      put(trie, id, makeKey(id));
      mapped.add(id);
    }
  }

  /** Return the (normalized) key for a name. */
  protected String makeKey(final String name) {
    return exactCaseMatching ? name : name.toLowerCase();
  }

  /** Place the key-to-ID mapping in the PATRICIA tree. */
  private static void put(PatriciaTree<Set<String>> tree, final String id, final String key) {
    Set<String> ids = tree.getValueForExactKey(key);
    if (ids == null) {
      ids = new HashSet<String>();
      tree.put(key, ids);
    }
    ids.add(id);
  }

  private static boolean isBoundary(String str, int offset) {
    int length = str.length();
    if (offset == 0 || offset == length) {
      return true; // the String ends are always boundaries
    } else if (offset <= length) {
      int currentType = getCharacterTypeBefore(str, offset + 1);
      int lastType = getCharacterTypeBefore(str, offset);
      if (currentType != lastType) {
        if (currentType == Character.LOWERCASE_LETTER && lastType == Character.UPPERCASE_LETTER) {
          if (offset > 1) {
            int subtract = 1;
            if (Character.getType(str.codePointAt(offset - 1)) == Character.SURROGATE)
              subtract = (offset > 2) ? 2 : 0;
            // consecutive uppercase letters before offset, lowercase after offset
            if (lastType == getCharacterTypeBefore(str, offset - subtract)) return true;
          }
          return false;  // capitalized token
        } else {
          return true; // change of Unicode category other than a upper-to-lower-case transition
        }
      } else {
        return false; // no change of Unicode category
      }
    } else {
      throw new IndexOutOfBoundsException("illegal offset " + Integer.toString(offset));
    }
  }

  private static int getCharacterTypeBefore(String str, int offset) {
    int lastPoint = str.codePointAt(offset - 1);
    int lastType = Character.getType(lastPoint);
    if (lastType == Character.SURROGATE && offset > 1) {
      lastPoint = str.codePointAt(offset - 2);
      lastType = Character.getType(lastPoint);
    }
    return lastType;
  }

  // GazetteerResource Methods
  /** {@inheritDoc} */
  public Map<Offset, String[]> match(String input) {
    return match(input, 0);
  }

  /** {@inheritDoc} */
  public Map<Offset, String[]> match(String input, int start) {
    return match(input, start, input.length());
  }

  /** {@inheritDoc} */
  public Map<Offset, String[]> match(String input, int start, int end) {
    Map<Offset, String[]> results = new HashMap<Offset, String[]>();
    String normal = input;
    if (!exactCaseMatching) normal = input.toLowerCase();
    if (boundaryMatch) {
      int length = input.length();
      for (int i = 0; i < length; ++i) {
        if (isBoundary(input, i)) {
          for (KeyValuePair<Set<String>> hit : trie.scanForKeyValuePairsAtStartOf(normal
              .subSequence(i, length))) {
            int j = i + hit.getKey().length();
            if (isBoundary(input, j)) {
              Set<String> ids = hit.getValue();
              results.put(new Offset(i, j), ids.toArray(new String[ids.size()]));
            }
          }
        }
      }
    } else {
      int length = input.length();
      for (int i = 0; i < length; ++i) {
        for (KeyValuePair<Set<String>> hit : trie.scanForKeyValuePairsAtStartOf(normal
            .subSequence(i, length))) {
          Set<String> ids = hit.getValue();
          results.put(new Offset(i, i + hit.getKey().length()),
              ids.toArray(new String[ids.size()]));
        }
      }
    }
    return results;
  }

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
