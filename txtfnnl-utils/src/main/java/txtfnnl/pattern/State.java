/* Created on Dec 21, 2012 by Florian Leitner.
 * Copyright 2012. All rights reserved. */
package txtfnnl.pattern;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A State vertex with outgoing labeled and empty (epsilon) edges.
 * <p>
 * It should never be necessary to fiddle with this class of the "abstract" regular expression
 * implementation provided by this package.
 * <p>
 * By default, a new State is never final (i.e., does not have the "accept" flag set).
 * 
 * @author Florian Leitner
 */
final class State<E> {
  private boolean accept = false;
  boolean captureStart = false;
  boolean captureEnd = false;
  Map<Transition<E>, Set<State<E>>> transitions = new HashMap<Transition<E>, Set<State<E>>>();
  Set<State<E>> epsilonTransitions = new HashSet<State<E>>();

  /** A representation of a State for debugging purposes. */
  @Override
  public String toString() {
    Set<State<E>> visited = new HashSet<State<E>>();
    visited.add(this);
    return toStringVisitor(visited, 0);
  }

  String toStringVisitor(Set<State<E>> visited, int level) {
    StringBuffer sb = new StringBuffer();
    List<State<E>> toVisit = new LinkedList<State<E>>();
    sb.append(State.class.getSimpleName());
    sb.append(String.format("[acc=%s cap=%s%s e:%d t:%d]\n", accept, captureStart ? "(" : "",
        captureEnd ? ")" : "", epsilonTransitions.size(), transitions.size()));
    for (int i = 0; i < level; i++)
      sb.append("  ");
    sb.append("{");
    boolean any = false;
    level++;
    for (State<E> s : epsilonTransitions) {
      if (!visited.contains(s)) {
        visited.add(s);
        toVisit.add(s);
      }
    }
    for (State<E> s : toVisit) {
      any = true;
      sb.append("\n");
      for (int i = 0; i < level; i++)
        sb.append("  ");
      sb.append("epsilon => ");
      sb.append(s.toStringVisitor(new HashSet<State<E>>(visited), level));
    }
    toVisit.clear();
    for (Transition<E> t : transitions.keySet()) {
      for (State<E> s : transitions.get(t)) {
        if (!visited.contains(s)) {
          visited.add(s);
          toVisit.add(s);
        }
      }
    }
    for (Transition<E> t : transitions.keySet()) {
      for (State<E> s : transitions.get(t)) {
        if (toVisit.contains(s)) {
          any = true;
          sb.append("\n");
          for (int i = 0; i < level; i++)
            sb.append("  ");
          sb.append(t);
          sb.append(" => ");
          sb.append(s.toStringVisitor(new HashSet<State<E>>(visited), level));
        }
      }
    }
    level--;
    if (any) {
      sb.append("\n");
      for (int i = 0; i < level; i++)
        sb.append("  ");
    }
    sb.append("}");
    return sb.toString();
  }

  /** Make this state a final state (sets the "accept" flag). */
  void makeFinal() {
    accept = true;
  }

  /** Make this state a non-final state (removes the "accept" flag). */
  void makeNonFinal() {
    accept = false;
  }

  /** Return <code>true</code> if this is a final ("accept") state. */
  boolean isFinal() {
    return accept;
  }

  /**
   * Add a transition <code>t</code> to another state <code>s</code> that only triggers if the
   * element in the sequence {@link Transition#matches matches} the transition's requirements.
   * <p>
   * If the element matches, it is consumed.
   * 
   * @param t transition (requirement)
   * @param s target state (for the transition)
   */
  void addTransition(Transition<E> t, State<E> s) {
    Set<State<E>> stateList;
    if (transitions.containsKey(t)) {
      stateList = transitions.get(t);
    } else {
      stateList = new HashSet<State<E>>();
      transitions.put(t, stateList);
    }
    stateList.add(s);
  }

  /** Add an empty (non-consuming) transition to another state. */
  void addEpsilonTransition(State<E> s) {
    epsilonTransitions.add(s);
  }

  /** Return <code>true</code> if this state is the start or end of a capture group. */
  public boolean isCapturing() {
    return captureStart || captureEnd;
  }
}
