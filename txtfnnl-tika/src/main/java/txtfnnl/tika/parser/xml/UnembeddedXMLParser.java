package txtfnnl.tika.parser.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.TaggedContentHandler;

/**
 * A more "natural" implementation of an XML parser. Instead of "generating" HTML-like wrapper
 * events and then producing the PCDATA (only - this is the reason why the default XMLParser should
 * be called "embedded"), this parser produces the actual XML start and end document and tag events
 * (that get wrapped by Tika's own {@link org.apache.tika.parser.xml.XMLParser XMLParser}).
 * Furthermore, this parser semi-structures the element's PCDATA text by separating content from
 * different elements by linebreaks, indenting PCDATA content according to the current element's
 * depth, and drops any ("ignorable") character stretches consisting only of spaces.
 * 
 * @author Florian Leitner
 */
public class UnembeddedXMLParser extends AbstractParser {
    /** Serial version UID */
    private static final long serialVersionUID = -6028860725229212437L;
    /** Only support XML */
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
        .unmodifiableSet(new HashSet<MediaType>(Arrays.asList(MediaType.application("xml"))));

    /** {@inheritDoc} */
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Parse the input stream with a SAX parser. Wraps the content handler with an
     * {@link org.apache.tika.sax.OfflineContentHandler} to avoid that any namespace lookups are
     * made. In addition, by overriding
     * {@link #getContentHandler(ContentHandler, Metadata, ParseContext)}, it is possible to add
     * additional wrappers.
     * 
     * @param stream that should be parsed
     * @param handler that will receive the SAX events
     * @param metadata of current document stream
     * @param context of current parse
     * @throws IOException if the stream cannot be read
     * @throws SAXException if the SAX parsing fails.
     * @throws TikaException if the XML parsing fails.
     */
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context) throws IOException, SAXException, TikaException {
        final TaggedContentHandler tagged = new TaggedContentHandler(handler);
        if (metadata.get(HttpHeaders.CONTENT_TYPE) == null) {
            metadata.set(HttpHeaders.CONTENT_TYPE, "application/xml");
        }
        try {
            context.getSAXParser().parse(new CloseShieldInputStream(stream),
                new OfflineContentHandler(getContentHandler(tagged, metadata, context)));
        } catch (final SAXException e) {
            tagged.throwIfCauseOf(e);
            throw new TikaException("XML parse error", e);
        }
    }

    /**
     * Return the handler (ie., does nothing). This method can be overridden to add wrap the
     * content handler with additional handlers.
     * 
     * @param handler to wrap
     * @param metadata of current document
     * @param context of current parse
     * @return
     */
    protected ContentHandler getContentHandler(ContentHandler handler, Metadata metadata,
            ParseContext context) {
        return handler;
    }
}
