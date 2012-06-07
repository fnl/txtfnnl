/**
 * 
 */
package txtfnnl.tika.uima;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.tika.TikaWrapper;
import txtfnnl.tika.sax.UIMAContentHandler;

/**
 * @author fleitner
 * 
 */
public class TikaAnnotator extends JCasAnnotator_ImplBase {

	/** The annotator URI of this AE. */
	public static final String URI = "http://tika.apache.org/";

	/** The actual annotator class for this AE. */
	private TikaWrapper annotator = new TikaWrapper();

	/** The view name/SOFA produced by this AE. */
	private String viewName = "contentText";

	/** A logger for this annotator. */
	private Logger logger = UIMAFramework.getLogger(TikaAnnotator.class);

	/**
	 * This AE takes a SOFA named "contentRaw" and uses Tika to produce a new
	 * plain-text SOFA named "contentText". It preserves all metadata
	 * annotations on the raw content that Tika itself preserves.
	 * 
	 * @see org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(org.apache.uima.jcas.JCas)
	 */
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		JCas newJCas;

		try {
			aJCas = aJCas.getView("contentRaw"); // input view
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		InputStream stream = aJCas.getSofaDataStream();

		if (stream == null) {
			logger.log(Level.SEVERE,
			    "no data stream from " + aJCas.getViewName());
			throw new AnalysisEngineProcessException(new AssertionError(
			    "no SOFA data stream"));
		} else {
			logger.log(Level.INFO, "parsing a " + aJCas.getSofaMimeType() +
			                       " file at " + aJCas.getSofaDataURI());
		}

		Metadata metadata = new Metadata();

		try {
			newJCas = aJCas.createView(viewName);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		UIMAContentHandler handler = new UIMAContentHandler(newJCas);

		try {
			annotator.parse(stream, handler, metadata);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (TikaException e) {
			throw new AnalysisEngineProcessException(e);
		}

		handler.addMetadata(metadata);
		newJCas.setDocumentLanguage(aJCas.getDocumentLanguage());
	}

}
