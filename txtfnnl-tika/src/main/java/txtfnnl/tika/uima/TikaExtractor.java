package txtfnnl.tika.uima;

import org.xml.sax.ContentHandler;

import org.apache.uima.jcas.JCas;

import txtfnnl.tika.sax.SimpleUIMAContentHandler;

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

  public static class Builder extends AbstractTikaAnnotator.Builder {
    public Builder() {
      super(TikaExtractor.class);
    }
  }
  
  /** Configure a TikaExtractor content extraction engine. */
  public static Builder configure() {
    return new Builder();
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
