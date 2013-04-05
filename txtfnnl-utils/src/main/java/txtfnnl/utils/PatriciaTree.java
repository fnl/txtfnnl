/* Created on Apr 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.utils;

import com.googlecode.concurrenttrees.common.KeyValuePair;
import com.googlecode.concurrenttrees.radix.RadixTree;

/**
 * A {@link PatriciaTree} extends a {@link RadixTree} to enable scanning for matches of whole keys
 * in the tree at the start of a {@link CharSequence}.
 * 
 * @author Florian Leitner
 */
public interface PatriciaTree<O> extends RadixTree<O> {
  /**
   * Returns a lazy iterable which returns the set of keys in the tree that match a the start of
   * input.
   * 
   * @param input the character sequence to scan for matching keys
   * @return The set of keys in the tree that are found at the start of the given input.
   */
  public Iterable<CharSequence> scanForKeysAtStartOf(final CharSequence input);

  /**
   * Returns a lazy iterable which returns the set of values associated with keys in the tree that
   * match a the start of input.
   * 
   * @param input the character sequence to scan for matching keys
   * @return The set of values associated with keys in the tree that are found at the start of the
   *         given input.
   */
  public Iterable<O> scanForValuesAtStartOf(final CharSequence input);

  /**
   * Returns a lazy iterable which returns the set of {@link KeyValuePair KeyValuePairs} for keys
   * and their associated values in the tree that match a the start of input.
   * 
   * @param input the character sequence to scan for matching keys
   * @return The set of {@link KeyValuePair KeyValuePairs} that are found at the start of the given
   *         input.
   */
  public Iterable<KeyValuePair<O>> scanForKeyValuePairsAtStartOf(final CharSequence input);
}
