/**
 * This package provides the necessary classes to extract text from files that
 * is more accessible for language processing than the text structure created
 * by the default Tika SAX handlers (for a few, chosen file types) and wraps
 * the Tika extractor into the UIMA framework.
 */
package txtfnnl.tika;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.sax.TeeContentHandler;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import txtfnnl.tika.parser.html.CleanHtmlMapper;
import txtfnnl.tika.parser.xml.UnembeddedXMLParser;
import txtfnnl.tika.sax.CleanBodyContentHandler;
import txtfnnl.tika.sax.CleanWriteOutContentHandler;
import txtfnnl.tika.sax.HTMLContentHandler;
import txtfnnl.tika.sax.XMLContentHandler;

/**
 * A wrapper for the {@link org.apache.tika.Tika} facade that re-packs some of
 * the functionality but changes the behavior of the underlying parsers and
 * content extraction. In addition, documents by default now can have up to 1M
 * characters (Tika's default is 100k).
 * 
 * The following formats have changed behavior:
 * <ul>
 * <li>application/xml</li>
 * <li>application/xhtml</li>
 * <li>text/xml</li>
 * <li>text/html</li>
 * <li></li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class TikaWrapper {

	/**
	 * The detector instance used by this wrapper.
	 */
	private Detector detector;

	/**
	 * The parser instance used by this wrapper.
	 */
	private Parser parser;

	/**
	 * Maximum length of the strings returned by the parseToString methods.
	 * Used to prevent out of memory problems with huge input documents. The
	 * default setting is 1M characters (ten times more than Tika's default
	 * and enough to parse a small-sized book).
	 */
	private int maxStringLength = 1000 * 1000;

	/**
	 * Creates a Tika wrapper using the given detector and parser instances.
	 * 
	 * @param detector type detector
	 * @param parser document parser
	 */
	public TikaWrapper(Detector detector, Parser parser) {
		this.detector = detector;
		this.parser = parser;
	}

	/**
	 * Creates a Tika wrapper using the given detector instance and the
	 * default parser configuration.
	 * 
	 * @param detector type detector
	 */
	public TikaWrapper(Detector detector) {
		this(detector, new AutoDetectParser(detector));
	}

	/**
	 * Creates a Tika wrapper using the given configuration.
	 * 
	 * @param config Tika configuration
	 */
	public TikaWrapper(TikaConfig config) {
		this(config.getDetector(), new AutoDetectParser(config));
	}

	/**
	 * Creates a Tika wrapper using the default Tika configuration.
	 */
	public TikaWrapper() {
		this(TikaConfig.getDefaultConfig());
	}

	/**
	 * Detects the media type of the given document. The type detection is
	 * based on the content of the given document stream and any given
	 * document metadata. The document stream can be <code>null</code>, in
	 * which case only the given document metadata is used for type detection.
	 * <p>
	 * If the document stream supports the {@link InputStream#markSupported()
	 * mark feature}, then the stream is marked and reset to the original
	 * position before this method returns. Only a limited number of bytes are
	 * read from the stream.
	 * <p>
	 * The given document stream is <em>not</em> closed by this method.
	 * <p>
	 * Unlike in the {@link #parse(InputStream, Metadata)} method, the given
	 * document metadata is <em>not</em> modified by this method.
	 * 
	 * @param stream the document stream, or <code>null</code>
	 * @param metadata document metadata
	 * @return detected media type
	 * @throws IOException if the stream can not be read
	 */
	MediaType detect(InputStream stream, Metadata metadata) throws IOException {
		if (stream == null || stream.markSupported()) {
			return detector.detect(stream, metadata);
		} else {
			return detector.detect(new BufferedInputStream(stream), metadata);
		}
	}

	/**
	 * Append a language profiler to a content handler, returning an updated
	 * (tee'd) content handler.
	 * 
	 * @return a new content handler
	 */
	public ContentHandler teeProfiler(ContentHandler handler,
	                                  ProfilingHandler profiler) {
		return new TeeContentHandler(handler, profiler);
	}

	/**
	 * Parse a content stream with the given handler and in the given context,
	 * using the provided metadata.
	 * 
	 * @param stream to parse
	 * @param handler to receive the SAX events
	 * @param metadata describing the stream
	 * @param context describing the parsing "environment"
	 * @throws IOException if any IO operation fails
	 * @throws TikaException if any extraction/parsing operation fails
	 */
	public void parse(InputStream stream, ContentHandler handler,
	                  Metadata metadata, ParseContext context)
	        throws IOException, TikaException {
		String mediaType = detect(stream, metadata).getBaseType().toString();
		Parser p = this.parser;
		context.set(HtmlMapper.class, CleanHtmlMapper.INSTANCE);

		if ("text/html".equals(mediaType) ||
		    mediaType.startsWith("application/xhtml")) {
			handler = new HTMLContentHandler(new CleanBodyContentHandler(
			    handler));
		} else if ("text/xml".equals(mediaType) ||
		           mediaType.startsWith("application/xml")) {
			handler = new XMLContentHandler(handler);
			p = new UnembeddedXMLParser();
		} else {
			handler = new CleanBodyContentHandler(handler);
		}

		try {
			p.parse(stream, handler, metadata, context);
		} catch (SAXException e) {
			throw new TikaException("SAX processing failure", e);
		} finally {
			stream.close();
		}
	}

	/**
	 * Creates a new context from scratch.
	 * 
	 * @see #parse(InputStream, ContentHandler, Metadata, ParseContext)
	 */
	public void parse(InputStream stream, ContentHandler handler,
	                  Metadata metadata) throws IOException, TikaException {
		ParseContext context = new ParseContext();
		context.set(Parser.class, parser);
		this.parse(stream, handler, metadata, context);
	}

	/**
	 * Creates a new metadata from scratch.
	 * 
	 * @see #parse(InputStream, ContentHandler, Metadata, ParseContext)
	 */
	public void parse(InputStream stream, ContentHandler handler,
	                  ParseContext context) throws IOException, TikaException {
		this.parse(stream, handler, new Metadata(), context);
	}

	/**
	 * Creates a new metadata from scratch.
	 * 
	 * @see #parse(InputStream, ContentHandler, Metadata)
	 */
	public void parse(InputStream stream, ContentHandler handler)
	        throws IOException, TikaException {
		this.parse(stream, handler, new Metadata());
	}

	/**
	 * Return a content handler that buffers the extracted content.
	 * 
	 * @see #parse(InputStream, ContentHandler, Metadata)
	 */
	public ContentHandler parse(InputStream stream, Metadata metadata)
	        throws IOException, TikaException {
		CleanWriteOutContentHandler handler = new CleanWriteOutContentHandler(
		    maxStringLength);
		this.parse(stream, handler, metadata);
		return handler;
	}

	/**
	 * Creates a new metadata object from scratch.
	 * 
	 * @see #parse(InputStream, Metadata)
	 */
	public ContentHandler parse(InputStream stream) throws IOException,
	        TikaException {
		return parse(stream, new Metadata());
	}

	/**
	 * Transforms the URL into an input stream and metadata.
	 * 
	 * @see #parse(InputStream, Metadata)
	 */
	public ContentHandler parse(URL url) throws IOException, TikaException {
		Metadata metadata = new Metadata();
		InputStream stream = TikaInputStream.get(url, metadata);
		return parse(stream, metadata);
	}

	/**
	 * Transforms the input file into an URL.
	 * 
	 * @see #parse(URL)
	 */
	public ContentHandler parse(File file) throws IOException, TikaException {
		return parse(file.toURI().toURL());
	}

	/**
	 * Extract the stream's content, described by some metadata, to a string.
	 * 
	 * @param stream to extract
	 * @param metadata describing the stream
	 * @return the extracted string content ("CDATA")
	 * @throws IOException if any IO operation fails
	 * @throws TikaException if any parsing/extraction operation fails
	 * 
	 * @see #parse(InputStream, ContentHandler, Metadata)
	 */
	public String parseToString(InputStream stream, Metadata metadata)
	        throws IOException, TikaException {
		CleanWriteOutContentHandler handler = new CleanWriteOutContentHandler(
		    maxStringLength);
		this.parse(stream, handler, metadata);
		return handler.toString();
	}

	/**
	 * Creates a new metadata from scratch.
	 * 
	 * @see #parseToString(InputStream, Metadata)
	 */
	public String parseToString(InputStream stream) throws IOException,
	        TikaException {
		return parseToString(stream, new Metadata());
	}

	/**
	 * Creates a stream and metadata from the URL.
	 * 
	 * @see #parseToString(InputStream, Metadata)
	 */
	public String parseToString(URL url) throws IOException, TikaException {
		Metadata metadata = new Metadata();
		InputStream stream = TikaInputStream.get(url, metadata);
		return parseToString(stream, metadata);
	}

	/**
	 * Creates an URL from the file.
	 * 
	 * @see #parseToString(URL)
	 */
	public String parseToString(File file) throws IOException, TikaException {
		return parseToString(file.toURI().toURL());
	}

	/**
	 * Returns the maximum length of strings returned by the parseToString
	 * methods.
	 * 
	 * @return maximum string length, or -1 if the limit has been disabled
	 */
	public int getMaxStringLength() {
		return maxStringLength;
	}

	/**
	 * Sets the maximum length of strings returned by the parseToString
	 * methods.
	 * 
	 * @param maxStringLength maximum string length, or -1 to disable this
	 *        limit
	 */
	public void setMaxStringLength(int maxStringLength) {
		this.maxStringLength = maxStringLength;
	}

	/**
	 * Returns the parser instance used by this wrapper.
	 * 
	 * @return parser instance
	 */
	public Parser getParser() {
		return parser;
	}

	/**
	 * Returns the detector instance used by this wrapper.
	 * 
	 * @return detector instance
	 */
	public Detector getDetector() {
		return detector;
	}

	// public static void main(String[] argv) throws IOException,
	// TikaException {
	// TikaWrapper wrapper = new TikaWrapper();
	//
	// while (true)
	// wrapper
	// .parseToString(new File("src/test/resources/21811562.html"));
	// }

}
