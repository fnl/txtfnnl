package txtfnnl.uima.analysis_component;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
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

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;

public class TestLinkGrammarAnnotator {
    private static AnalysisEngineDescription annotatorDesc;
    private static AnalysisEngine sentenceAnnotator;
    private static AnalysisEngine linkGrammarAnnotator;
    private JCas baseJCas;
    private JCas textJCas;

    @Before
    public void setUp() throws Exception {
        // set up AE descriptor under test
        if (sentenceAnnotator == null)
            sentenceAnnotator =
                AnalysisEngineFactory.createAnalysisEngine(SentenceAnnotator.configure(),
                    Views.CONTENT_TEXT.toString());
        if (annotatorDesc == null) annotatorDesc = LinkGrammarAnnotator.configure();
        if (linkGrammarAnnotator == null)
            linkGrammarAnnotator =
                AnalysisEngineFactory.createAnalysisEngine(annotatorDesc,
                    Views.CONTENT_TEXT.toString());
        baseJCas = sentenceAnnotator.newJCas();
        textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
    }

    @Test
    public void producesConstituents() throws AnalysisEngineProcessException {
        textJCas
            .setDocumentText("ARL, a regulator of cell death localized inside the nucleus, has been shown to bind the p53 promoter.");
        sentenceAnnotator.process(baseJCas.getCas());
        linkGrammarAnnotator.process(baseJCas.getCas());
        final AnnotationIndex<Annotation> idx = textJCas.getAnnotationIndex(SyntaxAnnotation.type);
        final FSIterator<Annotation> it = SyntaxAnnotation.getIterator(textJCas);
        final String[][] annotations =
            new String[][] {
                // {
                // "S",
                // "ARL, a regulator of cell death localized inside the nucleus, has been shown to bind the p53 promoter."
                // },
                { "NP", "ARL, a regulator of cell death localized inside the nucleus," },
                { "NP", "ARL" },
                { "NP", "a regulator of cell death localized inside the nucleus" },
                { "NP", "a regulator" }, { "PP", "of cell death localized inside the nucleus" },
                { "NP", "of cell death" }, { "VP", "localized inside the nucleus" },
                { "PP", "inside the nucleus" }, { "NP", "the nucleus" },
                { "VP", "has been shown to bind the p53 promoter" },
                { "VP", "been shown to bind the p53 promoter" },
                { "VP", "shown to bind the p53 promoter" }, { "VP", "to bind the p53 promoter" },
                { "VP", "bind the p53 promoter" }, { "NP", "the p53 promoter" }, };
        int a = 0;
        while (it.hasNext()) {
            final SyntaxAnnotation ann = (SyntaxAnnotation) it.next();
            if (LinkGrammarAnnotator.URI.equals(ann.getAnnotator())) {
                Assert.assertEquals(annotations[a][0], ann.getIdentifier());
                Assert.assertEquals(annotations[a++][1], ann.getCoveredText());
            } else {
                ensureTreeStructure(idx, (SentenceAnnotation) ann);
            }
        }
        Assert.assertEquals(annotations.length, a);
    }

    @Test
    public void withBrackets() throws AnalysisEngineProcessException, CASException {
        for (final String brackets : new String[] { "()", "[]", "{}" }) {
            final String open = brackets.substring(0, 1);
            final String close = brackets.substring(1, 2);
            final String npInBrackets = open + "a reg\u00FClator of cell death" + close;
            textJCas.setDocumentText("ARL " + npInBrackets + " binds to the p53 promoter.");
            sentenceAnnotator.process(baseJCas.getCas());
            linkGrammarAnnotator.process(baseJCas.getCas());
            final AnnotationIndex<Annotation> idx =
                textJCas.getAnnotationIndex(SyntaxAnnotation.type);
            final FSIterator<Annotation> it = SyntaxAnnotation.getIterator(textJCas);
            final String[][] annotations =
                new String[][] {
                    // { "S", "ARL " + npInBrackets +
                    // " binds to the p53 promoter." },
                    { "NP", "ARL " + npInBrackets }, { "NP", "ARL" }, { "NP", npInBrackets },
                    { "NP", open + "a reg\u00FClator" }, { "PP", "of cell death" },
                    { "VP", "binds to the p53 promoter" }, { "PP", "to the p53 promoter" },
                    { "NP", "the p53 promoter" }, };
            int a = 0;
            while (it.hasNext()) {
                final SyntaxAnnotation ann = (SyntaxAnnotation) it.next();
                if (LinkGrammarAnnotator.URI.equals(ann.getAnnotator())) {
                    Assert.assertEquals(
                        brackets + " a=" + a + ": " + annotations[a][0] + " != " +
                            ann.getIdentifier() + " in '" + annotations[a][1] + "' found '" +
                            ann.getCoveredText() + "' [" + ann.getBegin() + ":" + ann.getEnd() +
                            "]", annotations[a][0], ann.getIdentifier());
                    Assert.assertEquals(brackets + " a=" + a, annotations[a++][1],
                        ann.getCoveredText());
                } else {
                    ensureTreeStructure(idx, (SentenceAnnotation) ann);
                }
            }
            Assert.assertEquals(annotations.length, a);
            textJCas.reset();
            baseJCas.reset();
            textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
        }
    }

