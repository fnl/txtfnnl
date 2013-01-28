/* Created on Dec 21, 2012 by Florian Leitner.
 * Copyright 2012. All rights reserved. */
package txtfnnl.pattern;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A generic NFA-based pattern matching implementation using backtracking for capture groups.
 * <p>
 * This class provides methods to compile a non-deterministic state machine. In addition to this
 * class, the {@link Transition} interface has to be implemented, defining how elements on the
 * sequence should be matched. In other words, combined with the {@link Matcher}, this class,
 * {@link State}, and {@link Transition} collectively form a generic implementation of a
 * non-deterministic, finite state machine using weighted backtracking to find capture groups.
 * <p>
 * The entire API for this generic NFA is designed as close as possible to Java's own
 * {@link java.util.regex.Pattern} API. It is incomplete, because while the class is usable, it
 * provides no static <code>compile(String regex)</code> method. Therefore, a regular expression
 * language needs to be designed and a parser/compiler for it implemented. For the same reason,
 * there is no <code>toString()</code> method that would convert the Pattern to a String of the
 * regular expression.
 * <p>
 * <b>Compiling a Pattern</b>
 * <p>
 * Users of this generic NFA package should inherit this class and implement a method such as
 * <code>public static Pattern<E> compile(String)</code> that compiles the NFA from an (regular)
 * expression. The patterns should be constructed using the default constructor, and the static
 * methods {@link #match(Transition) element} (a single transition),
 * {@link #chain(Pattern, Pattern) chain} ("and", i.e., a sequence of transitions), and
 * {@link #branch(Pattern...) branch} ("or", "|"). The core sequence element matching should be
 * done by implementing the {@link Transition} interface. A pattern's behavior can be augmented by
 * making it {@link #optional() optional} ("?") and/or by allowing it to be {@link #repeat()
 * repeated} ("+"; a pattern that is made both optional and repeated effectively acts as a Kleene
 * closure, "*"). Unless there are good reasons not to, the last step of compiling a pattern should
 * be to call {@link #minimize()} on itself.
 * 
 * @author Florian Leitner
 */
public class Pattern<E> {
  private final State<E> entry;
  private State<E> exit;

  /**
   * Create a pattern that matches a single transition.
   * <p>
   * The perfect "seed" for assembling patterns if the required transition is known already.
   * 
   * @param t the transition that has to match
   * @return a NFA
   */
  public static final <T> Pattern<T> match(Transition<T> t) {
    State<T> entry = new State<T>();
    State<T> exit = new State<T>();
    entry.addTransition(t, exit);
    return new Pattern<T>(entry, exit);
  }

  /**
   * Link two successive patterns into one ("AND").
   * 
   * @param first pattern to match before second
   * @param second pattern to match after first
   * @return a NFA
   */
  public static final <T> Pattern<T> chain(Pattern<T> first, Pattern<T> second) {
    first.exit.makeNonFinal();
    first.exit.addEpsilonTransition(second.entry);
    return new Pattern<T>(first.entry, second.exit);
  }
  
  @Override
  public final String toString() {
    return String.format("Pattern:\n%s", entry.toString());
  }

  /**
   * Fork out into one of two patterns ("OR").
   * 
   * @param left optional pattern to match
   * @param right optional pattern to match
   * @return a NFA
   */
  public static final <T> Pattern<T> branch(Pattern<T> left, Pattern<T> right) {
    State<T> entry = new State<T>();
    State<T> exit = new State<T>();
    left.exit.makeNonFinal();
    right.exit.makeNonFinal();
    entry.addEpsilonTransition(left.entry);
    entry.addEpsilonTransition(right.entry);
    left.exit.addEpsilonTransition(exit);
    right.exit.addEpsilonTransition(exit);
    return new Pattern<T>(entry, exit);
  }

  /**
   * Make this pattern capturing, i.e., ensure the sequence offsets matched by it will be recored
   * as a capture group.
   * 
   * @param pattern to capture
   * @return a NFA
   */
  public static final <T> Pattern<T> capture(Pattern<T> pattern) {
    // note that a state with both the capture start and end flag set will be treated as
    // first ending a group, then starting a new one; therefore, if the pattern's entry and
    // exit states are equal, additional states need to be introduced, otherwise the
    // the matcher would try to first end a (not yet started) group and then start a group
    // (that never would be ended) because the start and end flags would be set on the same state
    if (!pattern.entry.equals(pattern.exit) && !pattern.entry.captureStart &&
        !pattern.exit.captureEnd) {
      pattern.entry.captureStart = true;
      pattern.exit.captureEnd = true;
      return pattern;
    } else {
      State<T> entry = new State<T>();
      State<T> exit = new State<T>();
      entry.captureStart = true;
      exit.captureEnd = true;
      entry.addEpsilonTransition(pattern.entry);
      pattern.exit.addEpsilonTransition(exit);
      pattern.exit.makeNonFinal();
      return new Pattern<T>(entry, exit);
    }
  }

  /**
   * Construct the simplest possible pattern: a two-state NFA joined by an epsilon transition.
   * <p>
   * This pattern will match anything, from the empty sequence ("lambda"), to the infinite one.
   * Therefore, it provides the perfect "seed" for assembling more complex patterns.
   */
  public Pattern() {
    entry = new State<E>();
    exit = new State<E>();
    entry.addEpsilonTransition(exit);
    exit.makeFinal();
  }

  /**
   * Construct an NFA from the given entry and exit states.
   * <p>
   * It is the responsibility of the user to ensure these two states are actually connected.
   * 
   * @param entry state
   * @param exit state
   */
  Pattern(State<E> entry, State<E> exit) {
    this.entry = entry;
    this.exit = exit;
    exit.makeFinal(); // ensure at least exit is a final state
  }

  /**
   * Augment this pattern to match zero or one iterations of itself, i.e., the pattern may
   * optionally be skipped.
   * <p>
   * It is allowed to use both {@link #optional()} and {@link #repeat()} on the same pattern.
   * 
   * @return itself/this pattern
   */
  public final Pattern<E> optional() {
    entry.addEpsilonTransition(exit);
    return this;
  }

  /**
   * Augment this pattern to match one or more repetitions of itself, i.e., the pattern may
   * optionally be matched several times.
   * <p>
   * It is allowed to use both {@link #optional()} and {@link #repeat()} on the same pattern.
   * 
   * @return itself/this pattern
   */
  public final Pattern<E> repeat() {
    exit.addEpsilonTransition(entry);
    return this;
  }

  /**
   * Remove states that only have epsilon transitions and instead connect their source and target
   * states directly. Final states and the entry and exit state will never be pruned.
   * <p>
   * This method should be used after the entire pattern has been compiled to reduce the total
   * number of transitions and states in the FSM.
   * 
   * @return itself/this pattern
   */
  public final Pattern<E> minimize() {
    State<E> state;
    Queue<State<E>> queue = new LinkedList<State<E>>(); // queue of states to check
    // a map of states with only epsilon transitions and their associated target states
    Map<State<E>, Set<State<E>>> invalidStates = new HashMap<State<E>, Set<State<E>>>();
    Set<State<E>> validStates = new HashSet<State<E>>(); // states that should not be pruned
    queue.add(entry);
    // detect invalid states: states that are non-final with no regular transitions
    // unless it is the entry state or a capture group-related state
    while (!queue.isEmpty()) {
      state = queue.remove();
      if (!state.isFinal() && !state.isCapturing() && state.transitions.size() == 0 &&
          !state.equals(entry)) {
        // for those invalid states, record their (epsilon transition) targets
        invalidStates.put(state, state.epsilonTransitions);
      } else {
        // everything else is a valid state
        validStates.add(state);
      }
      // find yet unseen states to queue
      for (State<E> next : state.epsilonTransitions) {
        if (!validStates.contains(next) && !invalidStates.containsKey(next)) queue.add(next);
        if (next.equals(state)) // safeguard to avoid infinite loops
          throw new RuntimeException("circular reference detected: " + state.toString());
      }
      for (Set<State<E>> stateSet : state.transitions.values()) {
        for (State<E> next : stateSet) {
          if (!validStates.contains(next) && !invalidStates.containsKey(next)) queue.add(next);
        }
      }
    }
    boolean pruning = true;
    // find invalid states that map to other invalid states and replace those
    // targets by continuously expanding them until they are only valid targets left
    while (pruning) {
      // while any invalid state contained a mapping to any other invalid state, keep pruning
      pruning = false; // assume pruning is done at the start of each round of pruning
      // iterate over all invalid states
      for (State<E> source : invalidStates.keySet()) {
        // if replaceAndExpand is true, this source state was pointing to another invalid state
        if (replaceAndExpand(invalidStates, invalidStates.get(source))) pruning = true;
      }
    }
    // after pruning the pointers, we can now expand all invalid states pointed at by valid ones
    // with their appropriate valid target states
    for (State<E> valid : validStates) {
      replaceAndExpand(invalidStates, valid.epsilonTransitions);
      for (Set<State<E>> targetStates : valid.transitions.values())
        replaceAndExpand(invalidStates, targetStates);
    }
    return this;
  }

  /**
   * Expand any invalid states in the given set of states.
   * 
   * @param expansions a mapping of invalid states to their target expansions
   * @param states a set of states possibly containing invalid states to be expanded
   * @return <code>true</code> if any expansion was made
   */
  private static final <T> boolean replaceAndExpand(Map<State<T>, Set<State<T>>> expansions,
      Set<State<T>> states) {
    State<T> s;
    Set<State<T>> expansion = null; // be lazy - only instantiate this set if necessary
    Iterator<State<T>> iter = states.iterator();
    // iterate over the states
    while (iter.hasNext()) {
      s = iter.next();
      if (expansions.containsKey(s)) {
        // the state is invalid: replace and expand with that state's expansions
        iter.remove();
        if (expansion == null) expansion = new HashSet<State<T>>();
        expansion.addAll(expansions.get(s));
      }
    }
    if (expansion != null) {
      states.addAll(expansion);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Creates a matcher that will match the input sequence against this pattern.
   * 
   * @param input sequence to be matched
   * @return a new matcher for this pattern
   */
  public final Matcher<E> matcher(List<E> input) {
    return new Matcher<E>(entry, exit, input);
  }
  
  // XXX: possible future additions to this class:
  // public final List<E>[] split(List<E> input)
  // public final List<E>[] split(List<E> input, int limit)
}
