/**
 * 
 */
package txtfnnl.uima.analysis_component;

import static org.junit.Assert.*;

import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.cas.text.AnnotationTreeNode;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class TestLinkGrammarAnnotator {

	private AnalysisEngineDescription annotatorDesc;
	private AnalysisEngine sentenceAnnotator;
	private AnalysisEngine linkGrammarAnnotator;
	private JCas baseJCas;
	private JCas textJCas;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		DisableLogging.enableLogging(Level.WARNING);

		// set up AE descriptor under test
		sentenceAnnotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPSentenceAEDescriptor");
		annotatorDesc = AnalysisEngineFactory
		    .createPrimitiveDescription(LinkGrammarAnnotator.class);
		linkGrammarAnnotator = AnalysisEngineFactory.createAnalysisEngine(
		    annotatorDesc, Views.CONTENT_TEXT.toString());
		baseJCas = sentenceAnnotator.newJCas();
		textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
	}

	@Test
	public void testConstituents() throws AnalysisEngineProcessException {
		textJCas
		    .setDocumentText("ARL, a regulator of cell death localized inside the nucleus, has been shown to bind the p53 promoter.");
		sentenceAnnotator.process(baseJCas.getCas());
		linkGrammarAnnotator.process(baseJCas.getCas());
		AnnotationIndex<Annotation> idx = textJCas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		FSIterator<Annotation> it = idx.iterator();
		String[][] annotations = new String[][] {
//		    {
//		        "S",
//		        "ARL, a regulator of cell death localized inside the nucleus, has been shown to bind the p53 promoter." },
		    {
		        "NP",
		        "ARL, a regulator of cell death localized inside the nucleus," },
		    { "NP", "ARL" },
		    { "NP", "a regulator of cell death localized inside the nucleus" },
		    { "NP", "a regulator" },
		    { "PP", "of cell death localized inside the nucleus" },
		    { "NP", "of cell death" },
		    { "VP", "localized inside the nucleus" },
		    { "PP", "inside the nucleus" },
		    { "NP", "the nucleus" },
		    { "VP", "has been shown to bind the p53 promoter" },
		    { "VP", "been shown to bind the p53 promoter" },
		    { "VP", "shown to bind the p53 promoter" },
		    { "VP", "to bind the p53 promoter" },
		    { "VP", "bind the p53 promoter" },
		    { "NP", "the p53 promoter" }, };
		int a = 0;

		while (it.hasNext()) {
			SyntaxAnnotation ann = (SyntaxAnnotation) it.next();

			if (LinkGrammarAnnotator.URI.equals(ann.getAnnotator())) {
				assertEquals(annotations[a][0], ann.getIdentifier());
				assertEquals(annotations[a++][1], ann.getCoveredText());
			} else {
				ensureTreeStructure(idx, ann);
			}
		}

		assertEquals(annotations.length, a);
	}

	@Test
	public void testBrackets() throws AnalysisEngineProcessException,
	        CASException {
		for (String brackets : new String[] { "()", "[]", "{}" }) {
			String open = brackets.substring(0, 1);
			String close = brackets.substring(1, 2);
			String npInBrackets = open + "a regülator of cell death" + close;
			textJCas.setDocumentText("ARL " + npInBrackets +
			                         " binds to the p53 promoter.");
			sentenceAnnotator.process(baseJCas.getCas());
			linkGrammarAnnotator.process(baseJCas.getCas());
			AnnotationIndex<Annotation> idx = textJCas
			    .getAnnotationIndex(SyntaxAnnotation.type);
			FSIterator<Annotation> it = idx.iterator();
			String[][] annotations = new String[][] {
//			    { "S", "ARL " + npInBrackets + " binds to the p53 promoter." },
			    { "NP", "ARL " + npInBrackets },
			    { "NP", "ARL" },
			    { "NP", npInBrackets },
			    { "NP", open + "a regülator" },
			    { "PP", "of cell death" },
			    { "VP", "binds to the p53 promoter" },
			    { "PP", "to the p53 promoter" },
			    { "NP", "the p53 promoter" }, };
			int a = 0;

			while (it.hasNext()) {
				SyntaxAnnotation ann = (SyntaxAnnotation) it.next();

				if (LinkGrammarAnnotator.URI.equals(ann.getAnnotator())) {
					assertEquals(
					    brackets + " a=" + a + ": " + annotations[a][0] +
					            " != " + ann.getIdentifier() + " in '" +
					            annotations[a][1] + "' found '" +
					            ann.getCoveredText() + "' [" + ann.getBegin() +
					            ":" + ann.getEnd() + "]", annotations[a][0],
					    ann.getIdentifier());
					assertEquals(brackets + " a=" + a, annotations[a++][1],
					    ann.getCoveredText());
				} else {
					ensureTreeStructure(idx, ann);
				}
			}

			assertEquals(annotations.length, a);
			textJCas.reset();
			baseJCas.reset();
			textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
		}
	}

	@Test
	public void testLongSentence() throws AnalysisEngineProcessException {
		String testString = "Here, we identify the c-Myc transcription factor as a direct mediator of telomerase activation in primary human fibroblasts through its ability to specifically induce TERT gene expression.";
		textJCas.setDocumentText(testString);
		sentenceAnnotator.process(baseJCas.getCas());
		linkGrammarAnnotator.process(baseJCas.getCas());
		AnnotationIndex<Annotation> idx = textJCas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		FSIterator<Annotation> it = SentenceAnnotator.getSentenceIterator(
		    textJCas, SentenceAnnotator.SENTENCE_TYPE_NAME);
		int count = 0;

		while (it.hasNext()) {
			ensureTreeStructure(idx, (SyntaxAnnotation) it.next());
			++count;
		}
		
		assertEquals(1, count);
	}

	private void ensureTreeStructure(AnnotationIndex<Annotation> idx,
                                     SyntaxAnnotation ann) {
	    AnnotationTreeNode<Annotation> root = idx.tree(ann).getRoot();
	    SyntaxAnnotation rootAnn = (SyntaxAnnotation) root.get();
	    assertEquals("Sentence", rootAnn.getIdentifier());
	    assertEquals(ann.getOffset(), rootAnn.getOffset());
	    assertTrue(root.getChildCount() > 0);
    }

}
