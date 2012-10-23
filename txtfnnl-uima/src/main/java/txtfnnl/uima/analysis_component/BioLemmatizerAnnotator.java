/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.util.Collection;
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
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import edu.ucdenver.ccp.nlp.biolemmatizer.BioLemmatizer;
import edu.ucdenver.ccp.nlp.biolemmatizer.LemmataEntry;
import edu.ucdenver.ccp.nlp.biolemmatizer.LemmataEntry.Lemma;

import opennlp.uima.util.UimaUtil;
import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.PartOfSpeechAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An CCP BioLemmatizer AE variant for the txtfnnl pipeline.
 * 
 * Mandatory parameters: NONE
 * 
 * The lemmata are added to the token annotations (a
 * {@link txtfnnl.uima.tcas.SyntaxAnnotation} type with a
 * {@link txtfnnl.uima.analysis_component.opennlp.TokenAnnotator#NAMESPACE}
 * namespace), as properties with name {@link #LEMMA_PROPERTY_NAME}.
 * 
 * <b>Note that this annotator requires at least 1 GB ("-Xmx1G") of available
 * runtime memory!</b>
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
public class BioLemmatizerAnnotator extends JCasAnnotator_ImplBase {

	/**
	 * The fully qualified name of the sentence type (defaults to {
	 * {@link SentenceAnnotator#SENTENCE_TYPE_NAME}).
	 */
	public static final String PARAM_SENTENCE_TYPE_NAME = UimaUtil.SENTENCE_TYPE_PARAMETER;

	public static final String LEMMA_PROPERTY_NAME = "lemma";

	static public String getLemma(SyntaxAnnotation token) {
		FSArray props = token.getProperties();

		for (int i = props.size(); i-- > 0;) {
			Property p = token.getProperties(i);
			if (LEMMA_PROPERTY_NAME.equals(p.getName()))
				return p.getValue();
		}

		return null;
	}

	protected Logger logger;

	private BioLemmatizer lemmatizer;

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

		lemmatizer = new BioLemmatizer();

		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(PARAM_SENTENCE_TYPE_NAME);

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		logger.log(Level.INFO, "BioLemmatizer initialized");
	}

	@Override
	public void process(JCas cas) throws AnalysisEngineProcessException {
		try {
			cas = cas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSMatchConstraint tokenConstraint = TokenAnnotator
		    .makeTokenConstraint(cas);
		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(cas, sentenceTypeName);
		AnnotationIndex<Annotation> annIdx = cas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		// buffer the properties to index them after iterating all of them
		// otherwise, a concurrent modification exception would occur
		// also, remove the old property array without the lemma
		List<FSArray> addBuffer = new LinkedList<FSArray>();
		List<FSArray> remBuffer = new LinkedList<FSArray>();

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			FSIterator<Annotation> tokenIt = cas.createFilteredIterator(
			    annIdx.subiterator(sentence, true, true), tokenConstraint);

			while (tokenIt.hasNext()) {
				SyntaxAnnotation tokenAnn = (SyntaxAnnotation) tokenIt.next();
				String token = tokenAnn.getCoveredText();
				String posTag = PartOfSpeechAnnotator.getPoSTag(tokenAnn)
				    .toLowerCase();
				LemmataEntry lemmata = lemmatizer.lemmatizeByLexiconAndRules(
				    token, posTag);
				String lemma = null;
				Collection<Lemma> lemmaColl = lemmata.getLemmas();

				for (Lemma l : lemmaColl) {
					if (posTag.equals(l.getPos().toLowerCase())) {
						lemma = l.getLemma();
						break;
					}
				}

				if (lemma == null && lemmaColl.size() == 1) {
					for (Lemma l : lemmaColl)
						lemma = l.getLemma();
				}

				if (lemma == null) {
					lemma = lemmata.lemmasToString();

					if (lemma.contains(LemmataEntry.lemmaSeparator)) {
						logger.log(Level.WARNING, "no unique lemma for '" +
						                          token + "' [" + posTag +
						                          "]: " + lemmata.toString());
						lemma = token;
					}
				}

				FSArray props = tokenAnn.getProperties();
				int len = props.size();
				FSArray newProps = new FSArray(cas, len + 1);

				for (int i = 0; i < len; i++)
					newProps.set(i, props.get(i));

				Property lemmaProp = new Property(cas);
				lemmaProp.setName(LEMMA_PROPERTY_NAME);
				lemmaProp.setValue(lemma);
				newProps.set(len, lemmaProp);
				tokenAnn.setProperties(newProps);
				addBuffer.add(newProps);
				remBuffer.add(props);
			}
		}

		for (FSArray p : remBuffer)
			p.removeFromIndexes(cas);
		for (FSArray p : addBuffer)
			p.addToIndexes(cas);

	}

	@Override
	public void destroy() {
		this.lemmatizer = null;
	}

}
