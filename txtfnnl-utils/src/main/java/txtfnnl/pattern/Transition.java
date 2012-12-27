package txtfnnl.pattern;

/**
 * Transitions define how elements of a sequence should be allowed to match in a pattern.
 * <p>
 * E.g., if the {@link Pattern} (a non-deterministic automaton) or the {@link ExactMatcher} (a
 * deterministic automaton) should match a List of elements of some generic type <code>E</code>,
 * i.e., a <code>List&lt;E&gt;</code>, the transitions should define if the current element in the
 * sequence is appropriate to allow the automaton to move to the next state.
 * <p>
 * In the case of a character sequence automaton (i.e., regular expression String matching), the
 * {@link Transition#matches(Object)} implementation would return the Boolean result of
 * {@link Character#compareTo(Character)} <code>== 0</code> and be instantiated by defining the
 * relevant character for the transition (This is just an example - for such a case, you naturally
 * should be using Java's own {@link java.util.regex.Pattern regular expression} implementation!):
 * 
 * <pre>
 * class CharacterTransition implements Transition&lt;Character&gt; {
 *   private Character c;
 * 
 *   public CharacterTransition(Character toMatch) {
 *     this.c = toMatch;
 *   }
 * 
 *   public boolean match(Character other) {
 *     return c.compareTo(other) == 0;
 *   }
 * }
 * </pre>
 * 
 * @author Florian Leitner
 */
public interface Transition<E> {
  /**
   * A transition implementation must define if, given some unknown element, it is valid to make
   * the transition or not.
   * 
   * @param element an element from the sequence being matched
   * @return <code>true</code> if the transition is valid, <code>false</code> otherwise.
   */
  public boolean matches(E element);
}
