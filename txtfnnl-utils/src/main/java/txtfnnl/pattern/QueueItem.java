package txtfnnl.pattern;

/**
 * Item wrapper for the priority queue for the breadth-first-search (BFS).
 * 
 * @author Florian Leitner
 */
final class QueueItem<T> implements Comparable<QueueItem<T>> {
  final int idx;
  final T item;

  /**
   * Create a new queue item that can be ordered by <code>index</code> (increasing).
   * 
   * @param index of the queue item (e.g., index position in the scanned sequence)
   * @param item the queue item itself
   */
  QueueItem(int index, T item) {
    this.idx = index;
    this.item = item;
  }

  /** Implements the Comparable interface by sorting on the <code>index</code> value. */
  public int compareTo(QueueItem<T> o) {
    return idx - o.idx;
  }

  /** Only compare the index and item. */
  public boolean equals(Object o) {
    if (o == this) return true;
    else if (!(o instanceof QueueItem)) return false;
    final QueueItem<?> other = (QueueItem<?>) o;
    return (idx == other.idx && item.equals(other.item));
  }

  /** Only use the index and item. */
  public int hashCode() {
    int code = 17;
    code = 31 * code + idx;
    code = 31 * code + (item == null ? 0 : item.hashCode());
    return code;
  }

  /** Get the index of this item. */
  int index() {
    return idx;
  }

  /** Get the queue item itself. */
  T get() {
    return item;
  }

  public String toString() {
    return String.format("%s[%d]=%s", QueueItem.class.getName(), idx, item.toString());
  }
}
