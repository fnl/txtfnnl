/**
 * 
 */
package txtfnnl.uima.analysis_component.opennlp;

import java.util.LinkedList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.uima.postag.POSModelResource;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;
import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An OpenNLP PoS Tagger AE variant for the txtfnnl pipeline.
 * 
 * Mandatory parameters (same as original parameters):
 * <ul>
 * <li>{@link #PARAM_MODEL_NAME} defines the PoS tagger model resource to use
 * (e.g., "EnglishPartOfSpeechModelResource")</li>
 * </ul>
 * 
 * The PoS tag values are added to the token annotations (a
 * {@link txtfnnl.uima.tcas.SyntaxAnnotation} type with a
 * {@link txtfnnl.uima.analysis_component.opennlp.TokenAnnotator#NAMESPACE}
 * namespace), as two properties: the (Penn) PoS tag itself, and a confidence
 * value. The names of these properties are defined in
 * {@link #POS_TAG_VALUE_PROPERTY_NAME} and
 * {@link #POS_TAG_CONFIDENCE_PROPERTY_NAME}. 
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
 * <td>{@link opennlp.uima.util.UimaUtil#SENTENCE_TYPE_PARAMETER}</td>
 * <td>The sentence annotation type to use (defaults to
 * {@link #SENTENCE_TYPE_NAME})</td>
 * <tr>
 * <tr>
 * <td>Double</td>
 * <td>{@link opennlp.uima.util.UimaUtil#BEAM_SIZE_PARAMETER</td>
 * <td>Beam size for the PoS tag EM search.</td>
 * </tr>
 * </table>
 * 
 * @author Florian Leitner
 */
public class PartOfSpeechAnnotator extends JCasAnnotator_ImplBase {

	/** The fully qualified PoS model name String. */
	public static final String PARAM_MODEL_NAME = UimaUtil.MODEL_PARAMETER;

	/**
	 * The fully qualified name of the sentence type (defaults to {
	 * {@link SentenceAnnotator#SENTENCE_TYPE_NAME}).
	 */
	public static final String PARAM_SENTENCE_TYPE_NAME = UimaUtil.SENTENCE_TYPE_PARAMETER;

	/**
	 * Optional beam size (integer) to use for the PoS tagger searches
	 * (defaults to {@link POSTaggerME#DEFAULT_BEAM_SIZE}).
	 */
	public static final String PARAM_BEAM_SIZE = UimaUtil.BEAM_SIZE_PARAMETER;

	/**
	 * The property name that will store the PoS tag value in the token
	 * annotation.
	 */
	public static final String POS_TAG_VALUE_PROPERTY_NAME = "pos-tag";

	/**
	 * The property name that will store the PoS tag confidence (as a
	 * probability score) in the token annotation.
	 */
	public static final String POS_TAG_CONFIDENCE_PROPERTY_NAME = "pos-confidence";

	protected Logger logger;

	/** The OpenNLP PoS tagger instance. */
	private POSTaggerME posTagger;

	/** The name of the sentence type to iterate over. */
	private String sentenceTypeName;

	/**
	 * Initializes the current instance with the given context.
	 * 
	 * Note: Do all initialization in this method, do not use the constructor.
	 */
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		logger = ctx.getLogger();
		POSModel model;
		String modelResourceKey = (String) ctx
		    .getConfigParameterValue(PARAM_MODEL_NAME);

		try {
			POSModelResource modelResource = (POSModelResource) ctx
			    .getResourceObject(modelResourceKey);
			model = modelResource.getModel();
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (NullPointerException e) {
			throw new ResourceInitializationException(new AssertionError(
			    "no PoS model resource for resource key '" + modelResourceKey +
			            "' found"));
		}

		Integer beamSize = AnnotatorUtil.getOptionalIntegerParameter(ctx,
		    PARAM_BEAM_SIZE);

		if (beamSize == null)
			beamSize = POSTaggerME.DEFAULT_BEAM_SIZE;

		posTagger = new POSTaggerME(model, beamSize, 0);

		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(PARAM_SENTENCE_TYPE_NAME);

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		logger.log(Level.INFO, "OpenNLP PoS tagger with model " +
		                       modelResourceKey);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			jcas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSMatchConstraint tokenConstraint = TokenAnnotator
		    .makeTokenConstraint(jcas);
		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(jcas, sentenceTypeName);
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		int count = 0;

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
			    annIdx.subiterator(sentence, true, true), tokenConstraint);

			List<SyntaxAnnotation> tokenAnns = new LinkedList<SyntaxAnnotation>();

			while (tokenIt.hasNext())
				tokenAnns.add((SyntaxAnnotation) tokenIt.next());

			if (tokenAnns.size() == 0)
				continue;
			
			String[] tokens = new String[tokenAnns.size()];
			int idx = 0;

			for (SyntaxAnnotation tokenAnn : tokenAnns)
				tokens[idx++] = tokenAnn.getCoveredText();

			String[] posTags = posTagger.tag(tokens);
			double[] posProbs = posTagger.probs();
			idx = 0;

			assert posTags.length == posProbs.length;
			assert posTags.length == tokens.length;

			for (SyntaxAnnotation tokenAnn : tokenAnns) {
				Property tag = new Property(jcas);
				Property prob = new Property(jcas);
				tag.setName(POS_TAG_VALUE_PROPERTY_NAME);
				prob.setName(POS_TAG_CONFIDENCE_PROPERTY_NAME);
				tag.setValue(posTags[idx]);
				prob.setValue(Double.toString(posProbs[idx++]));
				FSArray properties = new FSArray(jcas, 2);
				properties.set(0, tag);
				properties.set(1, prob);
				tokenAnn.setProperties(properties);
				count++;
			}
		}
		
		logger.log(Level.FINE, "tagged the PoS of " + count + " tokens");
	}

	static public String getPoSTag(SyntaxAnnotation token) {
		Property p = token.getProperties(0);
		assert POS_TAG_VALUE_PROPERTY_NAME == p.getName();
		return p.getValue();
	}

	static public double getPoSTagConfidence(SyntaxAnnotation token) {
		Property p = token.getProperties(1);
		assert POS_TAG_CONFIDENCE_PROPERTY_NAME == p.getName();
		return Double.parseDouble(p.getValue());
	}

	@Override
	public void destroy() {
		this.posTagger = null;
	}

}
