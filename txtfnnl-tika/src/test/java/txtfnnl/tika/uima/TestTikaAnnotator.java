package txtfnnl.tika.uima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.DocumentAnnotation;
import txtfnnl.uima.tcas.StructureAnnotation;

public class TestTikaAnnotator {

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
	}

	AnalysisEngine getEngine(String encoding, boolean normalizeGreek, String xmlHandler)
	        throws UIMAException, IOException {
		AnalysisEngineDescription aed = TikaAnnotator.configure(encoding, normalizeGreek,
		    xmlHandler);
		return AnalysisEngineFactory.createPrimitive(aed);
	}

	AnalysisEngine getEngine() throws UIMAException, IOException {
		return getEngine(null, false, null);
	}

	@Test
	public void testConfiguration() throws UIMAException, IOException {
		AnalysisEngineDescription aed = TikaAnnotator.configure();
		aed.doFullValidation();
	}

	@Test
	public void testProcessRequiresRawView() throws UIMAException, IOException {
		DisableLogging.disableLogging();
		AnalysisEngine tikaAnnotator = getEngine();
		JCas baseJCas = tikaAnnotator.newJCas();

		assertThrows(baseJCas, "No sofaFS with name CONTENT_RAW found.", CASRuntimeException.class);
		baseJCas.createView(Views.CONTENT_RAW.toString());
		assertThrows(baseJCas, "no SOFA data stream", AssertionError.class);
	}

	private void assertThrows(JCas cas, String message, Class<?> type) throws UIMAException,
	        IOException {
		boolean thrown = false;
		AnalysisEngine tikaAnnotator = getEngine();

		try {
			tikaAnnotator.process(cas);
		} catch (AnalysisEngineProcessException e) {
			thrown = true;
			assertEquals(message, e.getCause().getMessage());
			assertEquals(type, e.getCause().getClass());
		} finally {
			assertTrue("expected exception not thrown", thrown);
		}
	}

	@Test
	public void testProcessCreatesNewPlainTextView() throws UIMAException, IOException {
		AnalysisEngine tikaAnnotator = getEngine(null, true, null);
		JCas baseJCas = tikaAnnotator.newJCas();

		JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
		jCas.setSofaDataString("text ß", "text/plain"); // latin small sharp S
		tikaAnnotator.process(baseJCas);
		assertNotNull(baseJCas.getView(Views.CONTENT_TEXT.toString()));
		assertEquals("text beta", baseJCas.getView(Views.CONTENT_TEXT.toString())
		    .getDocumentText());
	}

	@Test
	public void testProcessHTML() throws UIMAException, IOException {
		AnalysisEngine tikaAnnotator = getEngine();
		JCas baseJCas = tikaAnnotator.newJCas();
		JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());

		jCas.setSofaDataString("<html><body><p id=1>test</p></body></html>", "text/html");
		tikaAnnotator.process(baseJCas);
		jCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
		int count = 0;

		for (StructureAnnotation ann : JCasUtil.select(jCas, StructureAnnotation.class)) {
			assertEquals(0, ann.getBegin());
			assertEquals(4, ann.getEnd());
			assertEquals(TikaAnnotator.class.getName(), ann.getAnnotator());
			assertEquals("http://www.w3.org/1999/xhtml#", ann.getNamespace());
			assertEquals("p", ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.0000001);
			assertNotNull(ann.getProperties());
			assertEquals(1, ann.getProperties().size());
			Property p = ann.getProperties(0);
			assertEquals("id", p.getName());
			assertEquals("1", p.getValue());
			count++;
		}

		assertEquals(1, count);
	}

	@Test
	public void testProcessElsevierXML() throws UIMAException, IOException {
		AnalysisEngine tikaAnnotator = getEngine();
		JCas baseJCas = tikaAnnotator.newJCas();
		JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());

		jCas.setSofaDataString("<ce:para xmlns:ce=\"url\" val='1' >"
		                       + "<ce:para val='1' >test</ce:para>" + "again</ce:para>",
		    "text/xml");
		tikaAnnotator = AnalysisEngineFactory.createPrimitive(TikaAnnotator.configure(null, false,
		    "txtfnnl.tika.sax.ElsevierXMLContentHandler"));
		tikaAnnotator.process(baseJCas);
		jCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
		assertEquals("test\n\nagain", jCas.getDocumentText());
		int count = 0;

		for (StructureAnnotation ann : JCasUtil.select(jCas, StructureAnnotation.class)) {
			assertEquals("url#", ann.getNamespace());
			assertEquals("ce:para", ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.0000001);
			assertNotNull(ann.getProperties());
			assertEquals(1, ann.getProperties().size());
			Property p = ann.getProperties(0);
			assertEquals("val", p.getName());
			assertEquals("1", p.getValue());
			count++;
		}

		assertEquals(2, count);
	}

	@Test
	public void testHandleMetadata() throws UIMAException, IOException {
		AnalysisEngine tikaAnnotator = getEngine();
		JCas baseJCas = tikaAnnotator.newJCas();
		Metadata metadata = new Metadata();
		TikaAnnotator real = new TikaAnnotator();

		metadata.add("test_name", "test_value");
		real.handleMetadata(metadata, baseJCas);
		int count = 0;

		for (DocumentAnnotation ann : JCasUtil.select(baseJCas, DocumentAnnotation.class)) {
			assertEquals(real.getAnnotatorURI(), ann.getAnnotator());
			assertEquals("test_name", ann.getNamespace());
			assertEquals("test_value", ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.0000001);
			assertNull(ann.getProperties());
			count++;
		}

		assertEquals(1, count);
	}

	@Test
	public void testEncoding() throws CASRuntimeException, IOException, UIMAException {
		AnalysisEngine tikaAnnotator = getEngine("UTF-8", true, null);
		JCas baseJCas = tikaAnnotator.newJCas();
		JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
		File infile = new File("src/test/resources/encoding.html");

		jCas.setSofaDataURI("file:" + infile.getCanonicalPath(), null);
		tikaAnnotator.process(baseJCas);
		jCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
		String text = jCas.getDocumentText();
		assertTrue(text, text.indexOf("and HIF-1alpha. A new p300") > -1);
	}

}
