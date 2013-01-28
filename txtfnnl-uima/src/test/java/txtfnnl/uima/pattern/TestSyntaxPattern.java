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

public class TestSyntaxPattern {
  @Before
  public void setUp() throws Exception {}

  @Test
  public final void testCompileEmpty() {
    assertTrue(SyntaxPattern.compile("") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchEmpty() {
    Pattern<TokenAnnotation> p = SyntaxPattern.compile("");
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
    assertTrue(SyntaxPattern.compile("*_*_*") instanceof Pattern<?>);
    assertTrue(SyntaxPattern.compile("a_b_c") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchSimple() {
    Pattern<TokenAnnotation> p = SyntaxPattern.compile("*_*");
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
    assertTrue(SyntaxPattern.compile(".") instanceof Pattern<?>);
    assertTrue(SyntaxPattern.compile("*") instanceof Pattern<?>);
    assertTrue(SyntaxPattern.compile("A") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchShort() {
    Pattern<TokenAnnotation> p = SyntaxPattern.compile(".");
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
    assertTrue(SyntaxPattern.compile("*_\\_") instanceof Pattern<?>);
    Pattern<TokenAnnotation> p = SyntaxPattern.compile("\\_a\\__*_*");
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
    assertTrue(SyntaxPattern.compile("( )") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchEmptyCapture() {
    Pattern<TokenAnnotation> p = SyntaxPattern.compile("( )");
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
    String expr = "( . )";
    Pattern<TokenAnnotation> p = SyntaxPattern.compile(expr);
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
    assertTrue(SyntaxPattern.compile("[ CHUNK ]") instanceof Pattern<?>);
  }

  @Test
  public final void testMatchChunk() {
    Pattern<TokenAnnotation> p = SyntaxPattern.compile("[ CHUNK . ]");
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
    Pattern<TokenAnnotation> p = SyntaxPattern.compile("( [ CHUNK . ] )");
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
    assertTrue(SyntaxPattern.compile("a_b_") instanceof Pattern<?>);
  }

  @Test(expected = PatternSyntaxException.class)
  public final void testCompileShortSafe() {
    assertTrue(SyntaxPattern.compile("_A") instanceof Pattern<?>);
  }

  @Test
  public final void testCompileEscape() {
    assertTrue(SyntaxPattern.compile("\\__A_B") instanceof Pattern<?>);
  }

  @Test
  public final void testCompileRealisticExpression() {
    String expr = "( [ NP DT_* ? . + NN_factor ] ) . * [ VP . * VB.?_bind|interact ] [ NP DT_* ? ( . + NN.*_gene ) ]";
    assertTrue(SyntaxPattern.compile(expr) instanceof Pattern<?>);
  }

  @Test
  public final void testMatchAnything() {
    Pattern<TokenAnnotation> p = SyntaxPattern.compile(". *");
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
    Pattern<TokenAnnotation> p = SyntaxPattern.compile(". +");
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
    Pattern<TokenAnnotation> p = SyntaxPattern.compile(". ?");
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
