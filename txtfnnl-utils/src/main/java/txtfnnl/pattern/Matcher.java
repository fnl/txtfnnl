/* Created on Dec 26, 2012 by Florian Leitner.
* Copyright 2012. All rights reserved. */
package txtfnnl.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

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
  private List<E> seq;
  private int len; // length of the previous match (-1 if the previous match attempt failed)
  private int idx; // offset of the previous match (-1 if no previous match attempt was made)
  private int[][] captureGroups; // capture group offsets (int[][2] arrays)
  private BFSQueue<E> queue;

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
    reset(sequence);
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
    int[] o = captureGroups[group - 1];
    return seq.subList(o[0], o[1]);
  }

  /** Returns the number of <b>capturing</b> groups in this matcher's pattern. */
  public int groupCount() {
    return captureGroups.length;
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
    return captureGroups[group - 1][0];
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
    return captureGroups[group - 1][1];
  }

  /** Resets this matcher, returning itself. */
  public Matcher<E> reset() {
    idx = -1;
    len = 1;
    return this;
  }

  /** Resets this matcher with a new sequence, returning itself. */
  public Matcher<E> reset(List<E> input) {
    seq = clone(input);
    idx = -1;
    len = 1;
    return this;
  }

  private List<E> clone(List<E> input) {
    List<E> duplicate = new ArrayList<E>(input.size());
    for (E item : input)
      duplicate.add(item);
    return duplicate;
  }

  /**
   * Breadth-first search of a match for the pattern in the input sequence.
   * 
   * @return the match length or <code>-1</code> if no match was made
   */
  private int match() {
    if (idx > seq.size()) throw new IndexOutOfBoundsException("offset exceeds sequence length");
    captureGroups = new int[][] {}; // reset capture groups
    // (capture groups will be built from the backtrace of the tracer queue)
    if (entry.isFinal()) return 0; // a "match anything" pattern...
    E element; // the currently consumed item
    State<E> state = entry; // the currently processed state
    int offset = idx; // the current position of the state machine in the sequence
    queue = new BFSQueue<E>(offset, state); // start a new tracer queue
    // search for an accept state on the queue while there are items in it
    while (!queue.isEmpty()) {
      QueueItem<State<E>> item = queue.remove();
      offset = item.index();
      state = item.get();
      if (state.isFinal()) {
        // backtrack captured groups and report the length of the shortest matching sequence
        setCaptureGroups(item);
        // ==== SUCCESS ====
        return offset - idx;
        // =================
      } else if (offset < seq.size()) {
        element = seq.get(offset); // get the item in the sequence at the relevant index
        for (Transition<E> t : state.transitions.keySet()) {
          if (t.matches(element)) {
            // add the result states of matching transitions (if they have not been added yet)
            queue.addTransistions(offset + 1, item, state.transitions.get(t));
          }
        }
      }
      if (state.epsilonTransitions.size() > 0)
        queue.addTransistions(offset, item, state.epsilonTransitions);
    }
    // FAILURE
    return -1;
    // =======
  }

  /**
   * Use a backtracking approach to identify any captured groups.
   * 
   * @param item final queue item from where to begin backtracking
   */
  private void setCaptureGroups(QueueItem<State<E>> item) {
    List<QueueItem<State<E>>> path = queue.backtrack(item);
    // collect one offset per state starting or ending a capture group
    Map<State<E>, int[]> starts = new HashMap<State<E>, int[]>();
    Map<State<E>, int[]> ends = new HashMap<State<E>, int[]>();
    int i = 0;
    for (QueueItem<State<E>> qi : path) {
      State<E> s = qi.get();
      // collect the minimum (i.e., first) recorded offset for a captureStart state
      if (s.captureStart && !starts.containsKey(s)) starts.put(s, new int[] { qi.index(), i, 1 });
      // collect the maximum (i.e., last) recorded offset for a captureEnd state
      if (s.captureEnd) ends.put(s, new int[] { qi.index(), i, 0 });
      i++;
    }
    int numGroups = starts.size();
    if (numGroups > 0) {
      // sort all start and end offsets by their positions, then by the order they were matched,
      // and last order start AFTER end positions, leaving no space for any ambiguity
      int[][] positions = new int[numGroups * 2][];
      i = 0;
      for (int[] s : starts.values())
        positions[i++] = s;
      for (int[] e : ends.values())
        positions[i++] = e;
      Arrays.sort(positions, new Comparator<int[]>() {
        public int compare(int[] a, int[] b) {
          if (a[0] == b[0]) {
            if (a[1] == b[1]) return a[2] - b[2];
            else return a[1] - b[1];
          }
          return a[0] - b[0];
        }
      });
      // populate the captureGroups offset array using the ordered positions
      i = 0;
      Stack<Integer> endIdx = new Stack<Integer>();
      captureGroups = new int[numGroups][];
      for (int[] p : positions) {
        if (p[2] == 1) {
          endIdx.push(i);
          captureGroups[i++] = new int[] { p[0], -1 };
        } else {
          captureGroups[endIdx.pop()][1] = p[0];
        }
      }
    }
  }
}
