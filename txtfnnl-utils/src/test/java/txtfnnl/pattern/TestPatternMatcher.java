package txtfnnl.pattern;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class TestPatternMatcher {
  public class CharTransition implements Transition<Character> {
    private Character character;

    public CharTransition(Character c) {
      if (c == null) throw new AssertionError("character may not be null");
      character = c;
    }

    public boolean matches(Character target) {
      return character.equals(target);
    }

    @Override
    public String toString() {
      return character.toString();
    }
  }

  @Before
  public void setUp() throws Exception {}

  static List<Character> toCharacterArray(String seq) {
    List<Character> l = new ArrayList<Character>(seq.length());
    for (int idx = 0; idx < seq.length(); idx++)
      l.add(seq.charAt(idx));
    return l;
  }

  final void matchNever(List<Character> seq, Pattern<Character> p) {
    Matcher<Character> m = p.matcher(seq);
    assertFalse(m.find());
  }

  final void matchOnce(List<Character> seq, Pattern<Character> p, int start, int end) {
    Matcher<Character> m = p.matcher(seq);
    assertTrue(m.find());
    assertEquals(start, m.start());
    assertEquals(end, m.end());
    assertFalse(m.find());
  }

  final void matchForever(List<Character> seq, Pattern<Character> p, int start, int end) {
    Matcher<Character> m = p.matcher(seq);
    assertTrue(m.find(0));
    assertEquals(start, m.start());
    assertEquals(end, m.end());
    assertTrue(m.find());
    assertEquals(start, m.start());
    assertEquals(end, m.end());
    assertTrue(m.find());
  }

  final void matchAll(List<Character> seq, Pattern<Character> p, int... offsets) {
    Matcher<Character> m = p.matcher(seq);
    for (int i = 0; i < offsets.length; i += 2) {
      assertTrue(m.find());
      assertEquals(offsets[i], m.start());
      assertEquals(offsets[i + 1], m.end());
    }
    assertFalse(m.find());
  }

  @Test
  public final void testDefaultPatternConstuctor() {
    Pattern<Character> p = new Pattern<Character>().minimize();
    matchForever(toCharacterArray(""), p, 0, 0);
    matchForever(toCharacterArray("a"), p, 0, 0);
    matchForever(toCharacterArray("abc"), p, 0, 0);
  }

  @Test
  public final void testPatternBasicTransitionMatch() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    matchOnce(toCharacterArray("x"), p, 0, 1);
    matchOnce(toCharacterArray("xyz"), p, 0, 1);
    matchOnce(toCharacterArray("axb"), p, 1, 2);
    matchOnce(toCharacterArray("vwx"), p, 2, 3);
    matchNever(toCharacterArray("a"), p);
    matchNever(toCharacterArray("abc"), p);
  }

  @Test
  public final void testPatternOptional() {
    Pattern<Character> p = Pattern.match(new CharTransition('x')).optional().minimize();
    matchForever(toCharacterArray("x"), p, 0, 0); // non-greedy!
    matchForever(toCharacterArray("xyz"), p, 0, 0);
    matchForever(toCharacterArray(""), p, 0, 0);
  }

  @Test
  public final void testPatternRepeat() {
    Pattern<Character> p = Pattern.match(new CharTransition('x')).repeat().minimize();
    matchOnce(toCharacterArray("x"), p, 0, 1);
    matchAll(toCharacterArray("xxx"), p, 0, 1, 1, 2, 2, 3);
    matchNever(toCharacterArray("abc"), p);
    matchNever(toCharacterArray(""), p);
    matchOnce(toCharacterArray("xyz"), p, 0, 1);
    matchOnce(toCharacterArray("axb"), p, 1, 2);
    matchOnce(toCharacterArray("vwx"), p, 2, 3);
  }

  @Test
  public final void testPatternOptionalRepeat() {
    Pattern<Character> p = Pattern.match(new CharTransition('x')).repeat().optional().minimize();
    matchForever(toCharacterArray("x"), p, 0, 0); // non-greedy!
    matchForever(toCharacterArray("abc"), p, 0, 0); // non-greedy!
    matchForever(toCharacterArray(""), p, 0, 0); // non-greedy!
  }

  @Test
  public final void testPatternChaining() {
    Pattern<Character> p1 = Pattern.match(new CharTransition('x'));
    Pattern<Character> p2 = Pattern.match(new CharTransition('x'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchNever(toCharacterArray("x"), p);
    matchOnce(toCharacterArray("xx"), p, 0, 2);
    matchOnce(toCharacterArray("xxx"), p, 0, 2);
    matchOnce(toCharacterArray("xxb"), p, 0, 2);
    matchOnce(toCharacterArray("axx"), p, 1, 3);
    matchOnce(toCharacterArray("axxb"), p, 1, 3);
  }

  @Test
  public final void testPatternBranching() {
    Pattern<Character> p1 = Pattern.match(new CharTransition('x'));
    Pattern<Character> p2 = Pattern.match(new CharTransition('y'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.branch(p1, p2).minimize();
    matchOnce(toCharacterArray("x"), p, 0, 1);
    matchOnce(toCharacterArray("y"), p, 0, 1);
    matchNever(toCharacterArray("z"), p);
    matchOnce(toCharacterArray("zx"), p, 1, 2);
    matchOnce(toCharacterArray("zy"), p, 1, 2);
    matchOnce(toCharacterArray("xz"), p, 0, 1);
    matchOnce(toCharacterArray("yz"), p, 0, 1);
    matchAll(toCharacterArray("xyz"), p, 0, 1, 1, 2);
    matchOnce(toCharacterArray("zxz"), p, 1, 2);
    matchOnce(toCharacterArray("zyz"), p, 1, 2);
    matchAll(toCharacterArray("zyzxz"), p, 1, 2, 3, 4);
  }

  @Test
  public final void testPatternCapturing() {
    Pattern<Character> p = Pattern.capture(Pattern.match(new CharTransition('x')));
    Matcher<Character> m = p.matcher(toCharacterArray("axa"));
    assertTrue(m.find());
    assertEquals(toCharacterArray("x"), m.group(0));
    assertEquals(toCharacterArray("x"), m.group(1));
    assertNotSame(toCharacterArray("a"), m.group(1));
    assertEquals(1, m.start(0));
    assertEquals(2, m.end(0));
    assertEquals(1, m.start(1));
    assertEquals(2, m.end(1));
  }

  @Test
  public final void testPatternCapturingRepeats() {
    Pattern<Character> x = Pattern.capture(Pattern.match(new CharTransition('x')).repeat());
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(x, Pattern.match(new CharTransition('z')));
    Matcher<Character> m = p.matcher(toCharacterArray("axxza"));
    assertTrue(m.find());
    assertEquals(toCharacterArray("xxz"), m.group());
    assertEquals(1, m.groupCount());
    assertEquals(toCharacterArray("xx"), m.group(1));
    assertEquals(1, m.start(1));
    assertEquals(3, m.end(1));
  }

  @Test
  public final void testPatternChainWithOptional() {
    Pattern<Character> p1 = Pattern.match(new CharTransition('a')).optional();
    Pattern<Character> p2 = Pattern.match(new CharTransition('b'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchOnce(toCharacterArray("ab"), p, 0, 2);
    matchOnce(toCharacterArray("b"), p, 0, 1);
    matchOnce(toCharacterArray("aaab"), p, 2, 4);
  }

  @Test
  public final void testPatternChainWithRepeat() {
    Pattern<Character> p1 = Pattern.match(new CharTransition('a')).repeat();
    Pattern<Character> p2 = Pattern.match(new CharTransition('b'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchOnce(toCharacterArray("ab"), p, 0, 2);
    matchOnce(toCharacterArray("aaab"), p, 0, 4);
    matchNever(toCharacterArray("b"), p);
    matchNever(toCharacterArray("xb"), p);
    matchOnce(toCharacterArray("xab"), p, 1, 3);
    matchOnce(toCharacterArray("xaaab"), p, 1, 5);
  }

  @Test
  public final void testPatternChainWithOptionalRepeat() {
    Pattern<Character> p1 = Pattern.match(new CharTransition('a')).optional().repeat();
    Pattern<Character> p2 = Pattern.match(new CharTransition('b'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchOnce(toCharacterArray("ab"), p, 0, 2);
    matchOnce(toCharacterArray("aaab"), p, 0, 4);
    matchOnce(toCharacterArray("b"), p, 0, 1);
    matchOnce(toCharacterArray("xb"), p, 1, 2);
    matchOnce(toCharacterArray("xab"), p, 1, 3);
    matchOnce(toCharacterArray("xaaab"), p, 1, 5);
  }

  @Test
  public final void testPatternBranchWithOptionalRepeat() {
    Pattern<Character> x = Pattern.match(new CharTransition('x')).optional().repeat();
    Pattern<Character> b = Pattern.match(new CharTransition('b'));
    Pattern<Character> a = Pattern.match(new CharTransition('a'));
    @SuppressWarnings("unchecked")
    Pattern<Character> xa = Pattern.chain(x, a);
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.branch(xa, b).minimize(); // x*a|b
    matchNever(toCharacterArray(""), p);
    matchNever(toCharacterArray("x"), p);
    matchOnce(toCharacterArray("a"), p, 0, 1);
    matchOnce(toCharacterArray("b"), p, 0, 1);
    matchOnce(toCharacterArray("bz"), p, 0, 1);
    matchOnce(toCharacterArray("zb"), p, 1, 2);
    matchOnce(toCharacterArray("az"), p, 0, 1);
    matchOnce(toCharacterArray("za"), p, 1, 2);
    matchOnce(toCharacterArray("xa"), p, 0, 2);
    matchOnce(toCharacterArray("xxxa"), p, 0, 4);
    matchOnce(toCharacterArray("zxa"), p, 1, 3);
    matchAll(toCharacterArray("ab"), p, 0, 1, 1, 2);
    matchAll(toCharacterArray("xab"), p, 0, 2, 2, 3);
    matchAll(toCharacterArray("xaaab"), p, 0, 2, 2, 3, 3, 4, 4, 5);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testMatcherFindOffsetExceptionLow() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xx"));
    m.find(-2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testMatcherFindOffsetExceptionMinusOne() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xx"));
    m.find(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testMatcherFindOffsetExceptionHigh() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xx"));
    m.find(3);
  }

  @Test
  public final void testMatcherLookingAt() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xx"));
    assertTrue(m.lookingAt());
    m = p.matcher(toCharacterArray("x"));
    assertTrue(m.lookingAt());
    m = p.matcher(toCharacterArray("zx"));
    assertFalse(m.lookingAt());
  }

  @Test
  public final void testMatcherMatches() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xx"));
    assertFalse(m.matches());
    m = p.matcher(toCharacterArray("x"));
    assertTrue(m.matches());
    m = p.matcher(toCharacterArray("zx"));
    assertFalse(m.matches());
  }

  @Test
  public final void testMatcherGroup() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    List<Character> test = toCharacterArray("xxz");
    Matcher<Character> m = p.matcher(test);
    assertTrue(m.find());
    assertEquals(test.subList(1, 2), m.group());
    assertNotSame(test.subList(2, 3), m.group());
  }

  @Test(expected = IllegalStateException.class)
  public final void testMatcherGroupStateException() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xxz"));
    m.group();
  }

  @Test
  public final void testMatcherStartEnd() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xxz"));
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
  }

  @Test(expected = IllegalStateException.class)
  public final void testMatcherStartStateException() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xxz"));
    m.start();
  }

  @Test(expected = IllegalStateException.class)
  public final void testMatcherEndStateException() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xxz"));
    m.end();
  }

  @Test
  public final void testMatcherReset() {
    Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(toCharacterArray("xx"));
    assertTrue(m.find());
    assertTrue(m.find());
    assertFalse(m.find());
    assertEquals(m, m.reset());
    assertTrue(m.find());
    assertTrue(m.find());
    assertFalse(m.find());
  }

  @Test
  public final void testPatternCapturingComplex() {
    Pattern<Character> x = Pattern.match(new CharTransition('x'));
    Pattern<Character> y = Pattern.match(new CharTransition('y'));
    @SuppressWarnings("unchecked")
    Pattern<Character> xory = Pattern.capture(Pattern.branch(x, y));
    Pattern<Character> a = Pattern.match(new CharTransition('a'));
    Pattern<Character> a2 = Pattern.match(new CharTransition('a'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(a, xory, a2); // "a(x|y)a"
    Matcher<Character> m = p.matcher(toCharacterArray("axa"));
    assertTrue(m.find());
    assertEquals(toCharacterArray("axa"), m.group(0));
    assertEquals(1, m.groupCount());
    assertEquals(toCharacterArray("x"), m.group(1));
    assertEquals(0, m.start(0));
    assertEquals(3, m.end(0));
    assertEquals(1, m.start(1));
    assertEquals(2, m.end(1));
  }

  @Test
  public final void testPatternCapturingAlt() {
    Pattern<Character> x = Pattern.match(new CharTransition('x'));
    Pattern<Character> y = Pattern.match(new CharTransition('y'));
    @SuppressWarnings("unchecked")
    Pattern<Character> xoryCap = Pattern.capture(Pattern.branch(x, y));
    Pattern<Character> z = Pattern.match(new CharTransition('z'));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.branch(xoryCap, z); // "(x|y)|z"
    // aza:
    Matcher<Character> m = p.matcher(toCharacterArray("aza"));
    assertTrue(m.find());
    assertEquals(toCharacterArray("z"), m.group(0));
    assertFalse(m.find());
    // aya:
    m = p.matcher(toCharacterArray("aya"));
    assertTrue(m.find());
    assertEquals(toCharacterArray("y"), m.group(1));
    assertEquals(m.group(), m.group(1));
    // axa:
    m = p.matcher(toCharacterArray("axa"));
    assertTrue(m.find());
    assertEquals(toCharacterArray("x"), m.group(1));
    assertEquals(m.group(), m.group(1));
    assertFalse(m.find());
    // ava:
    m = p.matcher(toCharacterArray("ava"));
    assertFalse(m.find());
  }

  @Test
  public final void testPatternConsecutiveCaptures() {
    Pattern<Character> x = Pattern.capture(Pattern.match(new CharTransition('x')));
    Pattern<Character> y = Pattern.capture(Pattern.match(new CharTransition('y')));
    Pattern<Character> z = Pattern.capture(Pattern.match(new CharTransition('z')));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(x, y, z); // "(x)(y)(z)"
    Matcher<Character> m = p.matcher(toCharacterArray("xyz"));
    assertTrue(m.find());
    assertEquals(3, m.groupCount());
    assertEquals(toCharacterArray("xyz"), m.group());
    assertEquals(toCharacterArray("x"), m.group(1));
    assertEquals(toCharacterArray("y"), m.group(2));
    assertEquals(toCharacterArray("z"), m.group(3));
    assertFalse(m.find());
  }

  @Test
  public final void testPatternNestedCaptures() {
    Pattern<Character> p = Pattern
        .capture(Pattern.capture(Pattern.match(new CharTransition('x'))));
    Matcher<Character> m = p.matcher(toCharacterArray("axb"));
    assertTrue(m.find());
    assertEquals(2, m.groupCount());
    assertEquals(toCharacterArray("x"), m.group());
    assertEquals(toCharacterArray("x"), m.group(1));
    assertEquals(toCharacterArray("x"), m.group(2));
    assertFalse(m.find());
  }

  @Test
  public final void testPatternNestedConsecutiveCaptures() {
    Pattern<Character> a = Pattern.match(new CharTransition('a'));
    Pattern<Character> a2 = Pattern.match(new CharTransition('a'));
    Pattern<Character> x = Pattern.capture(Pattern.match(new CharTransition('x')).optional()
        .repeat());
    Pattern<Character> y = Pattern.capture(Pattern.match(new CharTransition('y')));
    @SuppressWarnings("unchecked")
    Pattern<Character> xy = Pattern.capture(Pattern.chain(x, y));
    @SuppressWarnings("unchecked")
    Pattern<Character> p = Pattern.chain(a, xy, a2).minimize(); // "a((x*)(y))a"
    Matcher<Character> m = p.matcher(toCharacterArray("aya"));
    assertTrue(m.find());
    assertEquals(3, m.groupCount());
    assertEquals(toCharacterArray("aya"), m.group());
    assertEquals(toCharacterArray("y"), m.group(1));
    assertEquals(toCharacterArray(""), m.group(2));
    assertEquals(toCharacterArray("y"), m.group(3));
    assertFalse(m.find());
    m = p.matcher(toCharacterArray("axxxya"));
    assertTrue(m.find());
    assertEquals(3, m.groupCount());
    assertEquals(toCharacterArray("axxxya"), m.group());
    assertEquals(toCharacterArray("xxxy"), m.group(1));
    assertEquals(toCharacterArray("xxx"), m.group(2));
    assertEquals(toCharacterArray("y"), m.group(3));
    assertFalse(m.find());
  }
}
