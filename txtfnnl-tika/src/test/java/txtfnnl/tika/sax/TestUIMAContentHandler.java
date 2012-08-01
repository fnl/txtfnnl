package txtfnnl.tika.sax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

import org.easymock.EasyMock;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.DocumentAnnotation;
import txtfnnl.uima.tcas.StructureAnnotation;

public class TestUIMAContentHandler {

	UIMAContentHandler handler;
	ContentHandler mock;
	Level loglevel;

	/**
	 * Set up the test environment by attaching a mocked content handler to
	 * the HTML handler.
	 */
	@Before
	public void setUp() throws ResourceInitializationException {
		mock = EasyMock.createMock(ContentHandler.class);
		handler = new UIMAContentHandler(mock);
		assertNull(handler.getView());
		TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
		    .createTypeSystemDescription("txtfnnl.uima.typeSystemDescriptor");
		AnalysisEngine tikaAnnotator = AnalysisEngineFactory.createPrimitive(
		    TikaAnnotator.class, typeSystemDescription);
		handler.setView(tikaAnnotator.newJCas());
		handler.startDocument();
		loglevel = DisableLogging.disableLogging();
	}

	@After
	public void tearDown() {
		DisableLogging.enableLogging(loglevel);
	}

	@Test
	public void testAddMetadata() {
		Metadata metadata = new Metadata();
		metadata.add("test_name", "test_value");
		handler.addMetadata(metadata);
		int count = 0;

		for (DocumentAnnotation ann : JCasUtil.select(handler.getView(),
		    DocumentAnnotation.class)) {
			assertEquals(TikaAnnotator.URI, ann.getAnnotator());
			assertEquals("test_name", ann.getNamespace());
			assertEquals("test_value", ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.0000001);
			assertNull(ann.getProperties());
			count++;
		}

		assertEquals(1, count);
	}
	
	@Test
	public void testGreekCharacers() {
		handler.characters(new char[] { 'a', '\u0391', ' ', '\u03D7'}, 0, 4);
		handler.endDocument();
		JCas jcas = handler.getView();
		assertEquals("aAlpha kai", jcas.getDocumentText());
	}

	@Test
	public void testAddingElement() {
		String uri = "test_uri";
		String lName = "";
		String qName = "test_qname";
		AttributesImpl atts = new AttributesImpl();
		int count = 0;

		atts.addAttribute(uri, lName, qName, "test_type", "test_value");
		handler.characters("in".toCharArray(), 0, 2);
		handler.startElement(uri, lName, qName, atts);
		handler.characters("stuff".toCharArray(), 0, 5);
		handler.endElement(uri, lName, qName);
		handler.characters("out".toCharArray(), 0, 3);

		for (StructureAnnotation ann : JCasUtil.select(handler.getView(),
		    StructureAnnotation.class)) {
			assertEquals(2, ann.getBegin());
			assertEquals(7, ann.getEnd());
			assertEquals(TikaAnnotator.URI, ann.getAnnotator());
			assertEquals("test_uri#", ann.getNamespace());
			assertEquals("test_qname", ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.0000001);
			assertNotNull(ann.getProperties());
			assertEquals(1, ann.getProperties().size());
			Property p = ann.getProperties(0);
			assertEquals("test_qname", p.getName());
			assertEquals("test_value", p.getValue());
			count++;
		}

		assertEquals(1, count);
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
