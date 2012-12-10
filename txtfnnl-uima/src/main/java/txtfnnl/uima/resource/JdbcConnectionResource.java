package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.uima.resource.SharedResourceObject;

/**
 * A resource specification for SQL database connection objects.
 * 
 * @author Florian Leitner
 */
public interface JdbcConnectionResource extends SharedResourceObject {

	/**
	 * Fetch a new connection instance.
	 * 
	 * @see java.sql.DriverManager#getConnection(String)
	 * 
	 * @return a JDBC connection
	 */
	public Connection getConnection() throws SQLException;

	public String getUrl();
}
