/* Created on Dec 20, 2012 by Florian Leitner. Copyright 2012. All rights reserved. */
package txtfnnl.pattern;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;

/**
 * A generic DFA for doing exact pattern matching. The implementation is based on the classical
 * Knuth-Morris-Pratt algorithm.
 * <p>
 * In addition to this class, the {@link Transition} interface should be implemented to define how
 * elements on the sequence should be matched. This is an exact matcher, so it is not possible to
 * define optional or repeated transitions, and transitions can joined by "AND" logic and never can
 * branch out using "OR".
 * 
 * @author Florian Leitner
 */
public class ExactMatcher<T extends Transition<E>, E> {
  final T[] pattern;
  final int[] next;

  /**
   * Create a matcher from an array of transitions.
   * 
   * @param transitions if all transitions can be made, it should lead to a match
   */
  public ExactMatcher(T[] transitions) {
    this.pattern = transitions.clone();
    this.next = new int[transitions.length];
    compile();
  }

  /**
   * Create a matcher from a List of transitions (warning: unchecked/unsafe!).
   * 
   * @param transitions if all transitions can be made, it should lead to a match
   */
  @SuppressWarnings("unchecked")
  public ExactMatcher(List<T> pattern, Class<T> klass) {
    final int n = pattern.size();
    this.pattern = (T[]) Array.newInstance(klass, n);
    for (int i = 0; i < n; i++)
      this.pattern[i] = pattern.get(i);
    this.next = new int[n];
    compile();
  }

  /**
   * Return the array of transitions for this pattern.
   * 
   * @return a clone of the underlying transitions (for inspection).
   */
  public T[] getTransitions() {
    return pattern.clone();
  }

  /** Compile using the classical KMP DFA construction algorithm. */
  private void compile() {
    int x = 0;
    for (int i = 1; i < pattern.length; i++) {
      if (pattern[x].equals(pattern[i])) {
        next[i] = next[x];
        x += 1;
      } else {
        next[i] = x + 1;
        x = next[x];
      }
    }
  }

  /** Return the length of the pattern. */
  public int length() {
    return pattern.length;
  }

  /**
   * Find the index at which the pattern matches the array sequence or return <code>-1</code> if no
   * match is found.
   */
  public int find(E[] sequence) {
    return find(sequence, 0);
  }

  /**
   * Find the index at which the pattern matches the array sequence at or after position
   * <code>start</code> in the array or return <code>-1</code> if no match is found.
   */
  public int find(E[] sequence, int offset) {
    final int n = sequence.length;
    final int m = length();
    int j = 0;
    for (int i = offset; i < n; i++) {
      if (pattern[j].matches(sequence[i])) j++;
      else j = next[j];
      if (j == m) return i - m + 1;
    }
    return -1;
  }

  /**
   * Return the first element at which the pattern matches the sequence or return <code>null</code>
   * if no match was made.
   */
  public E search(Iterator<E> sequence) {
    final int m = length();
    int j = 0;
    E hit = null;
    while (sequence.hasNext()) {
      E element = sequence.next();
      if (pattern[j].matches(element)) {
        if (j == 0) hit = element;
        j++;
      } else {
        j = next[j];
      }
      if (j == m) return hit;
    }
    return null;
  }
}
