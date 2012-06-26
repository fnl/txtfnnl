package txtfnnl.opennlp.uima.sentdetect;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;
import opennlp.uima.sentdetect.AbstractSentenceDetector;
import opennlp.uima.sentdetect.SentenceModelResource;
import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.Views;

/**
 * An OpenNLP Sentence Detector AE variant for the txtfnnl pipeline.
 * 
 * <p>
 * Mandatory parameters (same as original parameters)
 * <table border=1>
 * <tr>
 * <th>Type</th>
 * <th>Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>String</td>
 * <td>opennlp.uima.ModelName</td>
 * <td>The name (key) of the sentence model resource (e.g.,
 * "EnglishSentenceModelResource").</td>
 * </tr>
 * <tr>
 * <td>String</td>
 * <td>opennlp.uima.SentenceType</td>
 * <td>The full name of the sentence annotation type (usually,
 * "txtfnnl.uima.SyntaxAnnotation"). Note that this AE assumes the chosen
 * annotation type has the features "annotator", "confidence", "identifier",
 * and "namespace".</td>
 * </tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <tr>
 * <th>Type</th>
 * <th>Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>String</td>
 * <td>opennlp.uima.ContainerType</td>
 * <td>The name of the container type (default: use the entire SOFA).</td>
 * </tr>
 * <tr>
 * <td>Boolean</td>
 * <td>opennlp.uima.IsRemoveExistingAnnotations</td>
 * <td>Remove existing annotations (from the container) before processing the
 * CAS.</td>
 * </tr>
 * </table>
 * 
 * @author Florian Leitner
 */
public final class SentenceAnnotator extends AbstractSentenceDetector {

	/** The OpenNLP sentence detector. */
	private SentenceDetectorME sentenceDetector;

	/** The annotator feature of the sentence annotation type. */
	private Feature annotatorFeature;

	/** The confidence feature of the sentence annotation type. */
	private Feature confidenceFeature;

	/** The identifier feature of the sentence annotation type. */
	private Feature identifierFeature;

	/** The namespace feature of the sentence annotation type. */
	private Feature namespaceFeature;

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = "http://opennlp.apache.org";

	/** The namespace to use for all annotations. */
	public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";

	/** The identifier to use for all annotations. */
	public static final String IDENTIFIER = "Sentence";

	public static final String PARAM_MODEL_NAME = UimaUtil.MODEL_PARAMETER;

	/**
	 * Load the sentence detector model resource and initialize the model
	 * evaluator.
	 */
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		String sentenceModelResourceKey = (String) ctx
		    .getConfigParameterValue(PARAM_MODEL_NAME);
		SentenceModel model;

		try {
			SentenceModelResource modelResource = (SentenceModelResource) ctx
			    .getResourceObject(sentenceModelResourceKey);
			model = modelResource.getModel();
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (NullPointerException e) {
			throw new ResourceInitializationException(new AssertionError("no sentence model resource for resource key '" + sentenceModelResourceKey + "' found"));
		}

		sentenceDetector = new SentenceDetectorME(model);
	}

	/**
	 * Initializes the type system and features.
	 */
	public void typeSystemInit(TypeSystem typeSystem)
	        throws AnalysisEngineProcessException {
		super.typeSystemInit(typeSystem);
		annotatorFeature = sentenceType.getFeatureByBaseName("annotator");
		confidenceFeature = sentenceType.getFeatureByBaseName("confidence");
		identifierFeature = sentenceType.getFeatureByBaseName("identifier");
		namespaceFeature = sentenceType.getFeatureByBaseName("namespace");
	}

	/**
	 * Detect sentences in the {@link Views.CONTENT_TEXT} view of a CAS.
	 */
	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		super.process(cas.getView(Views.CONTENT_TEXT.toString()));
	}

	/**
	 * Delegator method for the actual sentence detection call.
	 */
	@Override
	protected Span[] detectSentences(String text) {
		return sentenceDetector.sentPosDetect(text);
	}

	/**
	 * Add txtfnnl-specific text annotations features.
	 * 
	 * This method sets confidence, annotator, namespace, and identifier
	 * values on the chosen sentence annotation type.
	 */
	@Override
	protected void postProcessAnnotations(AnnotationFS sentences[]) {
		double sentenceProbabilities[] = sentenceDetector
		    .getSentenceProbabilities();

		for (int i = 0; i < sentences.length; i++) {
			sentences[i].setDoubleValue(confidenceFeature,
			    sentenceProbabilities[i]);
			sentences[i].setStringValue(annotatorFeature, URI);
			sentences[i].setStringValue(namespaceFeature, NAMESPACE);
			sentences[i].setStringValue(identifierFeature, IDENTIFIER);
		}
	}

	/**
	 * Releases allocated resources.
	 */
	public void destroy() {
		// dereference model to allow garbage collection
		sentenceDetector = null;
	}
}
