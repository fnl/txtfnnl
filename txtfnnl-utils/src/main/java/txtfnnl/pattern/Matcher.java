/* Created on Dec 26, 2012 by Florian Leitner.
 * Copyright 2012. All rights reserved. */
package txtfnnl.pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * An engine that performs match operation on a sequence of generic elements <code>E</code> by
 * interpreting a {@link Pattern}.
 * <p>
 * A matcher is created from a pattern by invoking the pattern's {@link Pattern#matcher(List)
 * matcher} method. Once created, a matcher can be used to perform different kinds of match
 * operations:
 * <ol>
 * <li>The {@link Matcher#matches matches} method attempts to match the entire input sequence
 * against the pattern.</li>
 * <li>The {@link Matcher#find() find} method scans the input sequence, looking for the next
 * subsequence that matches the pattern.</li>
 * <li>The {@link Matcher#lookingAt lookingAt} method attempts to match the input sequence,
 * starting at the beginning, against the pattern.</li>
 * </ol>
 * Each of these methods returns a Boolean value indicating success or failure. More information
 * about a successful match can be obtained by querying the state of the matcher.
 * <p>
 * The explicit state of a matcher includes the start and end indices of the most recent successful
 * match. It also includes the start and end indices of the input subsequence captured by each
 * capturing group in the pattern as well as a total count of such subsequences. As a convenience,
 * methods are also provided for returning these captured subsequences.
 * <p>
 * A few convenience methods present in {@link java.util.regex.Matcher} are not implemented,
 * particularly <code>appendReplacement</code>, <code>appendTail</code>, and
 * <code>replaceAll</code>.
 * 
 * @author Florian Leitner
 */
public final class Matcher<E> {
  final State<E> entry;
  final State<E> exit;
  final List<E> seq;
  private int len; // length of the previous match (-1 if the previous match attempt failed)
  private int idx; // offset of the previous match (-1 if no previous match attempt was made)

  /** Check if there was a previously made match. */
  private boolean noMatch() {
    return (len == -1 || idx == -1);
  }
  
  /**
   * Creates a new Matcher object.
   * 
   * @param entry pattern state
   * @param exit pattern state
   * @param sequence to match
   */
  Matcher(State<E> entry, State<E> exit, List<E> sequence) {
    this.entry = entry;
    this.exit = exit;
    this.seq = sequence;
    reset();
  }

  /** Returns the pattern that is interpreted by this matcher. */
  public Pattern<E> pattern() {
    return new Pattern<E>(entry, exit);
  }

  /**
   * Attempts to find the next subsequence of the input sequence that matches the pattern.
   * <p>
   * This method starts at the beginning of the input sequence or, if a previous invocation of the
   * method was successful and the matcher has not since been {@link #reset}, at the first
   * character not matched by the previous match.
   * <p>
   * If the match succeeds, more information can be obtained via the {@link #start}, {@link #end},
   * and {@link #group} methods.
   */
  public boolean find() {
    // if no failed previous attempt is indicated
    if (len != -1) {
      int max = seq.size();
      idx += len;
      while ((len = match()) == -1 && idx++ < max) {}
    }
    return (len != -1);
  }

  /**
   * Resets this matcher and then attempts to find the next subsequence of the input sequence that
   * matches the pattern, starting at the specified index.
   * <p>
   * If the match succeeds, more information can be obtained via the {@link #start}, {@link #end},
   * and {@link #group} methods.
   * 
   * @throws IndexOutOfBoundsException if start is less than zero or greater than the length of the
   *         input sequence
   */
  public boolean find(int start) {
    idx = start;
    len = 0;
    return find();
  }

  /**
   * Attempts to match the input sequence, starting at the beginning, against the pattern.
   * <p>
   * Like the {@link #matches} method, this method always starts at the beginning of the input
   * sequence; unlike that method, it does not require that the entire input sequence be matched.
   * <p>
   * If the match succeeds, more information can be obtained via the {@link #start}, {@link #end},
   * and {@link #group} methods.
   * 
   * @return <code>true</code> if any input sequence' prefix matches the pattern
   */
  public boolean lookingAt() {
    idx = 0;
    return ((len = match()) != -1);
  }

  /**
   * Return <code>true</code> if the whole (entire) sequence matches.
   * <p>
   * If the match succeeds, more information can be obtained via the {@link #start}, {@link #end},
   * and {@link #group} methods.
   */
  public boolean matches() {
    idx = 0;
    if ((len = match()) == seq.size()) {
      return true;
    } else {
      len = -1; // set the flag indicating that this previous match failed
      return false;
    }
  }

  /**
   * Return the subsequence matched by the previous match.
   * <p>
   * For a matcher <code>m</code> with input sequence <code>s</code>, the expressions
   * <code>m.group()</code> and <code>s.subList(m.start(),
   * m.end())</code> are equivalent.
   * <p>
   * Don't forget that the result could be an empty list for particular patterns.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   */
  public List<E> group() {
    if (noMatch()) throw new IllegalStateException("no previous match");
    return seq.subList(idx, idx + len);
  }

  /**
   * Returns the input subsequence captured by the given group during the previous match operation.
   * 
   * @param group index of a capturing group in this matcher's pattern
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   * @throws IndexOutOfBoundsException if there is no capturing group in the pattern with the given
   *         index
   */
  public List<E> group(int group) {
    if (noMatch()) throw new IllegalStateException("no previous match");
    // TODO: capture groups
    throw new RuntimeException("group captures not yet implemented");
  }

  /** Returns the number of capturing groups in this matcher's pattern. */
  public int groupCount() {
    // TODO: capture groups
    throw new RuntimeException("group captures not yet implemented");
  }

  /**
   * Return the start index of last match.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   */
  public int start() {
    if (noMatch()) throw new IllegalStateException("no previous match");
    return idx;
  }

  /**
   * Return the end index of last match.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   */
  public int end() {
    if (noMatch()) throw new IllegalStateException("no previous match");
    return idx + len;
  }

  /**
   * Returns the start index of the subsequence captured by the given group during the previous
   * match operation.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   * @throws IndexOutOfBoundsException if there is no capturing group in the pattern with the given
   *         index
   */
  public int start(int group) {
    if (noMatch()) throw new IllegalStateException("no previous match");
    // TODO: capture groups
    throw new RuntimeException("group captures not yet implemented");
  }

  /**
   * Returns the end index of the subsequence captured by the given group during the previous match
   * operation.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   * @throws IndexOutOfBoundsException if there is no capturing group in the pattern with the given
   *         index
   */
  public int end(int group) {
    if (noMatch()) throw new IllegalStateException("no previous match");
    // TODO: capture groups
    throw new RuntimeException("group captures not yet implemented");
  }

  /** Resets this matcher, returning itself. */
  public Matcher<E> reset() {
    idx = -1;
    len = 1;
    return this;
  }

  /**
   * Item wrapper for the priority queue for the breadth-first-search (BFS).
   * 
   * @author Florian Leitner
   */
  class QueueItem<T> implements Comparable<QueueItem<T>> {
    final int pos;
    final int weight;
    final T item;

    /**
     * Create a new queue item that will be ordered by <code>index</code>, then <code>order</code>,
     * both sorted in decreasing order.
     * 
     * @param index of the queue item (e.g., index position in the scanned sequence)
     * @param order of queue items with equal index (i.e., an optional weighting scheme)
     * @param item the queue item itself
     */
    public QueueItem(int index, int order, T item) {
      this.pos = index;
      this.weight = order;
      this.item = item;
    }

    /** Implements the Comparable interface by sorting on index, then order, descending. */
    public int compareTo(QueueItem<T> o) {
      if (pos == o.pos) return weight - o.weight;
      else return pos - o.pos;
    }

    /** Get the index of this item. */
    public int getIndex() {
      return pos;
    }

    /** Get the queue item itself. */
    public T getItem() {
      return item;
    }

    public String toString() {
      return String.format("%s[idx=%s, weight=%d, item=%s]", QueueItem.class.getName(), pos,
          weight, item.toString());
    }
  }

  /**
   * Breadth-first search of a match for the pattern in the input sequence.
   * 
   * @return the match length or <code>-1</code> if no match was made
   */
  private int match() {
    State<E> s = entry;
    if (s.isFinal()) return 0; // shortcut for a "match anything" pattern...
    int end = idx;
    int queueOrder = 0; // simple QueueItem weight model: by order of insertion
    Queue<QueueItem<State<E>>> q = new PriorityQueue<QueueItem<State<E>>>(); // the BFS queue
    QueueItem<State<E>> qi = new QueueItem<State<E>>(end, queueOrder++, s);
    q.add(qi); // add the initial state to the queue
    // record visited states at each sequence index to avoid circular references
    List<Set<State<E>>> visitedStateList;
    try {
      visitedStateList = new ArrayList<Set<State<E>>>(seq.size() - idx);
    } catch (IllegalArgumentException e) {
      if (seq.size() < idx) throw new IndexOutOfBoundsException("idx=" + idx);
      else throw e;
    }
    Set<State<E>> visited = new HashSet<State<E>>();
    // add the initial state to the set of visited states
    visited.add(s);
    visitedStateList.add(visited);
    // add any initial epsilon transitions (from the start state) to the queue
    for (State<E> next : s.epsilonTransitions) {
      if (!visited.contains(next)) {
        visited.add(next);
        q.add(new QueueItem<State<E>>(end, queueOrder++, next));
      }
    }
    // search for an accept state on the queue while there are items in it
    while (!q.isEmpty()) {
      qi = q.poll();
      s = qi.getItem();
      end = qi.getIndex();
      if (s.isFinal()) {
        return end - idx; // SUCCESS - report the length of the shortest matching sequence
      } else if (end < seq.size()) {
        E e = seq.get(end); // get the item in the sequence at the relevant index
        int v = end - idx + 1; // list index of the relevant set of visited states
        // fetch the set of visited states after the transition
        if (visitedStateList.size() == v) visitedStateList.add(new HashSet<State<E>>());
        visited = visitedStateList.get(v);
        // check transitions
        for (Transition<E> t : s.transitions.keySet()) {
          if (t.matches(e)) {
            for (State<E> next : s.transitions.get(t)) {
              // add the result states of matching transitions (if they have not been added yet)
              if (!visited.contains(next)) {
                visited.add(next);
                q.add(new QueueItem<State<E>>(end + 1, queueOrder++, next));
                // also add states reachable via epsilon transitions at that result state
                for (State<E> alt : next.epsilonTransitions) {
                  if (!visited.contains(alt)) {
                    visited.add(alt);
                    q.add(new QueueItem<State<E>>(end + 1, queueOrder++, alt));
                  }
                }
              }
            }
          }
        }
      } // end transitions
      // do unvisited epsilon transitions
      if (s.epsilonTransitions.size() > 0) {
        visited = visitedStateList.get(end - idx);
        for (State<E> next : s.epsilonTransitions) {
          if (!visited.contains(next)) {
            visited.add(next);
            q.add(new QueueItem<State<E>>(end, queueOrder++, next));
          }
        }
      }
    }
    return -1;
  }
}
