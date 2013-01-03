package txtfnnl.tika.sax;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts structured content from XML files following the Elsevier DTD 5 specification. This
 * handler adds in newlines where appropriate and removes consecutive white-spaces/newlines/tabs to
 * make the input more compact. Its inner workings are similar to the HTML parser's.
 * 
 * @author Florian Leitner
 */
public class ElsevierXMLContentHandler extends HTMLContentHandler {
  /* === CONFIGURATION === */
  /**
   * Block elements that should be followed by one newline. In addition, if the CDATA did not have
   * a line-break (followed by any number of space characters) just before opening the element, a
   * single newline is added before handling the next content.
   */
  @SuppressWarnings({ "serial", "hiding" })
  static final Set<String> ADD_LINEBREAK = Collections.unmodifiableSet(new HashSet<String>() {
    {
      add("cals:row");
      add("ce:affiliation");
      add("ce:author");
      add("ce:bib-reference");
      add("ce:caption");
      add("ce:collaboration");
      add("ce:correspondence");
      add("ce:def-description");
      add("ce:def-term");
      add("ce:doctopic");
      add("ce:further-reading-sec");
      add("ce:glossary-entry");
      add("ce:index-entry");
      add("ce:keyword");
      add("ce:label");
      add("ce:list-item");
      add("ce:note-para");
      // add("ce:refers-to-document");
      add("ce:salutation");
      add("ce:simple-para");
      add("rdf:li");
      add("sb:author");
      add("sb:comment");
      add("sb:maintitle");
      add("sb:subtitle");
      add("sb:translated-title");
    }
  });
  /**
   * Block elements that should be followed by two newlines. In addition, if the CDATA did not have
   * a line-break (followed by any number of space characters) just before opening the element, a
   * single newline is added before handling the next content.
   */
  @SuppressWarnings({ "serial", "hiding" })
  static final Set<String> ADD_TWO_LINEBREAKS = Collections.unmodifiableSet(new HashSet<String>() {
    {
      add("cals:tgroup");
      add("cals:tbody");
      add("ce:abstract");
      add("ce:abstract-sec");
      add("ce:acknowledgment");
      add("ce:alt-subtitle");
      add("ce:alt-title");
      add("ce:appendices");
      add("ce:article-footnote");
      add("ce:author-group");
      add("ce:bibliography");
      add("ce:bibliography-sec");
      add("ce:biography");
      add("ce:caption");
      add("ce:copyright");
      add("ce:copyright-line");
      add("ce:dedication");
      add("ce:def-list");
      add("ce:display");
      add("ce:displayed-quote");
      add("ce:dochead");
      add("ce:doctopics");
      add("ce:document-thread");
      add("ce:e-component");
      add("ce:editors");
      add("ce:enunciation");
      add("ce:exam-answers");
      add("ce:exam-questions");
      add("ce:figure");
      add("ce:footnote");
      add("ce:further-reading");
      add("ce:glossary");
      add("ce:glossary-sec");
      add("ce:index");
      add("ce:index-sec");
      add("ce:intro");
      add("ce:keywords");
      add("ce:lengend");
      add("ce:list");
      add("ce:miscellaneous");
      add("ce:nomenclature");
      add("ce:note");
      add("ce:other-ref");
      add("ce:para");
      add("ce:presented");
      add("ce:section");
      add("ce:sections");
      add("ce:section-title");
      add("ce:subtitle");
      add("ce:table");
      add("ce:table-footnote");
      add("ce:textbox");
      add("ce:textbox-body");
      add("ce:textbox-head");
      add("ce:textbox-tail");
      add("ce:title");
      add("dc:format");
      add("dc:title");
      add("dc:creator");
      add("dc:publisher");
      add("dp:document-properties");
      add("prism:aggregationType");
      add("prism:publicationName");
      add("prism:copyright");
      add("rdf:RDF");
      add("ja:article");
      add("ja:body");
      add("ja:head");
      add("ja:item-info");
      add("ja:tail");
      add("sb:authors");
      add("sb:reference");
      add("sb:title");
    }
  });

  /**
   * @param handler that is being decorated
   * @see org.apache.tika.sax.ContentHandlerDecorator#ContentHandlerDecorator(ContentHandler)
   */
  public ElsevierXMLContentHandler(ContentHandler handler) {
    super(handler);
    inTitle = false;
  }

  /* === PUBLIC API === */
  /**
   * Insert alt, label and title attributes in the character stream, replacing alt values of img
   * tags that represent a Greek character name with the actual (Greek) character. Special case
   * handling for TITLE and BR elements: Ignore TITLE elements and their character content and
   * force adding a newline on BR elements.
   * 
   * @see org.xml.sax.ContentHandler#startElement(String, String, String, Attributes)
   */
  @Override
  public void startElement(String uri, String localName, String name, Attributes atts)
      throws SAXException {
    // Special cases:
    if ("ce:br".equals(name) || "ce:vsp".equals(name)) {
      // - force a newline character whenever a BR or VSP occurs
      addCharacters(new char[] { '\n' });
      return;
    }
    super.startElement(uri, null, name, atts);
    // Redirect glyph attribute values to characters()
    if ("ce:glyph".equals(name) && atts.getLength() > 0) {
      handleAttributes(name, atts);
    }
    // Transition the parser state
    if (ADD_TWO_LINEBREAKS.contains(name)) {
      setNewlineState();
    } else if (ADD_LINEBREAK.contains(name)) {
      setNewlineState();
    } else {
      setWhitespaceState();
    }
  }

  /**
   * Detect and handle attribute values that should be processed when a new element starts.
   * Extracts values from name attributes.
   */
  private void handleAttributes(String elementName, Attributes atts) throws SAXException {
    for (final String key : new String[] { "name" }) {
      String value = atts.getValue(key);
      if (value != null) {
        value = value.trim();
        if (value.length() > 0) {
          setWhitespaceState();
          characters(value.toCharArray(), 0, value.length());
        }
      }
    }
  }

  /**
   * Transition to double newline or newline states for block elements, and to whitespace states
   * otherwise. Special case handling for TITLE and BR elements: ignore them.
   * 
   * @see org.xml.sax.ContentHandler#endElement(String, String, String)
   */
  @Override
  public void endElement(String uri, String localName, String name) throws SAXException {
    if (!"ce:br".equals(name)) {
      super.endElement(uri, null, name);
      // Transition the parser state
      if (ADD_TWO_LINEBREAKS.contains(name)) {
        setDoubleNewlineState();
      } else if (ADD_LINEBREAK.contains(name)) {
        setNewlineState();
      } else {
        setWhitespaceState();
      }
    }
  }
}
