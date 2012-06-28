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
import org.apache.uima.cas.ConstraintFactory;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.FSStringConstraint;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeaturePath;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An OpenNLP Sentence Detector AE variant for the txtfnnl pipeline.
 * 
 * Mandatory parameters (same as original parameters):
 * <ul>
 * <li>{@link opennlp.uima.util.UimaUtil#SENTENCE_TYPE_PARAMETER} the sentence
 * annotation type to use (usually, {@link #SENTENCE_TYPE_NAME})</li>
 * <li>{@link #PARAM_MODEL_NAME} defines the sentence model resource to use
 * (e.g., "EnglishSentenceModelResource")</li>
 * </ul>
 * Note that this AE assumes the chosen sentence annotation type has the
 * features "annotator", "confidence", "identifier", and "namespace".
 * 
 * Optional parameters (inherited from OpenNLP):
 * <table>
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

	/** The fully qualified sentence model name String. */
	public static final String PARAM_MODEL_NAME = UimaUtil.MODEL_PARAMETER;

	/**
	 * The default type name for the sentence annotation type.
	 * 
	 * In other words, this is the default value for the
	 * {@link UimaUtil#SENTENCE_TYPE_PARAMETER}.
	 */
	public static final String SENTENCE_TYPE_NAME = "txtfnnl.uima.tcas.SyntaxAnnotation";

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
			throw new ResourceInitializationException(new AssertionError(
			    "no sentence model resource for resource key '" +
			            sentenceModelResourceKey + "' found"));
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
	 * Return an annotation iterator for sentence annotations on the given
	 * CAS.
	 * 
	 * @param jcas to iterate over
	 * @param typeName fully qualified name of the used sentence annotation
	 *        type
	 * @return a sentence annotation iterator
	 */
	public static FSIterator<Annotation> getSentenceIterator(JCas jcas,
	                                                         String typeName) {
		Feature identifier = jcas.getTypeSystem().getFeatureByFullName(
		    typeName + ":identifier");
		Feature namespace = jcas.getTypeSystem().getFeatureByFullName(
		    typeName + ":namespace");
		FSIterator<Annotation> annIt = jcas.getAnnotationIndex(
		    SyntaxAnnotation.type).iterator();
		ConstraintFactory cf = jcas.getConstraintFactory();
		FeaturePath namespacePath = jcas.createFeaturePath();
		namespacePath.addFeature(namespace);
		FeaturePath identifierPath = jcas.createFeaturePath();
		identifierPath.addFeature(identifier);
		FSStringConstraint namespaceCons = cf.createStringConstraint();
		FSStringConstraint identifierCons = cf.createStringConstraint();
		namespaceCons.equals(NAMESPACE);
		identifierCons.equals(IDENTIFIER);
		FSMatchConstraint namespaceEmbed = cf.embedConstraint(namespacePath,
		    namespaceCons);
		FSMatchConstraint identifierEmbed = cf.embedConstraint(identifierPath,
		    identifierCons);
		FSMatchConstraint namespaceAndIdentifierCons = cf.and(identifierEmbed,
		    namespaceEmbed);
		FSIterator<Annotation> sentenceIt = jcas.createFilteredIterator(annIt,
		    namespaceAndIdentifierCons);
		return sentenceIt;
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
