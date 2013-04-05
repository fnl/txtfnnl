/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.uima.util.Level;

/**
 * The JdbcGazetteerResource uses a {@link JdbcConnectionResource#PARAM_DRIVER_CLASS JDBC database}
 * to retrieve the ID, name values used to populate the Gazetteer. It can use any user-defined
 * {@link GnamedGazetteerResource#PARAM_QUERY_SQL query} that selects these ID, name values and uses
 * regular expressions matching for those names.
 * 
 * @author Florian Leitner
 */
public class GnamedGazetteerResource extends JdbcGazetteerResource {

  /** Mappings of gene IDs to their taxon IDs. */
  private Map<String, String> taxonMap = new HashMap<String, String>();

  public static class Builder extends JdbcGazetteerResource.Builder {
    Builder(String url, String driverClass, String querySql) {
      super(GnamedGazetteerResource.class, url, driverClass, querySql);
    }
  }

  /**
   * Configure a resource for transaction-less, read-write JDBC connections.
   * 
   * @param databaseUrl a JDBC database URL
   * @param driverClassName a fully qualified JDBC driver class name
   * @param query that will retrieve ID, taxon ID, gene name triplets from the database
   */
  public static Builder configure(String databaseUrl, String driverClassName, String query) {
    return new Builder(databaseUrl, driverClassName, query);
  }

  /** Generate the keys, the trie and the key-to-ID mappings. */
  @Override
  public void afterResourcesInitialized() {
    initializeJdbc();
    // fetch a process the mappings
    // uses "key = makeKey(name) && if (key != null) processMapping(dbId, name, key)"
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      ResultSet result = stmt.executeQuery(querySql);
      while (result.next()) {
        final String geneId = result.getString(1);
        final String taxId = result.getString(2);
        final String name = result.getString(3);
        put(geneId, name);
        taxonMap.put(geneId, taxId);
      }
      conn.close();
    } catch (SQLException e) {
      logger.log(Level.SEVERE, "SQL error", e);
      throw new RuntimeException(e);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "unknown error", e);
      throw new RuntimeException(e);
    }
  }
  
  /** Return the associated taxon ID for the given gene ID. */
  public String getTaxId(String geneId) {
    return taxonMap.get(geneId);
  }
}
