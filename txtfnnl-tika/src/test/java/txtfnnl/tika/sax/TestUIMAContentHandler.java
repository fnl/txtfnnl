package txtfnnl.tika.sax;

import static org.junit.Assert.*;

import org.apache.tika.metadata.Metadata;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.util.JCasUtil;
import org.xml.sax.ContentHandler;

import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.uima.tcas.DocumentAnnotation;

public class TestUIMAContentHandler {

	UIMAContentHandler handler;
	ContentHandler mock;

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
	}

	@Test
	public void testAddMetadata() {
		Metadata metadata = new Metadata();
		metadata.add("test_name", "test_value");
		handler.addMetadata(metadata);
		int count = 0;

		for (DocumentAnnotation ann : JCasUtil.select(handler.getView(),
		    DocumentAnnotation.class)) {
			assertEquals("http://tika.apache.org/", ann.getAnnotator());
			assertEquals("test_name", ann.getNamespace());
			assertEquals("test_value", ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.0000001);
			assertNull(ann.getProperties());
			count++;
		}

		assertEquals(1, count);
	}

}
