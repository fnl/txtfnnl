package txtfnnl.uima.analysis_component;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.uima.tcas.SemanticAnnotation;

public class TestKnownEntityAnnotator {

	AnalysisEngineDescription annotatorDesc; // AE descriptor under test
	AnalysisEngine annotator; // AE under test
	Connection jdbc_resource; // example named entity DB resource
	File textFile; // example analysis file
	String docId; // doc ID of example analysis file
	JCas baseJCas;
	JCas textJCas;
	JCas rawJCas;

	static final String SEMANTIC_ANNOTATION_NAMESPACE = "http://example.com/namespace/";
	static final String SEMANTIC_ANNOTATION_IDENTIFIER = "entity type";
	static final String ENTITY_NAME = "entity mention";
	static final String ENTITY_NS = "namespace Y";
	static final String ENTITY_ID = "identifier Y";

	@Before
	public void setUp() throws ResourceInitializationException, CASException,
	        InvalidXMLException, IOException, SQLException {
		DisableLogging.enableLogging(Level.WARNING);

		// set up example analysis file (w/o content)
		textFile = File.createTempFile("example_text_", ".txt");
		textFile.deleteOnExit();
		docId = textFile.getName();
		docId = docId.substring(0, docId.lastIndexOf('.'));

		// set up AE descriptor under test
		annotatorDesc = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    SEMANTIC_ANNOTATION_NAMESPACE, KnownEntityAnnotator.PARAM_QUERIES,
		    new String[] { "SELECT name FROM entities "
		                   + "WHERE ns = ? AND id = ?" });

		// set up an entity string map TSV file resource
		File tmpMap = File.createTempFile("entity_string_map_", null);
		tmpMap.deleteOnExit();
		BufferedWriter out = new BufferedWriter(new FileWriter(tmpMap));
		out.write(docId + "\t" + SEMANTIC_ANNOTATION_IDENTIFIER + "\t" +
		          ENTITY_NS + "\t" + ENTITY_ID + "\n");
		out.write(docId + "\t" + SEMANTIC_ANNOTATION_IDENTIFIER +
		          "\tgene-ns\tgene-id-1\n");
		out.write(docId + "\t" + SEMANTIC_ANNOTATION_IDENTIFIER +
		          "\tgene-ns\tgene-id-2\n");
		out.close();
		ExternalResourceFactory
		    .createDependencyAndBind(annotatorDesc,
		        KnownEntityAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		        EntityStringMapResource.class,
		        "file:" + tmpMap.getCanonicalPath());

		// set up a named entity DB resource
		File tmpDb = File.createTempFile("jdbc_resource_", null);
		tmpDb.deleteOnExit();
		String connectionUrl = "jdbc:h2:" + tmpDb.getCanonicalPath();
		jdbc_resource = DriverManager.getConnection(connectionUrl);
		Statement stmt = jdbc_resource.createStatement();
		stmt.executeUpdate("CREATE TABLE entities(pk INT PRIMARY KEY,"
		                   + "                    ns VARCHAR, id VARCHAR, "
		                   + "                    name VARCHAR)");
		stmt.executeUpdate("INSERT INTO entities VALUES(1, '" + ENTITY_NS +
		                   "', '" + ENTITY_ID + "', '" + ENTITY_NAME + "')");
		stmt.executeUpdate("INSERT INTO entities VALUES(2, 'gene-ns', "
		                   + "'gene-id-1', 'tumor necrosis factor alpha')");
		stmt.executeUpdate("INSERT INTO entities VALUES(3, 'gene-ns', "
		                   + "'gene-id-2', 'TNF-alpha')");
		// create a case where the (case-insensitive) gene names overlap
		stmt.executeUpdate("INSERT INTO entities VALUES(4, 'gene-ns', "
		                   + "'gene-id-1', 'tnf-alpha')");
		stmt.close();
		jdbc_resource.commit();
		ExternalResourceFactory.createDependencyAndBind(annotatorDesc,
		    KnownEntityAnnotator.MODEL_KEY_JDBC_CONNECTION,
		    JdbcConnectionResourceImpl.class, connectionUrl,
		    JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS, "org.h2.Driver");

