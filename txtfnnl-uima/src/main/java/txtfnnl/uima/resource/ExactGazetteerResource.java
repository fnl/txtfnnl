/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import com.googlecode.concurrenttrees.common.KeyValuePair;
import org.apache.commons.lang.ArrayUtils;
import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.paukov.combinatorics.Factory;
import org.paukov.combinatorics.ICombinatoricsVector;
import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ExternalResourceFactory;
import txtfnnl.uima.SharedResourceBuilder;
import txtfnnl.utils.ConcurrentPatriciaTree;
import txtfnnl.utils.Offset;
import txtfnnl.utils.PatriciaTree;
import txtfnnl.utils.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * The ExactGazetteerResource implements matching without a defined way to do the initial loading of
 * the ID, name value pairs used to populate the Gazetteer. The matches are made independent of
 * (Unicode-) case, although it is possible to optionally enable {@link
 * ExactGazetteerResource#PARAM_CASE_MATCHING exact case matching}. Also, it is possible to define
 * if the {@link ExactGazetteerResource#PARAM_ID_MATCHING IDs should be matched}, too. Third,
 * matches may be limited to matches if the region begins and ends with a {@link
 * ExactGazetteerResource#PARAM_BOUNDARY_MATCH token boundary}. Finally, it is possible to generate
 * {@link ExactGazetteerResource#PARAM_GENERATE_VARIANTS variants} of the standard separators
 * (space, hyphen, or not separated) to join a name's tokens to the matching process.
 * <p/>
 * Names are tokenized at letter-digit boundaries, at spaces and hyphens, and at lower-to-upper-case
 * transitions: "MeToo3-Go agAin" is split into "Me", "Too", "3", "Go", "ag", "Ain", and all
 * possible permutations of combining these six tokens with the three strings "-", " ", and "" are
 * probed by the matcher. The only restriction is that two tokens that were originally separated by
 * a space, the empty separator ("") will not be used.
 * <p/>
 * The {@link ExactGazetteerResource#get(String) get} method returns a set of matching DB IDs for
 * any existing key, the {@link ExactGazetteerResource#size() size} reports the <b>total</b> number
 * of IDs, while {@link ExactGazetteerResource#iterator() iterator} provides a way to access all
 * IDs. If case-insensitve matching is used, all names are lower-cased.
 *
 * @author Florian Leitner
 */
abstract
class ExactGazetteerResource implements GazetteerResource, ExternalResourceAware {
  // external configuration
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
  /** Whether to generate all separator variants of a name (default: <code>false</code>). */
  public static final String PARAM_GENERATE_VARIANTS = "GeneratVariants";
  @ConfigurationParameter(name = PARAM_GENERATE_VARIANTS, mandatory = false, defaultValue = "false")
  private boolean generateVariants;
  // internal state
  /** The logger for this Resource. */
  protected Logger logger = null;
  /** The data resource itself. */
  protected DataResource resource;
  /** The data resource' URI. */
  protected String resourceUri = null;
  /** The compacted prefix tree created from all individual, normalized names. */
  protected PatriciaTree<List<String>> trie;
  /** A mapping of all the Gazetteer's IDs to their official names. */
  private Map<String, String[]> names;
  /** The initial size of the names Map. */
  private static final int INIT_MAP_SIZE = 256;

  public static
  class Builder extends SharedResourceBuilder {
    /** Protected constructor that must be extended by concrete implementations. */
    protected
    Builder(Class<? extends SharedResourceObject> klass, String url) {
      super(klass, url);
    }

    /** Match the Gazetteer's IDs themselves, too. */
    public
    Builder idMatching() {
      setOptionalParameter(PARAM_ID_MATCHING, Boolean.TRUE);
      return this;
    }

    /** Require case-sensitive matches. */
    public
    Builder caseMatching() {
      setOptionalParameter(PARAM_CASE_MATCHING, Boolean.TRUE);
      return this;
    }

    /** Require token boundaries to coincide with matches. */
    public
    Builder boundaryMatch() {
      setOptionalParameter(PARAM_BOUNDARY_MATCH, Boolean.TRUE);
      return this;
    }

    /** Match all space/no-space/hyphen variants of the tokenized names. */
    public
    Builder generateVariants() {
      setOptionalParameter(PARAM_GENERATE_VARIANTS, Boolean.TRUE);
      return this;
    }
  }

  /** {@inheritDoc} */
  public
  String getResourceName() {
    return resourceName;
  }

  /** {@inheritDoc} */
  public
  String getUrl() {
    return resourceUri;
  }

  public synchronized
  void load(DataResource dataResource) throws ResourceInitializationException {
    if (resource == null) {
      ConfigurationParameterInitializer.initialize(this, dataResource);
      resource = dataResource;
      resourceUri = dataResource.getUri().toString();
      logger = UIMAFramework.getLogger(this.getClass());
      trie = new ConcurrentPatriciaTree<List<String>>();
      names = new HashMap<String, String[]>(INIT_MAP_SIZE);
      logger.log(Level.CONFIG, "{0} resource loaded", resourceUri);
    }
  }

  /**
   * This method should implement the particular method of compiling the patterns (i.e., the
   * PATRICIA tree), given the final resource type.
   */
  public abstract
  void afterResourcesInitialized();

  /** Fetch the input stream for this resource. */
  protected
  InputStream getInputStream() throws IOException {
    return resource.getInputStream();
  }

  // == Methods for building a pattern from the name-id pairs ==

  /** Add an ID, name mapping to the Gazetteer, unless it is in the known keys for that ID. */
  protected
  void put(final String id, final String name, Set<String> knownKeys) {
    if (id == null) throw new IllegalArgumentException("id == null for name '" + name + "'");
    if (name == null) throw new IllegalArgumentException("name == null for ID '" + id + "'");
    String[] mapped = names.get(id);
    if (mapped == null) {
      mapped = new String[] {name};
      names.put(id, mapped);
    } else if (!ArrayUtils.contains(mapped, name)) {
      mapped = Arrays.copyOf(mapped, mapped.length + 1);
      mapped[mapped.length - 1] = name;
      names.put(id, mapped);
    }
    String key = makeKey(name);
    if (key.length() == 0) {
      logger.log(Level.WARNING, id + "=\"" + name + "\" has no content characters");
      return;
    }
    if (!knownKeys.contains(key)) {
      knownKeys.add(key);
      if (generateVariants) putVariants(id, name, knownKeys);
      put(trie, id, key);
      if (idMatching) {
        put(trie, id, makeKey(id));
        if (!ArrayUtils.contains(mapped, id)) {
          mapped = Arrays.copyOf(mapped, mapped.length + 1);
          mapped[mapped.length - 1] = id;
          names.put(id, mapped);
        }
      }
    }
  }

  /**
   * Calculate and {@link ExactGazetteerResource#put(txtfnnl.utils.PatriciaTree, String, String)
   * put} all possible variants into the trie.
   */
  private
  void putVariants(String id, String name, Set<String> knownKeys) {
    List<String> tokens = new LinkedList<String>();
    int last = 0;
    int len = name.length() - 1;
    List<Integer> spacePositions = new LinkedList<Integer>();
    for (int i = 1; i < len; ++i) {
      char currChar = name.charAt(i);
      if (currChar == ' ') {
        spacePositions.add(tokens.size());
        tokens.add(name.substring(last, i++));
        last = i;
      } else if (currChar == '-') {
        tokens.add(name.substring(last, i++));
        last = i;
      } else if (Character.isLetterOrDigit(currChar)) {
        char lastChar = name.charAt(i - 1);
        if (Character.isLetter(currChar) && Character.isDigit(lastChar) ||
            Character.isDigit(currChar) && Character.isLetter(lastChar) ||
            Character.isUpperCase(currChar) && Character.isLowerCase(lastChar)) {
          tokens.add(name.substring(last, i));
          last = i;
        }
      }
    }
    // NB: the last token has not yet been added to the tokens List!
    int num = tokens.size();
    if (num != 0) {
      int i = 0;
      int[] spaces = new int[spacePositions.size()];
      for (int pos : spacePositions) spaces[i++] = pos;
      tokens.add(name.substring(last, len + 1));
      ICombinatoricsVector<String> spacers = Factory.createVector(new String[] {"-", "", " "});
      String[] variant = new String[(num + 1) * 2 - 1];
      for (i = 0; i < num + 1; ++i)
        variant[i * 2] = tokens.get(i);
      for (ICombinatoricsVector<String> choices : Factory
          .createPermutationWithRepetitionGenerator(spacers, num)) {
        for (i = 0; i < num; ++i) {
          if (Arrays.binarySearch(spaces, i) < 0 || !"".equals(choices.getValue(i))) {
            variant[i * 2 + 1] = choices.getValue(i);
          }
        }
        String variantKey = makeKey(StringUtils.join(variant));
        if (!knownKeys.contains(variantKey)) {
          put(trie, id, variantKey);
          knownKeys.add(variantKey);
        }
      }
    }

  }

  /** Return the (normalized) key for a name. */
  protected
  String makeKey(final String name) {
    return exactCaseMatching ? name : name.toLowerCase();
  }

  /** Place the key-to-ID mapping in the PATRICIA tree. */
  private static
  void put(PatriciaTree<List<String>> tree, final String id, final String name) {
    List<String> ids = tree.getValueForExactKey(name);
    if (ids == null) {
      ids = new LinkedList<String>();
      ids.add(id);
      tree.put(name, ids);
    } else if (!ids.contains(id)) {
      ids.add(id);
    }
  }

  private static
  boolean isBoundary(String str, int offset) {
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

  private static
  int getCharacterTypeBefore(String str, int offset) {
    int lastPoint = str.codePointAt(offset - 1);
    int lastType = Character.getType(lastPoint);
    if (lastType == Character.SURROGATE && offset > 1) {
      lastPoint = str.codePointAt(offset - 2);
      lastType = Character.getType(lastPoint);
    }
    return lastType;
  }

  // == GazetteerResource Methods ==

  /** {@inheritDoc} */
  public
  Map<Offset, List<String>> match(String input) {
    return match(input, 0);
  }

  /** {@inheritDoc} */
  public
  Map<Offset, List<String>> match(String input, int start) {
    return match(input, start, input.length());
  }

  /** {@inheritDoc} */
  public
  Map<Offset, List<String>> match(String input, int start, int end) {
    Map<Offset, List<String>> results = new HashMap<Offset, List<String>>();
    String normal = input;
    if (!exactCaseMatching) normal = input.toLowerCase();
    if (boundaryMatch) {
      int length = Math.min(input.length(), end);
      for (int i = Math.max(0, start); i < length; ++i) {
        if (isBoundary(input, i)) {
          for (KeyValuePair<List<String>> hit : trie
              .scanForKeyValuePairsAtStartOf(normal.subSequence(i, length))) {
            int j = i + hit.getKey().length();
            if (isBoundary(input, j)) {
              results.put(new Offset(i, j), hit.getValue());
            }
          }
        }
      }
    } else {
      int length = input.length();
      for (int i = Math.max(0, start); i < length; ++i) {
        for (KeyValuePair<List<String>> hit : trie
            .scanForKeyValuePairsAtStartOf(normal.subSequence(i, length))) {
          results.put(new Offset(i, i + hit.getKey().length()), hit.getValue());
        }
      }
    }
    logger.log(
        Level.FINE, "made {0} matches on ''{1}''",
        new Object[] {results.size(), input.substring(start, end)}
    );
    return results;
  }

  // == StringMapResource Methods ==

  /** Return the official names for an ID. */
  public
  String[] get(String id) {
    return names.get(id);
  }

  /** Check if the ID exists (and therefore has a mapping to a Set of official names). */
  public
  boolean containsKey(String id) {
    return names.containsKey(id);
  }

  /** Return the number of IDs covered by the Gazetteer. */
  public
  int size() {
    return names.size();
  }

  /** Iterate over all IDs covered by the Gazetteer. */
  public
  Iterator<String> iterator() {
    return names.keySet().iterator();
  }
}
