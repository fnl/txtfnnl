package txtfnnl.tika.uima;

import org.xml.sax.ContentHandler;

import org.apache.uima.jcas.JCas;

//import org.uimafit.util.JCasUtil;

import txtfnnl.tika.sax.UIMAContentHandler;
//import txtfnnl.uima.tcas.DocumentAnnotation;

/**
 * This Tika-based AE extracts text content from an input view of the CAS and sets this text
 * content in a new output view, together with annotations of the structure or markup that was part
 * of the input data. Any markup that Tika extracts during the process is added to as
 * {@link txtfnnl.uima.tcas.StructureAnnotation StructureAnnotation} to the text content. If Tika
 * detects {@link org.apache.tika.metadata.Metadata Metadata}, it is added as
 * {@link txtfnnl.uima.tcas.DocumentAnnotation DocumentAnnotation}.
 * 
 * @author Florian Leitner
 */
public class TikaAnnotator extends AbstractTikaAnnotator {
  /** The annotator's URI (for the annotations) set by this AE. */
  public static final String URI = TikaAnnotator.class.getName();

  public static class Builder extends AbstractTikaAnnotator.Builder {
    public Builder() {
      super(TikaAnnotator.class);
    }
  }

  /** Configure a TikaAnnotator content extraction engine. */
  public static Builder configure() {
    return new Builder();
  }

// TODO: remove the next few lines?
  /** Extract the resource name (set by the Tika annotator) of this SOFA from the CAS. */
//  public static String getResourceName(JCas jcas) {
//    for (final DocumentAnnotation ann : JCasUtil.select(jcas, DocumentAnnotation.class))
//      if ("resourceName".equals(ann.getNamespace())) return ann.getIdentifier();
//    return null;
//  }

  @Override
  ContentHandler getContentHandler(JCas newJCas) {
    return new UIMAContentHandler(newJCas, URI);
  }

  @Override
  String getAnnotatorURI() {
    return URI;
  }
}
