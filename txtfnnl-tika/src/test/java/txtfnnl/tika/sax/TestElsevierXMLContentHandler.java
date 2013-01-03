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
public class TestElsevierXMLContentHandler {
  static Attributes dummyAtts = new AttributesImpl();
  static char[] content = "content".toCharArray();
  ElsevierXMLContentHandler handler;
  ContentHandler mock;

  /**
   * Set up the test environment by attaching a mocked content handler to the HTML handler.
   * 
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() {
    mock = EasyMock.createMock(ContentHandler.class);
    handler = new ElsevierXMLContentHandler(mock);
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
    mock.startElement("uri", null, "qName", dummyAtts);
    mock.characters(content, 0, content.length);
    mock.endElement("uri", null, "qName");
    EasyMock.replay(mock);
    handler.startDocument();
    handler.startElement("uri", "localName", "qName", dummyAtts);
    handler.characters(content, 0, content.length);
    handler.endElement("uri", "localName", "qName");
    EasyMock.verify(mock);
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
    mock.startElement("uri", null, "ce:para", dummyAtts);
    mock.startElement("uri", null, "qName", dummyAtts);
    recordNewline();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "para", "ce:para", dummyAtts);
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
    mock.startElement("uri", null, "qName", dummyAtts);
    mock.startElement("uri", null, "ce:para", dummyAtts);
    recordNewline();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "anything", "qName", dummyAtts);
    handler.startElement("uri", "para", "ce:para", dummyAtts);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }

  /**
   * Ensure that a white-space is added when inserting alt attribute values.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertGlyphSpaced() throws SAXException {
    dotestInsertAttributeSpaced("name", "ce:glyph");
  }

  void dotestInsertAttributeSpaced(String attName, String tagName) throws SAXException {
    final String value = "value";
    final char[] ch = "testing".toCharArray();
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", attName, "CDATA", value);
    mock.startElement("uri", null, tagName, atts);
    mock.characters(EasyMock.aryEq(value.toCharArray()), EasyMock.eq(0),
        EasyMock.eq(value.length()));
    recordSpace();
    mock.characters(ch, 0, ch.length);
    EasyMock.replay(mock);
    handler.startElement("uri", "lName", tagName, atts);
    handler.characters(ch, 0, ch.length);
    EasyMock.verify(mock);
  }

  /**
   * Test that attribute name content in glyph tags are surrounded by spaces.
   * 
   * @throws SAXException
   */
  @Test
  public void testInsertAttributeSpaced() throws SAXException {
    final String value = "content";
    final AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("uri", "name", "name", "CDATA", value);
    mock.characters(content, 0, content.length);
    recordSpace();
    mock.startElement("uri", null, "ce:glyph", atts);
    mock.characters(EasyMock.aryEq(content), EasyMock.eq(0), EasyMock.eq(7));
    recordSpace();
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.startElement("uri", "glyph", "ce:glyph", atts);
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
    mock.endElement("uri", null, "qName");
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
    mock.endElement("uri", null, "ce:list-item");
    recordNewline();
    dotestEndElementState("ce:list-item");
  }

  /**
   * Ensure a double newline is added when required by the element.
   * 
   * @throws SAXException
   */
  @Test
  public void testEndElementAddTwoLinebreaks() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.endElement("uri", null, "ce:para");
    recordDoubleNewline();
    dotestEndElementState("ce:para");
  }

  /**
   * Ensure a whitespace is added when required by the element.
   * 
   * @throws SAXException
   */
  @Test
  public void testEndElementAddWhitespace() throws SAXException {
    mock.characters(content, 0, content.length);
    mock.endElement("uri", null, "qName");
    recordSpace();
    dotestEndElementState("qName");
  }

  void dotestEndElementState(String tagName) throws SAXException {
    mock.characters(content, 0, content.length);
    EasyMock.replay(mock);
    handler.characters(content, 0, content.length);
    handler.endElement("uri", null, tagName);
    handler.characters(content, 0, content.length);
    EasyMock.verify(mock);
  }
}
