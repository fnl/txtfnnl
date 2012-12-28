/* Created on Dec 26, 2012 by Florian Leitner.
* Copyright 2012. All rights reserved. */
package txtfnnl.pattern;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
  private List<int[]> captureGroups;
  private Map<Set<State<E>>, List<int[]>> openOffsets;

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
   * <p>
   * Capturing groups are indexed from left to right, starting at one. Group zero denotes the
   * entire pattern, so the expression <code>m.{@link #group(int) group(0)}</code> is equivalent to
   * <code>m.{@link #group()}</code>.
   * 
   * @param group index of a capturing group in this matcher's pattern
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   * @throws IndexOutOfBoundsException if there is no capturing group in the pattern with the given
   *         index
   */
  public List<E> group(int group) {
    if (group == 0) return group();
    if (noMatch()) throw new IllegalStateException("no previous match");
    int[] o = captureGroups.get(group - 1);
    return seq.subList(o[0], o[1]);
  }

  /** Returns the number of <b>capturing</b> groups in this matcher's pattern. */
  public int groupCount() {
    return captureGroups.size();
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
   * <p>
   * Capturing groups are indexed from left to right, starting at one. Group zero denotes the
   * entire pattern, so the expression <code>m.{@link #start(int) start(0)}</code> is equivalent to
   * <code>m.{@link #start()}</code>.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   * @throws IndexOutOfBoundsException if there is no capturing group in the pattern with the given
   *         index
   */
  public int start(int group) {
    if (group == 0) return start();
    if (noMatch()) throw new IllegalStateException("no previous match");
    return captureGroups.get(group - 1)[0];
  }

  /**
   * Returns the end index of the subsequence captured by the given group during the previous match
   * operation.
   * <p>
   * Capturing groups are indexed from left to right, starting at one. Group zero denotes the
   * entire pattern, so the expression <code>m.{@link #end(int) end(0)}</code> is equivalent to
   * <code>m.{@link end()}</code>.
   * 
   * @throws IllegalStateException if no match has yet been attempted, or if the previous match
   *         operation failed
   * @throws IndexOutOfBoundsException if there is no capturing group in the pattern with the given
   *         index
   */
  public int end(int group) {
    if (group == 0) return end();
    if (noMatch()) throw new IllegalStateException("no previous match");
    return captureGroups.get(group - 1)[1];
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
    if (idx > seq.size()) throw new IndexOutOfBoundsException("offset exceeds sequence length");
    captureGroups = new LinkedList<int[]>(); // reset capture groups
    if (entry.isFinal()) return 0; // a "match anything" pattern...
    // == SETUP ==
    E element; // the currently consumed item
    State<E> state = entry; // the currently processed state
    int pos = idx; // the current position of the state machine in the sequence
    Queue<QueueItem<State<E>>> q = new PriorityQueue<QueueItem<State<E>>>(); // the BFS queue
    int queueOrder = 0; // a simplistic QueueItem weight model: by order of insertion
    QueueItem<State<E>> qi = new QueueItem<State<E>>(pos, queueOrder++, state); // first queue item
    // capture groups are built via openOffsets:
    // the keys are the sets of states that may be visited to build a capture group
    openOffsets = new HashMap<Set<State<E>>, List<int[]>>();
    // a list of the openOffsets keys matching at the current state
    LinkedList<Set<State<E>>> captureKeys;
    // record visited states at a given position in the sequence to avoid infinite loops that could
    // arise from circular epsilon transitions
    Set<State<E>> visited = new HashSet<State<E>>(); // at the current offset
    Set<State<E>> visitedNext = new HashSet<State<E>>(); // at the current offset + 1
    // add the entry state and to the queue
    visited.add(state);
    q.add(new QueueItem<State<E>>(pos, queueOrder++, state));
    // search for an accept state on the queue while there are items in it
    while (!q.isEmpty()) {
      // == NEXT STATE ==
      qi = q.poll();
      if (pos != qi.getIndex()) {
        // the state machine is moving on to next position
        pos = qi.getIndex();
        visited = visitedNext;
        visitedNext = new HashSet<State<E>>();
      }
      state = qi.getItem();
      captureKeys = captureGroupsCheck(state, pos);
      if (state.isFinal()) {
        // == SUCCESS ==
        return pos - idx; // report the length of the shortest matching sequence
      } else if (pos < seq.size()) {
        element = seq.get(pos); // get the item in the sequence at the relevant index
        // == STATE TRANSITIONS ==
        for (Transition<E> t : state.transitions.keySet()) {
          if (t.matches(element)) {
            // add the result states of matching transitions (if they have not been added yet)
            queueOrder = updateQueue(state.transitions.get(t), pos + 1, q, queueOrder, visitedNext);
            // update all current capture group keys
            Iterator<Set<State<E>>> iter = captureKeys.iterator();
            while (iter.hasNext()) {
              Set<State<E>> k = iter.next();
              List<int[]> l = openOffsets.remove(k);
              for (int[] o : l)
                if (o[0] == -1) o[0] = pos; // set the capture start position
              k.clear(); // drop the current capture key targets and ...
              // ... use the next states as new capture key targets
              updateCaptureMappings(k, state.transitions.get(t), l, iter);
            }
          }
        }
      }
      // == EPSILON TRANSITIONS ==
      if (state.epsilonTransitions.size() > 0) {
        queueOrder = updateQueue(state.epsilonTransitions, pos, q, queueOrder, visited);
        Iterator<Set<State<E>>> iter = captureKeys.iterator();
        while (iter.hasNext()) {
          Set<State<E>> k = iter.next();
          boolean missed = false;
          for (State<E> alt : state.epsilonTransitions) {
            if (!k.contains(alt)) {
              missed = true;
              break;
            }
          }
          if (missed) {
            List<int[]> l = openOffsets.remove(k);
            if (l == null) throw new NullPointerException("unknown key " + k + " at " + state);
            updateCaptureMappings(k, state.epsilonTransitions, l, iter);
          }
        }
      }
    }
    // == FAILURE ==
    return -1;
  }

  /**
   * Add all unvisited states to the queue, returning the updated queueOrder counter.
   * 
   * @param states to add
   * @param pos of these states relative to the sequence
   * @param q BFS queue
   * @param queueOrder queue weight
   * @param visited already queued states
   * @return
   */
  private int updateQueue(Set<State<E>> states, int pos, Queue<QueueItem<State<E>>> q,
      int queueOrder, Set<State<E>> visited) {
    for (State<E> next : states) {
      if (!visited.contains(next)) {
        visited.add(next);
        q.add(new QueueItem<State<E>>(pos, queueOrder++, next));
      }
    }
    return queueOrder;
  }

  /**
   * Process pending starting or ending capture groups at the <code>state</code> and return the
   * list of open capture group offset keys that for that <code>state</code>.
   * 
   * @param state current state of the FSM
   * @param pos offset of the FSM in the sequence
   * @return list of open capture group offset keys ({@link #openOffsets} keys)
   */
  private LinkedList<Set<State<E>>> captureGroupsCheck(State<E> state, int pos) {
    LinkedList<Set<State<E>>> captureKeys = new LinkedList<Set<State<E>>>();
    if (state.isCapturing()) {
      // start a new capture group, with an "empty" offset of [-1, -1]
      if (state.captureStart) {
        // NB that the current state should not form part of the key
        HashSet<State<E>> key = new HashSet<State<E>>(state.epsilonTransitions);
        if (openOffsets.containsKey(key)) {
          openOffsets.get(key).add(new int[] {-1, -1});
        } else {
          List<int[]> l = new LinkedList<int[]>();
          l.add(new int[] {-1, -1});
          openOffsets.put(key, l);
        }
        captureKeys.add(key);
      }
      if (state.captureEnd) {
        // remove the shortest open capture group with the current state in its key
        int[] shortest = new int[] {-1, -1};
        Set<State<E>> key = null;
        for (Set<State<E>> candidate : openOffsets.keySet()) {
          if (candidate.contains(state)) {
            List<int[]> offsets = openOffsets.get(candidate);
            for (int[] o : offsets) {
              if (o[0] > shortest[0]) {
                shortest = o;
                key = candidate;
              }
            }
          }
        }
        openOffsets.get(key).remove(shortest);
        if (openOffsets.get(key).isEmpty()) openOffsets.remove(key);
        shortest[1] = pos; // set the capture end position
        captureGroups.add(shortest);
      }
    }
    // detect open capture groups that can be processed at the current state
    for (Set<State<E>> key : openOffsets.keySet())
      if (key.contains(state) && !captureKeys.contains(key)) captureKeys.add(key);
    return captureKeys;
  }

  /**
   * Append or add (put) the offsets to the map after updating the key with the keyUpdate set,
   * removing the key form the iterator if it got merged into an existing map entry.
   * 
   * @param key set to update
   * @param keyUpdate set to add to key
   * @param offsets for the key
   * @param iter at the position of key in the captureKeys
   */
  private void updateCaptureMappings(Set<State<E>> key, Set<State<E>> keyUpdate,
      List<int[]> offsets, Iterator<Set<State<E>>> iter) {
    key.addAll(keyUpdate);
    if (openOffsets.containsKey(key)) {
      iter.remove();
      openOffsets.get(key).addAll(offsets);
    } else {
      openOffsets.put(key, offsets);
    }
  }
}
