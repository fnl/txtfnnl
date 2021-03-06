package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.SharedResourceBuilder;

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
   * The (optional) timeout (in seconds, i.e., Integer) that a driver will wait while attempting to
   * connect to a database (default: -1, no timeout).
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
   * An (optional) transaction isolation level (default: no isolation, <i>-1</i>).
   * <p>
   * May be set to any of the following values:
   * <ul>
   * <li>{@link java.sql.Connection#TRANSACTION_NONE TRANSACTION_NONE}</li>
   * <li>{@link java.sql.Connection#TRANSACTION_READ_COMMITTED TRANSACTION_READ_COMMITTED}</li>
   * <li>{@link java.sql.Connection#TRANSACTION_READ_UNCOMMITTED TRANSACTION_READ_UNCOMMITTED}</li>
   * <li>{@link java.sql.Connection#TRANSACTION_REPEATABLE_READ TRANSACTION_REPEATABLE_READ}</li>
   * <li>{@link java.sql.Connection#TRANSACTION_SERIALIZABLE TRANSACTION_SERIALIZABLE}</li>
   * </ul>
   */
  public static final String PARAM_ISOLATION_LEVEL = "IsolationLevel";
  @ConfigurationParameter(name = PARAM_ISOLATION_LEVEL, mandatory = false, defaultValue = "-1")
  protected int isolationLevel;
  protected Logger logger = null;

  public static class Builder extends SharedResourceBuilder implements AuthenticationResourceBuilder {
    protected Builder(Class<? extends SharedResourceObject> klass, String url, String driverClass) {
      super(klass, url);
      setRequiredParameter(PARAM_DRIVER_CLASS, driverClass);
    }

    Builder(String url, String driverClass) {
      this(JdbcConnectionResourceImpl.class, url, driverClass);
    }

    /** Define a <code>username</code> to authenticate DB connections. */
    public Builder setUsername(String username) {
      setOptionalParameter(PARAM_USERNAME, username);
      return this;
    }

    /** Define a <code>password</code> to authenticate DB connections. */
    public Builder setPassword(String password) {
      setOptionalParameter(PARAM_PASSWORD, password);
      return this;
    }

    /** Define a login <code>timeout</code> in seconds for the DB authentication process. */
    public Builder setLoginTimeout(int timeout) {
      if (timeout < 1 && timeout != -1)
        throw new IllegalArgumentException("illegal timeout value");
      setOptionalParameter(PARAM_LOGIN_TIMEOUT, timeout);
      return this;
    }

    /** Make the DB connections all read-only. */
    public Builder readOnly() {
      setOptionalParameter(PARAM_READ_ONLY, Boolean.TRUE);
      return this;
    }

    /** Dirty reads, non-repeatable reads, and phantom reads can occur. */
    public Builder readUncommittedTransactions() {
      setOptionalParameter(PARAM_ISOLATION_LEVEL, Connection.TRANSACTION_READ_UNCOMMITTED);
      return this;
    }

    /** Prevent dirty reads; non-repeatable reads and phantom reads can occur. */
    public Builder readCommittedTransactions() {
      setOptionalParameter(PARAM_ISOLATION_LEVEL, Connection.TRANSACTION_READ_COMMITTED);
      return this;
    }

    /** Prevent dirty reads and non-repeatable; phantom reads can occur. */
    public Builder repeatableReadTransactions() {
      setOptionalParameter(PARAM_ISOLATION_LEVEL, Connection.TRANSACTION_REPEATABLE_READ);
      return this;
    }

    /** Prevent dirty reads, non-repeatable and phantom reads. */
    public Builder serializableTransactions() {
      setOptionalParameter(PARAM_ISOLATION_LEVEL, Connection.TRANSACTION_SERIALIZABLE);
      return this;
    }
  }

  /**
   * Configure a resource for transaction-less, read-write JDBC connections.
   * 
   * @param databaseUrl a JDBC database URL
   * @param driverClassName a fully qualified JDBC driver class name
   */
  public static Builder configure(String databaseUrl, String driverClassName) {
    return new Builder(databaseUrl, driverClassName);
  }

  /** {@inheritDoc} */
  public synchronized Connection getConnection() throws SQLException {
    logger.log(Level.INFO, "connecting to '" + connectionUrl + "'");
    Connection conn;
    if (username == null || password == null) conn = DriverManager.getConnection(connectionUrl);
    else conn = DriverManager.getConnection(connectionUrl, username, password);
    if (isolationLevel > -1) conn.setTransactionIsolation(isolationLevel);
    conn.setReadOnly(readOnly);
    logger.log(Level.FINE, "connected to '" + connectionUrl + "'");
    return conn;
  }

  public void load(DataResource dataResource) throws ResourceInitializationException {
    ConfigurationParameterInitializer.initialize(this, dataResource);
    logger = dataResource.getLogger();
    if (logger == null) logger = UIMAFramework.getLogger(this.getClass());
    connectionUrl = dataResource.getUri().toString();
    logger.log(Level.INFO, "resource loaded");
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
          ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] { driverClass,
              PARAM_DRIVER_CLASS }, e));
    }
    // set the login timeout
    if (loginTimeout > 0) {
      DriverManager.setLoginTimeout(loginTimeout);
    } else if (loginTimeout != -1)
      throw new AssertionError(new ResourceInitializationException(
          ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] { loginTimeout,
              PARAM_LOGIN_TIMEOUT }));
  }

  /** {@inheritDoc} */
  public String getUrl() {
    return connectionUrl;
  }
}