    @Test
    public void onALongSentence() throws AnalysisEngineProcessException {
        final String testString =
            "Here, we identify the c-Myc transcription factor as a direct mediator of telomerase activation in primary human fibroblasts through its ability to specifically induce TERT gene expression.";
        textJCas.setDocumentText(testString);
        sentenceAnnotator.process(baseJCas.getCas());
        linkGrammarAnnotator.process(baseJCas.getCas());
        final AnnotationIndex<Annotation> idx = textJCas.getAnnotationIndex(SyntaxAnnotation.type);
        final FSIterator<Annotation> it = SentenceAnnotation.getIterator(textJCas);
        int count = 0;
        while (it.hasNext()) {
            ensureTreeStructure(idx, (SentenceAnnotation) it.next());
            ++count;
        }
        Assert.assertEquals(1, count);
    }

    @Test
    public void onAnotherLongSentence() throws UIMAException, IOException {
        final String testString =
            "The inability of TERT overexpression to substitute for Myc in the REF cooperation "
                + "assay suggests that the oncogenic actions of c-Myc extend beyond the "
                + "activation of TERT gene expression and telomerase activity.";
        annotatorDesc = LinkGrammarAnnotator.configure(30);
        linkGrammarAnnotator =
            AnalysisEngineFactory.createAnalysisEngine(annotatorDesc,
                Views.CONTENT_TEXT.toString());
        baseJCas = sentenceAnnotator.newJCas();
        textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
        textJCas.setDocumentText(testString);
        sentenceAnnotator.process(baseJCas.getCas());
        linkGrammarAnnotator.process(baseJCas.getCas());
        final AnnotationIndex<Annotation> idx = textJCas.getAnnotationIndex(SyntaxAnnotation.type);
        final FSIterator<Annotation> it = SentenceAnnotation.getIterator(textJCas);
        int count = 0;
        while (it.hasNext()) {
            ensureTreeStructure(idx, (SentenceAnnotation) it.next());
            ++count;
        }
        Assert.assertEquals(1, count);
    }

    @Test
    public void doesNotHangForever() throws UIMAException, IOException {
        AnalysisEngine lga =
            AnalysisEngineFactory.createPrimitive(LinkGrammarAnnotator.configure(1));
        baseJCas = sentenceAnnotator.newJCas();
        textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
        textJCas
            .setDocumentText("Original article Telomerase reverse transcriptase gene is a direct target of c-Myc but is not functionally equivalent in cellular transformation Roger A Greenberg 1,b , Rónán C O'Hagan 2,b , Hongyu Deng 1 , Qiurong Xiao 5 , Steven R Hann 5 , Robert R Adams 6 , Serge Lichtsteiner 6 , Lynda Chin 2,4 , Gregg B Morin 6 and Ronald A DePinho 2,3,a 1 Department of Microbiology & Immunology, Albert Einstein College of Medicine, 1300 Morris Park Avenue, Bronx, New York 10461, USA.");
        sentenceAnnotator.process(baseJCas.getCas());
        lga.process(baseJCas.getCas());
        final AnnotationIndex<Annotation> idx = textJCas.getAnnotationIndex(SyntaxAnnotation.type);
        final FSIterator<Annotation> it = SentenceAnnotation.getIterator(textJCas);
        int count = 0;
        while (it.hasNext()) {
            final SentenceAnnotation ann = (SentenceAnnotation) it.next();
            final AnnotationTreeNode<Annotation> root = idx.tree(ann).getRoot();
            final Annotation rootAnn = root.get();
            Assert.assertEquals("Sentence", ((TextAnnotation) rootAnn).getIdentifier());
            Assert.assertEquals(ann.getBegin(), rootAnn.getBegin());
            Assert.assertEquals(ann.getEnd(), rootAnn.getEnd());
            ++count;
        }
        Assert.assertEquals(1, count);
    }

    private void ensureTreeStructure(AnnotationIndex<Annotation> idx, SentenceAnnotation ann) {
        final AnnotationTreeNode<Annotation> root = idx.tree(ann).getRoot();
        final Annotation rootAnn = root.get();
        Assert.assertEquals("Sentence", ((TextAnnotation) rootAnn).getIdentifier());
        Assert.assertEquals(ann.getBegin(), rootAnn.getBegin());
        Assert.assertEquals(ann.getEnd(), rootAnn.getEnd());
        Assert.assertNotSame("sentence '" + ann.getCoveredText() +
            "' has no child nodes / node count = 0 (" + root.getChildCount() + ")", 0, root.getChildCount());
    }
}
