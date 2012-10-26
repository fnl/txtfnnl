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
import org.apache.uima.cas.ConstraintFactory;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.FSStringConstraint;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeaturePath;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import opennlp.uima.tokenize.TokenizerModelResource;
import opennlp.uima.util.UimaUtil;
import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An OpenNLP Token AE variant for the txtfnnl pipeline.
 * 
 * Mandatory parameters (same as original parameters):
 * <ul>
 * <li>{@link #PARAM_MODEL_NAME} defines the tokenization model resource to use
 * (e.g., "EnglishTokenModelResource")</li>
 * </ul>
 * 
 * The token annotations (a
 * {@link txtfnnl.uima.tcas.SyntaxAnnotation} type with a
 * {@link txtfnnl.uima.analysis_component.opennlp.TokenAnnotator#NAMESPACE}
 * namespace and a Penn tag as identifier) are added to the CAS. 
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
 * </table>
 * 
 * @author Florian Leitner
 */
public class TokenAnnotator extends JCasAnnotator_ImplBase {

	/** The fully qualified tokenizer model name String. */
	public static final String PARAM_MODEL_NAME = UimaUtil.MODEL_PARAMETER;

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = "http://opennlp.apache.org";

	/** The namespace used for all annotations. */
	public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";

	/** The identifier used for all annotations. */
	public static final String IDENTIFIER = "Word";

	/**
	 * The fully qualified name of the sentence type (defaults to {
	 * {@link SentenceAnnotator#SENTENCE_TYPE_NAME}).
	 */
	public static final String PARAM_SENTENCE_TYPE_NAME = UimaUtil.SENTENCE_TYPE_PARAMETER;

	protected Logger logger;

	/**
	 * The OpenNLP tokenizer instance.
	 */
	private TokenizerME tokenizer;

	/**
	 * The name of the sentence type to iterate over.
	 */
	private String sentenceTypeName;

	public static FSMatchConstraint makeTokenConstraint(JCas jcas) {
		ConstraintFactory cf = jcas.getConstraintFactory();
		
		Feature namespace = jcas.getTypeSystem().getFeatureByFullName(
		    SyntaxAnnotation.class.getName() + ":namespace");
		FeaturePath namespacePath = jcas.createFeaturePath();
		namespacePath.addFeature(namespace);
		FSStringConstraint namespaceCons = cf.createStringConstraint();
		namespaceCons.equals(TokenAnnotator.NAMESPACE);
		
		Feature identifier = jcas.getTypeSystem().getFeatureByFullName(
		    SyntaxAnnotation.class.getName() + ":identifier");
		FeaturePath identifierPath = jcas.createFeaturePath();
		identifierPath.addFeature(identifier);
		FSStringConstraint identifierCons = cf.createStringConstraint();
		identifierCons.equals(TokenAnnotator.IDENTIFIER);
		
		FSMatchConstraint namespaceEmbed = cf.embedConstraint(namespacePath,
		    namespaceCons);
		FSMatchConstraint identifierEmbed = cf.embedConstraint(identifierPath,
		    identifierCons);
		return cf.and(identifierEmbed, namespaceEmbed);
	}

	/**
	 * Initializes the current instance with the given context.
	 * 
	 * Note: Do all initialization in this method, do not use the constructor.
	 */
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		logger = ctx.getLogger();
		TokenizerModel model;
		String modelResourceKey = (String) ctx
		    .getConfigParameterValue(PARAM_MODEL_NAME);

		try {
			TokenizerModelResource modelResource = (TokenizerModelResource) ctx
			    .getResourceObject(modelResourceKey);
			model = modelResource.getModel();
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (NullPointerException e) {
			throw new ResourceInitializationException(new AssertionError(
			    "no tokenizer model resource for resource key '" +
			            modelResourceKey + "' found"));
		}

		tokenizer = new TokenizerME(model);

		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(PARAM_SENTENCE_TYPE_NAME);

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		logger.log(Level.INFO, "OpenNLP tokenizer with model " +
		                       modelResourceKey);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			jcas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(jcas, sentenceTypeName);
		// buffer new tokens and only add them to the index after we have
		// iterated over all sentences - otherwise a concurrent modification
		// exception would be raised
		List<SyntaxAnnotation> buffer = new LinkedList<SyntaxAnnotation>();

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			Span tokenSpans[] = tokenizer.tokenizePos(sentence
			    .getCoveredText());
			double tokenProbabilties[] = tokenizer.getTokenProbabilities();
			assert tokenSpans.length == tokenProbabilties.length;
			int sentenceOffset = sentence.getBegin();

			for (int i = 0; i < tokenSpans.length; i++) {
				SyntaxAnnotation token = new SyntaxAnnotation(jcas,
				    sentenceOffset + tokenSpans[i].getStart(),
				    sentenceOffset + tokenSpans[i].getEnd());
				token.setAnnotator(URI);
				token.setConfidence(tokenProbabilties[i]);
				token.setIdentifier(IDENTIFIER);
				token.setNamespace(NAMESPACE);
				buffer.add(token);
			}
		}
		
		for (SyntaxAnnotation token : buffer)
			token.addToIndexes(jcas);
		
		
		logger.log(Level.FINE, "annotated " + buffer.size() + " tokens");
	}

	@Override
	public void destroy() {
		this.tokenizer = null;
	}

}
