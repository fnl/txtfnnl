/**
 * A content handler that bridges SAX, Tika, and UIMA.
 */
package txtfnnl.tika.sax;

import org.xml.sax.ContentHandler;

import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.uima.jcas.JCas;

/**
 * An event handler that specifically "works" with the UIMA AE philosophy. This special SAX handler
 * is intended to be used by any {@link txtfnnl.tika.uima.AbstractTikaAnnotator} to extract content
 * for UIMA. It only populates a CAS with the extracted plain-text.
 * 
 * @author Florian Leitner
 */
public class SimpleUIMAContentHandler extends ContentHandlerDecorator {
    /** The CAS view that will be populated by the handler. */
    private JCas view = null;
    /** The characters received from the parser. */
    private StringBuffer textBuffer;

    /**
     * Content handler that will populate the given CAS' view.
     * 
     * @param cas view to populate
     */
    public SimpleUIMAContentHandler(JCas cas) {
        super();
        setView(cas);
    }

    /**
     * Decorative content handler for the given handler. Needs to have a view assigned first
     * (setView(JCas)).
     * 
     * @param handler to decorate
     */
    public SimpleUIMAContentHandler(ContentHandler handler) {
        super(handler);
    }

    /**
     * Decorative content handler for the given handler.
     * 
     * @param handler to decorate
     * @param cas view to populate
     */
    public SimpleUIMAContentHandler(ContentHandler handler, JCas cas) {
        super(handler);
        setView(cas);
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

    /**
     * Start a new document, initializing the handler's internal buffer.
     */
    @Override
    public void startDocument() {
        textBuffer = new StringBuffer();
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
     * End a document, setting the SOFA's document text from the text buffer.
     */
    @Override
    public void endDocument() {
        view.setSofaDataString(textBuffer.toString(), "text/plain");
    }
}
