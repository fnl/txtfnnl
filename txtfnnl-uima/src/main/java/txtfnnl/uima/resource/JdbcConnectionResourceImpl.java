package txtfnnl.uima.resource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.utils.UIMAUtils;

/**
 * A JDBC connector for arbitrary databases.
 * 
 * Any DB that has a JDBC connector can be connected as an external resource
 * of an AE.
 * 
 * Parameter settings:
 * <ul>
 * <li>String {@link #PARAM_DRIVER_CLASS} (required)</li>
 * <li>String {@link #PARAM_USERNAME} (default: none)</li>
 * <li>String {@link #PARAM_PASSWORD} (default: none)</li>
 * <li>Boolean {@link #PARAM_READ_ONLY} (default: false)</li>
 * <li>String {@link #PARAM_ISOLATION_LEVEL} (default: none)</li>
 * <li>Integer {@link #PARAM_LOGIN_TIMEOUT} (default: no timeout)</li>
 * </ul>
 * 
 * @author Florian Leitner
 */
public class JdbcConnectionResourceImpl implements JdbcConnectionResource, ExternalResourceAware {

	@ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
	private String resourceName;

	/**
	 * The JDBC URL String for the connection to make, i.e., the data resource
	 * URI.
	 */
	private String connectionUrl = null;

	/**
	 * The (required) fully qualified name of the JDBC driver to use.
	 * 
	 * Examples:
	 * <ul>
	 * <li><code>com.mysql.jdbc.Driver</code> (for MySQL)</li>
	 * <li><code>sun.jdbc.odbc.JdbcOdbcDriver</code> (for ODBC)</li>
	 * <li><code>oracle.jdbc.driver.OracleDriver</code> (for Oracle)</li>
	 * <li><code>org.postgresql.Driver</code> (for PostgreSQL)</li>
	 * <li><code>com.ibm.db2.jdbc.app.DB2Driver</code> (for DB2)</li>
	 * <li><code>org.h2.Driver</code> (for H2)</li>
	 * </ul>
	 */
	public static final String PARAM_DRIVER_CLASS = "DriverClass";
	@ConfigurationParameter(name = PARAM_DRIVER_CLASS, mandatory = true)
	private String driverClass;

	/** The (optional) username String for DB authentication. */
	public static final String PARAM_USERNAME = "Username";
	@ConfigurationParameter(name = PARAM_USERNAME, mandatory = false)
	private String username;

	/** The (optional) password String for DB authentication. */
	public static final String PARAM_PASSWORD = "Password";
	@ConfigurationParameter(name = PARAM_PASSWORD, mandatory = false)
	private String password;

	/**
	 * The (optional) timeout (in seconds, i.e., Integer) that a driver will
	 * wait while attempting to connect to a database.
	 */
	public static final String PARAM_LOGIN_TIMEOUT = "LoginTimeout";
	@ConfigurationParameter(name = PARAM_LOGIN_TIMEOUT, mandatory = false, defaultValue = "-1")
	private int loginTimeout = -1;

	/** An (optional) Boolean flag to make the connections all read-only. */
	public static final String PARAM_READ_ONLY = "ReadOnly";
	@ConfigurationParameter(name = PARAM_READ_ONLY, mandatory = false, defaultValue = "false")
	private boolean readOnly = false;

