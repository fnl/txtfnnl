package txtfnnl.tika.uima;

import java.io.IOException;
import java.util.HashMap;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;

import org.xml.sax.ContentHandler;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.tika.sax.UIMAContentHandler;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * This Tika-based AE extracts text content from an input view of the CAS and
 * sets this text content in a new output view, together with annotations of
 * the structure or markup that was part of the input data.
 * 
 * Any markup that Tika extracts during the process is added to as
 * {@link txtfnnl.uima.tcas.StructureAnnotation} to the text content. If Tika
 * detects {@link org.apache.tika.metadata.Metadata}, it is added as
 * {@link txtfnnl.uima.tcas.DocumentAnnotation}.
 * 
 * @author Florian Leitner
 */
public class TikaAnnotator extends AbstractTikaAnnotator {

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = TikaAnnotator.class.getName();

	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final String encoding,
	                                                  final boolean normalizeGreek,
	                                                  final String xmlHandlerClass)
	        throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(TikaAnnotator.class,
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
		return configure(encoding, normalizeGreek, null);
	}

	public static AnalysisEngineDescription configure(String encoding)
	        throws UIMAException, IOException {
		return configure(encoding, false);
	}

	public static AnalysisEngineDescription configure() throws UIMAException, IOException {
		return configure(null);
	}
	
	ContentHandler getContentHandler(JCas newJCas) {
		return new UIMAContentHandler(newJCas, URI);
	}
	
	String getAnnotatorURI() {
		return URI;
	}
}
