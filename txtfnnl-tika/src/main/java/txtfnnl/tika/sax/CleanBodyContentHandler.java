package txtfnnl.tika.sax;

import java.io.OutputStream;
import java.io.Writer;

import org.apache.tika.sax.BodyContentHandler;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Extends the BodyContentHandler to skip ignorable whitespaces.
 * 
 * @author Florian Leitner
 */
public class CleanBodyContentHandler extends BodyContentHandler {

	/**
     * Creates a content handler that passes all XHTML body events to the
     * given underlying content handler.
     *
     * @param handler content handler
     */
    public CleanBodyContentHandler(ContentHandler handler) {
        super(handler);
    }

	/**
     * Creates a content handler that writes XHTML body character events to
     * the given writer.
     *
     * @param writer writer
     */
    public CleanBodyContentHandler(Writer writer) {
        super(new CleanWriteOutContentHandler(writer));
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * the given output stream using the default encoding.
     *
     * @param stream output stream
     */
    public CleanBodyContentHandler(OutputStream stream) {
        super(new CleanWriteOutContentHandler(stream));
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * an internal string buffer. The contents of the buffer can be retrieved
     * using the {@link #toString()} method.
     * <p>
     * The internal string buffer is bounded at the given number of characters.
     * If this write limit is reached, then a {@link SAXException} is thrown.
     *
     * @param writeLimit maximum number of characters to include in the string,
     *                   or -1 to disable the write limit
     */
    public CleanBodyContentHandler(int writeLimit) {
        super(new CleanWriteOutContentHandler(writeLimit));
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * an internal string buffer. The contents of the buffer can be retrieved
     * using the {@link #toString()} method.
     * <p>
     * The internal string buffer is bounded at 100k characters. If this write
     * limit is reached, then a {@link SAXException} is thrown.
     */
    public CleanBodyContentHandler() {
        super(new CleanWriteOutContentHandler());
    }
    
}
