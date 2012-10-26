/**
 * A Tika-based AE for (plain) text extraction from "binary" SOFAs.
 */
package txtfnnl.tika.uima;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import txtfnnl.tika.TikaWrapper;
import txtfnnl.tika.parser.html.CleanHtmlMapper;
import txtfnnl.tika.parser.xml.UnembeddedXMLParser;
import txtfnnl.tika.sax.CleanBodyContentHandler;
import txtfnnl.tika.sax.ElsevierXMLContentHandler;
import txtfnnl.tika.sax.GreekLetterContentHandler;
import txtfnnl.tika.sax.HTMLContentHandler;
import txtfnnl.tika.sax.XMLContentHandler;
import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.DocumentAnnotation;

/**
 * An abstract AE that uses Tika to extract text content from an "raw content"
 * view of the SOFA, placing this plain-text in a new "text" output view of
 * the CAS.
 * 
 * The plain text will contain no "ignorable whitespaces" (see
 * {@link txtfnnl.tika.sax.ToCleanTextContentHandler} ) and may have all Greek
 * characters replaced with the character name spelled out using Latin (ASCII)
 * letters (see {@link txtfnnl.tika.sax.GreekLetterContentHandler} and
 * {@link PARAM_NORMALIZE_GREEK_CHARACTERS}).
 * 
 * Sometimes the Tika encoding detection algorithm predicts the wrong encoding
 * of the content, therefore it is possible to force the encoding as a
 * Metadata element of the Tika setup (see {@link PARAM_ENCODING}).
 * 
 * If the input stream MIME type is <code>text/html</code> or starts with
 * <code>application/xhtml</code>, the
 * {@link txtfnnl.tika.sax.HTMLContentHandler} is used; if it is
 * <code>text/xml</code> or starts with <code>application/xml</code>, the
 * {@link txtfnnl.tika.sax.XMLContentHandler} will be used.
 * 
 * If Tika detects {@link org.apache.tika.metadata.Metadata}, it is added as
 * {@link txtfnnl.uima.tcas.DocumentAnnotation} to the output view.
 * 
 * @author Florian Leitner
 */
public abstract class AbstractTikaAnnotator extends JCasAnnotator_ImplBase {

	/** Set the content encoding metadata value. */
	public static final String PARAM_ENCODING = "Encoding";

	/**
	 * Replace Greek characters with their names spelled out using Latin
	 * (ASCII) letters. Note that this will normalize all "sharp S" ('ß' or
	 * U+00DF) letters to "beta", too.
	 */
	public static final String PARAM_NORMALIZE_GREEK_CHARACTERS = "NormalizeGreek";

	/**
	 * Use the Elsevier-specific XML handler instead of the default handler.
	 */
	public static final String PARAM_USE_ELSEVIER_XML_HANDLER = "UseElsevierXMLHandler";

	/** The encoding to force (if any). */
	String encoding;

	/** Normalize Greek letters to Latin words. */
	boolean normalizeGreek = false;

	/** For XML, use the Elsevier-specific handler. */
	boolean useElsevierXMLHandler = false;

	/** A logger for this AE. */
	Logger logger;

	/** The Tika API wrapper for this AE. */
	TikaWrapper tika = new TikaWrapper();

	/** The input view name/SOFA expected by this AE. */
	private String inputView = Views.CONTENT_RAW.toString();

	/** The output view name/SOFA produced by this AE. */
	private String outputView = Views.CONTENT_TEXT.toString();

	@Override
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();
		encoding = (String) ctx.getConfigParameterValue(PARAM_ENCODING);

		Boolean val = (Boolean) ctx
		    .getConfigParameterValue(PARAM_NORMALIZE_GREEK_CHARACTERS);

		if (val != null && val)
			normalizeGreek = true;

		val = (Boolean) ctx
		    .getConfigParameterValue(PARAM_USE_ELSEVIER_XML_HANDLER);

