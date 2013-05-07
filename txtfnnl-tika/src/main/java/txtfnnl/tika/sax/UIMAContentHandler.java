/**
 * A content handler that bridges SAX, Tika, and UIMA.
 */
package txtfnnl.tika.sax;

import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;

import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.uima.UIMAFramework;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.StructureAnnotation;

/**
 * An event handler that specifically "works" with the UIMA AE philosophy.
 * <p>
 * This special SAX handler extracts content for UIMA. It automatically populates a CAS with the
 * extracted plaintext and annotates that text with the original structure of the content using
 * {@link txtfnnl.uima.tcas.StructureAnnotation} elements.
 * 
 * @author Florian Leitner
 */
public class UIMAContentHandler extends ContentHandlerDecorator {
  /** The CAS view that will be populated by the handler. */
  private JCas view = null;
  /** The characters received from the parser. */
  private StringBuffer textBuffer;
  /** The Annotator URI to use for the annotations. */
  private String annotatorURI;
  /** Started markup elements that are not yet ended (closed). */
  private Stack<StructureAnnotation> annotationStack;
  /** A logger for this handler. */
  private final Logger logger = UIMAFramework.getLogger(UIMAContentHandler.class);

  /**
   * Decorative content handler for the given handler. Needs to have a view assigned first
   * (setView(JCas)) and should have an annotator URI set (setAnnotatorURI(String)).
   * 
   * @param handler to decorate
   */
  public UIMAContentHandler(ContentHandler handler) {
    super(handler);
  }

  /**
   * Content handler that will populate the given CAS' view.
   * 
   * @param cas view to populate
   * @param annotatorURI to use for the annotations
   */
  public UIMAContentHandler(JCas cas, String annotatorURI) {
    super();
    setView(cas);
    setAnnotatorURI(annotatorURI);
  }

  /**
   * Decorative content handler for the given handler.
   * 
   * @param handler to decorate
   * @param cas view to populate
   * @param annotatorURI to use for the annotations
   */
  public UIMAContentHandler(ContentHandler handler, JCas cas, String annotatorURI) {
    super(handler);
    setView(cas);
    setAnnotatorURI(annotatorURI);
  }

  /**
   * Get the the CAS view being annotated by the handler.
   * 
   * @return a JCas with the handler's SOFA
   */
  public JCas getView() {
    return view;
  }

  /**
   * Set the CAS view the handler should populate with text and annotations.
   * 
   * @param cas SOFA view to populate
   */
  public void setView(JCas cas) {
    view = cas;
  }

  public void setAnnotatorURI(String uri) {
    annotatorURI = uri;
  }

  /**
   * Start a new document, initializing the handler's internal buffers.
   */
  @Override
  public void startDocument() {
    textBuffer = new StringBuffer();
    annotationStack = new Stack<StructureAnnotation>();
  }

  /**
   * Create a new Element starting at the current offset. The element offset is placed at the
   * currently extracted text by the Tika parser, and the method adds the relevant namespace,
   * identifier, annotator and property annotations. The confidence of the annotations will always
   * be 1.
   */
  @Override
  public void startElement(String uri, String lName, String qName, Attributes atts) {
    final int num_atts = (atts == null) ? 0 : atts.getLength();
    final String name = UIMAContentHandler.chooseName(lName, qName);;
    final StructureAnnotation ann = new StructureAnnotation(view);
    if (!(uri.endsWith("#") || uri.endsWith("/") || uri.endsWith("="))) {
      uri += "#";
    }
    ann.setBegin(textBuffer.length());
    ann.setNamespace(uri);
    ann.setIdentifier(name);
    ann.setAnnotator(annotatorURI);
    ann.setConfidence(1.0);
    if (num_atts > 0) {
      final FSArray attributes = new FSArray(view, num_atts);
      for (int idx = 0; idx < num_atts; ++idx) {
        final Property prop = new Property(view);
        final String pName = UIMAContentHandler.chooseName(atts.getLocalName(idx),
            atts.getQName(idx));
        prop.setName(pName);
        prop.setValue(atts.getValue(idx));
        attributes.set(idx, prop);
      }
      ann.setProperties(attributes);
    }
    annotationStack.push(ann);
  }

  /**
   * Store characters extracted by the Tika parser in a text buffer.
   */
  @Override
  public void characters(char ch[], int offset, int len) {
    if (len > 0) {
      textBuffer.append(ch, offset, len);
    }
  }

  /**
   * Set the end of the first matching, started element to offset in the text being extracted (into
   * a buffer) and add the finished annotation to the indexes of the CAS view.
   * 
   * @throws AssertionError if no matching, started element is found
   */
  @Override
  public void endElement(String uri, String lName, String qName) {
    StructureAnnotation ann = annotationStack.pop();
    final String name = UIMAContentHandler.chooseName(lName, qName);
    if (!(uri.endsWith("#") || uri.endsWith("/"))) {
      uri += "#";
    }
    if (!UIMAContentHandler.matchesElement(ann, uri, name)) {
      while (!annotationStack.empty()) {
        logger.log(Level.WARNING, "unmatched " + ann + " while looking for <{" + uri + "}" +
            lName + ": '" + qName + "'>");
        ann = annotationStack.pop();
        if (UIMAContentHandler.matchesElement(ann, uri, name)) {
          break;
        }
      }
      if (!UIMAContentHandler.matchesElement(ann, uri, name))
        throw new AssertionError("endElement: no matching Element");
    }
    ann.setEnd(textBuffer.length());
    ann.addToIndexes();
  }

  /**
   * End a document, setting the SOFA's document text from the text buffer.
   * 
   * @throws AssertionError if any unclosed elements remain
   */
  @Override
  public void endDocument() {
    if (!annotationStack.empty()) {
      while (!annotationStack.empty()) {
        logger.log(Level.SEVERE, "unconsumed " + annotationStack.pop());
      }
      throw new AssertionError("endDocument: unconsumed elements");
    }
    view.setSofaDataString(textBuffer.toString(), "text/plain");
  }

  /**
   * Return the first string (lName) if the second (qName) is <code>null</code> or if qName has
   * zero length and lName is not <code>null</code>, otherwise default to qName.
   * 
   * @param lName the first string
   * @param qName the second string
   * @return qName if defined and non-empty or if lName is undefined, lName otherwise
   */
  static private String chooseName(String lName, String qName) {
    if (qName == null || (qName.length() == 0 && lName != null)) return lName;
    return qName;
  }

  /**
   * Return true if the StructureAnnotation has the given URI and name.
   * 
   * @param e the annotation to be matched
   * @param uri the namespace the annotation should have
   * @param name the identifier the annotation should have
   * @return true if the strings match the ones set on the annotation
   */
  static private boolean matchesElement(StructureAnnotation e, String uri, String name) {
    return e.getNamespace().equals(uri) && e.getIdentifier().equals(name);
  }
}
