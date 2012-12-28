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
 * A NFA-based pattern matching implementation.
 * <p>
 * This is a "pseudo-abstract" class that should be extended with a parser that implements some
 * expression grammar to describe the state machine a pattern should create. In addition to this
 * class, the {@link Transition} interface should be implemented to define how elements on the
 * sequence should be matched. In other words, combined with the {@link Matcher}, this class,
 * {@link State}, and {@link Transition} collectively are an "abstract" implementation of a finite
 * state machine based on a non-deterministic implementation of the state automaton.
 * <p>
 * The entire API for this "abstract" NFA is designed as close as possible to Java's own
 * {@link java.util.regex.Pattern} API.
 * 
 * @author Florian Leitner
 */
public class Pattern<E> {
  private final State<E> entry;
  private final State<E> exit;

  /**
   * This method should be overridden by inheriting classes and compile a NFA from a given
   * (grammatical) expression. <b>If called, this method only throws a RuntimeException!</b>
   * <p>
   * Inheriting classes should compile the given expression into a pattern: Patterns can be
   * constructed with the default constructor, and the static methods {@link #match(Transition)
   * element} (a single transition), {@link #chain(Pattern...) chain} ("and", i.e., a sequence of
   * transitions), and {@link #branch(Pattern...) branch} ("or", "|"). The core sequence element
   * matching should be done by implementing the {@link Transition} interface. A pattern's behavior
   * can be augmented by making it {@link #optional() optional} ("?") and/or by allowing it to be
   * {@link #repeat() repeated} ("+"; a pattern that is made both optional and repeated effectively
   * acts as a Kleene closure, "*"). Unless there are good reasons to not do so, the last step of
   * compiling a pattern should be to call {@link #minimize()} on itself.
   * 
   * @param expression to be compiled
   * @return the compiled NFA pattern
   */
  public static final <E> Pattern<E> compile(String expression) {
    throw new RuntimeException("pseudo-abstract method called");
  }

  /**
   * Create a NFA that matches a single transition.
   * 
   * @param t transition that needs to match
   * @return a NFA
   */
  protected static final <E> Pattern<E> match(Transition<E> t) {
    State<E> entry = new State<E>();
    State<E> exit = new State<E>();
    entry.addTransition(t, exit);
    return new Pattern<E>(entry, exit);
  }

  /**
   * Link successive patterns into one NFA ("AND").
   * 
   * @param patterns to chain together
   * @return a NFA
   */
  protected static final <E> Pattern<E> chain(Pattern<E>... patterns) {
    final int n = patterns.length;
    if (n == 0) return null;
    if (n == 1) return patterns[0];
    for (int i = 1; i < n; i++) {
      patterns[i - 1].exit.makeNonFinal();
      patterns[i - 1].exit.addEpsilonTransition(patterns[i].entry);
    }
    return new Pattern<E>(patterns[0].entry, patterns[n - 1].exit);
  }

  /**
   * Branch out into all listed patterns ("OR").
   * 
   * @param patterns to fan out
   * @return a NFA
   */
  protected static final <E> Pattern<E> branch(Pattern<E>... patterns) {
    final int n = patterns.length;
    if (n == 0) return null;
    if (n == 1) return patterns[0];
    State<E> entry = new State<E>();
    State<E> exit = new State<E>();
    for (int i = 0; i < n; i++) {
      patterns[i].exit.makeNonFinal();
      entry.addEpsilonTransition(patterns[i].entry);
      patterns[i].exit.addEpsilonTransition(exit);
    }
    return new Pattern<E>(entry, exit);
  }

  /**
   * Make this pattern capturing, i.e., ensure the sequences matched will be recored as a capture
   * group.
   * 
   * @param pattern to capture
   * @return a NFA
   */
  protected static final <E> Pattern<E> capture(Pattern<E> pattern) {
    if (!pattern.entry.equals(pattern.exit) && !pattern.entry.captureStart &&
        !pattern.exit.captureEnd) {
      pattern.entry.captureStart = true;
      pattern.exit.captureEnd = true;
      return pattern;
    } else {
      State<E> entry = new State<E>();
      State<E> exit = new State<E>();
      entry.captureStart = true;
      exit.captureEnd = true;
      entry.addEpsilonTransition(pattern.entry);
      pattern.exit.addEpsilonTransition(exit);
      pattern.exit.makeNonFinal();
      return new Pattern<E>(entry, exit);
    }
  }

  /**
   * Construct an NFA from the given entry and exit states.
   * <p>
   * It is the responsibility of the user to ensure these two states are actually connected.
   * 
   * @param entry state
   * @param exit state
   */
  protected Pattern(State<E> entry, State<E> exit) {
    this.entry = entry;
    this.exit = exit;
    exit.makeFinal(); // ensure exit is a final state
    // NB: there is no safeguard to ensure the states are actually connected!
  }
  
  /**
   * Construct the simplest possible pattern: a two-state NFA joined by an epsilon transition.
   * <p>
   * This pattern will match anything, from the empty sequence ("lambda"), to the infinite one.
   */
  protected Pattern() {
    entry = new State<E>();
    exit = new State<E>();
    entry.addEpsilonTransition(exit);
    exit.makeFinal();
  }

  /**
   * Augment this pattern to match zero or one iterations of itself, i.e., the pattern may
   * optionally be skipped.
   * <p>
   * It is allowed to use both {@link #optional()} and {@link #repeat()} on the same pattern.
   * 
   * @return itself/this pattern
   */
  protected final Pattern<E> optional() {
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
  protected final Pattern<E> repeat() {
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
  protected final Pattern<E> minimize() {
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
  private static <E> boolean replaceAndExpand(Map<State<E>, Set<State<E>>> expansions,
      Set<State<E>> states) {
    State<E> s;
    Set<State<E>> expansion = null; // be lazy - only instantiate this set if necessary
    Iterator<State<E>> iter = states.iterator();
    // iterate over the states
    while (iter.hasNext()) {
      s = iter.next();
      if (expansions.containsKey(s)) {
        // the state is invalid: replace and expand with that state's expansions
        iter.remove();
        if (expansion == null) expansion = new HashSet<State<E>>();
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
}
