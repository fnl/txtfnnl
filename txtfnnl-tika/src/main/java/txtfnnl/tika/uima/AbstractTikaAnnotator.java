/**
 * A Tika-based AE for (plain) text extraction from "binary" SOFAs.
 */
package txtfnnl.tika.uima;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.text.View;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

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
 * An abstract AE that uses Tika to extract text content from an "raw" content view of the SOFA,
 * placing this plaintext in a new "text" output view of the CAS.
 * <p>
 * The plaintext will contain no "ignorable whitespaces" (see
 * {@link txtfnnl.tika.sax.ToCleanTextContentHandler ToCleanTextContentHandler}) and may have all
 * Greek characters replaced with the character name spelled out using Latin (ASCII) letters (see
 * {@link txtfnnl.tika.sax.GreekLetterContentHandler GreekLetterContentHandler} and
 * {@link PARAM_NORMALIZE_GREEK_CHARACTERS}).
 * <p>
 * Sometimes, the Tika encoding detection algorithm predicts the wrong encoding of the content,
 * therefore it is possible to force the encoding by pre-defining it as a {@link Metadata} element
 * of the Tika extraction setup (see {@link PARAM_ENCODING}). If the input stream MIME type is
 * <code>text/html</code> or starts with <code>application/xhtml</code>, the
 * {@link txtfnnl.tika.sax.HTMLContentHandler} is used; if it is <code>text/xml</code> or starts
 * with <code>application/xml</code>, the {@link txtfnnl.tika.sax.XMLContentHandler} will be used.
 * For XML, in addition, any other known content handler can be set using the
 * {@link #PARAM_XML_HANDLER} setting (see parameter documentation). For all other MIME types,
 * Tika's {@link AutoDetectParser} will be used. If Tika detects
 * {@link org.apache.tika.metadata.Metadata}, it is added as
 * {@link txtfnnl.uima.tcas.DocumentAnnotation} to the output view.
 * 
 * @author Florian Leitner
 */
public abstract class AbstractTikaAnnotator extends JCasAnnotator_ImplBase {
  /** The encoding to enforce (if any). */
  public static final String PARAM_ENCODING = "Encoding";
  @ConfigurationParameter(name = PARAM_ENCODING)
  private String encoding;
  /**
   * Optionally normalize Greek letters to Latin words. Replace Greek characters with their names
   * spelled out using Latin (ASCII) letters. Note that this will normalize all "sharp S" ('ß' or
   * U+00DF) letters to "beta", too.
   */
  public static final String PARAM_NORMALIZE_GREEK_CHARACTERS = "NormalizeGreek";
  @ConfigurationParameter(name = PARAM_NORMALIZE_GREEK_CHARACTERS, defaultValue = "false")
  private boolean normalizeGreek;
  /**
   * For XML, optionally use a specific, known handler. The available handlers (String values) are:
   * <ul>
   * <li>{@link txtfnnl.tika.sax.XMLContentHandler} introduces line-breaks at element boundaries,
   * the <b>default</b> handler</li>
   * <li>{@link txtfnnl.tika.sax.ElsevierXMLContentHandler} for XML in Elsevier's DTD format</li>
   * <li>{@link txtfnnl.tika.sax.CleanBodyContentHandler} only removes "ignorable whitespaces" and
   * uses the default Tika XML parser</li>
   * </ul>
   * Use the Elsevier-specific XML handler instead of the default handler.
   */
  public static final String PARAM_XML_HANDLER = "XMLHandlerClass";
  @ConfigurationParameter(name = PARAM_XML_HANDLER,
      defaultValue = "txtfnnl.tika.sax.XMLContentHandler")
  private String xmlHandlerClass;
  /** A logger for this AE. */
  Logger logger;
  /** The Tika API wrapper for this AE. */
  TikaWrapper tika = new TikaWrapper();
  /** The input view name/SOFA expected by this AE. */
  private static final String inputView = Views.CONTENT_RAW.toString();
  /** The output view name/SOFA produced by this AE. */
  private static final String outputView = Views.CONTENT_TEXT.toString();

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
  }

  /**
   * The AE process expects a SOFA with a {@link View.CONTENT_RAW} and uses Tika to produce a new
   * plain-text SOFA with a {@link View.CONTENT_TEXT}. It preserves all metadata annotations on the
   * raw content that Tika was able to detect.
   */
  @Override
  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    JCas newJCas;
    try {
      aJCas = aJCas.getView(inputView);
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    final InputStream stream = aJCas.getSofaDataStream();
    if (stream == null) {
      logger.log(Level.SEVERE, "no data stream for view {0}", aJCas.getViewName());
      throw new AnalysisEngineProcessException(new AssertionError("no SOFA data stream"));
    } else {
      logger.log(Level.INFO, "parsing {0} from {1}",
          new Object[] { aJCas.getSofaMimeType(), aJCas.getSofaDataURI() });
    }
    final Metadata metadata = new Metadata();
    if (aJCas.getSofaMimeType() != null) {
      metadata.set(HttpHeaders.CONTENT_TYPE, aJCas.getSofaMimeType());
    }
    if (encoding != null) {
      metadata.set(HttpHeaders.CONTENT_ENCODING, encoding);
    }
    if (aJCas.getSofaDataURI() != null) {
      try {
        metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY,
            resourceName(new URI(aJCas.getSofaDataURI())));
      } catch (final URISyntaxException e) {
        logger.log(Level.WARNING, "URI ''{0}'' not valid", aJCas.getSofaDataURI());
      } catch (final MalformedURLException e) {
        logger.log(Level.WARNING, "URI ''{0}'' not valid", aJCas.getSofaDataURI());
      }
    }
    try {
      newJCas = aJCas.createView(outputView);
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    ContentHandler handler = getContentHandler(newJCas);
    if (normalizeGreek) {
      handler = new GreekLetterContentHandler(handler);
    }
    final Detector detector = TikaConfig.getDefaultConfig().getDetector();
    final ParseContext context = new ParseContext();
    String mediaType = metadata.get(HttpHeaders.CONTENT_TYPE);
    Parser parser;
    try {
      if (mediaType == null) {
        mediaType = detector.detect(stream, metadata).getBaseType().toString();
      }
      if ("text/html".equals(mediaType) || mediaType.startsWith("application/xhtml")) {
        context.set(HtmlMapper.class, CleanHtmlMapper.INSTANCE);
        handler = new HTMLContentHandler(new CleanBodyContentHandler(handler));
        parser = new HtmlParser();
      } else if ("text/xml".equals(mediaType) || mediaType.startsWith("application/xml")) {
        if (XMLContentHandler.class.getName().equals(xmlHandlerClass)) {
          handler = new XMLContentHandler(handler);
          parser = new UnembeddedXMLParser();
        } else if (ElsevierXMLContentHandler.class.getName().equals(xmlHandlerClass)) {
          handler = new ElsevierXMLContentHandler(handler);
          parser = new UnembeddedXMLParser();
        } else if (CleanBodyContentHandler.class.getName().equals(xmlHandlerClass)) {
          handler = new CleanBodyContentHandler(handler);
          parser = new XMLParser();
        } else {
          logger.log(Level.WARNING, "unknown XML handler {0} - using default", xmlHandlerClass);
          handler = new XMLContentHandler(handler);
          parser = new UnembeddedXMLParser();
        }
      } else {
        handler = new CleanBodyContentHandler(handler);
        parser = new AutoDetectParser(detector);
      }
      context.set(Parser.class, parser);
      try {
        parser.parse(stream, handler, metadata, context);
      } catch (final SAXException e) {
        throw new TikaException("SAX processing failure", e);
      } finally {
        stream.close();
      }
    } catch (final IOException e) {
      throw new AnalysisEngineProcessException(e);
    } catch (final TikaException e) {
      throw new AnalysisEngineProcessException(e);
    }
    handleMetadata(metadata, newJCas);
    newJCas.setDocumentLanguage(aJCas.getDocumentLanguage());
  }

  /** Add Tika Metadata as DocumentAnnotations to the ouptut JCas. */
  void handleMetadata(Metadata metadata, JCas outputJCas) {
    final int num_names = metadata.size();
    if (num_names > 0) {
      for (final String name : metadata.names()) {
        for (final String val : metadata.getValues(name)) {
          final DocumentAnnotation da = new DocumentAnnotation(outputJCas);
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
      final File file = new File(uri);
      if (file.isFile()) return resourceName(file);
    }
    return resourceName(uri.toURL());
  }

  private String resourceName(File file) {
    return file.getName();
  }

  private String resourceName(URL url) {
    final String path = url.getPath();
    final int slash = path.lastIndexOf('/');
    return path.substring(slash + 1);
  }

  /** Provide the core content handler. */
  abstract ContentHandler getContentHandler(JCas newJCas);

  /** Fetch the URI of this AE (for the structured annotations). */
  abstract String getAnnotatorURI();
}
