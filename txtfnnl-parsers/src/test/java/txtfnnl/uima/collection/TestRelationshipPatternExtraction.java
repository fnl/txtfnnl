package txtfnnl.uima.collection;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.analysis_component.LinkGrammarAnnotator;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.uima.resource.RelationshipStringMapResource;

public class TestRelationshipPatternExtraction {

	AnalysisEngine sentenceAE, entityAE, relationshipAE, parserAE, patternAE;
	AnalysisEngineDescription entityAEDesc, relationshipAEDesc;
	File entityMap, relationshipMap;
	BufferedWriter entityTSVWriter, relationshipTSVWriter;
	PreparedStatement entityDBStmt;
	Connection entityDBConnection;
	String dbConnectionUrl, docId;

	@Before
	public void setUp() throws UIMAException, IOException, SQLException {
		DisableLogging.enableLogging(Level.WARNING);
		docId = UUID.randomUUID().toString();
		setUpSentenceAE();
		setUpEntityAE();
		setUpRelationshipAE();
		setUpParserAE();
		setUpPatternAE();
	}

	/**
	 * Must be called before running a test.
	 * 
	 * @throws IOException
	 * @throws SQLException
	 * @throws InvalidXMLException
	 * @throws ResourceInitializationException
	 */
	void finalizeSetUp() throws IOException, SQLException,
	        InvalidXMLException, ResourceInitializationException {
		entityDBStmt.close();
		entityDBConnection.commit();
		entityDBConnection.close();
		entityTSVWriter.close();
		relationshipTSVWriter.close();

		ExternalResourceFactory.createDependencyAndBind(entityAEDesc,
		    KnownEntityAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		    EntityStringMapResource.class,
		    "file:" + entityMap.getCanonicalPath());
		ExternalResourceFactory.createDependencyAndBind(entityAEDesc,
		    KnownEntityAnnotator.MODEL_KEY_JDBC_CONNECTION,
		    JdbcConnectionResourceImpl.class, dbConnectionUrl,
		    JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS, "org.h2.Driver");
		entityAE = AnalysisEngineFactory.createPrimitive(entityAEDesc);

		ExternalResourceFactory.createDependencyAndBind(relationshipAEDesc,
		    KnownRelationshipAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		    RelationshipStringMapResource.class,
		    "file:" + relationshipMap.getCanonicalPath());
		relationshipAE = AnalysisEngineFactory
		    .createPrimitive(relationshipAEDesc);
	}

	void setUpSentenceAE() throws UIMAException, IOException {
		sentenceAE = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPSentenceAEDescriptor");
	}

	void setUpEntityAE() throws ResourceInitializationException, IOException,
	        SQLException {
		entityAEDesc = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    "entity:", KnownEntityAnnotator.PARAM_QUERIES,
		    new String[] { "SELECT name FROM entities "
		                   + "WHERE ns=? AND id=?" });

		// set up an entity string map TSV file resource
		entityMap = File.createTempFile("entity_string_map_", null);
		entityMap.deleteOnExit();
		entityTSVWriter = new BufferedWriter(new FileWriter(entityMap));

