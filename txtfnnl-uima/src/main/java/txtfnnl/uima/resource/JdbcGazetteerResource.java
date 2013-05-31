/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.apache.uima.util.Level;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.descriptor.ConfigurationParameter;

/**
 * The JdbcGazetteerResource uses a {@link JdbcConnectionResource#PARAM_DRIVER_CLASS JDBC database}
 * to retrieve the ID, name values used to populate the Gazetteer. It can use any user-defined
 * {@link JdbcGazetteerResource#PARAM_QUERY_SQL query} that selects these ID, name values and uses
 * regular expressions matching for those names.
 * 
 * @author Florian Leitner
 */
public class JdbcGazetteerResource extends AbstractExactGazetteerResource implements
    JdbcConnectionResource, ExternalResourceAware {
  /** The <b>mandatory</b> SQL query used to fetch the entity names. */
  public static final String PARAM_QUERY_SQL = "QuerySQL";
  @ConfigurationParameter(name = PARAM_QUERY_SQL, mandatory = true)
  protected String querySql;
  /** @see JdbcConnectionResourceImpl#PARAM_DRIVER_CLASS */
  public static final String PARAM_DRIVER_CLASS = JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS;
  @ConfigurationParameter(name = PARAM_DRIVER_CLASS, mandatory = true)
  protected String driverClass;
  /** @see JdbcConnectionResourceImpl#PARAM_USERNAME */
  public static final String PARAM_USERNAME = JdbcConnectionResourceImpl.PARAM_USERNAME;
  @ConfigurationParameter(name = PARAM_USERNAME, mandatory = false)
  protected String username;
  /** @see JdbcConnectionResourceImpl#PARAM_PASSWORD */
  public static final String PARAM_PASSWORD = JdbcConnectionResourceImpl.PARAM_PASSWORD;
  @ConfigurationParameter(name = PARAM_PASSWORD, mandatory = false)
  protected String password;
  /** @see JdbcConnectionResourceImpl#PARAM_LOGIN_TIMEOUT */
  public static final String PARAM_LOGIN_TIMEOUT = JdbcConnectionResourceImpl.PARAM_LOGIN_TIMEOUT;
  @ConfigurationParameter(name = PARAM_LOGIN_TIMEOUT, mandatory = false, defaultValue = "-1")
  protected int loginTimeout;
  /** @see JdbcConnectionResourceImpl#PARAM_READ_ONLY */
  public static final String PARAM_READ_ONLY = JdbcConnectionResourceImpl.PARAM_READ_ONLY;
  @ConfigurationParameter(name = PARAM_READ_ONLY, mandatory = false, defaultValue = "true")
  protected boolean readOnly;
  /** @see JdbcConnectionResourceImpl#PARAM_ISOLATION_LEVEL */
  public static final String PARAM_ISOLATION_LEVEL = JdbcConnectionResourceImpl.PARAM_ISOLATION_LEVEL;
  @ConfigurationParameter(name = PARAM_ISOLATION_LEVEL, mandatory = false, defaultValue = "-1")
  protected int isolationLevel;

  public static class Builder extends AbstractExactGazetteerResource.Builder implements
      AuthenticationResourceBuilder {
    /** Protected constructor for inherited implementations. */
    protected Builder(Class<? extends SharedResourceObject> klass, String url, String driverClass,
        String querySql) {
      super(klass, url);
      setRequiredParameter(PARAM_DRIVER_CLASS, driverClass);
      setRequiredParameter(PARAM_QUERY_SQL, querySql);
    }

    Builder(String url, String driverClass, String querySql) {
      super(JdbcGazetteerResource.class, url);
      setRequiredParameter(PARAM_DRIVER_CLASS, driverClass);
      setRequiredParameter(PARAM_QUERY_SQL, querySql);
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
   * @param query that will retrieve ID, name pairs from the database
   */
  public static Builder configure(String databaseUrl, String driverClassName, String query) {
    return new Builder(databaseUrl, driverClassName, query);
  }

  /** Generate the keys, the trie and the key-to-ID mappings. */
  @Override
  public void afterResourcesInitialized() {
    // note: "afterResourcesInitialized()" is a sort-of broken uimaFIT API,
    // because it cannot throw a ResourceInitializationException
    // therefore, this code throws assertion errors to the same effect...
    initializeJdbc();
    // fetch and process the mappings
    // uses "key = makeKey(name) && if (key != null) processMapping(dbId, name, key)"
    try {
      Connection conn = getConnection();
      Statement stmt = conn.createStatement();
      logger.log(Level.INFO, "running SQL query: ''{0}''", querySql);
      ResultSet result = stmt.executeQuery(querySql);
      while (result.next()) {
        final String dbId = result.getString(1);
        final String name = result.getString(2);
        put(dbId, name);
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

  protected void initializeJdbc() {
    // load the DB driver
    try {
      Class.forName(driverClass);
    } catch (final ClassNotFoundException e) {
      throw new RuntimeException(new ResourceInitializationException(
          ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] { driverClass,
              PARAM_DRIVER_CLASS }, e));
    }
    // set the DB login timeout
    if (loginTimeout > 0) {
      DriverManager.setLoginTimeout(loginTimeout);
    } else if (loginTimeout != -1)
      throw new RuntimeException(new ResourceInitializationException(
          ResourceInitializationException.RESOURCE_DATA_NOT_VALID, new Object[] { loginTimeout,
              PARAM_LOGIN_TIMEOUT }));
  }

  /** {@inheritDoc} */
  public synchronized Connection getConnection() throws SQLException {
    logger.log(Level.INFO, "connecting to '" + resourceUri + "'");
    Connection conn;
    if (username == null || password == null) conn = DriverManager.getConnection(resourceUri);
    else conn = DriverManager.getConnection(resourceUri, username, password);
    if (isolationLevel > -1) conn.setTransactionIsolation(isolationLevel);
    conn.setReadOnly(readOnly);
    logger.log(Level.FINE, "connected to '" + resourceUri + "'");
    return conn;
  }
}
