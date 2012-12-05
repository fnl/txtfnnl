/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class RelationshipPatternAnnotator extends JCasAnnotator_ImplBase {

	// config?
	private String tokenNamespace;
	
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

	protected Logger logger;

	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		logger = ctx.getLogger();
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			jcas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSMatchConstraint tokenConstraint = TokenAnnotation
		    .makeConstraint(jcas, tokenNamespace);
		FSIterator<Annotation> sentenceIt = SentenceAnnotation
		    .getIterator(jcas);
		AnnotationIndex<Annotation> tokenIdx = jcas
		    .getAnnotationIndex(TokenAnnotation.type);

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			@SuppressWarnings("unused")
            FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
			    tokenIdx.subiterator(sentence, true, true), tokenConstraint);
		}
	}

}
