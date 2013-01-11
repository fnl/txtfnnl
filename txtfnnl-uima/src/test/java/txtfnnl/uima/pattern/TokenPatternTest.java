package txtfnnl.uima.pattern;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.Before;
import org.junit.Test;

import org.easymock.EasyMock;

import txtfnnl.pattern.Matcher;
import txtfnnl.pattern.Pattern;
import txtfnnl.uima.tcas.TokenAnnotation;

public class TokenPatternTest {
  @Before
  public void setUp() throws Exception {}

  @Test
  public final void testCompileEmpty() {
    assertTrue(TokenPattern.compile("") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchEmpty() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    l.add(mock);
    EasyMock.replay(mock);
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
  }

  @Test
  public final void testCompileSimple() {
    assertTrue(TokenPattern.compile("*_*_*") instanceof Pattern<?>);
    assertTrue(TokenPattern.compile("a_b_c") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchSimple() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("*_*_*");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    l.add(mock);
    l.add(mock);
    EasyMock.replay(mock);
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
  }

  @Test
  public final void testCompileShort() {
    assertTrue(TokenPattern.compile("*_*") instanceof Pattern<?>);
    assertTrue(TokenPattern.compile("a_b") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchShort() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("*_*");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    l.add(mock);
    l.add(mock);
    EasyMock.replay(mock);
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
  }

  @Test
  public final void testMatchEscape() {
    assertTrue(TokenPattern.compile("*_\\_") instanceof Pattern<?>);
    Pattern<TokenAnnotation> p = TokenPattern.compile("\\_a\\__*_*");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    mock.getCoveredText();
    EasyMock.expectLastCall().andReturn("_a_");
    EasyMock.replay(mock);
    l.add(mock);
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
  }

  @Test
  public final void testCompileCapture() {
    assertTrue(TokenPattern.compile("( )") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchEmptyCapture() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("( )");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
    assertEquals(1, m.groupCount());
    TokenAnnotation token = EasyMock.createMock(TokenAnnotation.class);
    l.add(token);
    m.reset(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
    assertEquals(1, m.groupCount());
    assertEquals(0, m.start(0));
    assertEquals(0, m.end(0));
  }

  @Test
  public final void testMatchCaptureGroup() {
    String expr = "( *_* )";
    Pattern<TokenAnnotation> p = TokenPattern.compile(expr);
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertFalse(m.find());
    TokenAnnotation token = EasyMock.createMock(TokenAnnotation.class);
    l.add(token);
    EasyMock.replay(token);
    m.reset(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
    assertEquals(1, m.groupCount());
    assertEquals(0, m.start(0));
    assertEquals(1, m.end(0));
  }

  @Test
  public final void testCompileChunk() {
    assertTrue(TokenPattern.compile("[ CHUNK ]") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchChunk() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("[ CHUNK *_* ]");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertFalse(m.find());
    TokenAnnotation token = EasyMock.createMock(TokenAnnotation.class);
    EasyMock.expect(token.getChunkBegin()).andReturn(true);
    EasyMock.expect(token.getChunkEnd()).andReturn(true);
    EasyMock.expect(token.getChunk()).andReturn("CHUNK");
    EasyMock.expect(token.getChunk()).andReturn("CHUNK");
    EasyMock.replay(token);
    l.add(token);
    m.reset(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
  }

  @Test
  public final void testMatchCapturedChunk() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("( [ CHUNK *_* ] )");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    TokenAnnotation token = EasyMock.createMock(TokenAnnotation.class);
    EasyMock.expect(token.getChunkBegin()).andReturn(true);
    EasyMock.expect(token.getChunkEnd()).andReturn(true);
    EasyMock.expect(token.getChunk()).andReturn("CHUNK");
    EasyMock.expect(token.getChunk()).andReturn("CHUNK");
    EasyMock.replay(token);
    l.add(token);
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
    assertEquals(1, m.groupCount());
    assertEquals(0, m.start(0));
    assertEquals(1, m.end(0));
  }

  @Test(expected = PatternSyntaxException.class)
  public final void testCompileSafe() {
    assertTrue(TokenPattern.compile("a_b_") instanceof Pattern<?>);
  }

  @Test(expected = PatternSyntaxException.class)
  public final void testCompileShortSafe() {
    assertTrue(TokenPattern.compile("_*") instanceof Pattern<?>);
  }

  @Test
  public final void testCompileEscape() {
    assertTrue(TokenPattern.compile("\\__*_*") instanceof Pattern<?>);
  }

  @Test
  public final void testCompileRealisticExpression() {
    String expr = "( [ NP + NN.*_factor ] ) * [ VP * VB.?_bind|interact ] ( [ NP + NN.*_gene ] )";
    assertTrue(TokenPattern.compile(expr) instanceof Pattern<?>);
  }

  @Test
  public final void testMatchAnything() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("*");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    l.add(mock);
    m.reset(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
  }

  @Test
  public final void testMatchOneOrMore() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("+");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertFalse(m.find());
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    l.add(mock);
    l.add(mock);
    m.reset(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
  }

  @Test
  public final void testMatchOptional() {
    Pattern<TokenAnnotation> p = TokenPattern.compile("*_* ?");
    List<TokenAnnotation> l = new LinkedList<TokenAnnotation>();
    Matcher<TokenAnnotation> m = p.matcher(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
    TokenAnnotation mock = EasyMock.createMock(TokenAnnotation.class);
    l.add(mock);
    m.reset(l);
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(0, m.end());
  }

}
