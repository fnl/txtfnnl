package txtfnnl.uima.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.resource.JdbcGazetteerResource.Builder;

public class TestJdbcGazetteerResource {
  public static class DummyAnnotator extends JCasAnnotator_ImplBase {
    static final String GAZETTEER = "GazetteerResource";
    @ExternalResource(key = GAZETTEER, mandatory = true)
    public GazetteerResource gazetteerResource;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {}
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

  private GazetteerResource newGazetteer(String... names)
      throws IOException, ResourceInitializationException, ResourceAccessException, SQLException {
    createTable(names);
    AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnnotator.class,
        DummyAnnotator.GAZETTEER, builder.create());
    UimaContext ctx = ae.getUimaContext();
    return (GazetteerResource) ctx.getResourceObject(DummyAnnotator.GAZETTEER);
  }

  private void createTable(String[] names) throws SQLException {
    final Connection conn = DriverManager.getConnection(url);
    final Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(id INT PRIMARY KEY,"
        + "                    name VARCHAR)");
    stmt.close();
    final PreparedStatement insert = conn.prepareStatement("INSERT INTO entities VALUES(?, ?)");
    for (int i = 0; i < names.length; ++i) {
      insert.setInt(1, i + 1);
      insert.setString(2, names[i]);
      assertEquals(1, insert.executeUpdate());
    }
    insert.close();
    conn.close();
  }

  @Test
  public void testFullConfigure() throws ResourceInitializationException {
    builder.idMatching().caseMatching().boundaryMatch();//.setCharsetRegex("[hello]");
    final String config = builder.create().toString();
    assertTrue(config.contains(url));
    assertTrue(config.contains("SELECT id, name FROM entities"));
    //assertTrue(config.contains("[hello]"));
  }

  @Test
  public void testDefaultSetup() throws SQLException, UIMAException, IOException {
    GazetteerResource gr = newGazetteer("some dummy name");
    for (String id : gr)
      assertEquals("1", id);
    assertTrue(gr.containsKey("1"));
    assertEquals(1, gr.size());
    assertArrayEquals(new String[] { "some dummy name" }, gr.get("1").toArray(new String[1]));
  }

  @Test
  public void testDefaultResult() throws SQLException, UIMAException, IOException {
    GazetteerResource gr = newGazetteer("ABC 123", "ABC", "123", "123 ABC");
    assertEquals(3, gr.match("abc 123").size());
  }
  
  @Test
  public void testCaseMatchingResult() throws SQLException, UIMAException, IOException {
    builder.caseMatching();
    GazetteerResource gr = newGazetteer("ABC 123", "ABC", "123", "123 ABC");
    assertEquals(1, gr.match("abc 123").size());
  }

  @Test
  public void testBoundaryMatchingResult() throws SQLException, UIMAException, IOException {
    builder.boundaryMatch();
    GazetteerResource gr = newGazetteer("ABC 123", "ABC", "123", "123 ABC");
    assertEquals(1, gr.match("dabc 123").size());
  }

//  @Test
//  public void testCharsetRegex() throws SQLException, UIMAException, IOException {
//    builder.setCharsetRegex("[0-9]+");
//    GazetteerResource gr = newGazetteer("0A1B2C3");
//    assertEquals(1, gr.match("0ABC9").size());
//    assertEquals(1, gr.match(" A1B2C ").size());
//    assertEquals(0, gr.match("A B C").size());
//  }
}