		if (val != null && val)
			useElsevierXMLHandler = true;
	}

	/**
	 * The AE process expects a SOFA with a {@link View.CONTENT_RAW} and uses
	 * Tika to produce a new plain-text SOFA with a {@link View.CONTENT_TEXT}.
	 * It preserves all metadata annotations on the raw content that Tika was
	 * able to detect.
	 */
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		JCas newJCas;

		try {
			aJCas = aJCas.getView(inputView);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		InputStream stream = aJCas.getSofaDataStream();

		if (stream == null) {
			logger.log(Level.SEVERE,
			    "no data stream for view '" + aJCas.getViewName() + "'");
			throw new AnalysisEngineProcessException(new AssertionError(
			    "no SOFA data stream"));
		} else {
			logger.log(Level.INFO, "parsing " + aJCas.getSofaMimeType() +
			                       " at " + aJCas.getSofaDataURI());
		}

		Metadata metadata = new Metadata();

		if (aJCas.getSofaMimeType() != null)
			metadata.set(Metadata.CONTENT_TYPE, aJCas.getSofaMimeType());

		if (encoding != null)
			metadata.set(Metadata.CONTENT_ENCODING, encoding);

		if (aJCas.getSofaDataURI() != null) {
			try {
				metadata.set(Metadata.RESOURCE_NAME_KEY, resourceName(new URI(
				    aJCas.getSofaDataURI())));
			} catch (URISyntaxException e) {
				logger.log(Level.WARNING, "URI '" + aJCas.getSofaDataURI() +
				                          "' not a valid URI");
			} catch (MalformedURLException e) {
				logger.log(Level.WARNING, "URI '" + aJCas.getSofaDataURI() +
				                          "' not a valid URL");
			}
		}

		try {
			newJCas = aJCas.createView(outputView);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}


		ContentHandler handler = getContentHandler(newJCas);
		
		if (normalizeGreek)
			handler = new GreekLetterContentHandler(handler);

		Detector detector = TikaConfig.getDefaultConfig().getDetector();
		ParseContext context = new ParseContext();
		String mediaType = metadata.get(Metadata.CONTENT_TYPE);
		Parser parser;

		try {
			if (mediaType == null)
				mediaType = detector.detect(stream, metadata).getBaseType().toString();

			if ("text/html".equals(mediaType) ||
			    mediaType.startsWith("application/xhtml")) {
				context.set(HtmlMapper.class, CleanHtmlMapper.INSTANCE);
				handler = new HTMLContentHandler(new CleanBodyContentHandler(
				    handler));
				parser = new HtmlParser();
			} else if ("text/xml".equals(mediaType) ||
			           mediaType.startsWith("application/xml")) {
				if (useElsevierXMLHandler)
					handler = new ElsevierXMLContentHandler(handler);
				else
					handler = new XMLContentHandler(handler);
				
				parser = new UnembeddedXMLParser();
			} else {
				handler = new CleanBodyContentHandler(handler);
				parser = new AutoDetectParser(detector);
			}
			
			context.set(Parser.class, parser);
			
			try {
				parser.parse(stream, handler, metadata, context);
			} catch (SAXException e) {
				throw new TikaException("SAX processing failure", e);
			} finally {
				stream.close();
			}

		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (TikaException e) {
			throw new AnalysisEngineProcessException(e);
		}

		handleMetadata(metadata, newJCas);
		newJCas.setDocumentLanguage(aJCas.getDocumentLanguage());
	}

	/** Add Tika Metadata as DocumentAnnotations to the ouptut JCas. */
	void handleMetadata(Metadata metadata, JCas outputJCas) {
		int num_names = metadata.size();

		if (num_names > 0) {
			for (String name : metadata.names()) {
				for (String val : metadata.getValues(name)) {
					DocumentAnnotation da = new DocumentAnnotation(outputJCas);
					da.setNamespace(name);
					da.setIdentifier(val);
					da.setAnnotator(getAnnotatorURI());
					da.setConfidence(1.0);
					da.addToIndexes();
				}
			}
		}
	}

	private String resourceName(URI uri) throws MalformedURLException {
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			File file = new File(uri);
			if (file.isFile()) {
				return resourceName(file);
			}
		}

		return resourceName(uri.toURL());
	}

	private String resourceName(File file) {
		return file.getName();
	}

	private String resourceName(URL url) {
		String path = url.getPath();
		int slash = path.lastIndexOf('/');
		return path.substring(slash + 1);
	}

	abstract ContentHandler getContentHandler(JCas newJCas);

	abstract String getAnnotatorURI();

}
