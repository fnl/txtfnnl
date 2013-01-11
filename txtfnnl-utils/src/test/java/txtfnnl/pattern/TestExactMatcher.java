package txtfnnl.pattern;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class TestExactMatcher {
  public class CharElement implements Transition<Character> {
    private Character character;

    public CharElement(Character c) {
      if (c == null) throw new AssertionError("character may not be null");
      character = c;
    }

    public boolean matches(Character target) {
      return character.equals(target);
    }
    
    public double weight() {
      return 1.0;
    }
  }

  Character[] sequence;
  ExactMatcher<CharElement, Character> pattern;

  @Before
  public void setUp() throws Exception {}

  @Test
  public final void testMatcherTArray() {
    CharElement[] p = new CharElement[] { new CharElement(Character.valueOf('a')) };
    new ExactMatcher<CharElement, Character>(p);
  }

  @Test
  public final void testMatcherListOfTClassOfT() {
    List<CharElement> p = new LinkedList<CharElement>();
    p.add(new CharElement(Character.valueOf('a')));
    new ExactMatcher<CharElement, Character>(p, CharElement.class);
  }

  @Test
  public final void testGetPattern() {
    CharElement[] p = new CharElement[] { new CharElement(Character.valueOf('a')) };
    pattern = new ExactMatcher<CharElement, Character>(p);
    assertArrayEquals(p, pattern.getTransitions());
  }

  @Test
  public final void testLength() {
    CharElement[] p = new CharElement[] { new CharElement(Character.valueOf('a')) };
    pattern = new ExactMatcher<CharElement, Character>(p);
    assertEquals(1, pattern.length());
  }

  @Test
  public final void testFind() {
    CharElement[] p = new CharElement[] { new CharElement(Character.valueOf('a')),
        new CharElement(Character.valueOf('b')), new CharElement(Character.valueOf('b')),
        new CharElement(Character.valueOf('a')), };
    Character[] s = new Character[] { Character.valueOf('a'), Character.valueOf('a'),
        Character.valueOf('a'), Character.valueOf('b'), Character.valueOf('b'),
        Character.valueOf('a'), Character.valueOf('a') };
    pattern = new ExactMatcher<CharElement, Character>(p);
    assertEquals(2, pattern.find(s));
    s[4] = Character.valueOf('x');
    assertEquals(-1, pattern.find(s));
  }

  @Test
  public final void testFindOffset() {
    CharElement[] p = new CharElement[] { new CharElement(Character.valueOf('a')),
        new CharElement(Character.valueOf('b')), new CharElement(Character.valueOf('b')),
        new CharElement(Character.valueOf('a')), };
    Character[] s = new Character[] { Character.valueOf('a'), Character.valueOf('a'),
        Character.valueOf('a'), Character.valueOf('b'), Character.valueOf('b'),
        Character.valueOf('a'), Character.valueOf('a') };
    pattern = new ExactMatcher<CharElement, Character>(p);
    assertEquals(2, pattern.find(s, 1));
    s[4] = Character.valueOf('x');
    assertEquals(-1, pattern.find(s, 1));
  }

  @Test
  public final void testSearch() {
    List<CharElement> p = new LinkedList<CharElement>();
    p.add(new CharElement('c'));
    p.add(new CharElement('d'));
    p.add(new CharElement('e'));
    pattern = new ExactMatcher<CharElement, Character>(p, CharElement.class);
    List<Character> s = new ArrayList<Character>(8);
    s.add('a');
    s.add('b');
    s.add('c');
    s.add('d');
    s.add('e');
    s.add('f');
    s.add('g');
    s.add('h');
    assertEquals(Character.valueOf('c'), pattern.search(s.iterator()));
    assertEquals(4, s.indexOf('e'));
    s.set(s.indexOf('e'), 'x');
    assertNull("pattern 'cde' found in " + Arrays.toString(s.toArray(new Character[s.size()])) +
        " but should not match", pattern.search(s.iterator()));
  }
}
