package txtfnnl.tika.uima;

import org.apache.uima.jcas.JCas;

import org.xml.sax.ContentHandler;

import txtfnnl.tika.sax.SimpleUIMAContentHandler;

/**
 * This Tika-based AE extracts text content from an input view of the CAS and
 * sets this text content in a new output view.
 * 
 * If Tika detects {@link org.apache.tika.metadata.Metadata}, it is added as
 * {@link txtfnnl.uima.tcas.DocumentAnnotation}.
 * 
 * @author Florian Leitner
 */
public class SimpleTikaAnnotator extends AbstractTikaAnnotator {

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = "http://txtfnnl/TikaExtractor";

	@Override
	ContentHandler getContentHandler(JCas jcas) {
		return new SimpleUIMAContentHandler(jcas);
	}

	@Override
    String getAnnotatorURI() {
	    return URI;
    }
}
