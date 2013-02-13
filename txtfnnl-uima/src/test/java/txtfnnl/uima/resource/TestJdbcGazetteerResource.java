package txtfnnl.uima.resource;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.resource.JdbcGazetteerResource.Offset;

public class TestJdbcGazetteerResource {
  public static class DummyAnalysisEngine extends JCasAnnotator_ImplBase {
    static final String GAZETTEER = "JdbcGazetteer";
    @ExternalResource(key = GAZETTEER, mandatory = true)
    private JdbcGazetteerResource gazetteerResource;
    static final String TEST_TARGET_SIZE = "TestTargetSize";
    @ConfigurationParameter(name = TEST_TARGET_SIZE, mandatory = true)
    private int targetSize;
    static final String TEST_NORMAL_KEY = "TestNormalKey";
    @ConfigurationParameter(name = TEST_NORMAL_KEY, mandatory = false)
    private String normalKey;
    static final String TEST_FAKE_NORMAL_KEY = "TestNormalKeyIsNull";
    @ConfigurationParameter(name = TEST_FAKE_NORMAL_KEY, mandatory = false)
    private String fakeNormalKey;
    static final String TEST_MATCH_SIZE = "TestMatchSize";
    @ConfigurationParameter(name = TEST_MATCH_SIZE, mandatory = false, defaultValue = "0")
    private int matchSize;
    static final String TEST_MATCH_VALUE = "TestMatchValue";
    @ConfigurationParameter(name = TEST_MATCH_VALUE, mandatory = false)
    private String matchValue;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
      if (targetSize != gazetteerResource.size()) {
        for (String key : gazetteerResource)
          System.err.println(key);
      }
      assertEquals(targetSize, gazetteerResource.size());
      if (normalKey != null) assertNotNull(gazetteerResource.getNormal(normalKey));
      String msg = (fakeNormalKey != null && gazetteerResource.getNormal(fakeNormalKey) != null)
          ? Arrays.toString(gazetteerResource.getNormal(fakeNormalKey).toArray()) : "null";
      if (fakeNormalKey != null)
        assertNull(msg, gazetteerResource.getNormal(fakeNormalKey));
      Map<Offset, String> result = gazetteerResource.match("bla Ab Ab bla abab bla");
      assertEquals(matchSize, result.size());
      if (matchValue != null) {
        msg = (result.size() > 0) ? Arrays.toString(result.values().toArray()) : "null";
        assertTrue(msg, result.containsValue(matchValue));
      }
    }
  }

  ExternalResourceDescription descriptor;
  String url;
  String query = "SELECT id, name FROM entities";

  @Before
  public void setUp() throws Exception {
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    DisableLogging.enableLogging(Level.WARNING);
    tmpDb.deleteOnExit();
    url = "jdbc:h2:" + tmpDb.getCanonicalPath();
    descriptor = JdbcGazetteerResource.configure(url, query, "org.h2.Driver");
  }

  private void createTable(String[] names) throws SQLException {
    final Connection conn = DriverManager.getConnection(url);
    final Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(id INT PRIMARY KEY,"
        + "                    name VARCHAR)");
    stmt.close();
    final PreparedStatement insert = conn.prepareStatement("INSERT INTO entities VALUES(?, ?)");
    for (int i = 0; i < names.length; ++i) {
      insert.setInt(1, i);
      insert.setString(2, names[i]);
      assertEquals(1, insert.executeUpdate());
    }
    insert.close();
    conn.close();
  }

  @Test
  public void testFullConfigure() throws IOException {
    final String config = JdbcGazetteerResource.configure("urlDummy", "queryDummy", false, false,
        "driverDummy", "userDummy", "passDummy", 999, "isolationDummy", false).toString();
    assertTrue(config.contains("urlDummy"));
    assertTrue(config.contains("queryDummy"));
    assertTrue(config.contains("driverDummy"));
    assertTrue(config.contains("userDummy"));
    assertTrue(config.contains("passDummy"));
    assertTrue(config.contains("999"));
    assertTrue(config.contains("isolationDummy"));
  }

  @Test
  public void testBasicFunctionality() throws SQLException, UIMAException {
    createTable(new String[] { "ab-ab" });
    // default settings produce three keys per name:
    // "0" (ID), "ab-ab" (exact match), "~ab-ab" (case insensitive)
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 3,
        DummyAnalysisEngine.TEST_MATCH_SIZE, 2);
    ae.process(ae.newJCas());
  }

  @Test
  public void testSkippingSingleLetterNames() throws SQLException, UIMAException {
    createTable(new String[] { "a", "1" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 2);
    ae.process(ae.newJCas());
  }

  @Test
  public void testDefaultSetup() throws SQLException, UIMAException {
    createTable(new String[] { "one", "two", "three" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 9);
    ae.process(ae.newJCas());
  }

  @Test
  public void testWithoutIdMatching() throws SQLException, UIMAException, IOException {
    descriptor = JdbcGazetteerResource.configure(url, query, false, false, "org.h2.Driver", null,
        null, -1, null, false);
    createTable(new String[] { "one", "two", "three" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 6);
    ae.process(ae.newJCas());
  }

  @Test
  public void testExactCaseMatching() throws SQLException, UIMAException, IOException {
    descriptor = JdbcGazetteerResource.configure(url, query, true, true, "org.h2.Driver", null,
        null, -1, null, false);
    createTable(new String[] { "one", "two", "three" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 6);
    ae.process(ae.newJCas());
  }

  @Test
  public void testWithoutIdAndExactCaseMatching() throws SQLException, UIMAException, IOException {
    descriptor = JdbcGazetteerResource.configure(url, query, false, true, "org.h2.Driver", null,
        null, -1, null, false);
    createTable(new String[] { "one", "two", "three" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 3);
    ae.process(ae.newJCas());
  }

  @Test
  public void testNormalMatches() throws SQLException, UIMAException {
    createTable(new String[] { "a-b-a-b", "a/ba b" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 6,
        DummyAnalysisEngine.TEST_NORMAL_KEY, "abab", DummyAnalysisEngine.TEST_FAKE_NORMAL_KEY,
        "ab-ab", DummyAnalysisEngine.TEST_MATCH_SIZE, 2);
    ae.process(ae.newJCas());
  }

  @Test
  public void testMatchResult() throws SQLException, UIMAException, IOException {
    // use an exact case matcher (only)
    descriptor = JdbcGazetteerResource.configure(url, query, false, true, "org.h2.Driver", null,
        null, -1, null, false);
    createTable(new String[] { "ab-ab" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, descriptor, DummyAnalysisEngine.TEST_TARGET_SIZE, 1,
        DummyAnalysisEngine.TEST_MATCH_SIZE, 1,
        DummyAnalysisEngine.TEST_MATCH_VALUE, "abab",
        DummyAnalysisEngine.TEST_NORMAL_KEY, "abab");
    ae.process(ae.newJCas());
  }
}
