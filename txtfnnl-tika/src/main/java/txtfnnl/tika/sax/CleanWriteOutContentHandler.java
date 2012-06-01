package txtfnnl.tika.sax;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.SAXException;

/**
 * SAX event handler that writes content up to an optional write
 * limit out to a character stream or other decorated handler.
 * 
 * Contrary to the extended handler, ignorableWhitespaces are dropped
 * by the underlying string writer.
 * 
 * @author Florian Leitner
 */
public class CleanWriteOutContentHandler extends WriteOutContentHandler {
	
    /**
     * Creates a content handler that writes content up to the given
     * write limit to the given character stream.
     *
     * @param writer character stream
     * @param writeLimit write limit
     */
    public CleanWriteOutContentHandler(Writer writer, int writeLimit) {
        super(new ToCleanTextContentHandler(writer), writeLimit);
    }

    /**
     * Creates a content handler that writes character events to
     * the given writer.
     *
     * @param writer writer
     */
    public CleanWriteOutContentHandler(Writer writer) {
        this(writer, -1);
    }

    /**
     * Creates a content handler that writes character events to
     * the given output stream using the default encoding.
     *
     * @param stream output stream
     */
    public CleanWriteOutContentHandler(OutputStream stream) {
        this(new OutputStreamWriter(stream));
    }

    /**
     * Creates a content handler that writes character events
     * to an internal string buffer. Use the {@link #toString()}
     * method to access the collected character content.
     * <p>
     * The internal string buffer is bounded at the given number of characters.
     * If this write limit is reached, then a {@link SAXException} is thrown.
     * The {@link #isWriteLimitReached(Throwable)} method can be used to
     * detect this case.
     *
     * @param writeLimit maximum number of characters to include in the string,
     *                   or -1 to disable the write limit
     */
    public CleanWriteOutContentHandler(int writeLimit) {
        this(new StringWriter(), writeLimit);
    }

    /**
     * Creates a content handler that writes character events
     * to an internal string buffer. Use the {@link #toString()}
     * method to access the collected character content.
     * <p>
     * The internal string buffer is bounded at 1M characters. If this
     * write limit is reached, then a {@link SAXException} is thrown. The
     * {@link #isWriteLimitReached(Throwable)} method can be used to detect
     * this case.
     */
    public CleanWriteOutContentHandler() {
        this(1000 * 1000);
    }

}
