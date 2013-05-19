package txtfnnl.uima.analysis_component;

import java.util.Collection;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;

import edu.ucdenver.ccp.nlp.biolemmatizer.BioLemmatizer;
import edu.ucdenver.ccp.nlp.biolemmatizer.LemmataEntry;
import edu.ucdenver.ccp.nlp.biolemmatizer.LemmataEntry.Lemma;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * The CCP <a href="http://biolemmatizer.sourceforge.net/">BioLemmatizer</a>, wrapped as an AE for
 * the <b><code>txtfnnl</b></code> pipeline.
 * <p>
 * Requires no particular configuration, but does require at least prior tokenization of the input
 * CAS (i.e., the presence of {@link TokenAnnotation TokenAnnotations}), that should have been PoS
 * tagged, too. The lemmas are set in the <code>:stem</code> property of the tokens.
 * 
 * @author Florian Leitner
 */
public class BioLemmatizerAnnotator extends JCasAnnotator_ImplBase {
  /**
   * The annotator's URI special case: lemmas are set directly on the tokens, so there is no actual
   * URI, NAMESPACE, or IDENTIFIER used on the annotations.
   */
  public static final String URI = "http://biolemmatizer.sourceforge.net/";
  protected Logger logger;
  private BioLemmatizer lemmatizer;

  public static class Builder extends AnalysisComponentBuilder {
    public Builder() {
      super(BioLemmatizerAnnotator.class);
    }
  }

  /** Configure a BioLemmatizer AE Builder. */
  public static Builder configure() {
    return new Builder();
  }

  /**
   * Initializes the current instance with the given context. Note: Do all initialization in this
   * method, do not use the constructor.
   */
  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    lemmatizer = new BioLemmatizer();
    logger.log(Level.CONFIG, "BioLemmatizer initialized");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    final FSIterator<Annotation> tokenIt = TokenAnnotation.getIterator(jcas);
    while (tokenIt.hasNext()) {
      final TokenAnnotation tokenAnn = (TokenAnnotation) tokenIt.next();
      final String token = tokenAnn.getCoveredText();
      String posTag;
      try {
        posTag = tokenAnn.getPos().toLowerCase();
      } catch (final NullPointerException ex) {
        String msg = String.format("token '%s' has no PoS tag", token);
        throw new AnalysisEngineProcessException(new AssertionError(msg));
      }
      tokenAnn.setStem(lemmatize(token, posTag));
    }
  }

  /**
   * Find the best possible lemma for the given result.
   * 
   * @param token that was lemmatized
   * @param posTag of the token
   * @return the best possible (all lower-cased) lemma
   */
  private String lemmatize(String token, String posTag) {
    final LemmataEntry lemmata = lemmatizer.lemmatizeByLexiconAndRules(token, posTag);
    // to find the most likely correct lemma, we need to poke around
    // in the LemmataEntry...
    String lemma = null, alt = null;
    final Collection<Lemma> lemmaColl = lemmata.getLemmas();
    final String pos = posTag.toLowerCase();
    // if we have a a lemma for a matching Penn PoS tag, set it;
    // if just the PoS tag matches (but it is not a Penn tag), assign
    // the lemma as a possible alternative
    for (final Lemma l : lemmaColl) {
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
      // if no lemma was found, check if we found an alt
      if (alt == null) {
        // if not, try using the lemmata's representation
        lemma = lemmata.lemmasToString();
        if (lemma.contains(LemmataEntry.lemmaSeparator)) {
          // if there is no unique lemma representation either,
          // use the lower-case form of the token as lemma
          logger.log(Level.FINE, "no unique lemma for ''{0}'' [{1}]: {2}", new Object[] { token,
              posTag, lemmata.toString() });
          lemma = token;
        }
      } else {
        // use the found alternative for the PoS
        logger.log(Level.FINE, "using alt lemma for ''{0}'' [{1}]: {2}", new Object[] { token,
            posTag, alt });
        lemma = alt;
      }
    }
    return lemma.toLowerCase();
  }

  @Override
  public void destroy() {
    super.destroy();
    lemmatizer = null;
  }
}
