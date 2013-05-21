/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.utils.Offset;
import txtfnnl.utils.StringUtils;

import com.googlecode.concurrenttrees.common.KeyValuePair;

/**
 * The AbstractGazetteerResource implements matching without a defined way to do the initial
 * loading of the ID, name value pairs used to populate the Gazetteer. The
 * {@link AbstractGazetteerResource#PARAM_SEPARATORS token-separating characters} that should be
 * ignored and the {@link AbstractGazetteerResource#PARAM_SEPARATOR_LENGTH max. length} of these
 * separating spans can be parameterized in addition to all options provided by the
 * {@link AbstractExactGazetteerResource exact gazetteer}.
 * 
 * @author Florian Leitner
 */
public abstract class AbstractGazetteerResource extends AbstractExactGazetteerResource {
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

  public static class Builder extends AbstractExactGazetteerResource.Builder {
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
  }

  @Override
  public synchronized void load(DataResource dataResource) throws ResourceInitializationException {
    super.load(dataResource);
    charset = Pattern.compile(charsetRegex);
  }

  // methods for building a pattern from the name-id pairs
  @Override
  protected String makeKey(final String name) {
    String key = StringUtils.join(charset.split(name));
    return exactCaseMatching ? key : key.toLowerCase();
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
      int normalPos = 0;
      int seqPos = 0;
      int counter = 0;
      normal = StringUtils.join(items);
      offset = new int[normal.length()];
      if (!exactCaseMatching) normal = normal.toLowerCase();
      tokenList.add(normalPos);
      for (String tok : items) {
        int tokPos = 0;
        seqPos = seq.indexOf(tok, seqPos);
        while ((tokPos = nextBoundary(tok, tokPos)) < tok.length())
          tokenList.add(normalPos + tokPos);
        tokenList.add(normalPos + tokPos);
        for (int i = 0; i < tok.length(); ++i)
          offset[counter++] = seqPos + i;
        normalPos += tokPos;
        seqPos += tokPos;
      }
      tokens = new int[tokenList.size()];
      counter = 0;
      for (int t : tokenList)
        tokens[counter++] = t;
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
  @Override
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
}
