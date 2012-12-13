package txtfnnl.tika.sax;

import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import org.easymock.EasyMock;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.StructureAnnotation;

public class TestUIMAContentHandler {
    UIMAContentHandler handler;
    ContentHandler mock;
    Level loglevel;

    /**
     * Set up the test environment by attaching a mocked content handler to the HTML handler.
     */
    @Before
    public void setUp() throws ResourceInitializationException {
        mock = EasyMock.createMock(ContentHandler.class);
        handler = new UIMAContentHandler(mock);
        handler.setAnnotatorURI("testURI");
        Assert.assertNull(handler.getView());
        final TypeSystemDescription typeSystemDescription =
            TypeSystemDescriptionFactory
                .createTypeSystemDescription("txtfnnl.uima.typeSystemDescriptor");
        final AnalysisEngine tikaAnnotator =
            AnalysisEngineFactory.createPrimitive(TikaAnnotator.class, typeSystemDescription);
        handler.setView(tikaAnnotator.newJCas());
        handler.startDocument();
        loglevel = DisableLogging.disableLogging();
    }

    @After
    public void tearDown() {
        DisableLogging.enableLogging(loglevel);
    }

    @Test
    public void testGreekCharacers() throws SAXException {
        final GreekLetterContentHandler greek = new GreekLetterContentHandler(handler);
        greek.characters(new char[] { 'a', '\u0391', ' ', '\u03D7' }, 0, 4);
        handler.endDocument();
        final JCas jcas = handler.getView();
        Assert.assertEquals("aAlpha kai", jcas.getDocumentText());
    }

    @Test
    public void testAddingElement() {
        final String uri = "test_uri";
        final String lName = "";
        final String qName = "test_qname";
        final AttributesImpl atts = new AttributesImpl();
        int count = 0;
        atts.addAttribute(uri, lName, qName, "test_type", "test_value");
        handler.characters("in".toCharArray(), 0, 2);
        handler.startElement(uri, lName, qName, atts);
        handler.characters("stuff".toCharArray(), 0, 5);
        handler.endElement(uri, lName, qName);
        handler.characters("out".toCharArray(), 0, 3);
        for (final StructureAnnotation ann : JCasUtil.select(handler.getView(),
            StructureAnnotation.class)) {
            Assert.assertEquals(2, ann.getBegin());
            Assert.assertEquals(7, ann.getEnd());
            Assert.assertEquals("testURI", ann.getAnnotator());
            Assert.assertEquals("test_uri#", ann.getNamespace());
            Assert.assertEquals("test_qname", ann.getIdentifier());
            Assert.assertEquals(1.0, ann.getConfidence(), 0.0000001);
            Assert.assertNotNull(ann.getProperties());
            Assert.assertEquals(1, ann.getProperties().size());
            final Property p = ann.getProperties(0);
            Assert.assertEquals("test_qname", p.getName());
            Assert.assertEquals("test_value", p.getValue());
            count++;
        }
        Assert.assertEquals(1, count);
    }

    @Test(expected = AssertionError.class)
    public void testUnconsumedElement() {
        handler.startElement("uri", "lName", "qName", null);
        handler.endDocument();
    }

    @Test(expected = AssertionError.class)
    public void testUnmatchedElement() {
        handler.startElement("uri", "e1", "e1", null);
        handler.endElement("uri", "e2", "e2");
    }
}
