package txtfnnl.tika.sax;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.easymock.EasyMock;

/**
 * @author Florian Leitner
 */
public class TestHTMLContentHandler {
  static Attributes dummyAtts = new AttributesImpl();
  static char[] content = "content".toCharArray();
  HTMLContentHandler handler;
  ContentHandler mock;

  /**
   * Set up the test environment by attaching a mocked content handler to the HTML handler.
   * 
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() {
    mock = EasyMock.createMock(ContentHandler.class);
    handler = new HTMLContentHandler(mock);
    Assert.assertFalse(handler.hadContent());
  }

  /**
   * Helper method to record a newline handling event on the mocked handler.
   * 
   * @throws SAXException
   */
  void recordNewline() throws SAXException {
    mock.characters(EasyMock.aryEq(new char[] { '\n' }), EasyMock.eq(0), EasyMock.eq(1));
  }

  /**
   * Helper method to record a double newline handling event on the mocked handler.
   * 
   * @throws SAXException
   */
  void recordDoubleNewline() throws SAXException {
    mock.characters(EasyMock.aryEq(new char[] { '\n', '\n' }), EasyMock.eq(0), EasyMock.eq(2));
  }

  /**
   * Helper method to record a whitespace handling event on the mocked handler.
   * 
   * @throws SAXException
   */
  void recordSpace() throws SAXException {
    mock.characters(EasyMock.aryEq(new char[] { ' ' }), EasyMock.eq(0), EasyMock.eq(1));
  }

  /**
   * Ensure the test setup works as expected.
   * 
   * @throws SAXException
   */
  @Test
  public void testSetUp() throws SAXException {
    mock.startDocument();
    mock.startElement("uri", "localName", "qName", dummyAtts);
    mock.characters(content, 0, content.length);
    mock.endElement("uri", "localName", "qName");
    EasyMock.replay(mock);
    handler.startDocument();
    handler.startElement("uri", "localName", "qName", dummyAtts);
    handler.characters(content, 0, content.length);
    handler.endElement("uri", "localName", "qName");
    EasyMock.verify(mock);
  }

  //
  // public void characters(char[] ch, int start, int length)
  // throws SAXException
  //
  /**
   * Test basic character handling capabilities.
   * 
   * @throws SAXException
   */
  @Test
  public void testCharacters() throws SAXException {
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
    Assert.assertTrue(handler.hadContent());
  }

  /**
   * Ensure that no characters are handled when only spaces are found.
   * 
   * @throws SAXException
   */
  @Test
  public void testAllWhitesapces() throws SAXException {
    final char[] ch = "\t\n\r\f ".toCharArray();
    EasyMock.replay(mock);
    handler.characters(ch, 0, ch.length);
    EasyMock.verify(mock);
    Assert.assertArrayEquals("\t\n\r\f ".toCharArray(), ch);
    Assert.assertFalse(handler.hadContent());
  }

  /**
   * Ensure handling of characters that contain non-space characters at the end.
   * 
   * @throws SAXException
   */
  @Test
  public void testWhitesapcesWithCharAtEnd() throws SAXException {
    final char[] ch = " x".toCharArray();
    mock.characters(ch, 1, 1);
    recordSpace();
    mock.characters(ch, 1, 1);
    doCharWhitespaceTest(ch);
  }

  /**
   * Ensure handling of characters that contain non-space characters in the middle.
   * 
   * @throws SAXException
   */
  @Test
  public void testWhitesapcesWithCharInMiddle() throws SAXException {
    final char[] ch = " x ".toCharArray();
    mock.characters(ch, 1, 1);
    recordSpace();
    mock.characters(ch, 1, 1);
    doCharWhitespaceTest(ch);
  }

  /**
   * Ensure handling of characters that contain non-space characters at the start.
   * 
   * @throws SAXException
   */
  @Test
  public void testWhitesapcesWithCharAtStart() throws SAXException {
    final char[] ch = "x ".toCharArray();
    mock.characters(ch, 0, 1);
    recordSpace();
    mock.characters(ch, 0, 1);
    doCharWhitespaceTest(ch);
  }

  /**
   * Ensure handling of characters with mixed space and non-space characters.
   * 
   * @throws SAXException
   */
  @Test
  public void testMixedCharWhitesapces() throws SAXException {
    final char[] ch = " x x ".toCharArray();
    mock.characters(ch, 1, 1);
    recordSpace();
    mock.characters(ch, 3, 1);
    recordSpace();
    mock.characters(ch, 1, 1);
    recordSpace();
    mock.characters(ch, 3, 1);
    doCharWhitespaceTest(ch);
  }

