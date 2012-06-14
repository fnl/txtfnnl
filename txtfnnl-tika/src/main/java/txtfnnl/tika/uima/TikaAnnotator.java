/**
 * A Tika-based AE for (plain) text extraction from "binary" SOFAs.
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
import txtfnnl.uima.Views;

/**
 * This Tika-based AE extracts text content from an input view of the CAS and
 * sets this text content in a new output view.
 * 
 * Any markup that Tika extracts during the process is added to as
 * {@link txtfnnl.uima.tcas.TextAnnotation} to the text content. If Tika
 * detects {@link org.apache.tika.metadata.Metadata}, it is added as
 * {@link txtfnnl.uima.tcas.DocumentAnnotation}. As Tika takes care of
 * "everything" there is nothing to configure for this AE.
 * 
 * @author Florian Leitner
 */
public class TikaAnnotator extends JCasAnnotator_ImplBase {

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = "http://tika.apache.org/";

	/** The actual "annotator" for this AE. */
	private TikaWrapper annotator = new TikaWrapper();

	/** The input view name/SOFA expected by this AE. */
	private String inputView = Views.CONTENT_RAW.toString();

	/** The output view name/SOFA produced by this AE. */
	private String outputView = Views.CONTENT_TEXT.toString();

	/** A logger for this annotator. */
	private Logger logger = UIMAFramework.getLogger(TikaAnnotator.class);

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
			logger.log(Level.INFO, "parsing a " + aJCas.getSofaMimeType() +
			                       " file at " + aJCas.getSofaDataURI());
		}

		Metadata metadata = new Metadata();

		try {
			newJCas = aJCas.createView(outputView);
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
