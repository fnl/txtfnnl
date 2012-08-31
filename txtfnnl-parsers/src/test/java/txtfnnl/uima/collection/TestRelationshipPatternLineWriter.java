package txtfnnl.uima.collection;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.LinkedList;
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
import org.apache.uima.cas.text.AnnotationTreeNode;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Offset;
import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.utils.IOUtils;

public class TestRelationshipPatternLineWriter {

	public static final String DESCRIPTOR = "txtfnnl.uima.relationshipPatternLineWriterDescriptor";

	RelationshipPatternLineWriter writer;
	AnalysisEngine ae;
	JCas jcas;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		writer = new RelationshipPatternLineWriter();
		ae = AnalysisEngineFactory.createAnalysisEngine(DESCRIPTOR,
		    RelationshipPatternLineWriter.PARAM_PRINT_TO_STDOUT, Boolean.TRUE);
		jcas = ae.newJCas();
	}

	@Test
	public void testInitializeUimaContext() throws UIMAException, IOException {
		for (String p : new String[] {
		    RelationshipPatternLineWriter.PARAM_OUTPUT_DIRECTORY,
		    RelationshipPatternLineWriter.PARAM_OVERWRITE_FILES,
		    // RelationshipPatternLineWriter.PARAM_PRINT_TO_STDOUT,
		    RelationshipPatternLineWriter.PARAM_ENCODING }) {
			assertNull("Parameter " + p + " does not default to null.",
			    ae.getConfigParameterValue(p));
		}
		String p = RelationshipPatternLineWriter.PARAM_PRINT_TO_STDOUT;
		AnalysisEngine check = AnalysisEngineFactory
		    .createAnalysisEngine(DESCRIPTOR);
		assertNull("Parameter " + p + " does not default to null.",
		    check.getConfigParameterValue(p));
	}

	@Test
	public void testIterateAnnotationsNull() {
		List<TextAnnotation[]> result = writer.iterateAnnotations(null);

		assertEquals(0, result.size());
	}

	@Test
	public void testIterateAnnotationsZero() {
		FSArray a = createTAArray(0);
		List<TextAnnotation[]> result = writer.iterateAnnotations(a);

		assertEquals(0, result.size());
	}

	@Test
	public void testIterateAnnotationsDefault() {
		FSArray a = createTAArray(2);
		List<TextAnnotation[]> result = writer.iterateAnnotations(a);

		assertEquals(2, result.size());
		assertEquals(1, result.get(0).length);
		assertEquals(1, result.get(1).length);
	}

	@Test
	public void testIterateAnnotationsThreeTypes() {
		List<TextAnnotation[]> result = writer
		    .iterateAnnotations(createTAArray(3, true));

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
		List<TextAnnotation[]> result = writer.iterateAnnotations(a);

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
		List<TextAnnotation[]> result = writer.iterateAnnotations(test);

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
			a.set(i, getTextAnnotation(randomId
			        ? UUID.randomUUID().toString()
			        : "id"));
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
	public void testFindPaths() {
		TextAnnotation[] ta = new TextAnnotation[3];
		jcas.setDocumentText("..0..1..2..");
		ta[0] = getTextAnnotation("id-0");
		ta[0].setBegin(2);
		ta[0].setEnd(3);
		ta[1] = getTextAnnotation("id-1");
		ta[1].setBegin(5);
		ta[1].setEnd(6);
		ta[2] = getTextAnnotation("id-2");
		ta[2].setBegin(8);
		ta[2].setEnd(9);
		SyntaxAnnotation r = new SyntaxAnnotation(jcas, 0, 11);
		SyntaxAnnotation n123 = new SyntaxAnnotation(jcas, 1, 10);
		SyntaxAnnotation n23 = new SyntaxAnnotation(jcas, 4, 10);
		SyntaxAnnotation n1 = new SyntaxAnnotation(jcas, 1, 3);
		SyntaxAnnotation n2 = new SyntaxAnnotation(jcas, 5, 7);
		SyntaxAnnotation n3 = new SyntaxAnnotation(jcas, 8, 10);
		SyntaxAnnotation nX = new SyntaxAnnotation(jcas, 9, 10);
		r.addToIndexes();
		n123.addToIndexes();
		n23.addToIndexes();
		n1.addToIndexes();
		n2.addToIndexes();
		n3.addToIndexes();
		nX.addToIndexes();
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		AnnotationTreeNode<Annotation> root = annIdx.tree(r).getRoot();
		List<List<AnnotationTreeNode<Annotation>>> paths = writer.findPaths(
		    ta, root);

		assertEquals(3, paths.size());
		assertEquals(3, paths.get(0).size());
		assertEquals(4, paths.get(1).size());
		assertEquals(4, paths.get(2).size());
		assertEquals(r, paths.get(0).get(0).get());
		assertEquals(r, paths.get(1).get(0).get());
		assertEquals(r, paths.get(2).get(0).get());
		assertEquals(n123, paths.get(0).get(1).get());
		assertEquals(n123, paths.get(1).get(1).get());
		assertEquals(n123, paths.get(2).get(1).get());
		assertEquals(n23, paths.get(1).get(2).get());
		assertEquals(n23, paths.get(2).get(2).get());
		assertEquals(n1, paths.get(0).get(2).get());
		assertEquals(n2, paths.get(1).get(3).get());
		assertEquals(n3, paths.get(2).get(3).get());
		assertEquals(".0", paths.get(0).get(2).get().getCoveredText());
		assertEquals("1.", paths.get(1).get(3).get().getCoveredText());
		assertEquals("2.", paths.get(2).get(3).get().getCoveredText());
	}

	private List<AnnotationTreeNode<Annotation>>
	        makePath(AnnotationTreeNode<Annotation>... nodes) {
		List<AnnotationTreeNode<Annotation>> p = new LinkedList<AnnotationTreeNode<Annotation>>();

		for (int i = 0; i < nodes.length; ++i)
			p.add(nodes[i]);

		return p;
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFindCommonRootDefault() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 3);
		SyntaxAnnotation common = new SyntaxAnnotation(jcas, 0, 2);
		SyntaxAnnotation other = new SyntaxAnnotation(jcas, 1, 2);
		root.addToIndexes();
		common.addToIndexes();
		other.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();
		AnnotationTreeNode<Annotation> commonNode = rootNode.getChild(0);
		AnnotationTreeNode<Annotation> otherNode = commonNode.getChild(0);
		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode, commonNode, otherNode));
		paths.add(makePath(rootNode, commonNode));
		paths.add(makePath(rootNode, commonNode, otherNode));
		assertEquals(1, writer.findCommonRoot(paths));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFindCommonRootZeroLengthFirstPath() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 3);
		SyntaxAnnotation common = new SyntaxAnnotation(jcas, 0, 2);
		SyntaxAnnotation other = new SyntaxAnnotation(jcas, 1, 2);
		root.addToIndexes();
		common.addToIndexes();
		other.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();
		AnnotationTreeNode<Annotation> commonNode = rootNode.getChild(0);
		AnnotationTreeNode<Annotation> otherNode = commonNode.getChild(0);
		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode, commonNode));
		paths.add(makePath());
		paths.add(makePath(rootNode, commonNode, otherNode));
		assertEquals(-1, writer.findCommonRoot(paths));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testFindCommonRootZeroLengthPaths() {
		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		assertEquals(-1, writer.findCommonRoot(paths));
	}

	@Test(expected = NullPointerException.class)
	public void testFindCommonRootNull() {
		assertEquals(-1, writer.findCommonRoot(null));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLongestCommonSpansDefault() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 10);
		SyntaxAnnotation a123 = new SyntaxAnnotation(jcas, 1, 9);
		SyntaxAnnotation a23 = new SyntaxAnnotation(jcas, 4, 9);
		SyntaxAnnotation a1 = new SyntaxAnnotation(jcas, 1, 4);
		SyntaxAnnotation a2 = new SyntaxAnnotation(jcas, 4, 7);
		SyntaxAnnotation a3 = new SyntaxAnnotation(jcas, 7, 9);
		root.addToIndexes();
		a123.addToIndexes();
		a23.addToIndexes();
		a1.addToIndexes();
		a2.addToIndexes();
		a3.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();
		AnnotationTreeNode<Annotation> n123 = rootNode.getChild(0);
		AnnotationTreeNode<Annotation> n1 = n123.getChild(0);
		AnnotationTreeNode<Annotation> n23 = n123.getChild(1);
		AnnotationTreeNode<Annotation> n2 = n23.getChild(0);
		AnnotationTreeNode<Annotation> n3 = n23.getChild(1);
		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode, n123, n1));
		paths.add(makePath(rootNode, n123, n23, n2));
		paths.add(makePath(rootNode, n123, n23, n3));
		LinkedList<Annotation> spans = writer.longestCommonSpans(root, paths,
		    1);
		assertEquals(2, spans.size());
		assertEquals(a1, spans.get(0));
		assertEquals(a23, spans.get(1));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLongestCommonSpansCommonLeaf() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 10);
		SyntaxAnnotation a123 = new SyntaxAnnotation(jcas, 1, 9);
		SyntaxAnnotation a2 = new SyntaxAnnotation(jcas, 4, 7);
		root.addToIndexes();
		a123.addToIndexes();
		a2.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();
		AnnotationTreeNode<Annotation> n123 = rootNode.getChild(0);
		AnnotationTreeNode<Annotation> n2 = n123.getChild(0);
		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode, n123));
		paths.add(makePath(rootNode, n123, n2));
		paths.add(makePath(rootNode, n123));
		LinkedList<Annotation> spans = writer.longestCommonSpans(root, paths,
		    1);
		assertEquals(1, spans.size());
		assertEquals(a123, spans.get(0));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLongestCommonSpansNoPath() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 10);
		SyntaxAnnotation a23 = new SyntaxAnnotation(jcas, 4, 9);
		SyntaxAnnotation a2 = new SyntaxAnnotation(jcas, 4, 7);
		root.addToIndexes();
		a23.addToIndexes();
		a2.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();
		AnnotationTreeNode<Annotation> n23 = rootNode.getChild(0);
		AnnotationTreeNode<Annotation> n2 = n23.getChild(0);
		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode));
		paths.add(makePath(rootNode, n23, n2));
		paths.add(makePath(rootNode, n23));
		LinkedList<Annotation> spans = writer.longestCommonSpans(root, paths,
		    -1);
		assertEquals(1, spans.size());
		assertEquals(root, spans.get(0));
	}

	@Test
	public void testCommonSpan() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 8);
		SyntaxAnnotation a1 = new SyntaxAnnotation(jcas, 2, 6);
		SyntaxAnnotation a2 = new SyntaxAnnotation(jcas, 4, 6);
		SyntaxAnnotation a3 = new SyntaxAnnotation(jcas, 6, 8);
		root.addToIndexes();
		a1.addToIndexes();
		a2.addToIndexes();
		a3.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();

		TextAnnotation e = new TextAnnotation(jcas, 1, 2);
		List<Offset> l = writer.shortestSpan(e, rootNode);
		assertEquals(1, l.size());
		assertEquals(l.get(0).toString(), new Offset(0, 2), l.get(0));

		e = new TextAnnotation(jcas, 2, 3);
		l = writer.shortestSpan(e, rootNode);
		assertEquals(2, l.size());
		assertEquals(l.get(0).toString(), new Offset(0, 2), l.get(0));
		assertEquals(l.get(0).toString(), new Offset(2, 4), l.get(1));

		e = new TextAnnotation(jcas, 4, 6);
		l = writer.shortestSpan(e, rootNode);
		assertEquals(3, l.size());
		assertEquals(l.get(0).toString(), new Offset(0, 2), l.get(0));
		assertEquals(l.get(0).toString(), new Offset(2, 4), l.get(1));
		assertEquals(l.get(0).toString(), new Offset(4, 6), l.get(2));

		e = new TextAnnotation(jcas, 7, 8);
		l = writer.shortestSpan(e, rootNode);
		assertEquals(2, l.size());
		assertEquals(l.get(0).toString(), new Offset(0, 2), l.get(0));
		assertEquals(l.get(0).toString(), new Offset(6, 8), l.get(1));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testShortestCommonSpan() {
		SyntaxAnnotation root = new SyntaxAnnotation(jcas, 0, 8);
		SyntaxAnnotation a1 = new SyntaxAnnotation(jcas, 2, 6);
		SyntaxAnnotation a2 = new SyntaxAnnotation(jcas, 4, 6);
		SyntaxAnnotation a3 = new SyntaxAnnotation(jcas, 6, 8);
		root.addToIndexes();
		a1.addToIndexes();
		a2.addToIndexes();
		a3.addToIndexes();
		AnnotationTreeNode<Annotation> rootNode = jcas.getAnnotationIndex()
		    .tree(root).getRoot();
		AnnotationTreeNode<Annotation> n1 = rootNode.getChild(0);
		AnnotationTreeNode<Annotation> n2 = n1.getChild(0);
		AnnotationTreeNode<Annotation> n3 = rootNode.getChild(1);
		TextAnnotation[] entities = new TextAnnotation[2];

		List<List<AnnotationTreeNode<Annotation>>> paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode, n1));
		paths.add(makePath(rootNode, n3));
		entities[0] = new TextAnnotation(jcas, 2, 3);
		entities[1] = new TextAnnotation(jcas, 7, 8);
		List<LinkedList<Annotation>> allSpans = writer.shortestCommonSpans(
		    entities, paths, 0, rootNode);
		LinkedList<Annotation> spans = allSpans.get(allSpans.size() - 1);
		assertEquals(2, spans.size());
		// assertEquals(0, spans.get(0).getBegin());
		// assertEquals(2, spans.get(0).getEnd());
		assertEquals(2, spans.get(0).getBegin());
		assertEquals(4, spans.get(0).getEnd());
		assertEquals(6, spans.get(1).getBegin());
		assertEquals(8, spans.get(1).getEnd());

		paths = new LinkedList<List<AnnotationTreeNode<Annotation>>>();
		paths.add(makePath(rootNode));
		paths.add(makePath(rootNode, n1, n2));
		entities[0] = new TextAnnotation(jcas, 1, 2);
		entities[1] = new TextAnnotation(jcas, 4, 6);
		root.setIdentifier("dummy");
		a1.setIdentifier("dummy");
		a2.setIdentifier("dummy");
		a3.setIdentifier("dummy");
		allSpans = writer.shortestCommonSpans(entities, paths, 0, rootNode);
		spans = allSpans.get(allSpans.size() - 1);
		assertEquals(3, spans.size());
		assertEquals(0, spans.get(0).getBegin());
		assertEquals(2, spans.get(0).getEnd());
		assertEquals(2, spans.get(1).getBegin());
		assertEquals(4, spans.get(1).getEnd());
		assertEquals(4, spans.get(2).getBegin());
		assertEquals(6, spans.get(2).getEnd());
	}

	@Test
	public void testClean() {
		assertEquals("a \n\nb", writer.clean(" \n  a  \n\n b\n "));
	}

	@Test
	public void testProcessRelationship()
	        throws AnalysisEngineProcessException {
		String test = "This is a sentence.";
		jcas.setDocumentText(test);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		RelationshipAnnotation relAnn = createDummyAnnotation(test, jcas);
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		writer.printToStdout = true;
		writer.process(relAnn, test, annIdx);
		assertTrue(outputStream.toString(), outputStream.toString()
		    .startsWith("[[ns:id]]" + test.substring(0, test.length() - 1)));
	}

	@Test
	public void testProcessInnerRelationship()
	        throws AnalysisEngineProcessException {
		String test = "This is a sentence.";
		jcas.setDocumentText(test);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		RelationshipAnnotation relAnn = createDummyAnnotation(test, jcas, 5, 7);
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		writer.printToStdout = true;
		writer.process(relAnn, test, annIdx);
		assertTrue(outputStream.toString(), outputStream.toString()
		    .startsWith("This [[ns:id]] a sentence."));
	}

	RelationshipAnnotation createDummyAnnotation(String test, JCas aJCas) {
		return createDummyAnnotation(test, aJCas, 0, 0);
	}

	RelationshipAnnotation createDummyAnnotation(String text, JCas aJCas,
	                                             int start, int end) {
		// sentence
		SyntaxAnnotation sentAnn = new SyntaxAnnotation(aJCas, 0,
		    text.length());
		sentAnn.setNamespace(SentenceAnnotator.NAMESPACE);
		sentAnn.setIdentifier(SentenceAnnotator.IDENTIFIER);
		sentAnn.addToIndexes();
		// entity
		SemanticAnnotation entityAnn = new SemanticAnnotation(aJCas, start,
		    end);
		entityAnn.setNamespace("ns:");
		entityAnn.setIdentifier("id");
		entityAnn.addToIndexes();
		// relationship
		RelationshipAnnotation relAnn = new RelationshipAnnotation(aJCas);
		relAnn.setNamespace("http://purl.org/relationship/");
		FSArray sources = new FSArray(aJCas, 1);
		sources.set(0, sentAnn);
		sources.addToIndexes();
		FSArray targets = new FSArray(aJCas, 1);
		targets.set(0, entityAnn);
		targets.addToIndexes();
		relAnn.setSources(sources);
		relAnn.setTargets(targets);
		relAnn.addToIndexes();
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

		ae = AnalysisEngineFactory.createAnalysisEngine(DESCRIPTOR,
		    RelationshipPatternLineWriter.PARAM_OUTPUT_DIRECTORY,
		    tmpDir.getCanonicalPath(),
		    RelationshipPatternLineWriter.PARAM_ENCODING, "UTF-32");
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

	@Test
	public void testProcessOfARealSetting() throws UIMAException, IOException {
		String e1 = "ENT1";
		String e2 = "ENT2";
		String e3 = "ENT3";
		String text = "This test shows how " + e1 +
		              ", an experimental phrase, interacts with " + e2 +
		              " and " + e3 + ".";
		JCas textCas = jcas.createView(Views.CONTENT_TEXT.toString());
		JCas rawCas = jcas.createView(Views.CONTENT_RAW.toString());
		rawCas.setSofaDataURI("test.txt", "mime");
		textCas.setDocumentText(text);
		// sentence
		SyntaxAnnotation sentAnn = new SyntaxAnnotation(textCas, 0,
		    text.length());
		sentAnn.setNamespace(SentenceAnnotator.NAMESPACE);
		sentAnn.setIdentifier(SentenceAnnotator.IDENTIFIER);
		sentAnn.addToIndexes();
		// entity1
		int idx = text.indexOf(e1);
		SemanticAnnotation entityAnn1 = new SemanticAnnotation(textCas, idx,
		    idx + e1.length());
		entityAnn1.setNamespace("ns-1:");
		entityAnn1.setIdentifier("id-1");
		entityAnn1.addToIndexes();
		// all inner phrase
		SyntaxAnnotation syntax = new SyntaxAnnotation(textCas, idx,
		    text.length());
		syntax.setNamespace("ns");
		syntax.setIdentifier("SBAR");
		syntax.addToIndexes();
		// segment e1
		syntax = new SyntaxAnnotation(textCas, idx, idx + e1.length());
		syntax.setNamespace("ns");
		syntax.setIdentifier("NP");
		syntax.addToIndexes();
		// entity2
		idx = text.indexOf(e2);
		SemanticAnnotation entityAnn2 = new SemanticAnnotation(textCas, idx,
		    idx + e2.length());
		entityAnn2.setNamespace("ns-2:");
		entityAnn2.setIdentifier("id-1");
		entityAnn2.addToIndexes();
		// segment e2
		syntax = new SyntaxAnnotation(textCas, idx, idx + e2.length());
		syntax.setNamespace("ns");
		syntax.setIdentifier("NP");
		syntax.addToIndexes();
		// segment e2 + e3
		idx = text.indexOf("interacts");
		syntax = new SyntaxAnnotation(textCas, idx, text.length() - 1);
		syntax.setNamespace("ns");
		syntax.setIdentifier("VP");
		syntax.addToIndexes();
		// entity3
		idx = text.indexOf(e3);
		SemanticAnnotation entityAnn3 = new SemanticAnnotation(textCas, idx,
		    idx + e3.length());
		entityAnn3.setNamespace("ns-1:");
		entityAnn3.setIdentifier("id-2");
		entityAnn3.addToIndexes();
		// segment e3
		syntax = new SyntaxAnnotation(textCas, idx, idx + e3.length());
		syntax.setNamespace("ns");
		syntax.setIdentifier("NP");
		syntax.addToIndexes();
		// relationship
		RelationshipAnnotation relAnn = new RelationshipAnnotation(textCas);
		relAnn.setNamespace("http://purl.org/relationship/");
		FSArray sources = new FSArray(textCas, 1);
		sources.set(0, sentAnn);
		sources.addToIndexes();
		FSArray targets = new FSArray(textCas, 3);
		targets.set(0, entityAnn1);
		targets.set(1, entityAnn2);
		targets.set(2, entityAnn3);
		targets.addToIndexes();
		relAnn.setSources(sources);
		relAnn.setTargets(targets);
		relAnn.addToIndexes();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		ae.process(jcas);
		assertTrue(
		    outputStream.toString(),
		    outputStream
		        .toString()
		        .startsWith(
		            "[[ns-1:id-1]] interacts with [[ns-2:id-1]] and [[ns-1:id-2]]"));
	}

}
