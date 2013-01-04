package txtfnnl.pattern;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestPatternMatcher {
  public class CharTransition implements Transition<Character> {
    private final Character character;

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
    final List<Character> l = new ArrayList<Character>(seq.length());
    for (int idx = 0; idx < seq.length(); idx++) {
      l.add(seq.charAt(idx));
    }
    return l;
  }

  final void matchNever(List<Character> seq, Pattern<Character> p) {
    final Matcher<Character> m = p.matcher(seq);
    Assert.assertFalse(m.find());
  }

  final void matchOnce(List<Character> seq, Pattern<Character> p, int start, int end) {
    final Matcher<Character> m = p.matcher(seq);
    Assert.assertTrue(m.find());
    Assert.assertEquals(start, m.start());
    Assert.assertEquals(end, m.end());
    Assert.assertFalse(m.find());
  }

  final void matchForever(List<Character> seq, Pattern<Character> p, int start, int end) {
    final Matcher<Character> m = p.matcher(seq);
    Assert.assertTrue(m.find(0));
    Assert.assertEquals(start, m.start());
    Assert.assertEquals(end, m.end());
    Assert.assertTrue(m.find());
    Assert.assertEquals(start, m.start());
    Assert.assertEquals(end, m.end());
    Assert.assertTrue(m.find());
  }

  final void matchAll(List<Character> seq, Pattern<Character> p, int... offsets) {
    final Matcher<Character> m = p.matcher(seq);
    for (int i = 0; i < offsets.length; i += 2) {
      Assert.assertTrue(m.find());
      Assert.assertEquals(offsets[i], m.start());
      Assert.assertEquals(offsets[i + 1], m.end());
    }
    Assert.assertFalse(m.find());
  }

  @Test
  public final void testDefaultPatternConstuctor() {
    final Pattern<Character> p = new Pattern<Character>().minimize();
    matchForever(TestPatternMatcher.toCharacterArray(""), p, 0, 0);
    matchForever(TestPatternMatcher.toCharacterArray("a"), p, 0, 0);
    matchForever(TestPatternMatcher.toCharacterArray("abc"), p, 0, 0);
  }

  @Test
  public final void testPatternBasicTransitionMatch() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    matchOnce(TestPatternMatcher.toCharacterArray("x"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("xyz"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("axb"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("vwx"), p, 2, 3);
    matchNever(TestPatternMatcher.toCharacterArray("a"), p);
    matchNever(TestPatternMatcher.toCharacterArray("abc"), p);
  }

  @Test
  public final void testPatternOptional() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x')).optional().minimize();
    matchForever(TestPatternMatcher.toCharacterArray("x"), p, 0, 0); // non-greedy!
    matchForever(TestPatternMatcher.toCharacterArray("xyz"), p, 0, 0);
    matchForever(TestPatternMatcher.toCharacterArray(""), p, 0, 0);
  }

  @Test
  public final void testPatternRepeat() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x')).repeat().minimize();
    matchOnce(TestPatternMatcher.toCharacterArray("x"), p, 0, 1);
    matchAll(TestPatternMatcher.toCharacterArray("xxx"), p, 0, 1, 1, 2, 2, 3);
    matchNever(TestPatternMatcher.toCharacterArray("abc"), p);
    matchNever(TestPatternMatcher.toCharacterArray(""), p);
    matchOnce(TestPatternMatcher.toCharacterArray("xyz"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("axb"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("vwx"), p, 2, 3);
  }

  @Test
  public final void testPatternOptionalRepeat() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x')).repeat().optional()
        .minimize();
    matchForever(TestPatternMatcher.toCharacterArray("x"), p, 0, 0); // non-greedy!
    matchForever(TestPatternMatcher.toCharacterArray("abc"), p, 0, 0); // non-greedy!
    matchForever(TestPatternMatcher.toCharacterArray(""), p, 0, 0); // non-greedy!
  }

  @Test
  public final void testPatternChaining() {
    final Pattern<Character> p1 = Pattern.match(new CharTransition('x'));
    final Pattern<Character> p2 = Pattern.match(new CharTransition('x'));
    final Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchNever(TestPatternMatcher.toCharacterArray("x"), p);
    matchOnce(TestPatternMatcher.toCharacterArray("xx"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("xxx"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("xxb"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("axx"), p, 1, 3);
    matchOnce(TestPatternMatcher.toCharacterArray("axxb"), p, 1, 3);
  }

  @Test
  public final void testPatternBranching() {
    final Pattern<Character> p1 = Pattern.match(new CharTransition('x'));
    final Pattern<Character> p2 = Pattern.match(new CharTransition('y'));
    final Pattern<Character> p = Pattern.branch(p1, p2).minimize();
    matchOnce(TestPatternMatcher.toCharacterArray("x"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("y"), p, 0, 1);
    matchNever(TestPatternMatcher.toCharacterArray("z"), p);
    matchOnce(TestPatternMatcher.toCharacterArray("zx"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("zy"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("xz"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("yz"), p, 0, 1);
    matchAll(TestPatternMatcher.toCharacterArray("xyz"), p, 0, 1, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("zxz"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("zyz"), p, 1, 2);
    matchAll(TestPatternMatcher.toCharacterArray("zyzxz"), p, 1, 2, 3, 4);
  }

  @Test
  public final void testPatternCapturing() {
    final Pattern<Character> p = Pattern.capture(Pattern.match(new CharTransition('x')));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("axa"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(0));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(1));
    Assert.assertNotSame(TestPatternMatcher.toCharacterArray("a"), m.group(1));
    Assert.assertEquals(1, m.start(0));
    Assert.assertEquals(2, m.end(0));
    Assert.assertEquals(1, m.start(1));
    Assert.assertEquals(2, m.end(1));
  }

  @Test
  public final void testPatternCapturingRepeats() {
    final Pattern<Character> x = Pattern.capture(Pattern.match(new CharTransition('x')).repeat());
    final Pattern<Character> p = Pattern.chain(x, Pattern.match(new CharTransition('z')));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("axxza"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("xxz"), m.group());
    Assert.assertEquals(1, m.groupCount());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("xx"), m.group(1));
    Assert.assertEquals(1, m.start(1));
    Assert.assertEquals(3, m.end(1));
  }

  @Test
  public final void testPatternChainWithOptional() {
    final Pattern<Character> p1 = Pattern.match(new CharTransition('a')).optional();
    final Pattern<Character> p2 = Pattern.match(new CharTransition('b'));
    final Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchOnce(TestPatternMatcher.toCharacterArray("ab"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("b"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("aaab"), p, 2, 4);
  }

  @Test
  public final void testPatternChainWithRepeat() {
    final Pattern<Character> p1 = Pattern.match(new CharTransition('a')).repeat();
    final Pattern<Character> p2 = Pattern.match(new CharTransition('b'));
    final Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchOnce(TestPatternMatcher.toCharacterArray("ab"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("aaab"), p, 0, 4);
    matchNever(TestPatternMatcher.toCharacterArray("b"), p);
    matchNever(TestPatternMatcher.toCharacterArray("xb"), p);
    matchOnce(TestPatternMatcher.toCharacterArray("xab"), p, 1, 3);
    matchOnce(TestPatternMatcher.toCharacterArray("xaaab"), p, 1, 5);
  }

  @Test
  public final void testPatternChainWithOptionalRepeat() {
    final Pattern<Character> p1 = Pattern.match(new CharTransition('a')).optional().repeat();
    final Pattern<Character> p2 = Pattern.match(new CharTransition('b'));
    final Pattern<Character> p = Pattern.chain(p1, p2).minimize();
    matchOnce(TestPatternMatcher.toCharacterArray("ab"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("aaab"), p, 0, 4);
    matchOnce(TestPatternMatcher.toCharacterArray("b"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("xb"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("xab"), p, 1, 3);
    matchOnce(TestPatternMatcher.toCharacterArray("xaaab"), p, 1, 5);
  }

  @Test
  public final void testPatternBranchWithOptionalRepeat() {
    final Pattern<Character> x = Pattern.match(new CharTransition('x')).optional().repeat();
    final Pattern<Character> b = Pattern.match(new CharTransition('b'));
    final Pattern<Character> a = Pattern.match(new CharTransition('a'));
    final Pattern<Character> xa = Pattern.chain(x, a);
    final Pattern<Character> p = Pattern.branch(xa, b).minimize(); // x*a|b
    matchNever(TestPatternMatcher.toCharacterArray(""), p);
    matchNever(TestPatternMatcher.toCharacterArray("x"), p);
    matchOnce(TestPatternMatcher.toCharacterArray("a"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("b"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("bz"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("zb"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("az"), p, 0, 1);
    matchOnce(TestPatternMatcher.toCharacterArray("za"), p, 1, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("xa"), p, 0, 2);
    matchOnce(TestPatternMatcher.toCharacterArray("xxxa"), p, 0, 4);
    matchOnce(TestPatternMatcher.toCharacterArray("zxa"), p, 1, 3);
    matchAll(TestPatternMatcher.toCharacterArray("ab"), p, 0, 1, 1, 2);
    matchAll(TestPatternMatcher.toCharacterArray("xab"), p, 0, 2, 2, 3);
    matchAll(TestPatternMatcher.toCharacterArray("xaaab"), p, 0, 2, 2, 3, 3, 4, 4, 5);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testMatcherFindOffsetExceptionLow() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xx"));
    m.find(-2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testMatcherFindOffsetExceptionMinusOne() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xx"));
    m.find(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testMatcherFindOffsetExceptionHigh() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xx"));
    m.find(3);
  }

  @Test
  public final void testMatcherLookingAt() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xx"));
    Assert.assertTrue(m.lookingAt());
    m = p.matcher(TestPatternMatcher.toCharacterArray("x"));
    Assert.assertTrue(m.lookingAt());
    m = p.matcher(TestPatternMatcher.toCharacterArray("zx"));
    Assert.assertFalse(m.lookingAt());
  }

  @Test
  public final void testMatcherMatches() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xx"));
    Assert.assertFalse(m.matches());
    m = p.matcher(TestPatternMatcher.toCharacterArray("x"));
    Assert.assertTrue(m.matches());
    m = p.matcher(TestPatternMatcher.toCharacterArray("zx"));
    Assert.assertFalse(m.matches());
  }

  @Test
  public final void testMatcherGroup() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final List<Character> test = TestPatternMatcher.toCharacterArray("xxz");
    final Matcher<Character> m = p.matcher(test);
    Assert.assertTrue(m.find());
    Assert.assertEquals(test.subList(1, 2), m.group());
    Assert.assertNotSame(test.subList(2, 3), m.group());
  }

  @Test(expected = IllegalStateException.class)
  public final void testMatcherGroupStateException() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xxz"));
    m.group();
  }

  @Test
  public final void testMatcherStartEnd() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xxz"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(0, m.start());
    Assert.assertEquals(1, m.end());
  }

  @Test(expected = IllegalStateException.class)
  public final void testMatcherStartStateException() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xxz"));
    m.start();
  }

  @Test(expected = IllegalStateException.class)
  public final void testMatcherEndStateException() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xxz"));
    m.end();
  }

  @Test
  public final void testMatcherReset() {
    final Pattern<Character> p = Pattern.match(new CharTransition('x'));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xx"));
    Assert.assertTrue(m.find());
    Assert.assertTrue(m.find());
    Assert.assertFalse(m.find());
    Assert.assertEquals(m, m.reset());
    Assert.assertTrue(m.find());
    Assert.assertTrue(m.find());
    Assert.assertFalse(m.find());
  }

  @Test
  public final void testPatternCapturingComplex() {
    final Pattern<Character> x = Pattern.match(new CharTransition('x'));
    final Pattern<Character> y = Pattern.match(new CharTransition('y'));
    final Pattern<Character> xory = Pattern.capture(Pattern.branch(x, y));
    final Pattern<Character> a = Pattern.match(new CharTransition('a'));
    final Pattern<Character> a2 = Pattern.match(new CharTransition('a'));
    final Pattern<Character> p = Pattern.chain(Pattern.chain(a, xory), a2); // "a(x|y)a"
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("axa"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("axa"), m.group(0));
    Assert.assertEquals(1, m.groupCount());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(1));
    Assert.assertEquals(0, m.start(0));
    Assert.assertEquals(3, m.end(0));
    Assert.assertEquals(1, m.start(1));
    Assert.assertEquals(2, m.end(1));
  }

  @Test
  public final void testPatternCapturingAlt() {
    final Pattern<Character> x = Pattern.match(new CharTransition('x'));
    final Pattern<Character> y = Pattern.match(new CharTransition('y'));
    final Pattern<Character> xoryCap = Pattern.capture(Pattern.branch(x, y));
    final Pattern<Character> z = Pattern.match(new CharTransition('z'));
    final Pattern<Character> p = Pattern.branch(xoryCap, z); // "(x|y)|z"
    // aza:
    Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("aza"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("z"), m.group(0));
    Assert.assertFalse(m.find());
    // aya:
    m = p.matcher(TestPatternMatcher.toCharacterArray("aya"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("y"), m.group(1));
    Assert.assertEquals(m.group(), m.group(1));
    // axa:
    m = p.matcher(TestPatternMatcher.toCharacterArray("axa"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(1));
    Assert.assertEquals(m.group(), m.group(1));
    Assert.assertFalse(m.find());
    // ava:
    m = p.matcher(TestPatternMatcher.toCharacterArray("ava"));
    Assert.assertFalse(m.find());
  }

  @Test
  public final void testPatternConsecutiveCaptures() {
    final Pattern<Character> x = Pattern.capture(Pattern.match(new CharTransition('x')));
    final Pattern<Character> y = Pattern.capture(Pattern.match(new CharTransition('y')));
    final Pattern<Character> z = Pattern.capture(Pattern.match(new CharTransition('z')));
    final Pattern<Character> p = Pattern.chain(Pattern.chain(x, y), z); // "(x)(y)(z)"
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("xyz"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(3, m.groupCount());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("xyz"), m.group());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(1));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("y"), m.group(2));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("z"), m.group(3));
    Assert.assertFalse(m.find());
  }

  @Test
  public final void testPatternNestedCaptures() {
    final Pattern<Character> p = Pattern.capture(Pattern.capture(Pattern.match(new CharTransition(
        'x'))));
    final Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("axb"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(2, m.groupCount());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(1));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("x"), m.group(2));
    Assert.assertFalse(m.find());
  }

  @Test
  public final void testPatternNestedConsecutiveCaptures() {
    final Pattern<Character> a = Pattern.match(new CharTransition('a'));
    final Pattern<Character> a2 = Pattern.match(new CharTransition('a'));
    final Pattern<Character> x = Pattern.capture(Pattern.match(new CharTransition('x')).optional()
        .repeat());
    final Pattern<Character> y = Pattern.capture(Pattern.match(new CharTransition('y')));
    final Pattern<Character> xy = Pattern.capture(Pattern.chain(x, y));
    final Pattern<Character> p = Pattern.chain(Pattern.chain(a, xy), a2).minimize(); // "a((x*)(y))a"
    Matcher<Character> m = p.matcher(TestPatternMatcher.toCharacterArray("aya"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(3, m.groupCount());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("aya"), m.group());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("y"), m.group(1));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray(""), m.group(2));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("y"), m.group(3));
    Assert.assertFalse(m.find());
    m = p.matcher(TestPatternMatcher.toCharacterArray("axxxya"));
    Assert.assertTrue(m.find());
    Assert.assertEquals(3, m.groupCount());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("axxxya"), m.group());
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("xxxy"), m.group(1));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("xxx"), m.group(2));
    Assert.assertEquals(TestPatternMatcher.toCharacterArray("y"), m.group(3));
    Assert.assertFalse(m.find());
  }
}
