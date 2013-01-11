package txtfnnl.uima.pattern;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Test;

public class TokenLexerTest {
  
  @Test
  public final void testSetup() {
    TokenLexer l = new TokenLexer("expression");
    assertNotNull(l);
  }
  
  @Test
  public final void testIterator() {
    Iterator<String> it = (new TokenLexer("expression")).iterator();
    assertNotNull(it);
  }
  
  @Test
  public final void testBasicIteration() {
    TokenLexer l = new TokenLexer("a  b");
    assertEquals(0, l.offset());
    assertTrue(l.hasNext());
    assertEquals("a", l.peek());
    assertEquals(0, l.offset());
    assertEquals("a", l.next());
    assertEquals(2, l.offset());
    assertTrue(l.hasNext());
    assertEquals("b", l.peek());
    assertEquals(2, l.offset());
    assertEquals("b", l.next());
    assertEquals(3, l.offset());
    assertFalse(l.hasNext());
  }
  
  @Test
  public final void testEscapedWhitespace() {
    TokenLexer l = new TokenLexer("a\\ \\ \\ b");
    assertTrue(l.hasNext());
    assertEquals("a   b", l.peek());
    assertEquals("a   b", l.next());
    assertFalse(l.hasNext());
  }
  
  @Test
  public final void testEscapedEscape() {
    TokenLexer l = new TokenLexer("a\\\\ b");
    assertTrue(l.hasNext());
    assertEquals("a\\\\", l.peek());
    assertEquals("a\\\\", l.next());
    assertTrue(l.hasNext());
    assertEquals("b", l.peek());
    assertEquals("b", l.next());
    assertFalse(l.hasNext());
  }
  
  @Test
  public final void testMultiEscapedWhitespace() {
    TokenLexer l = new TokenLexer("a\\\\\\ b");
    assertTrue(l.hasNext());
    assertEquals("a\\\\ b", l.peek());
    assertEquals("a\\\\ b", l.next());
    assertFalse(l.hasNext());
  }

  @Test
  public final void testEndWithWhitespace() {
    TokenLexer l = new TokenLexer("a\\  b");
    assertTrue(l.hasNext());
    assertEquals("a ", l.peek());
    assertEquals("a ", l.next());
    assertTrue(l.hasNext());
    assertEquals("b", l.peek());
    assertEquals("b", l.next());
    assertFalse(l.hasNext());
  }
  

  @Test
  public final void testStartWithWhitespace() {
    TokenLexer l = new TokenLexer("a \\ b");
    assertTrue(l.hasNext());
    assertTrue(l.hasNext());
    assertEquals("a", l.peek());
    assertEquals("a", l.next());
    assertTrue(l.hasNext());
    assertEquals(" b", l.peek());
    assertEquals(" b", l.next());
    assertFalse(l.hasNext());
  }
  


}