	/**
	 * An (optional) transaction isolation level String.
	 * 
	 * May be any of the following strings:
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
	@ExternalResource(key = PARAM_ISOLATION_LEVEL, mandatory = false)
	private String isolationStr;
	private int isolationLevel = -1;

	private final Lock lock = new ReentrantLock();

	private Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Configure an external resource description for an AE.
	 * 
	 * @param connectionUrl the (pure) URL to connect to (w/o driver)
	 * @param driverClass the driver name to use (see
	 *        {@link #PARAM_DRIVER_CLASS})
	 * @param username the username to use
	 * @param password the password to use
	 * @param loginTimeout set to timeout the connection
	 * @param isolationLevel set to an isolation level name (see
	 *        {@link #PARAM_ISOLATION_LEVEL})
	 * @param readOnly set to <code>true</code> if this connection should only
	 *        read
	 * @return a new resource descriptor
	 * @throws IOException
	 */
	@SuppressWarnings("serial")
	public static ExternalResourceDescription configure(String connectionUrl,
	                                                    final String driverClass,
	                                                    final String username,
	                                                    final String password,
	                                                    final int loginTimeout,
	                                                    final String isolationLevel,
	                                                    final boolean readOnly) throws IOException {
		return ExternalResourceFactory.createExternalResourceDescription(
		    JdbcConnectionResourceImpl.class, connectionUrl,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(PARAM_DRIVER_CLASS, driverClass);
				    put(PARAM_USERNAME, username);
				    put(PARAM_PASSWORD, password);
				    put(PARAM_ISOLATION_LEVEL, isolationLevel);
				    put(PARAM_LOGIN_TIMEOUT, loginTimeout);
				    put(PARAM_READ_ONLY, readOnly);
			    }
		    }));
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password,
	                                                    int loginTimeout, String isolationLevel)
	        throws IOException {
		return configure(connectionUrl, driverClass, username, password, loginTimeout,
		    isolationLevel, false);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password,
	                                                    int loginTimeout, boolean readOnly)
	        throws IOException {
		return configure(connectionUrl, driverClass, username, password, loginTimeout, null,
		    readOnly);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password,
	                                                    String isolationLevel, boolean readOnly)
	        throws IOException {
		return configure(connectionUrl, driverClass, username, password, -1, isolationLevel,
		    readOnly);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    int loginTimeout, String isolationLevel,
	                                                    boolean readOnly) throws IOException {
		return configure(connectionUrl, driverClass, null, null, loginTimeout, isolationLevel,
		    readOnly);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password,
	                                                    int loginTimeout) throws IOException {
		return configure(connectionUrl, driverClass, username, password, loginTimeout, null);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password,
	                                                    boolean readOnly) throws IOException {
		return configure(connectionUrl, driverClass, username, password, -1, readOnly);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password,
	                                                    String isolationLevel) throws IOException {
		return configure(connectionUrl, driverClass, username, password, -1, isolationLevel);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    String username, String password)
	        throws IOException {
		return configure(connectionUrl, driverClass, username, password, -1);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
	                                                    boolean readOnly) throws IOException {
		return configure(connectionUrl, driverClass, null, null, readOnly);
	}

	public static ExternalResourceDescription configure(String connectionUrl, String driverClass)
	        throws IOException {
		return configure(connectionUrl, driverClass, null, null);
	}

	/** @see txtfnnl.uima.resource.JdbcConnectionResource#getConnection() */
	public Connection getConnection() throws SQLException {
		logger.info("connecting to '" + connectionUrl + "'");
		Connection conn;

		lock.lock();

		try {
			if (username == null || password == null)
				conn = DriverManager.getConnection(connectionUrl);
			else
				conn = DriverManager.getConnection(connectionUrl, username, password);

			if (isolationLevel > -1)
				conn.setTransactionIsolation(isolationLevel);

			conn.setReadOnly(readOnly);
			logger.fine("connected to '" + connectionUrl + "'");
			return conn;
		} finally {
			lock.unlock();
		}
	}

	public void load(DataResource dataResource) throws ResourceInitializationException {
		ConfigurationParameterInitializer.initialize(this, dataResource);
		connectionUrl = dataResource.getUri().toString();
	}

	public String getResourceName() {
		return resourceName;
	}

	public void afterResourcesInitialized() {
		// load the driver
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			throw new AssertionError(new ResourceInitializationException(
			    ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] {
			        driverClass,
			        PARAM_DRIVER_CLASS }, e));
		}

		// determine the isolation level
		if (isolationStr != null) {
			try {
				isolationLevel = Connection.class.getField(isolationStr).getInt(null);
			} catch (Exception e) {
				throw new AssertionError(new ResourceInitializationException(
				    ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] {
				        isolationStr,
				        PARAM_ISOLATION_LEVEL }, e));
			}
		}

		// set the login timeout
		if (loginTimeout != -1) {
			if (loginTimeout > 0)
				DriverManager.setLoginTimeout(loginTimeout);
			else
				throw new AssertionError(new ResourceInitializationException(
				    ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] {
				        loginTimeout,
				        PARAM_LOGIN_TIMEOUT }));
		}
		// nothing to do...
	}

}
