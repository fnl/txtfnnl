package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.uima.resource.SharedResourceObject;

/**
 * A resource specification providing database connections.
 * 
 * @author Florian Leitner
 */
public interface JdbcConnectionResource extends SharedResourceObject {
  /**
   * Provides a connection instance.
   * 
   * @see java.sql.DriverManager#getConnection(String)
   */
  public Connection getConnection() throws SQLException;

  /**
   * Fetch the JDBC URL for the {@link Connection}.
   * 
   * @return the JDBC URL
   */
  public String getUrl();
}