  void doCharWhitespaceTest(char[] ch) throws SAXException {
    EasyMock.replay(mock);
    handler.characters(ch, 0, ch.length);
    handler.characters(ch, 0, ch.length);
    EasyMock.verify(mock);
    Assert.assertTrue(handler.hadContent());
  }

  /**
   * Ensure that a newline is added if in content context and newline state.
   * 
   * @throws SAXException
   */
  @Test
  public void testAddNewline() throws SAXException {
    recordNewline();
    handler.characters(content, 0, content.length); // activate content ctx
    handler.setNewlineState();
    doStateDependentSpaceTest();
  }

  /**
   * Ensure that a double newline is added if in content context and double newline state.
   * 
   * @throws SAXException
   */
  @Test
  public void testAddDoubleNewline() throws SAXException {
    recordDoubleNewline();
    handler.characters(content, 0, content.length); // activate content ctx
    handler.setDoubleNewlineState();
    doStateDependentSpaceTest();
  }

  /**
   * Ensure a space is added if in content context and whitespace state.
   * 
   * @throws SAXException
   */
  @Test
  public void testAddSpace() throws SAXException {
    recordSpace();
    handler.characters(content, 0, content.length); // activate content ctx
    handler.setWhitespaceState();
    doStateDependentSpaceTest();
  }

  void doStateDependentSpaceTest() throws SAXException {
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
    Assert.assertTrue(handler.hadContent());
  }

  /**
   * Ensure a newline is not added without content context.
   * 
   * @throws SAXException
   */
  @Test
  public void testDoNotAddNewlineWithoutContent() throws SAXException {
    handler.setNewlineState();
    doNegativeStateDependentSpaceTest();
  }

  /**
   * Ensure a double newline is not added without content context.
   * 
   * @throws SAXException
   */
  @Test
  public void testDoNotAddDoubleNewlineWithoutContent() throws SAXException {
    handler.setDoubleNewlineState();
    doNegativeStateDependentSpaceTest();
  }

  /**
   * Ensure a space is not added without content context.
   * 
   * @throws SAXException
   */
  @Test
  public void testDoNotAddSpaceWithoutContent() throws SAXException {
    handler.setWhitespaceState();
    doNegativeStateDependentSpaceTest();
  }

  /**
   * Ensure a space is not added if not in whitespace state.
   * 
   * @throws SAXException
   */
  @Test
  public void testAddSpaceOnlyIfNecessary() throws SAXException {
    doNegativeStateDependentSpaceTest();
  }

  void doNegativeStateDependentSpaceTest() throws SAXException {
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
    Assert.assertTrue(handler.hadContent());
  }

