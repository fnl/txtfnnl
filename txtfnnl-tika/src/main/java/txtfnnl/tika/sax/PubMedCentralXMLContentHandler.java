package txtfnnl.tika.sax;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Extracts structured content from XML files following the NLM's arcive DTD 3.0 specification. The
 * handler adds in newlines where appropriate and removes consecutive white-spaces/newlines/tabs to
 * make the input more compact. Its inner workings are similar to the HTML parser's.
 * 
 * @author Florian Leitner
 */
public class PubMedCentralXMLContentHandler extends HTMLContentHandler {
  /* === CONFIGURATION === */
  /**
   * Block elements that should be followed by one newline. In addition, if the CDATA did not have
   * a line-break (followed by any number of space characters) just before opening the element, a
   * single newline is added before handling the next content.
   */
  @SuppressWarnings({ "serial", "hiding" })
  static final Set<String> ADD_LINEBREAK = Collections.unmodifiableSet(new HashSet<String>() {
    {
      add("abbrev-journal-title");
      add("addr-line");
      add("aff"); // iliation
      add("alt-title");
      add("article-categories");
      add("article-id");
      add("article-title");
      add("compound-kwd");
      add("conference");
      add("country");
      add("custom-meta");
      add("def-head");
      add("def-item");
      add("disp-formula");
      add("disp-quote");
      add("fn"); // footnote
      add("institution");
      add("issn");
      add("isbn");
      add("journal-id");
      add("journal-subtitle");
      add("journal-title");
      add("kwd");
      add("product");
      add("pub-date");
      add("publisher");
      add("related-article");
      add("related-object");
      add("series-title");
      add("series-text");
      add("speech");
      add("subject");
      add("subtitle");
      add("term-head");
      add("tr");
      add("trans-subtitle");
      add("trans-title");
      add("unstructured-kwd-group");
      add("verse-line");
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
      add("abstract");
      add("ack"); // nowledgements
      add("address");
      add("app"); // endix
      add("app-group");
      add("array");
      add("article-meta");
      add("award-group");
      add("author-notes");
      add("back");
      add("bio"); // graphy
      add("body");
      add("boxed-text");
      add("caption");
      add("chem-struct-wrap");
      add("contrib-group");
      add("custom-meta-group");
      add("def-list");
      add("disp-formula-group");
      add("fig");
      add("fig-group");
      add("floats-group");
      add("fn-group");
      add("front");
      add("funding-group");
      add("glossary");
      add("graphic");
      add("history");
      add("journal-meta");
      add("journal-title-group");
      add("kwd-group");
      add("list");
      add("nlm-citation");
      add("notes");
      add("p");
      add("permissions");
      add("ref");
      add("ref-list");
      add("response");
      add("sec");
      add("sig-block");
      add("statement");
      add("sub-article");
      add("subj-group");
      add("supplementary-material");
      add("table");
      add("table-wrap");
      add("table-wrap-footer");
      add("table-wrap-group");
      add("title");
      add("title-group");
      add("trans-abstract");
      add("verse-group");
    }
  });

  /**
   * @param handler that is being decorated
   * @see org.apache.tika.sax.ContentHandlerDecorator#ContentHandlerDecorator(org.xml.sax.ContentHandler)
   */
  public
  PubMedCentralXMLContentHandler(ContentHandler handler) {
    super(handler);
    inTitle = false;
  }

  /* === PUBLIC API === */
  /**
   * Start element handler. Special case for handling for BREAK and HR elements: add a newline.
   *
   * @see org.xml.sax.ContentHandler#startElement(String, String, String, org.xml.sax.Attributes)
   */
  @Override
  public void startElement(String uri, String localName, String name, Attributes atts)
      throws SAXException {
    // Special cases:
    if ("break".equals(name) || "hr".equals(name)) {
      // - force a newline character whenever a BR or VSP occurs
      addCharacters(new char[] { '\n' });
      return;
    }
    super.startElement(uri, null, name, atts);
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
   * Transition to double newline or newline states for block elements, and to whitespace states
   * otherwise. Special case handling for BREAK and HR elements: ignore them.
   * 
   * @see org.xml.sax.ContentHandler#endElement(String, String, String)
   */
  @Override
  public void endElement(String uri, String localName, String name) throws SAXException {
    if (!"break".equals(name) || !"hr".equals(name)) {
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
