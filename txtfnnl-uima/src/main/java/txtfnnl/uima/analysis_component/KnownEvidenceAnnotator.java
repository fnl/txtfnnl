package txtfnnl.uima.analysis_component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ExternalResource;

import txtfnnl.uima.Views;
import txtfnnl.uima.resource.LineBasedStringMapResource;

/**
 * The abstract implementation for all evidence AEs provides the necessary
 * basis to process a Views.CONTENT_TEXT JCas with some given Evidence.
 * 
 * Evidence is mapped to an external LineBasedStringMapResource with some form
 * of Evidence type returned for a given SOFA "ID" key, which is generated
 * from the basename of the CAS' URI without the extension (if any).
 * 
 * In addition, counters for found and missed evidence are provided and the
 * overall recall score is logged when the AE is destroyed. Last, this
 * implementation takes care of instantiating a Logger.
 * 
 * @author Florian Leitner
 */
public abstract class KnownEvidenceAnnotator<Evidence> extends JCasAnnotator_ImplBase {

	/** The key used for the LineBasedStringMapResource. */
	public static final String MODEL_KEY_EVIDENCE_STRING_MAP = "KnownEvidence";
	@ExternalResource(key = MODEL_KEY_EVIDENCE_STRING_MAP)
	private LineBasedStringMapResource<Evidence> documentEvidenceMap;

	/** The logger for this Annotator. */
	Logger logger;

	int truePositives; // total TP count over all SOFAs
	int falseNegatives; // total FN count over all SOFAs
	int checksum; // to ensure FP/FN counts are correct

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);

		logger = ctx.getLogger();
		truePositives = 0;
		falseNegatives = 0;
		checksum = 0;

		if (documentEvidenceMap.size() == 0)
			logger.log(Level.WARNING, "no evidences loaded by resource {0}", this.getClass()
			    .getSimpleName());
		else
			logger
			    .log(Level.INFO, "loaded evidence for {0} documents", documentEvidenceMap.size());

		ensureNotNull(documentEvidenceMap,
		    ResourceInitializationException.NO_RESOURCE_FOR_PARAMETERS,
		    MODEL_KEY_EVIDENCE_STRING_MAP);
	}

	/**
	 * Ensure theObject is not <code>null</code>.
	 * 
	 * @param theObject to ensure
	 * @param msg key to use for the exception
	 * @param params for the message associated to the msg key
	 * @throws ResourceInitializationException if theObject is null
	 */
	void ensureNotNull(Object theObject, String msg, Object... params)
	        throws ResourceInitializationException {
		if (theObject == null)
			throw new ResourceInitializationException(msg, params);
	}

	/**
	 * Logs the overall evidence recall.
	 */
	public void destroy() {
		super.destroy();

		int sum = truePositives + falseNegatives;
		float recall = 100 * (float) truePositives / sum;
		logger.log(Level.INFO, String.format("known evidence annotation recall=%.2f%%", recall));

		if (checksum != sum)
			logger.log(Level.WARNING, "TP+FP=" + sum + ", expected " + checksum);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		// Setup ...
		JCas textCas;
		JCas rawCas;
		String documentId;

		try {
			textCas = jcas.getView(Views.CONTENT_TEXT.toString());
			rawCas = jcas.getView(Views.CONTENT_RAW.toString());
			documentId = new File(new URI(rawCas.getSofaDataURI()).getPath()).getName();
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (URISyntaxException e) {
			throw new AnalysisEngineProcessException(e);
		}

		if (documentId.indexOf('.') > -1)
			documentId = documentId.substring(0, documentId.lastIndexOf('.'));

		// Fetch the known relationships for this document
		Evidence evidence = documentEvidenceMap.get(documentId);

		if (evidence == null) {
			logger
			    .log(Level.WARNING,
			        "no evidence mapped to doc '" + documentId + "' (" + rawCas.getSofaDataURI() +
			                ")");
		} else {
			process(documentId, textCas, evidence);
		}
	}

	/**
	 * Any concrete implementation needs to implement ways to annotate the
	 * Evidence in the SOFA on the CAS.
	 * 
	 * In addition, the counter vars truePositives, falsePositivies, and
	 * checksum should be incremented are appropriate (checksum should be the
	 * same as the sum of TPs and FPs).
	 * 
	 * @param documentId being processed (URI basename w/o extension)
	 * @param textCas holding the SOFA
	 * @param evidence for the given document, as provided by the evidence
	 *        resource
	 * @throws AnalysisEngineProcessException
	 */
	abstract void process(String documentId, JCas textJCas, Evidence evidence)
	        throws AnalysisEngineProcessException;
}