  //
  // public void startElement(String uri, String localName, String name,
  // Attributes atts) throws SAXException
  //
  /**
   * Ensure a newline is added after an element that requires a newline is added is started, even
   * if it is followed by any other element.
   * 
   * @throws SAXException
   */
  @Test
  public void testStartElementAddNewline() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.startElement("uri", "div", "qName", dummyAtts);
    mock.startElement("uri", "anything", "qName", dummyAtts);
    recordNewline();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "div", "qName", dummyAtts);
    handler.startElement("uri", "anything", "qName", dummyAtts);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }

  /**
   * Ensure a newline is added after an element that requires a newline is added is started, even
   * if it is preceded by any other element.
   * 
   * @throws SAXException
   */
  @Test
  public void testStartElementAddNewlinePreferred() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.startElement("uri", "anything", "qName", dummyAtts);
    mock.startElement("uri", "div", "qName", dummyAtts);
    recordNewline();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "anything", "qName", dummyAtts);
    handler.startElement("uri", "div", "qName", dummyAtts);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }

  /**
   * Ensure that a white-space is added when starting an element.
   * 
   * @throws SAXException
   */
  @Test
  public void testStartElementAddSpace() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.startElement("uri", "anything", "qName", dummyAtts);
    recordSpace();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "anything", "qName", dummyAtts);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }

  /**
   * Ensure that a white-space is added when inserting alt attribute values.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertAltSpaced() throws SAXException {
    dotestInsertAttributeSpaced("alt", "area");
  }

  /**
   * Ensure that a white-space is added when inserting title attribute values.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertTitleSpaced() throws SAXException {
    dotestInsertAttributeSpaced("title", "anything");
  }

  /**
   * Ensure that a white-space is added when inserting label attribute values.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertLabelSpaced() throws SAXException {
    dotestInsertAttributeSpaced("label", "command");
  }

  void dotestInsertAttributeSpaced(String attName, String tagName) throws SAXException {
    final String value = "value";
    final char[] ch = "testing".toCharArray();
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", attName, "CDATA", value);
    mock.startElement("uri", tagName, "qName", atts);
    mock.characters(EasyMock.aryEq(value.toCharArray()), EasyMock.eq(0),
        EasyMock.eq(value.length()));
    recordSpace();
    mock.characters(ch, 0, ch.length);
    EasyMock.replay(mock);
    handler.startElement("uri", tagName, "qName", atts);
    handler.characters(ch, 0, ch.length);
    EasyMock.verify(mock);
  }

  /**
   * Ensure that the alt attribute values are followed by a newline when the element start requires
   * inserting newlines.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertAltNewlined() throws SAXException {
    dotestInsertAttributeNewlined("alt", "img");
  }

  /**
   * Ensure that the title attribute values are followed by a newline when the element start
   * requires inserting newlines.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertTitleNewlined() throws SAXException {
    dotestInsertAttributeNewlined("title", "span");
  }

  /**
   * Ensure that the label attribute values are followed by a newline when the element start
   * requires inserting newlines.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertLabelNewlined() throws SAXException {
    dotestInsertAttributeNewlined("label", "b");
  }

  void dotestInsertAttributeNewlined(String attName, String tagName) throws SAXException {
    final String value = "value";
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", attName, "CDATA", value);
    mock.characters(content, 0, content.length);
    mock.startElement("uri", "div", "qName", dummyAtts);
    mock.startElement("uri", tagName, "qName", atts);
    recordNewline();
    mock.characters(EasyMock.aryEq(value.toCharArray()), EasyMock.eq(0),
        EasyMock.eq(value.length()));
    recordSpace();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "div", "qName", dummyAtts);
    handler.startElement("uri", tagName, "qName", atts);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }

  /**
   * Test that Greek letter name alts in img tags are translated to acutal Greek letters.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertGreekAlt() throws SAXException {
    final String value = "ALPHA";
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", "alt", "CDATA", value);
    mock.startElement("uri", "img", "qName", atts);
    mock.characters(EasyMock.aryEq(new char[] { 'α' }), EasyMock.eq(0), EasyMock.eq(1));
    EasyMock.replay(mock);
    handler.startElement("uri", "img", "qName", atts);
    EasyMock.verify(mock);
  }

  /**
   * Test that capitalized Greek letter name alts in img tags are translated to actual upper-case
   * Greek letters.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertGreekUpperAlt() throws SAXException {
    final String value = "Omega";
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", "alt", "CDATA", value);
    mock.startElement("uri", "img", "qName", atts);
    mock.characters(EasyMock.aryEq(new char[] { 'Ω' }), EasyMock.eq(0), EasyMock.eq(1));
    EasyMock.replay(mock);
    handler.startElement("uri", "img", "qName", atts);
    EasyMock.verify(mock);
  }

  /**
   * Test that Greek letter name alts in img tags are not surrounded by spaces.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertGreekAltUnspaced() throws SAXException {
    final String value = "ALPHA";
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", "alt", "CDATA", value);
    mock.characters(content, 0, content.length);
    mock.startElement("uri", "img", "qName", atts);
    mock.characters(EasyMock.aryEq(new char[] { 'α' }), EasyMock.eq(0), EasyMock.eq(1));
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "img", "qName", atts);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }

  //
  // public void endElement(String uri, String localName, String name)
  // throws SAXException
  //
  /**
   * Ensure default behavior when ending elements.
   * 
   * @throws SAXException
   */
  @Test
  public void testEndElementDefault() throws SAXException {
    mock.endElement("uri", "localName", "qName");
    EasyMock.replay(mock);
    handler.endElement("uri", "localName", "qName");
    EasyMock.verify(mock);
  }

  /**
   * Ensure a newline is added when required by the element.
   * 
   * @throws SAXException
   */
  @Test
  public void testEndElementAddLinebreak() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.endElement("uri", "li", "qName");
    recordNewline();
    dotestEndElementState("li");
  }

  /**
   * Ensure a double newline is added when required by the element.
   * 
   * @throws SAXException
   */
  @Test
  public void testEndElementAddTwoLinebreaks() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.endElement("uri", "div", "qName");
    recordDoubleNewline();
    dotestEndElementState("div");
  }

  /**
   * Ensure a whitespace is added when required by the element.
   * 
   * @throws SAXException
   */
  @Test
  public void testEndElementAddWhitespace() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.endElement("uri", "span", "qName");
    recordSpace();
    dotestEndElementState("span");
  }

  void dotestEndElementState(String tagName) throws SAXException {
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.endElement("uri", tagName, "qName");
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }
}
