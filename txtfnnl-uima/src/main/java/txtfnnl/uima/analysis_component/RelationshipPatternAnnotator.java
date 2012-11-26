/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.ChunkAnnotator;
import txtfnnl.uima.analysis_component.opennlp.PartOfSpeechAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class RelationshipPatternAnnotator extends JCasAnnotator_ImplBase {

	public static final Set<String> TRIGGER_WORDS = new HashSet<String>(
	    Arrays.asList(new String[] {
	        "gene",
	        "promoter",
	        "enhancer",
	        "silencer",
	        "element",
	        "motif",
	        "sequence",
	        "site" })); // TODO

	/**
	 * The fully qualified name of the sentence type (defaults to {
	 * {@link SentenceAnnotator#SENTENCE_TYPE_NAME}).
	 */
	public static final String PARAM_SENTENCE_TYPE_NAME = UimaUtil.SENTENCE_TYPE_PARAMETER;

	protected Logger logger;

	/** The name of the sentence type to iterate over. */
	private String sentenceTypeName;

	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		logger = ctx.getLogger();

		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(PARAM_SENTENCE_TYPE_NAME);

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;
	}

	/* (non-Javadoc)
	 * 
	 * @see
	 * org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(
	 * org.apache.uima.jcas.JCas) */
	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			jcas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSMatchConstraint tokenConstraint = TokenAnnotator
		    .makeTokenConstraint(jcas);
		FSMatchConstraint chunkConstraint = ChunkAnnotator
		    .makeChunkConstraint(jcas);
		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(jcas, sentenceTypeName);
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
			    annIdx.subiterator(sentence, true, true), tokenConstraint);

			// TODO:
			if (TRIGGER_WORDS.size() > 0) {
				while (tokenIt.hasNext()) {
					SyntaxAnnotation token = (SyntaxAnnotation) tokenIt.next();
					String lemma = BioLemmatizerAnnotator.getLemma(token);
					String posTag = PartOfSpeechAnnotator.getPoSTag(token);
					tokenIt.moveToFirst();

					if (TRIGGER_WORDS.contains(lemma) &&
					    posTag.startsWith("NN")) // TODO
						matchSentence(jcas, annIdx, sentence, tokenIt,
						    chunkConstraint);
				}
			} else {
				matchSentence(jcas, annIdx, sentence, tokenIt, chunkConstraint);
			}
		}
	}

	private void matchSentence(JCas jcas, AnnotationIndex<Annotation> annIdx,
	                           Annotation sentence,
	                           FSIterator<Annotation> tokenIt,
	                           FSMatchConstraint chunkConstraint) {
		FSIterator<Annotation> chunkIt = jcas.createFilteredIterator(
		    annIdx.subiterator(sentence, true, true), chunkConstraint);

		SyntaxAnnotation chunk = null;

		if (chunkIt.hasNext())
			chunk = (SyntaxAnnotation) chunkIt.next();

		//SyntaxAnnotation span = null;

		while (tokenIt.hasNext()) {
			SyntaxAnnotation token = (SyntaxAnnotation) tokenIt.next();
			//String lemma = BioLemmatizerAnnotator.getLemma(token);

			while (chunk != null) {
				if (chunk.contains(token)) {
					//span = chunk;
					break;
				} else if (chunk.getEnd() <= token.getBegin()) {
					//span = null;

					if (chunkIt.hasNext())
						chunk = (SyntaxAnnotation) chunkIt.next();
					else
						chunk = null;
				} else {
					//span = null;
					break;
				}
			}

		}
	}
}
