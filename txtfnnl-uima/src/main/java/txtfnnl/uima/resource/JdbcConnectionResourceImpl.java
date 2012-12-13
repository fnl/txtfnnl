package txtfnnl.uima.resource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.utils.UIMAUtils;

/**
 * A JDBC resource to interface with arbitrary databases. Any DB that has a JDBC driver can be used
 * as an external resource of an AE. The resource then generates {@link Connection} instances.
 * 
 * @author Florian Leitner
 */
public class JdbcConnectionResourceImpl implements JdbcConnectionResource, ExternalResourceAware {
    @ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
    private String resourceName;
    /**
     * The JDBC URL String for the connection to make, i.e., the data resource URI.
     */
    private String connectionUrl = null;
    /**
     * The (required) fully qualified name of the JDBC driver to use.
     * <p>
     * Example driver classes for JDBC-enabled databases:
     * <ul>
     * <li><code>com.mysql.jdbc.Driver</code> (MySQL)</li>
     * <li><code>sun.jdbc.odbc.JdbcOdbcDriver</code> (any ODBC)</li>
     * <li><code>oracle.jdbc.driver.OracleDriver</code> (Oracle)</li>
     * <li><code>org.postgresql.Driver</code> (PostgreSQL)</li>
     * <li><code>com.ibm.db2.jdbc.app.DB2Driver</code> (DB2)</li>
     * <li><code>org.h2.Driver</code> (H2)</li>
     * </ul>
     */
    public static final String PARAM_DRIVER_CLASS = "DriverClass";
    @ConfigurationParameter(name = PARAM_DRIVER_CLASS, mandatory = true)
    protected String driverClass;
    /** The (optional) username String for DB authentication. */
    public static final String PARAM_USERNAME = "Username";
    @ConfigurationParameter(name = PARAM_USERNAME, mandatory = false)
    protected String username;
    /** The (optional) password String for DB authentication. */
    public static final String PARAM_PASSWORD = "Password";
    @ConfigurationParameter(name = PARAM_PASSWORD, mandatory = false)
    protected String password;
    /**
     * The (optional) timeout (in seconds, i.e., Integer) that a driver will wait while attempting
     * to connect to a database (default: -1, no timeout).
     */
    public static final String PARAM_LOGIN_TIMEOUT = "LoginTimeout";
    @ConfigurationParameter(name = PARAM_LOGIN_TIMEOUT, mandatory = false, defaultValue = "-1")
    protected int loginTimeout;
    /**
     * An flag to indicate the connections are all read-only (default: <code>true</code>).
     */
    public static final String PARAM_READ_ONLY = "ReadOnly";
    @ConfigurationParameter(name = PARAM_READ_ONLY, mandatory = false, defaultValue = "true")
    protected boolean readOnly;
    /**
     * An (optional) transaction isolation level String (default: no isolation, <i>-1</i>).
     * <p>
     * May be set to any of the following String values:
     * <ul>
     * <li>" <code>{@link java.sql.Connection#TRANSACTION_NONE TRANSACTION_NONE}</code> "</li>
     * <li>"
     * <code>{@link java.sql.Connection#TRANSACTION_READ_COMMITTED TRANSACTION_READ_COMMITTED}</code>
     * "</li>
     * <li>"
     * <code>{@link java.sql.Connection#TRANSACTION_READ_UNCOMMITTED TRANSACTION_READ_UNCOMMITTED}</code>
     * "</li>
     * <li>"
     * <code>{@link java.sql.Connection#TRANSACTION_REPEATABLE_READ TRANSACTION_REPEATABLE_READ}</code>
     * "</li>
     * <li>"
     * <code>{@link java.sql.Connection#TRANSACTION_SERIALIZABLE TRANSACTION_SERIALIZABLE}</code> "
     * </li>
     * </ul>
     */
    public static final String PARAM_ISOLATION_LEVEL = "IsolationLevel";
    @ConfigurationParameter(name = PARAM_ISOLATION_LEVEL, mandatory = false)
    private String isolationStr;
    protected int isolationLevel = -1;
    private final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Configure a JDBC Connection Resource.
     * 
     * @param connectionUrl the JDBC URL to connect to ("<code>jdbc:</code> <i>[provider]</i>
     *        <code>://</code><i>[host[:port]]</i> <code>/</code><i>[database]</i>")
     * @param driverClass the driver's class name to use (see {@link #PARAM_DRIVER_CLASS})
     * @param username the username to use (optional)
     * @param password the password to use (optional)
     * @param loginTimeout seconds before timing out the connection (optional)
     * @param isolationLevel an isolation level name (see {@link #PARAM_ISOLATION_LEVEL}, optional)
     * @param readOnly if <code>false</code>, the resulting connections can be used to write to the
     *        DB
     * @return a configured descriptor
     * @throws IOException
     */
    @SuppressWarnings("serial")
    public static ExternalResourceDescription configure(String connectionUrl,
            final String driverClass, final String username, final String password,
            final int loginTimeout, final String isolationLevel, final boolean readOnly)
            throws IOException {
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

    /**
     * Configure a JDBC Connection Resource that requires no username or password.
     * 
     * @param connectionUrl the URL to connect to
     * @param driverClass the driver's class name to use (see {@link #PARAM_DRIVER_CLASS})
     * @param loginTimeout seconds before timing out the connection (optional)
     * @param isolationLevel an isolation level name (see {@link #PARAM_ISOLATION_LEVEL}, optional)
     * @param readOnly if <code>false</code>, the resulting connections can be used to write to the
     *        DB
     * @return a configured descriptor
     * @throws IOException
     */
    public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
            int loginTimeout, String isolationLevel, boolean readOnly) throws IOException {
        return JdbcConnectionResourceImpl.configure(connectionUrl, driverClass, null, null,
            loginTimeout, isolationLevel, readOnly);
    }

    /**
     * Configure a <b>read-only</b>, non-isolated JDBC Connection Resource.
     * 
     * @param connectionUrl the URL to connect to
     * @param driverClass the driver's class name to use (see {@link #PARAM_DRIVER_CLASS})
     * @param username the username to use (optional)
     * @param password the password to use (optional)
     * @return a configured descriptor
     * @throws IOException
     */
    public static ExternalResourceDescription configure(String connectionUrl, String driverClass,
            String username, String password) throws IOException {
        return JdbcConnectionResourceImpl.configure(connectionUrl, driverClass, username,
            password, -1, null, true);
    }

    /**
     * Configure a <b>read-only</b>, non-isolated JDBC Connection Resource that requires no
     * username or password.
     * 
     * @param connectionUrl the URL to connect to
     * @param driverClass the driver's class name to use (see {@link #PARAM_DRIVER_CLASS})
     * @return a configured descriptor
     * @throws IOException
     */
    public static ExternalResourceDescription configure(String connectionUrl, String driverClass)
            throws IOException {
        return JdbcConnectionResourceImpl.configure(connectionUrl, driverClass, null, null);
    }

    /** {@inheritDoc} */
    public synchronized Connection getConnection() throws SQLException {
        logger.info("connecting to '" + connectionUrl + "'");
        Connection conn;
        if (username == null || password == null) {
            conn = DriverManager.getConnection(connectionUrl);
        } else {
            conn = DriverManager.getConnection(connectionUrl, username, password);
        }
        if (isolationLevel > -1) {
            conn.setTransactionIsolation(isolationLevel);
        }
        conn.setReadOnly(readOnly);
        logger.fine("connected to '" + connectionUrl + "'");
        return conn;
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
        } catch (final ClassNotFoundException e) {
            throw new AssertionError(new ResourceInitializationException(
                ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] {
                    driverClass, PARAM_DRIVER_CLASS }, e));
        }
        // determine the isolation level
        if (isolationStr != null) {
            try {
                isolationLevel = Connection.class.getField(isolationStr).getInt(null);
            } catch (final Exception e) {
                throw new AssertionError(new ResourceInitializationException(
                    ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] {
                        isolationStr, PARAM_ISOLATION_LEVEL }, e));
            }
        }
        // set the login timeout
        if (loginTimeout > 0) {
            DriverManager.setLoginTimeout(loginTimeout);
        } else if (loginTimeout != -1)
            throw new AssertionError(new ResourceInitializationException(
                ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] {
                    loginTimeout, PARAM_LOGIN_TIMEOUT }));
    }

    /** {@inheritDoc} */
    public String getUrl() {
        return connectionUrl;
    }
}