		// set up a named entity DB resource
		File tmpDb = File.createTempFile("entity_resource_", null);
		tmpDb.deleteOnExit();
		dbConnectionUrl = "jdbc:h2:" + tmpDb.getCanonicalPath();
		entityDBConnection = DriverManager.getConnection(dbConnectionUrl);
		Statement stmt = entityDBConnection.createStatement();
		stmt.executeUpdate("CREATE TABLE entities(ns VARCHAR, id VARCHAR, "
		                   + "                    name VARCHAR)");
		stmt.close();
		entityDBStmt = entityDBConnection
		    .prepareStatement("INSERT INTO entities VALUES(?, ?, ?)");
	}

	void setUpRelationshipAE() throws ResourceInitializationException,
	        IOException, InvalidXMLException {
		relationshipAEDesc = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownRelationshipAnnotator.class,
		    KnownRelationshipAnnotator.PARAM_ENTITY_NAMESPACE, "entity:",
		    KnownRelationshipAnnotator.PARAM_RELATIONSHIP_NAMESPACE,
		    "relationship:",
		    KnownRelationshipAnnotator.PARAM_REMOVE_SENTENCE_ANNOTATIONS,
		    Boolean.TRUE);

		// set up an entity string map TSV file resource
		relationshipMap = File.createTempFile("relationship_map_", null);
		relationshipMap.deleteOnExit();
		relationshipTSVWriter = new BufferedWriter(new FileWriter(
		    relationshipMap));
	}

	void setUpParserAE() throws ResourceInitializationException {
		AnalysisEngineDescription annotatorDesc = AnalysisEngineFactory
		    .createPrimitiveDescription(LinkGrammarAnnotator.class);
		parserAE = AnalysisEngineFactory.createPrimitive(annotatorDesc);
	}

	void setUpPatternAE() throws ResourceInitializationException {
		AnalysisEngineDescription annotatorDesc = AnalysisEngineFactory
		    .createPrimitiveDescription(RelationshipPatternLineWriter.class,
		        RelationshipPatternLineWriter.PARAM_PRINT_TO_STDOUT,
		        Boolean.TRUE,
		        RelationshipPatternLineWriter.PARAM_RELATIONSHIP_NAMESPACE,
		        "relationship:");

		patternAE = AnalysisEngineFactory.createPrimitive(annotatorDesc);
	}

	JCas setUpJCas(String text) throws ResourceInitializationException,
	        CASException {
		JCas baseCas = sentenceAE.newJCas();
		JCas textCas = baseCas.createView(Views.CONTENT_TEXT.toString());
		JCas rawCas = baseCas.createView(Views.CONTENT_RAW.toString());
		textCas.setDocumentText(text);
		rawCas.setSofaDataURI(docId + ".txt", "text/plain");
		return baseCas;
	}

	void addRelationship(String... names) throws IOException, SQLException {
		int count = 1;
		relationshipTSVWriter.write(docId);

		for (String n : names) {
			String id = UUID.randomUUID().toString();
			addEntity("type-" + count, "ns:", id, n);
			relationshipTSVWriter.write("\t");
			relationshipTSVWriter.write("type-" + count);
			relationshipTSVWriter.write("\t");
			relationshipTSVWriter.write("ns:");
			relationshipTSVWriter.write("\t");
			relationshipTSVWriter.write(id);
			count++;
		}

		relationshipTSVWriter.write("\n");
	}

	private void addEntity(String type, String ns, String id, String name)
	        throws IOException, SQLException {
		entityTSVWriter.write(docId);
		entityTSVWriter.write("\t");
		entityTSVWriter.write(type);
		entityTSVWriter.write("\t");
		entityTSVWriter.write(ns);
		entityTSVWriter.write("\t");
		entityTSVWriter.write(id);
		entityTSVWriter.write("\n");
		entityDBStmt.setString(1, ns);
		entityDBStmt.setString(2, id);
		entityDBStmt.setString(3, name);
		assertEquals(1, entityDBStmt.executeUpdate());
	}

	String process(JCas jcas) throws AnalysisEngineProcessException,
	        UnsupportedEncodingException {
		sentenceAE.process(jcas);
		entityAE.process(jcas);
		relationshipAE.process(jcas);
		parserAE.process(jcas);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PrintStream systemOut = System.out;
		System.setOut(new PrintStream(outputStream, true, "UTF-8"));
		patternAE.process(jcas);
		System.setOut(systemOut);
		return outputStream.toString("UTF-8");
	}

	void checkForResult(String pattern, String result) {
		assertTrue("Pattern\n'" + pattern + "'\nnot found in:\n" +
		           prettyPrint(result),
		    ("\n" + result).contains("\n" + pattern + "\n"));
	}

	private String prettyPrint(String result) {
		StringBuilder sb = new StringBuilder("'");

		for (String pattern : result.split("\n")) {
			sb.append(pattern);
			sb.append("'\n'");
		}

		sb.delete(sb.length() - 2, sb.length());
		return sb.toString();
	}

	Set<String> resultSet(String output) {
		return new HashSet<String>(Arrays.asList(output.substring(0,
		    output.length() - 2).split("\n")));
	}

	@Test
	public void testExtractionSystem() throws UIMAException, IOException,
	        SQLException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("ENT1 interacts with ENT2.");
		checkForResult("[[entity:type-1]] interacts with [[entity:type-2]]",
		    process(jcas));
	}

	@Test
	public void testExtractionSkipsNounPhrases() throws UIMAException,
	        IOException, SQLException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("ENT1, a regulator, interacts with ENT2.");
		String result = process(jcas);
		checkForResult(
		    "[[entity:type-1]], a regulator, interacts with [[entity:type-2]]",
		    result);
		checkForResult("[[entity:type-1]] interacts with [[entity:type-2]]",
		    result);
	}

	public void testExtractionRemovesEmptyApposition() throws IOException,
	        SQLException, UIMAException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("ENT1 - a regulator - interacts with ENT2.");
		String result = process(jcas);
		checkForResult("[[entity:type-1]] interacts with [[entity:type-2]]",
		    result);
	}

	@Test
	public void testExtractionSkipsVerbPhrases() throws UIMAException,
	        IOException, SQLException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("ENT1 is a regulator that interacts with ENT2.");
		checkForResult("[[entity:type-1]] interacts with [[entity:type-2]]",
		    process(jcas));
	}

	@Test
	public void testExtractionSkipsPrepositionPhrases() throws UIMAException,
	        IOException, SQLException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("ENT1 interacts with ENT2 inside the nucleus.");
		String result = process(jcas);
		checkForResult("[[entity:type-1]] interacts with [[entity:type-2]]",
		    result);
		checkForResult(
		    "[[entity:type-1]] interacts with [[entity:type-2]] inside the nucleus",
		    result);
	}

	@Test
	public void testExtractionCompressesNounPhrases() throws UIMAException,
	        IOException, SQLException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("The ENT1 transcription factor interacts with the ENT2 promoter.");
		String result = process(jcas);
		checkForResult("[[entity:type-1]] interacts with [[entity:type-2]]",
		    result);
		checkForResult(
		    "The [[entity:type-1]] transcription factor interacts with the [[entity:type-2]] promoter",
		    result);
	}

	@Test
	public void testParsingLongPhrase() throws UIMAException, IOException,
	        SQLException {
		String sentence = "The inability of TERT overexpression to substitute "
		                  + "for Myc in the REF cooperation assay, in "
		                  + "conjunction with the previous observation that "
		                  + "c-Myc can bypass replicative senesence despite "
		                  + "substantial telomere loss ( Wang et al ., 1998 "
		                  + "), suggests that the oncogenic actions of c-Myc "
		                  + "extend beyond the activation of TERT gene "
		                  + "expression and telomerase activity.";
		addRelationship("TERT", "c-Myc");
		finalizeSetUp();
		JCas jcas = setUpJCas(sentence);
		String result = process(jcas);
		// the parser is not quite deterministic - sometimes, "that" is
		// included, at other times, not; so:
		String pattern = "the oncogenic actions of [[entity:type-2]] extend beyond the "
		                 + "activation of [[entity:type-1]] gene expression and "
		                 + "telomerase activity";
		assertTrue(
		    "Pattern\n'" + pattern + "'\nnot found in:\n" +
		            prettyPrint(result),
		    ("\n" + result).contains("\n" + pattern) ||
		            ("\n" + result).contains("\nthat " + pattern));
	}

	@Test
	public void testExtractionOfComplexPhrases() throws UIMAException,
	        IOException, SQLException {
		addRelationship("ENT1", "ENT2");
		finalizeSetUp();
		JCas jcas = setUpJCas("The ENT1 factor, a nice gene, "
		                      + "binds ENT2 promoter in the nucleus.");
		String result = process(jcas);
		checkForResult("[[entity:type-1]] binds "
		               + "[[entity:type-2]] promoter", result);
		checkForResult("[[entity:type-1]], a nice gene, binds "
		               + "[[entity:type-2]] promoter", result);
		checkForResult("[[entity:type-1]] binds "
		               + "[[entity:type-2]] promoter in the nucleus", result);
		checkForResult("[[entity:type-1]], a nice gene, binds "
		               + "[[entity:type-2]] promoter in the nucleus", result);
		checkForResult("The [[entity:type-1]] factor binds "
		               + "[[entity:type-2]] promoter", result);
		checkForResult("The [[entity:type-1]] factor, a nice gene, "
		               + "binds [[entity:type-2]] promoter", result);
		checkForResult("The [[entity:type-1]] factor binds "
		               + "[[entity:type-2]] promoter in the nucleus", result);
		checkForResult("The [[entity:type-1]] factor, a nice gene, binds "
		               + "[[entity:type-2]] promoter in the nucleus", result);
	}

	@Test
	public void testSkippingOfModifierPhrases() throws UIMAException,
	        IOException, SQLException {
		addRelationship("AAA-1", "BBB-2");
		finalizeSetUp();
		JCas jcas = setUpJCas("Inhibition of AAA-1 expression in positively "
		                      + "HCT116 cells results in growth suppression "
		                      + "in a BBB-2-dependent manner.");
		String result = process(jcas);
		System.out.println(result);
		checkForResult(
		    "Inhibition of [[entity:type-1]] expression results in growth "
		            + "suppression in a [[entity:type-2]]-dependent manner",
		    result);
		checkForResult(
		    "Inhibition of [[entity:type-1]] expression in HCT116 cells "
		            + "results in growth suppression in a "
		            + "[[entity:type-2]]-dependent manner", result);
	}

}
