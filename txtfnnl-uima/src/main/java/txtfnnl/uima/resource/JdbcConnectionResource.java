package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A resource specification for SQL database connection objects.
 *
 * @author Florian Leitner
 */
public interface JdbcConnectionResource {

	/**
	 * Fetch a new connection instance.
	 * 
	 * @see java.sql.DriverManager#getConnection(String)
	 * 
	 * @return a JDBC connection
	 */
	public Connection getConnection() throws SQLException;
}
