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
import java.util.Set;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.resource.JdbcGazetteerResource.Builder;
import txtfnnl.utils.Offset;
import txtfnnl.utils.StringUtils;

public class TestJdbcGazetteerResource {
  public static class DummyAnalysisEngine extends JCasAnnotator_ImplBase {
    static final String GAZETTEER = "JdbcGazetteerResource";
    @ExternalResource(key = GAZETTEER, mandatory = true)
    private GazetteerResource<Set<String>> gazetteerResource;
    static final String TEST_GAZETTEER_SIZE = "TestGazetteerSize";
    @ConfigurationParameter(name = TEST_GAZETTEER_SIZE, mandatory = true)
    private int gazetteerSize;
    static final String TEST_KEY = "TestKey";
    @ConfigurationParameter(name = TEST_KEY, mandatory = false)
    private String key;
    static final String TEST_UNKNOWN_KEY = "TestKeyIsNull";
    @ConfigurationParameter(name = TEST_UNKNOWN_KEY, mandatory = false)
    private String unknownKey;
    static final String TEST_MATCH_SIZE = "TestMatchSize";
    @ConfigurationParameter(name = TEST_MATCH_SIZE, mandatory = false, defaultValue = "0")
    private int matchSize;
    static final String TEST_MATCH_VALUE = "TestMatchValue";
    @ConfigurationParameter(name = TEST_MATCH_VALUE, mandatory = false)
    private String matchValue;
    static final String TEST_RESOLUTION_SIZE = "TestResolutionSize";
    @ConfigurationParameter(name = TEST_RESOLUTION_SIZE, mandatory = false, defaultValue = "0")
    private int resolutionSize;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
      StringBuilder buff = new StringBuilder("'");
      String sep = JdbcGazetteerResource.SEPARATOR;
      for (String k : gazetteerResource)
        buff.append(k.replace(sep, "-")).append("', '");
      if (buff.length() > 3) buff.replace(buff.length() - 3, buff.length(), "");
      assertEquals(buff.toString(), gazetteerSize, gazetteerResource.size());
      if (key != null)
        assertNotNull("'" + key.replace(sep, "-") + "' not in " + buff.toString(),
            gazetteerResource.get(key));
      String msg = (unknownKey != null && gazetteerResource.get(unknownKey) != null) ? Arrays
          .toString(gazetteerResource.get(unknownKey).toArray()) : "null";
      if (unknownKey != null)
        assertNull(msg.replace(sep, "-"), gazetteerResource.get(unknownKey));
      Map<Offset, String> result = gazetteerResource.match("bla Abc Abc bla 1 bla abab bla");
      assertEquals(matchSize, result.size());
      if (matchValue != null) {
        msg = (result.size() > 0) ? Arrays.toString(result.values().toArray()) : "null";
        assertTrue(msg.replace(sep, "-"), result.containsValue(matchValue));
        if (resolutionSize > 0 && !gazetteerResource.containsKey(matchValue)) {
          Set<String> resolvedKeys = gazetteerResource.resolve(matchValue);
          msg = matchValue +
              ": " +
              (resolvedKeys.size() > 0 ? Arrays.toString(resolvedKeys.toArray()).replace(sep, "-")
                  : "null");
          assertEquals(msg, resolutionSize, resolvedKeys.size());
          for (String k : resolvedKeys)
            assertTrue(k, gazetteerResource.containsKey(k));
        }
      }
    }
  }

  Builder builder;
  String url;

  @Before
  public void setUp() throws Exception {
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    url = "jdbc:h2:" + tmpDb.getCanonicalPath();
    DisableLogging.enableLogging(Level.WARNING);
    tmpDb.deleteOnExit();
    builder = JdbcGazetteerResource.configure(url, "org.h2.Driver",
        "SELECT id, name FROM entities");
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
  public void testFullConfigure() throws ResourceInitializationException {
    builder.idMatching().caseMatching().setSeparatorLengths(65873)
        .setSeparators("separatorsDummy");
    final String config = builder.create().toString();
    assertTrue(config.contains(url));
    assertTrue(config.contains("SELECT id, name FROM entities"));
    assertTrue(config.contains("separatorsDummy"));
    assertTrue(config.contains("65873"));
  }

  @Test
  public void testDefaultSetup() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "NAME" });
    // default setups produce four keys per name:
    // exact key, case insensitive key, normal key, and a combined ci + n key
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.create(), DummyAnalysisEngine.TEST_GAZETTEER_SIZE,
        4);
    ae.process(ae.newJCas());
  }

  private static String makeKey(String... tokens) {
    return StringUtils.join(JdbcGazetteerResource.SEPARATOR, tokens);
  }

  @Test
  public void testBasicFunctionality() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "\u2000AbcAbc\u2010" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.create(), DummyAnalysisEngine.TEST_GAZETTEER_SIZE,
        4, DummyAnalysisEngine.TEST_KEY, makeKey("Abc", "Abc"),
        DummyAnalysisEngine.TEST_MATCH_SIZE, 1, DummyAnalysisEngine.TEST_MATCH_VALUE,
        makeKey("Abc", "Abc"));
    ae.process(ae.newJCas());
  }

  @Test
  public void testSingleCharacterNames() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "x", "1" }); // NB: "1" could shadow its own ID key!
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.create(), DummyAnalysisEngine.TEST_GAZETTEER_SIZE,
        8, DummyAnalysisEngine.TEST_MATCH_SIZE, 1);
    ae.process(ae.newJCas());
  }

  @Test
  public void testIdMatchingSetup() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "NAME" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.idMatching().create(),
        DummyAnalysisEngine.TEST_GAZETTEER_SIZE, 8);
    ae.process(ae.newJCas());
  }

  @Test
  public void testCaseMatchingSetup() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "NAME" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.caseMatching().create(),
        DummyAnalysisEngine.TEST_GAZETTEER_SIZE, 2);
    ae.process(ae.newJCas());
  }

  @Test
  public void testCaseInsensitiveMatching() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "unmatched", "AB-AB" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.create(), DummyAnalysisEngine.TEST_GAZETTEER_SIZE,
        8, DummyAnalysisEngine.TEST_MATCH_SIZE, 1, DummyAnalysisEngine.TEST_MATCH_VALUE, "abab");
    ae.process(ae.newJCas());
  }

  @Test
  public void testWithIdAndCaseMatching() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "NAME" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.idMatching().caseMatching().create(),
        DummyAnalysisEngine.TEST_GAZETTEER_SIZE, 4);
    ae.process(ae.newJCas());
  }

  @Test
  public void testNormalMatches() throws SQLException, UIMAException, IOException {
    createTable(new String[] { "abAb", " ab-ab " });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.create(), DummyAnalysisEngine.TEST_GAZETTEER_SIZE,
        6, DummyAnalysisEngine.TEST_KEY, JdbcGazetteerResource.NORMAL +
            JdbcGazetteerResource.LOWERCASE + "abab", DummyAnalysisEngine.TEST_MATCH_VALUE,
        "abab", DummyAnalysisEngine.TEST_UNKNOWN_KEY, "abab", DummyAnalysisEngine.TEST_MATCH_SIZE,
        1, DummyAnalysisEngine.TEST_RESOLUTION_SIZE, 2);
    ae.process(ae.newJCas());
  }

  @Test
  public void testMatchResult() throws SQLException, UIMAException, IOException {
    // use an exact case matcher (only)
    createTable(new String[] { "ab_ab" });
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnalysisEngine.class,
        DummyAnalysisEngine.GAZETTEER, builder.caseMatching().create(),
        DummyAnalysisEngine.TEST_GAZETTEER_SIZE, 2, DummyAnalysisEngine.TEST_MATCH_SIZE, 1,
        DummyAnalysisEngine.TEST_MATCH_VALUE, "abab", DummyAnalysisEngine.TEST_KEY, "ab" +
            JdbcGazetteerResource.SEPARATOR + "ab", DummyAnalysisEngine.TEST_RESOLUTION_SIZE, 1);
    ae.process(ae.newJCas());
  }
}
