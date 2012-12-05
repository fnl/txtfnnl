package txtfnnl.uima.analysis_component;

import java.io.IOException;
import java.util.Collection;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.factory.AnalysisEngineFactory;

import edu.ucdenver.ccp.nlp.biolemmatizer.BioLemmatizer;
import edu.ucdenver.ccp.nlp.biolemmatizer.LemmataEntry;
import edu.ucdenver.ccp.nlp.biolemmatizer.LemmataEntry.Lemma;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * The CCP {@link http://biolemmatizer.sourceforge.net/ BioLemmatizer} as an
 * AE for the txtfnnl pipeline.
 * 
 * Requires no particular configuration. Requires prior tokenization of the
 * input CAS (i.e., the presence of {@link TokenAnnotation}s).
 * 
 * @author Florian Leitner
 */
public class BioLemmatizerAnnotator extends JCasAnnotator_ImplBase {

	/**
	 * The annotator's URI (for the annotations) set by this AE.
	 * 
	 * Special case: lemmas are set directly on the tokens, so there is no
	 * actual URI, NAMESPACE, or IDENTIFIER used.
	 */
	public static final String URI = "http://biolemmatizer.sourceforge.net/";

	protected Logger logger;

	private BioLemmatizer lemmatizer;

	public static AnalysisEngineDescription configure() throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(BioLemmatizerAnnotator.class);
	}

	/**
	 * Initializes the current instance with the given context.
	 * 
	 * Note: Do all initialization in this method, do not use the constructor.
	 */
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();
		lemmatizer = new BioLemmatizer();
		logger.log(Level.INFO, "BioLemmatizer initialized");
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas base = jcas;
		try {
			jcas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSIterator<Annotation> tokenIt = TokenAnnotation.getIterator(jcas);

		while (tokenIt.hasNext()) {
			TokenAnnotation tokenAnn = (TokenAnnotation) tokenIt.next();
			String token = tokenAnn.getCoveredText();
			String posTag;

			try {
				posTag = tokenAnn.getPos().toLowerCase();
			} catch (NullPointerException ex) {
				Error e = new AssertionError("token '" + token + "' in '" + base.getSofaDataURI() +
				                             "' has no PoS tag");
				throw new AnalysisEngineProcessException(e);
			}
			
			LemmataEntry lemmata = lemmatizer.lemmatizeByLexiconAndRules(token, posTag);

			// to find the most likely lemma, we need to poke around...
			String lemma = null, alt = null;
			Collection<Lemma> lemmaColl = lemmata.getLemmas();
			String pos = posTag.toLowerCase();

			// if we have a a lemma for the matching Penn PoS, set it;
			// if just the PoS tag matches, assign the lemma as a possible
			// alternative
			for (Lemma l : lemmaColl) {
				if (pos.equals(l.getPos().toLowerCase())) {
					if ("PennPOS".equals(l.getTagSetName())) {
						lemma = l.getLemma();
						break;
					} else {
						alt = l.getLemma();
					}
				}
			}

			if (lemma == null) {
				// if no lemma was found, check if we have an alternative
				// lemma
				if (alt == null) {
					// if not, use the lemma representation
					lemma = lemmata.lemmasToString();

					if (lemma.contains(LemmataEntry.lemmaSeparator)) {
						// if there is no unique lemma representation either,
						// use the lower-case form of the token as lemma
						logger.log(Level.FINE, "no unique lemma for '" + token + "' [" + posTag +
						                       "]: " + lemmata.toString());
						lemma = token.toLowerCase();
					}
				} else {
					// use the found alternative for the PoS
					logger.log(Level.FINE, "using alt lemma for '" + token + "' [" + posTag +
					                       "]: " + alt);
					lemma = alt;
				}
			}

			tokenAnn.setStem(lemma);
		}
	}

	@Override
	public void destroy() {
		lemmatizer = null;
	}

}
