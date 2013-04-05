/* Created on Apr 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.utils;

import java.util.Iterator;

import com.googlecode.concurrenttrees.common.CharSequences;
import com.googlecode.concurrenttrees.common.KeyValuePair;
import com.googlecode.concurrenttrees.common.LazyIterator;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.node.Node;
import com.googlecode.concurrenttrees.radix.node.NodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;

/**
 * The {@link ConcurrentPatriciaTree} extends the {@link ConcurrentRadixTree} implementation to
 * enable scanning for matches of whole keys in the tree at the start of {@link CharSequence}.
 * 
 * @author Florian Leitner
 */
public class ConcurrentPatriciaTree<O> extends ConcurrentRadixTree<O> implements PatriciaTree<O> {
  /**
   * Creates a new {@link ConcurrentPatriciaTree} which will use the {@link new
   * DefaultCharArrayNodeFactory} to create nodes.
   * <p/>
   * This choice of node factory is based on the optimal node factory for prefix trees {@link http
   * ://code.google.com/p/concurrent-trees/wiki/NodeFactoryAndMemoryUsage NodeFactory and memory
   * usage}.
   */
  public ConcurrentPatriciaTree() {
    super(new DefaultCharArrayNodeFactory());
  }

  /**
   * Creates a new {@link ConcurrentPatriciaTree} which will use the given {@link NodeFactory} to
   * create nodes.
   * 
   * @param nodeFactory An object which creates {@link Node} objects on-demand, and which might
   *        return node implementations optimized for storing the values supplied to it for the
   *        creation of each node, such as the {@link DefaultCharArrayNodeFactory}
   */
  public ConcurrentPatriciaTree(NodeFactory nodeFactory) {
    super(nodeFactory);
  }

  /**
   * Creates a new {@link ConcurrentPatriciaTree} which will use the given {@link NodeFactory} to
   * create nodes.
   * 
   * @param nodeFactory An object which creates {@link Node} objects on-demand, and which might
   *        return node implementations optimized for storing the values supplied to it for the
   *        creation of each node
   * @param restrictConcurrency If true, configures use of a
   *        {@link java.util.concurrent.locks.ReadWriteLock} allowing concurrent reads, except when
   *        writes are being performed by other threads, in which case writes block all reads; if
   *        false, configures lock-free reads; allows concurrent non-blocking reads, even if writes
   *        are being performed by other threads
   */
  public ConcurrentPatriciaTree(NodeFactory nodeFactory, boolean restrictConcurrency) {
    super(nodeFactory, restrictConcurrency);
  }

  /**
   * Lazily traverses the tree based on characters in the given input, and returns from the tree
   * the next node and its value where the key associated with the node matches the characters from
   * the input. More than one matching keyword can be found for the same input, if there are keys
   * in the tree which are prefixes of each other.
   * <p/>
   * Example:<br/>
   * Given two keywords in the tree: "Ford" and "Ford Focus"<br/>
   * Given the input: "Ford Focus car"<br/>
   * ...then this method will return both "Ford" and "Ford Focus".<br/>
   * 
   * @param input A sequence of characters which controls traversal of the tree
   * @return An iterable which will search for the next node in the tree matching the input
   */
  public Iterable<KeyValuePair<O>> scanForKeyValuePairsAtStartOf(final CharSequence input) {
    return new Iterable<KeyValuePair<O>>() {
      public Iterator<KeyValuePair<O>> iterator() {
        return new LazyIterator<KeyValuePair<O>>() {
          Node currentNode = root;
          int charsMatched = 0;
          final int documentLength = input.length();

          @Override
          protected KeyValuePair<O> computeNext() {
            outer_loop:
            while (charsMatched < documentLength) {
              Node nextNode = currentNode.getOutgoingEdge(input.charAt(charsMatched));
              if (nextNode == null) {
                // Next node is a dead end...
                // noinspection UnnecessaryLabelOnBreakStatement
                break outer_loop;
              }
              currentNode = nextNode;
              CharSequence currentNodeEdgeCharacters = currentNode.getIncomingEdge();
              int charsMatchedThisEdge = 0;
              for (int i = 0, j = Math.min(currentNodeEdgeCharacters.length(), documentLength -
                  charsMatched); i < j; i++) {
                if (currentNodeEdgeCharacters.charAt(i) != input.charAt(charsMatched + i)) {
                  // Found a difference in chars between character in key and a character in
                  // current node.
                  // Current node is the deepest match (inexact match)....
                  break outer_loop;
                }
                charsMatchedThisEdge++;
              }
              if (charsMatchedThisEdge == currentNodeEdgeCharacters.length()) {
                // All characters in the current edge matched, add this number to total chars
                // matched...
                charsMatched += charsMatchedThisEdge;
                if (currentNode.getValue() != null) { return new KeyValuePairImpl<O>(
                    CharSequences.toString(input.subSequence(0, charsMatched)),
                    currentNode.getValue()); }
              }
            }
            return endOfData();
          }
        };
      }
    };
  }

  public Iterable<CharSequence> scanForKeysAtStartOf(final CharSequence input) {
    return new Iterable<CharSequence>() {
      public Iterator<CharSequence> iterator() {
        return new LazyIterator<CharSequence>() {
          Iterator<KeyValuePair<O>> matchesForCurrentSuffix = scanForKeyValuePairsAtStartOf(input)
              .iterator();

          @Override
          protected CharSequence computeNext() {
            if (!matchesForCurrentSuffix.hasNext()) return endOfData();
            return matchesForCurrentSuffix.next().getKey();
          }
        };
      }
    };
  }

  public Iterable<O> scanForValuesAtStartOf(final CharSequence input) {
    return new Iterable<O>() {
      public Iterator<O> iterator() {
        return new LazyIterator<O>() {
          Iterator<KeyValuePair<O>> matchesForCurrentSuffix = scanForKeyValuePairsAtStartOf(input)
              .iterator();

          @Override
          protected O computeNext() {
            if (!matchesForCurrentSuffix.hasNext()) return endOfData();
            return matchesForCurrentSuffix.next().getValue();
          }
        };
      }
    };
  }
}