		// finally, create the AE and some undefined example CAS instances.
		annotator = AnalysisEngineFactory.createPrimitive(annotatorDesc);
		baseJCas = annotator.newJCas();
		textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
		rawJCas = baseJCas.createView(Views.CONTENT_RAW.toString());
	}

	@Test(expected = ResourceInitializationException.class)
	public void testInitializeUimaContextWithoutParameters()
	        throws ResourceInitializationException {
		AnalysisEngineFactory.createPrimitive(KnownEntityAnnotator.class);
		fail("not using the required parameters should have failed");
	}

	@Test
	public void testProcessJCas() throws CASRuntimeException, IOException,
	        AnalysisEngineProcessException {
		// This example will test that given some example text, the default
		// setup entity string map and named entity DB resources, and the
		// configured AE, the AE annotates the string "entity mention" in
		// the example text as a SemanticAnnotation with the correct
		// properties
		String text = "This is an " + ENTITY_NAME + " inside the text.\n";
		int offset = "This is an ".length();
		BufferedWriter out = new BufferedWriter(new FileWriter(textFile));
		out.write(text);
		out.close();
		textJCas.setDocumentText(text);
		rawJCas.setSofaDataURI("file:" + textFile.getCanonicalPath(),
		    "text/plain");
		annotator.process(baseJCas.getCas());
		int count = 0;

		for (SemanticAnnotation ann : JCasUtil.select(textJCas,
		    SemanticAnnotation.class)) {
			assertEquals(offset, ann.getBegin());
			assertEquals(offset + ENTITY_NAME.length(), ann.getEnd());
			assertEquals(KnownEntityAnnotator.URI, ann.getAnnotator());
			assertEquals(SEMANTIC_ANNOTATION_NAMESPACE, ann.getNamespace());
			assertEquals(SEMANTIC_ANNOTATION_IDENTIFIER, ann.getIdentifier());
			assertEquals(1.0, ann.getConfidence(), 0.000001);
			FSArray a = ann.getProperties();
			assertEquals(2, a.size());
			Property p0 = (Property) a.get(0);
			Property p1 = (Property) a.get(1);
			assertEquals("namespace", p0.getName());
			assertEquals(ENTITY_NS, p0.getValue());
			assertEquals("identifier", p1.getName());
			assertEquals(ENTITY_ID, p1.getValue());
			count++;
		}

		assertEquals(1, count);
	}

	@Test
	public void testCaseInsensitiveMatching() throws CASRuntimeException,
	        IOException, AnalysisEngineProcessException {
		String text = "The mouse Tumor necrosis factor alpha (Tnf-alpha) "
		              + "gene is one of the earliest genes expressed.\n";
		BufferedWriter out = new BufferedWriter(new FileWriter(textFile));
		out.write(text);
		out.close();
		textJCas.setDocumentText(text);
		rawJCas.setSofaDataURI("file:" + textFile.getCanonicalPath(),
		    "text/plain");
		annotator.process(baseJCas.getCas());
		int count = 0;
		Set<String> ids = new HashSet<String>();
		ids.add("gene-id-1");
		ids.add("gene-id-2");

		for (SemanticAnnotation ann : JCasUtil.select(textJCas,
		    SemanticAnnotation.class)) {
			FSArray a = ann.getProperties();
			Property p0 = (Property) a.get(0);
			Property p1 = (Property) a.get(1);
			assertEquals("namespace", p0.getName());
			assertEquals("gene-ns", p0.getValue());
			assertEquals("identifier", p1.getName());
			
			if (count == 0)
				assertEquals("gene-id-1", p1.getValue());
			else
				assertTrue(p1.getValue(), ids.remove(p1.getValue()));
			
			if (count == 0)
				assertEquals("Tumor necrosis factor alpha",
				    ann.getCoveredText());
			else
				assertEquals("Tnf-alpha", ann.getCoveredText());

			count++;
		}

		assertEquals(3, count);
	}

	@Test
	public void testGenerateRegex() {
		assertNull(KnownEntityAnnotator.generateRegex(new ArrayList<String>(),
		    Pattern.UNICODE_CASE));
		ArrayList<String> example = new ArrayList<String>();
		example.add("ot$1her");
		example.add("short");
		example.add("xlong-na\\Eme");
		Pattern regex = KnownEntityAnnotator.generateRegex(example,
		    Pattern.UNICODE_CASE);
		assertEquals(
		    "\\b\\Qxlong-na\\E\\\\E\\Qme\\E\\b|\\bxlong\\W*na\\W*Eme\\b" + "|"
		            + "\\b\\Qot$1her\\E\\b|\\bot\\W*1\\W*her\\b" + "|"
		            + "\\b\\Qshort\\E\\b|\\bshort\\b", regex.pattern());
	}
}
