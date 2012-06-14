package txtfnnl.tika.uima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.TextAnnotation;

public class TestTikaAnnotator {

	AnalysisEngine tikaAnnotator;
	JCas baseJCas;

	@Before
	public void setUp() throws ResourceInitializationException {
		// tikaAnnotator =
		// AnalysisEngineFactory.createAnalysisEngine("uima.tikaAEDescriptor");
		TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
		    .createTypeSystemDescription("txtfnnl.uima.typeSystemDescriptor");
		tikaAnnotator = AnalysisEngineFactory.createPrimitive(
		    TikaAnnotator.class, typeSystemDescription);
		baseJCas = tikaAnnotator.newJCas();
		DisableLogging.enableLogging(Level.WARNING);

	}

	@Test
	public void testProcessRequiresRawView() throws CASException {
		DisableLogging.disableLogging();
		assertThrows(baseJCas, "No sofaFS with name CONTENT_RAW found.",
		    CASRuntimeException.class);
		baseJCas.createView(Views.CONTENT_RAW.toString());
		assertThrows(baseJCas, "no SOFA data stream", AssertionError.class);
	}

	private void assertThrows(JCas cas, String message, Class<?> type) {
		boolean thrown = false;

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
	public void testProcessCreatesNewPlainTextView()
	        throws AnalysisEngineProcessException, CASException {
		JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
		jCas.setSofaDataString("text", "text/plain");
		tikaAnnotator.process(baseJCas);
		assertNotNull(baseJCas.getView(Views.CONTENT_TEXT.toString()));
		assertEquals("text", baseJCas.getView(Views.CONTENT_TEXT.toString()).getDocumentText());
	}

	@Test
	public void testProcessHTML() throws AnalysisEngineProcessException,
	        CASException {
		JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
		jCas.setSofaDataString("<html><body><p id=1>test</p></body></html>",
		    "text/html");
		tikaAnnotator.process(baseJCas);
		jCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
		int count = 0;

		for (TextAnnotation ann : JCasUtil.select(jCas, TextAnnotation.class)) {
			assertEquals(0, ann.getBegin());
			assertEquals(4, ann.getEnd());
			assertEquals("http://tika.apache.org/", ann.getAnnotator());
			assertEquals("http://www.w3.org/1999/xhtml", ann.getNamespace());
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

}
