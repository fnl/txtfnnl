package txtfnnl.pattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * A priority queue for breadth-first search that can backtrack the list of states that lead to
 * some particular state.
 * <p>
 * Items are queued by their offsets, i.e., higher offsets are queued later.
 * 
 * @author Florian Leitner
 */
final class BFSQueue<E> {
  private QueueItem<State<E>> start;
  private Map<QueueItem<State<E>>, QueueItem<State<E>>> moves;
  private Queue<QueueItem<State<E>>> queue;

  BFSQueue() {
    start = null;
    moves = new HashMap<QueueItem<State<E>>, QueueItem<State<E>>>();
    queue = new PriorityQueue<QueueItem<State<E>>>();
  }

  BFSQueue(int offset, State<E> init) {
    moves = new HashMap<QueueItem<State<E>>, QueueItem<State<E>>>();
    queue = new PriorityQueue<QueueItem<State<E>>>();
    setStart(offset, init);
  }

  /**
   * Set the initial start state of search.
   * 
   * @param offset of this state in the input sequence
   * @param init the starting state to backtrack too
   */
  void setStart(int offset, State<E> init) {
    if (init == null) throw new IllegalArgumentException("init state may never be null");
    if (queue.size() != 0 || moves.size() != 0)
      throw new IllegalStateException("tracer already running");
    start = new QueueItem<State<E>>(offset, init);
    queue.add(start);
  }

  /**
   * Add the all target states that can be reached to the queue.
   * 
   * @param offset of the target states in the input sequence
   * @param source queue item from where the transitions were made from
   * @param targets states to where the source state transitioned to
   */
  void addTransistions(int offset, QueueItem<State<E>> source, Set<State<E>> targets) {
    assert source.equals(start) || moves.containsKey(source);
    for (State<E> t : targets) {
      QueueItem<State<E>> target = new QueueItem<State<E>>(offset, t);
      if (!moves.containsKey(target) && t != null) {
        moves.put(target, source);
        queue.add(target);
      }
    }
  }

  /**
   * Find the list of states that were visited at each offset to reach this particular state.
   * 
   * @param from queue item from which to start backtracking to the start state
   * @return the list of queue items, starting with start, and ending with the <code>from</code>
   *         state
   */
  List<QueueItem<State<E>>> backtrack(QueueItem<State<E>> from) {
    List<QueueItem<State<E>>> bt = new LinkedList<QueueItem<State<E>>>();
    while (!from.equals(start)) {
      bt.add(from);
      if (!moves.containsKey(from))
        throw new NullPointerException("illegal item " + from.toString());
      from = moves.get(from);
    }
    bt.add(from);
    Collections.reverse(bt);
    return bt;
  }

  /** Return <code>true</code> if the queue is empty. */
  boolean isEmpty() {
    return queue.isEmpty();
  }

  /** Return the head of the queue. */
  QueueItem<State<E>> remove() {
    return queue.remove();
  }
}
