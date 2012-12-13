package txtfnnl.tika.sax;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.xml.sax.SAXException;

import org.apache.tika.sax.ToTextContentHandler;

/**
 * An extension that drops ignorable whitespaces from the text stream.
 * 
 * @author Florian Leitner
 */
public class ToCleanTextContentHandler extends ToTextContentHandler {
    /**
     * Creates a content handler that writes character events to the given writer.
     * 
     * @param writer writer
     */
    public ToCleanTextContentHandler(Writer writer) {
        super(writer);
    }

    /**
     * Creates a content handler that writes character events to the given output stream using the
     * platform default encoding.
     * 
     * @param stream output stream
     */
    public ToCleanTextContentHandler(OutputStream stream) {
        this(new OutputStreamWriter(stream));
    }

    /**
     * Creates a content handler that writes character events to the given output stream using the
     * given encoding.
     * 
     * @param stream output stream
     * @param encoding output encoding
     * @throws UnsupportedEncodingException if the encoding is unsupported
     */
    public ToCleanTextContentHandler(OutputStream stream, String encoding)
            throws UnsupportedEncodingException {
        this(new OutputStreamWriter(stream, encoding));
    }

    /**
     * Creates a content handler that writes character events to an internal string buffer. Use the
     * {@link #toString()} method to access the collected character content.
     */
    public ToCleanTextContentHandler() {
        this(new StringWriter());
    }

    /**
     * Drop ignorable whitespaces from the character stream, ie., do nothing.
     */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}
}
