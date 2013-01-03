package txtfnnl.tika.uima;

import java.io.IOException;
import java.util.HashMap;

import org.xml.sax.ContentHandler;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.tika.sax.SimpleUIMAContentHandler;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * This Tika-based AE extracts text content from an input view of the CAS and sets this text
 * content in a new output view. If Tika detects {@link org.apache.tika.metadata.Metadata Metadata}
 * , it is added as {@link txtfnnl.uima.tcas.DocumentAnnotation DocumentAnnotation}. No structural
 * text annotations ( {@link txtfnnl.uima.tcas.StructureAnnotation StructureAnnotation} ) are made
 * by this AE, i.e., it only extracts the text content.
 * 
 * @author Florian Leitner
 */
public class TikaExtractor extends AbstractTikaAnnotator {
  /** The annotator's URI (for the annotations) set by this AE. */
  public static final String URI = TikaExtractor.class.getName();

  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(final String encoding,
      final boolean normalizeGreek, final String xmlHandlerClass) throws UIMAException,
      IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(TikaExtractor.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_ENCODING, encoding);
            put(PARAM_NORMALIZE_GREEK_CHARACTERS, normalizeGreek);
            put(PARAM_XML_HANDLER, xmlHandlerClass);
          }
        }));
  }

  public static AnalysisEngineDescription configure(String encoding, boolean normalizeGreek)
      throws UIMAException, IOException {
    return TikaExtractor.configure(encoding, normalizeGreek, null);
  }

  public static AnalysisEngineDescription configure(String encoding) throws UIMAException,
      IOException {
    return TikaExtractor.configure(encoding, false);
  }

  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return TikaExtractor.configure(null);
  }

  @Override
  ContentHandler getContentHandler(JCas jcas) {
    return new SimpleUIMAContentHandler(jcas);
  }

  @Override
  String getAnnotatorURI() {
    return URI;
  }
}
