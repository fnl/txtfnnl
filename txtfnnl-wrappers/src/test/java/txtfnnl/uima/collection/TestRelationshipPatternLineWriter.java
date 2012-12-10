package txtfnnl.uima.collection;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.utils.IOUtils;

public class TestRelationshipPatternLineWriter {

	RelationshipPatternLineWriter writer;
	AnalysisEngine ae;
	JCas jcas;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		writer = new RelationshipPatternLineWriter();
		ae = AnalysisEngineFactory.createPrimitive(RelationshipPatternLineWriter
		    .configure("http://purl.org/relationship/"));
		jcas = ae.newJCas();
	}

	@Test
	public void testInitializeUimaContext() throws UIMAException, IOException {
		for (String p : new String[] {
		    RelationshipPatternLineWriter.PARAM_OUTPUT_DIRECTORY,
		    RelationshipPatternLineWriter.PARAM_ENCODING }) {
			assertNull("Parameter " + p + " does not default to null.",
			    ae.getConfigParameterValue(p));

		}
		assertFalse((Boolean) ae
		    .getConfigParameterValue(RelationshipPatternLineWriter.PARAM_OVERWRITE_FILES));
		assertTrue((Boolean) ae
		    .getConfigParameterValue(RelationshipPatternLineWriter.PARAM_PRINT_TO_STDOUT));
	}

	@Test
	public void testIterateAnnotationsNull() {
		List<TextAnnotation[]> result = writer.listSeparateEntities(null);

		assertEquals(0, result.size());
	}

	@Test
	public void testIterateAnnotationsZero() {
		FSArray a = createTAArray(0);
		List<TextAnnotation[]> result = writer.listSeparateEntities(a);

		assertEquals(0, result.size());
	}

	@Test
	public void testIterateAnnotationsDefault() {
		FSArray a = createTAArray(2);
		List<TextAnnotation[]> result = writer.listSeparateEntities(a);

		assertEquals(2, result.size());
		assertEquals(1, result.get(0).length);
		assertEquals(1, result.get(1).length);
	}

	@Test
	public void testIterateAnnotationsThreeTypes() {
		List<TextAnnotation[]> result = writer.listSeparateEntities(createTAArray(3, true));

		assertEquals(1, result.size());
		assertEquals(3, result.get(0).length);
	}

	@Test
	public void testIterateAnnotationsMultipleTypes() {
		FSArray a = createTAArray(7, true);
		((TextAnnotation) a.get(0)).setIdentifier("a");
		((TextAnnotation) a.get(1)).setIdentifier("b");
		((TextAnnotation) a.get(2)).setIdentifier("c");
		((TextAnnotation) a.get(3)).setIdentifier("a");
		((TextAnnotation) a.get(4)).setIdentifier("c");
		((TextAnnotation) a.get(5)).setIdentifier("b");
		((TextAnnotation) a.get(6)).setIdentifier("c");
		Set<String> check = new HashSet<String>();
		check.add("a");
		check.add("b");
		check.add("c");
		List<TextAnnotation[]> result = writer.listSeparateEntities(a);

		assertEquals(12, result.size());

		for (int i = 0; i < 12; i++) {
			assertEquals(3, result.get(i).length);
			Set<String> c = new HashSet<String>(check);

			for (TextAnnotation ta : result.get(i)) {
				assertTrue(c.remove(ta.getIdentifier()));
			}
		}
	}

	@Test
	public void testRegroupEntitiesZero() {
		FSArray a = new FSArray(jcas, 0);
		Map<String, List<TextAnnotation>> result = writer.groupEntities(a);

		assertEquals(0, result.size());
	}

	@Test
	public void testRegroupEntitiesNull() {
		Map<String, List<TextAnnotation>> result = writer.groupEntities(null);

		assertEquals(0, result.size());
	}

	@Test
	public void testRegroupEntitiesDefault() {
		FSArray a = createTAArray(2);
		Map<String, List<TextAnnotation>> result = writer.groupEntities(a);

		assertEquals(1, result.size());
		assertTrue(result.containsKey("ns:id"));
		assertEquals(2, result.get("ns:id").size());
		assertEquals(a.get(0), result.get("ns:id").get(1));
		assertEquals(a.get(1), result.get("ns:id").get(0));
	}

	@Test
	public void testOrderEntitiesZero() {
		TextAnnotation[] a0, a3;
		a0 = writer.orderEntities(new TextAnnotation[0]);
		a3 = writer.orderEntities(new TextAnnotation[3]);

		assertNotNull(a0);
		assertNotNull(a3);
		assertEquals(0, a0.length);
		assertNull(a3[0]);
	}

	@Test
	public void testOrderEntitiesNull() {
		TextAnnotation[] a = writer.orderEntities(null);

		assertNull(a);
	}

	@Test
	public void testOrderEntitiesDefault() {
		TextAnnotation[] a = new TextAnnotation[3];
		a[0] = getTextAnnotation("id-1");
		a[1] = getTextAnnotation("id-2");
		a[2] = getTextAnnotation("id-3");
		a[2].setBegin(0);
		a[2].setEnd(10);
		a[1].setBegin(1);
		a[1].setEnd(9);
		a[0].setBegin(10);
		a[0].setEnd(12);
		a = writer.orderEntities(a);

		assertNotNull(a);
		assertEquals(3, a.length);
		assertEquals(0, a[0].getBegin());
		assertEquals("id-3", a[0].getIdentifier());
		assertEquals(1, a[1].getBegin());
		assertEquals("id-2", a[1].getIdentifier());
		assertEquals(10, a[2].getBegin());
		assertEquals("id-1", a[2].getIdentifier());
	}

	@Test
	public void testOrderEntitiesOverlapping() {
		TextAnnotation[] a = new TextAnnotation[3];
		a[0] = getTextAnnotation("id-1");
		a[1] = getTextAnnotation("id-2");
		a[2] = getTextAnnotation("id-3");
		a[2].setBegin(0);
		a[2].setEnd(10);
		a[1].setBegin(1);
		a[1].setEnd(9);
		a[0].setBegin(5);
		a[0].setEnd(10);
		a = writer.orderEntities(a);

		assertNull(a);
	}

	@Test
	public void testIterateOverlappingEntities() {
		FSArray test = createTAArray(5);
		TextAnnotation[] a = new TextAnnotation[5];
		a[0] = (TextAnnotation) test.get(0);
		a[1] = (TextAnnotation) test.get(1);
		a[2] = (TextAnnotation) test.get(2);
		a[3] = (TextAnnotation) test.get(3);
		a[4] = (TextAnnotation) test.get(4);
		a[0].setIdentifier("type-0");
		a[0].setBegin(0);
		a[0].setEnd(10);
		a[1].setIdentifier("type-0");
		a[1].setBegin(0);
		a[1].setEnd(15);
		a[2].setIdentifier("type-1");
		a[2].setBegin(0);
		a[2].setEnd(5);
		a[3].setIdentifier("type-1");
		a[3].setBegin(7);
		a[3].setEnd(12);
		a[4].setIdentifier("type-2");
		a[4].setBegin(10);
		a[4].setEnd(15);
		List<TextAnnotation[]> result = writer.listSeparateEntities(test);

		assertEquals(2, result.size());
		for (int i = 0; i < result.size(); ++i) {
			TextAnnotation[] ta = result.get(i);
			assertEquals(ta[0].getIdentifier(), "type-0");
			assertEquals(ta[1].getIdentifier(), "type-1");
			assertEquals(ta[2].getIdentifier(), "type-2");
		}
	}

	private FSArray createTAArray(int size) {
		return createTAArray(size, false);
	}

	private FSArray createTAArray(int size, boolean randomId) {
		FSArray a = new FSArray(jcas, size);

		for (int i = 0; i < size; i++) {
			a.set(i, getTextAnnotation(randomId ? UUID.randomUUID().toString() : "id"));
		}

		return a;
	}

	private TextAnnotation getTextAnnotation(String id) {
		TextAnnotation ta = new TextAnnotation(jcas);
		ta.setIdentifier(id);
		ta.setNamespace("ns:");
		return ta;
	}

	@Test
	public void testClean() {
		assertEquals("a \n\nb", writer.clean(" \n  a  \n\n b\n "));
	}

	@Test
	public void testProcessRelationship() throws AnalysisEngineProcessException {
		String test = "This is a sentence.";
		jcas.setDocumentText(test);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		RelationshipAnnotation relAnn = createDummyAnnotation(test, jcas);
		AnnotationIndex<Annotation> annIdx = jcas.getAnnotationIndex(SyntaxAnnotation.type);
		writer.printToStdout = true;
		writer.process(relAnn, test, annIdx,
		    RelationshipPatternLineWriter.makeConstituentSyntaxAnnotationConstraint(jcas), jcas);
		assertTrue(outputStream.toString(),
		    outputStream.toString().startsWith("[[ns:id]]" + test.substring(0, test.length() - 1)));
	}

	@Test
	public void testProcessInnerRelationship() throws AnalysisEngineProcessException {
		String test = "This is a sentence.";
		jcas.setDocumentText(test);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		RelationshipAnnotation relAnn = createDummyAnnotation(test, jcas, 5, 7); // is
		AnnotationIndex<Annotation> annIdx = jcas.getAnnotationIndex(SyntaxAnnotation.type);
		writer.printToStdout = true;
		writer.process(relAnn, test, annIdx,
		    RelationshipPatternLineWriter.makeConstituentSyntaxAnnotationConstraint(jcas), jcas);
		assertTrue(outputStream.toString(),
		    outputStream.toString().startsWith("This [[ns:id]] a sentence."));
	}

	RelationshipAnnotation createDummyAnnotation(String test, JCas aJCas) {
		return createDummyAnnotation(test, aJCas, 0, 0);
	}

	RelationshipAnnotation createDummyAnnotation(String text, JCas aJCas, int start, int end) {
		// sentence
		SentenceAnnotation sentAnn = new SentenceAnnotation(aJCas, 0, text.length());
		sentAnn.setNamespace(SentenceAnnotator.NAMESPACE);
		sentAnn.setIdentifier(SentenceAnnotator.IDENTIFIER);
		sentAnn.addToIndexes(aJCas);
		// entity
		SemanticAnnotation entityAnn = new SemanticAnnotation(aJCas, start, end);
		entityAnn.setNamespace("ns:");
		entityAnn.setIdentifier("id");
		entityAnn.addToIndexes(aJCas);
		// relationship
		RelationshipAnnotation relAnn = new RelationshipAnnotation(aJCas);
		relAnn.setNamespace("http://purl.org/relationship/");
		FSArray sources = new FSArray(aJCas, 1);
		sources.set(0, sentAnn);
		sources.addToIndexes(aJCas);
		FSArray targets = new FSArray(aJCas, 1);
		targets.set(0, entityAnn);
		targets.addToIndexes(aJCas);
		relAnn.setSources(sources);
		relAnn.setTargets(targets);
		relAnn.addToIndexes(aJCas);
		return relAnn;
	}

	@Test
	public void testProcessJCasToStdout() throws UIMAException, IOException {
		String input = "Hello World!";
		ByteArrayOutputStream outputStream = processHelper(input);
		assertTrue(outputStream.toString(), outputStream.toString()
		    .startsWith("[[ns:id]]" + input));
	}

	@Test
	public void testProcessJCasOutputDir() throws UIMAException, IOException {
		File tmpDir = IOUtils.mkTmpDir();
		File existing = new File(tmpDir, "test.txt.txt");
		existing.deleteOnExit();
		tmpDir.deleteOnExit();

		assertTrue(existing.createNewFile());

		ae = AnalysisEngineFactory.createPrimitive(RelationshipPatternLineWriter.configure(
		    "http://purl.org/relationship/", tmpDir, "UTF-32", false, false, 0));
		String input = "Hello World!";
		processHelper(input);
		File created = new File(tmpDir, "test.txt.2.txt");

		assertTrue(created.exists());

		FileInputStream fis = new FileInputStream(created);
		String result = IOUtils.read(fis, "UTF-32");

		assertTrue(result, result.startsWith("[[ns:id]]" + input));

		fis.close();
		existing.delete();
		created.delete();
		tmpDir.delete();
	}

	ByteArrayOutputStream processHelper(String input) throws CASException,
	        ResourceInitializationException, AnalysisEngineProcessException {
		JCas textCas = jcas.createView(Views.CONTENT_TEXT.toString());
		JCas rawCas = jcas.createView(Views.CONTENT_RAW.toString());
		rawCas.setSofaDataURI("test.txt", "mime");
		textCas.setDocumentText(input);
		createDummyAnnotation(input, textCas);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		ae.process(jcas);
		return outputStream;
	}

}
