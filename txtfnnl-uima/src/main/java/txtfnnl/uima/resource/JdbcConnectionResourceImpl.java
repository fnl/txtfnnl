package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.apache.uima.resource.metadata.ConfigurationParameterSettings;

/**
 * A JDBC connector.
 * 
 * Parameter settings:
 * <ul>
 *   <li>String {@link #PARAM_DRIVER_CLASS} (required)</li>
 *   <li>String {@link #PARAM_ISOLATION_LEVEL}</li>
 *   <li>String {@link #PARAM_USERNAME}</li>
 *   <li>String {@link #PARAM_PASSWORD}</li>
 *   <li>Boolean {@link #PARAM_READ_ONLY}</li>
 *   <li>Integer {@link #PARAM_LOGIN_TIMEOUT}</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class JdbcConnectionResourceImpl implements JdbcConnectionResource,
        SharedResourceObject {

	/**
	 * The (required) fully qualified name String of the JDBC driver to use.
	 * 
	 * Examples:
	 * <ul>
	 * <li><code>com.mysql.jdbc.Driver</code> MySQL</li>
	 * <li><code>sun.jdbc.odbc.JdbcOdbcDriver</code> ODBC</li>
	 * <li><code>oracle.jdbc.driver.OracleDriver</code> Oracle</li>
	 * <li><code>org.postgresql.Driver</code> PostgreSQL</li>
	 * <li><code>com.ibm.db2.jdbc.app.DB2Driver</code> DB2</li>
	 * </ul>
	 * 
	 * */
	public static final String PARAM_DRIVER_CLASS = "DriverClass";

	// /** The JDBC URL String for the connection to make. */
	// public static final String PARAM_CONNECTION_URL = "ConnectionURL";

	/** The (optional) username String for DB authentication. */
	public static final String PARAM_USERNAME = "Username";

	/** The (optional) password String for DB authentication. */
	public static final String PARAM_PASSWORD = "Password";

	/**
	 * The (optional) timeout (in seconds, i.e., Integer) that a driver will
	 * wait while attempting to connect to a database.
	 */
	public static final String PARAM_LOGIN_TIMEOUT = "LoginTimeout";

	/** An (optional) Boolean flag to make the connections all read-only. */
	public static final String PARAM_READ_ONLY = "ReadOnly";

	/**
	 * An (optional) transaction isolation level String.
	 * 
	 * May be any of the following:
	 * <ul>
	 * <li>
	 * <code>{@link java.sql.Connection#TRANSACTION_NONE TRANSACTION_NONE}</code>
	 * </li>
	 * <li>
	 * <code>{@link java.sql.Connection#TRANSACTION_READ_COMMITTED TRANSACTION_READ_COMMITTED}</code>
	 * </li>
	 * <li>
	 * <code>{@link java.sql.Connection#TRANSACTION_READ_UNCOMMITTED TRANSACTION_READ_UNCOMMITTED}</code>
	 * </li>
	 * <li>
	 * <code>{@link java.sql.Connection#TRANSACTION_REPEATABLE_READ TRANSACTION_REPEATABLE_READ}</code>
	 * </li>
	 * <li>
	 * <code>{@link java.sql.Connection#TRANSACTION_SERIALIZABLE TRANSACTION_SERIALIZABLE}</code>
	 * </li>
	 * </ul>
	 */
	public static final String PARAM_ISOLATION_LEVEL = "IsolationLevel";

	private boolean readOnly = false;
	private int isolationLevel = -1;
	private String connectionUrl = null;
	private String password = null;
	private String username = null;

	private final Lock lock = new ReentrantLock();
	private Logger logger = Logger.getLogger(getClass().getName());

	/** @see txtfnnl.uima.resource.JdbcConnectionResource#getConnection() */
	public Connection getConnection() throws SQLException {
		logger.info("connecting to '" + connectionUrl + "'");
		Connection conn;

		lock.lock();

		try {
			if (username == null || password == null)
				conn = DriverManager.getConnection(connectionUrl);
			else
				conn = DriverManager.getConnection(connectionUrl, username,
				    password);

			if (isolationLevel > -1)
				conn.setTransactionIsolation(isolationLevel);

			conn.setReadOnly(readOnly);
			return conn;
		} finally {
			lock.unlock();
			logger.fine("connected to '" + connectionUrl + "'");
		}
	}

	public void load(DataResource dataResource)
	        throws ResourceInitializationException {
		lock.lock();

		try {
			ConfigurationParameterSettings settings = dataResource
			    .getMetaData().getConfigurationParameterSettings();

			connectionUrl = dataResource.getUri().toString();
			String driverClassName = (String) settings
			    .getParameterValue(PARAM_DRIVER_CLASS);
			// connectionUrl = (String) settings
			// .getParameterValue(PARAM_CONNECTION_URL);
			String isolationStr = (String) settings
			    .getParameterValue(PARAM_ISOLATION_LEVEL);
			password = (String) settings.getParameterValue(PARAM_PASSWORD);
			username = (String) settings.getParameterValue(PARAM_USERNAME);
			Integer timeoutInt = (Integer) settings
			    .getParameterValue(PARAM_LOGIN_TIMEOUT);
			Boolean readOnlyBool = (Boolean) settings
			    .getParameterValue(PARAM_READ_ONLY);

			// CONNECTION_URL
			// if (connectionUrl == null)
			// throw new ResourceInitializationException(
			// ResourceInitializationException.CONFIG_SETTING_ABSENT,
			// new Object[] { PARAM_CONNECTION_URL });

			// DRIVER_CLASS
			if (driverClassName == null)
				throw new ResourceInitializationException(
				    ResourceInitializationException.CONFIG_SETTING_ABSENT,
				    new Object[] { PARAM_DRIVER_CLASS });

			try {
				Class.forName(driverClassName);
			} catch (ClassNotFoundException e) {
				throw new ResourceInitializationException(
				    ResourceInitializationException.RESOURCE_DATA_NOT_VALID,
				    new Object[] { driverClassName, PARAM_DRIVER_CLASS }, e);
			}

			// ISLOLATION_LEVEL
			if (isolationStr != null) {
				try {
					isolationLevel = Connection.class.getField(isolationStr)
					    .getInt(null);
				} catch (Exception e) {
					throw new ResourceInitializationException(
					    ResourceInitializationException.RESOURCE_DATA_NOT_VALID,
					    new Object[] { isolationStr, PARAM_ISOLATION_LEVEL },
					    e);
				}
			}

			// LOGIN_TIMEOUT
			if (timeoutInt != null) {
				if (timeoutInt > 0)
					DriverManager.setLoginTimeout(timeoutInt);
				else
					throw new ResourceInitializationException(
					    ResourceInitializationException.RESOURCE_DATA_NOT_VALID,
					    new Object[] { timeoutInt, PARAM_LOGIN_TIMEOUT });
			}

			// READ_ONLY
			if (readOnlyBool != null)
				readOnly = readOnlyBool.booleanValue();
		} finally {
			lock.unlock();
		}
	}

}
