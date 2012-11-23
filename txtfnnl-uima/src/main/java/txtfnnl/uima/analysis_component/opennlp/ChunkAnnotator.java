/**
 * 
 */
package txtfnnl.uima.analysis_component.opennlp;

import java.util.ArrayList;
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
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.uima.chunker.ChunkerModelResource;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;
import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An OpenNLP Chunker AE variant for the txtfnnl pipeline.
 * 
 * Mandatory parameters (same as original parameters):
 * <ul>
 * <li>{@link #PARAM_MODEL_NAME} defines the chunker model resource to use
 * (e.g., "EnglishChunkModelResource")</li>
 * </ul>
 * 
 * The chunk annotations (a
 * {@link txtfnnl.uima.tcas.SyntaxAnnotation} type with a
 * {@link #NAMESPACE}
 * namespace and a Penn phrase tag as identifier) are added to the CAS. 
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
 * <td>Beam size for the chunk tag EM search.</td>
 * </tr>
 * </table>
 * 
 * @author Florian Leitner
 */
public class ChunkAnnotator extends JCasAnnotator_ImplBase {

	/** The fully qualified Chunk model name. */
	public static final String PARAM_MODEL_NAME = UimaUtil.MODEL_PARAMETER;

	/**
	 * The fully qualified name of the sentence type (defaults to {
	 * {@link SentenceAnnotator#SENTENCE_TYPE_NAME}).
	 */
	public static final String PARAM_SENTENCE_TYPE_NAME = UimaUtil.SENTENCE_TYPE_PARAMETER;

	/**
	 * Optional beam size (integer) to use for the chunker's searches
	 * (defaults to {@link POSTaggerME#DEFAULT_BEAM_SIZE}).
	 */
	public static final String PARAM_BEAM_SIZE = UimaUtil.BEAM_SIZE_PARAMETER;

	public static final String NAMESPACE = "http://bulba.sdsu.edu/jeanette/thesis/PennTags.html#";

	/** The annotator's URI (for the annotations) set by this AE. */
	private static final String URI = "http://opennlp.apache.org";

	protected Logger logger;

	/** The OpenNLP PoS tagger instance. */
	private ChunkerME chunker;

	/** The name of the sentence type to iterate over. */
	private String sentenceTypeName;

	public static FSMatchConstraint makeChunkConstraint(JCas jcas) {
		ConstraintFactory cf = jcas.getConstraintFactory();

		Feature namespace = jcas.getTypeSystem().getFeatureByFullName(
		    SyntaxAnnotation.class.getName() + ":namespace");
		FeaturePath namespacePath = jcas.createFeaturePath();
		namespacePath.addFeature(namespace);
		FSStringConstraint namespaceCons = cf.createStringConstraint();
		namespaceCons.equals(ChunkAnnotator.NAMESPACE);

//		Feature annotator = jcas.getTypeSystem().getFeatureByFullName(
//		    SyntaxAnnotation.class.getName() + ":annotator");
//		FeaturePath annotatorPath = jcas.createFeaturePath();
//		annotatorPath.addFeature(annotator);
//		FSStringConstraint annotatorCons = cf.createStringConstraint();
//		annotatorCons.equals(ChunkAnnotator.URI);

		FSMatchConstraint namespaceEmbed = cf.embedConstraint(namespacePath,
		    namespaceCons);
		return namespaceEmbed;
//		FSMatchConstraint annotatorEmbed = cf.embedConstraint(annotatorPath,
//		    annotatorCons);
//		return cf.and(annotatorEmbed, namespaceEmbed);
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
		ChunkerModel model;
		String modelResourceKey = (String) ctx
		    .getConfigParameterValue(PARAM_MODEL_NAME);

		try {
			ChunkerModelResource modelResource = (ChunkerModelResource) ctx
			    .getResourceObject(modelResourceKey);
			model = modelResource.getModel();
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (NullPointerException e) {
			throw new ResourceInitializationException(new AssertionError(
			    "no chunk model resource for resource key '" +
			            modelResourceKey + "' found"));
		}

		Integer beamSize = AnnotatorUtil.getOptionalIntegerParameter(ctx,
		    PARAM_BEAM_SIZE);

		if (beamSize == null)
			beamSize = ChunkerME.DEFAULT_BEAM_SIZE;

		chunker = new ChunkerME(model, beamSize);

		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(PARAM_SENTENCE_TYPE_NAME);

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		logger.log(Level.INFO, "OpenNLP Chunker with model " +
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
		// buffer the chunks to index them after iterating all of them
		// otherwise, a concurrent modification exception would occur
		List<SyntaxAnnotation> buffer = new LinkedList<SyntaxAnnotation>();

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
			    annIdx.subiterator(sentence, true, true), tokenConstraint);

			List<SyntaxAnnotation> tokenAnns = new ArrayList<SyntaxAnnotation>();

			while (tokenIt.hasNext())
				tokenAnns.add((SyntaxAnnotation) tokenIt.next());

			String[] tokens = new String[tokenAnns.size()];
			String[] pos_tags = new String[tokenAnns.size()];
			int idx = 0;

			for (SyntaxAnnotation tokenAnn : tokenAnns) {
				pos_tags[idx] = PartOfSpeechAnnotator.getPoSTag(tokenAnn);
				tokens[idx++] = tokenAnn.getCoveredText();
			}

			String[] result = chunker.chunk(tokens, pos_tags);
			double[] probs = chunker.probs();
			assert result.length == probs.length;
			assert result.length == tokenAnns.size();

			int start = -1;
			int end = -1;

			for (int i = 0; i < result.length; i++) {
				switch (result[i].charAt(0)) {
				case 'B':
					if (start != -1)
						// annotate last chunk (started with B)
						buffer.add(createChunkAnnotation(jcas, result[start]
						    .substring(2), probs[start], tokenAnns.get(start)
						    .getBegin(), tokenAnns.get(end).getEnd()));
					start = i;
					end = i;
				break;
				case 'I':
					end = i;
				break;
				case 'O':
					if (start != -1) {
						// annotate last chunk (started with B)
						buffer.add(createChunkAnnotation(jcas, result[start]
						    .substring(2), probs[start], tokenAnns.get(start)
						    .getBegin(), tokenAnns.get(end).getEnd()));

						start = -1;
						end = -1;
					}
				break;
				default:
					throw new AssertionError("Unexpected tag: " + result[i] +
					                         " at postion " + i + " in '" +
					                         sentence.getCoveredText() + "'");

				}
			}

			if (start != -1)
				// annotate trailing chunk
				buffer.add(createChunkAnnotation(jcas,
				    result[start].substring(2),
				    probs[start], tokenAnns.get(start).getBegin(),
				    tokenAnns.get(end).getEnd()));

		}

		for (SyntaxAnnotation chunk : buffer)
			chunk.addToIndexes(jcas);

		logger.log(Level.FINE, "annotated " + buffer.size() + " chunks");
	}

	private SyntaxAnnotation createChunkAnnotation(JCas cas, String tag,
	                                               double conf, int start,
	                                               int end) {
		SyntaxAnnotation chunk = new SyntaxAnnotation(cas, start, end);
		chunk.setAnnotator(URI);
		chunk.setConfidence(conf);
		chunk.setNamespace(NAMESPACE);
		chunk.setIdentifier(tag);
		return chunk;
	}

	@Override
	public void destroy() {
		this.chunker = null;
	}

}
