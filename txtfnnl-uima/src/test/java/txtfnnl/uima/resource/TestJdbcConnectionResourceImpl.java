package txtfnnl.uima.resource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

public class TestJdbcConnectionResourceImpl {
  public static class DummyAnalysisEngine extends JCasAnnotator_ImplBase {
    static final String RES_JDBC_CONNECTOR = "JdbcConnector";
    @ExternalResource(key = RES_JDBC_CONNECTOR, mandatory = true)
    JdbcConnectionResourceImpl jdbcResource;
    Connection connection;
    static final String PARAM_QUERY = "Query";
    @ConfigurationParameter(name = PARAM_QUERY, mandatory = true)
    String query;

    @Override
    public void initialize(UimaContext ctx) throws ResourceInitializationException {
      super.initialize(ctx);
      try {
        connection = jdbcResource.getConnection();
      } catch (final SQLException e) {
        throw new ResourceInitializationException(e);
      }
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
      PreparedStatement stmt;
      try {
        stmt = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY);
      } catch (final SQLException e1) {
        throw new AnalysisEngineProcessException(e1);
      }
      final String[] names = new String[] { null, "one", "two", "three" };
      int tested = 0;
      for (int i = 1; i < 4; i++) {
        try {
          stmt.setInt(1, i);
          final ResultSet result = stmt.executeQuery();
          while (result.next()) {
            final String name = result.getString(1);
            tested++;
            if (!names[i].equals(name))
              throw new AssertionError("expected '" + names[i] + "' - got '" + name + "'");
          }
        } catch (final SQLException e2) {
          throw new AnalysisEngineProcessException(e2);
        }
      }
      if (tested != 3) throw new AssertionError("expected 3 tests, made " + tested);
    }
  }

  ExternalResourceDescription descriptor;
  String url;
  String query = "SELECT value FROM entities WHERE id = ?";

  @Before
  public void setUp() throws Exception {
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    tmpDb.deleteOnExit();
    url = "jdbc:h2:" + tmpDb.getCanonicalPath();
    descriptor = JdbcConnectionResourceImpl.configure(url, "org.h2.Driver").create();
  }

  @Test
  public void testConfigureComplete() throws IOException {
    final String config = JdbcConnectionResourceImpl.configure("urlDummy", "driverDummy",
        "userDummy", "passDummy", 999, "loginDummy", false).toString();
    Assert.assertTrue(config.contains("urlDummy"));
    Assert.assertTrue(config.contains("driverDummy"));
    Assert.assertTrue(config.contains("userDummy"));
    Assert.assertTrue(config.contains("passDummy"));
    Assert.assertTrue(config.contains("999"));
    Assert.assertTrue(config.contains("loginDummy"));
  }

  @Test
  public void testConfigureStringString() {
    final String config = descriptor.toString();
    Assert.assertTrue(config.contains(url));
    Assert.assertTrue(config.contains("org.h2.Driver"));
  }

  @Test
  public void testConnection() throws SQLException, UIMAException {
    final Connection conn = DriverManager.getConnection(url);
    final Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(id INT PRIMARY KEY,"
        + "                    value VARCHAR)");
    stmt.executeUpdate("INSERT INTO entities VALUES(1, 'one')");
    stmt.executeUpdate("INSERT INTO entities VALUES(2, 'two')");
    stmt.executeUpdate("INSERT INTO entities VALUES(3, 'three')");
    stmt.close();
    conn.commit();
    conn.close();
    DisableLogging.enableLogging(Level.WARNING);
    final AnalysisEngine ae = AnalysisEngineFactory
        .createPrimitive(DummyAnalysisEngine.class, DummyAnalysisEngine.RES_JDBC_CONNECTOR,
            descriptor, DummyAnalysisEngine.PARAM_QUERY, query);
    ae.process(ae.newJCas());
  }
}
