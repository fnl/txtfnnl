package txtfnnl.uima.pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;

public class TestRegExLexer {
  @Test
  public final void testSetup() {
    RegExLexer l = new RegExLexer("expression");
    assertNotNull(l);
  }

  @Test
  public final void testIterator() {
    Iterator<String> it = (new RegExLexer("expression")).iterator();
    assertNotNull(it);
  }

  @Test
  public final void testBasicIteration() {
    RegExLexer l = new RegExLexer("a  b");
    assertEquals(0, l.offset());
    assertTrue(l.hasNext());
    assertEquals("a", l.peek());
    assertEquals(0, l.offset());
    assertEquals("a", l.next());
    assertEquals(3, l.offset());
    assertTrue(l.hasNext());
    assertEquals("b", l.peek());
    assertEquals(3, l.offset());
    assertEquals("b", l.next());
    assertEquals(4, l.offset());
    assertFalse(l.hasNext());
  }

  @Test
  public final void testEscapedWhitespace() {
    RegExLexer l = new RegExLexer("a\\ \\ \\ b");
    assertTrue(l.hasNext());
    assertEquals("a   b", l.peek());
    assertEquals("a   b", l.next());
    assertFalse(l.hasNext());
  }

  @Test
  public final void testEscapedEscape() {
    RegExLexer l = new RegExLexer("a\\\\ b");
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
    RegExLexer l = new RegExLexer("a\\\\\\ b");
    assertTrue(l.hasNext());
    assertEquals("a\\\\ b", l.peek());
    assertEquals("a\\\\ b", l.next());
    assertFalse(l.hasNext());
  }

  @Test
  public final void testEndWithWhitespace() {
    RegExLexer l = new RegExLexer("a\\  b");
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
    RegExLexer l = new RegExLexer("a \\ b");
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
