/* Created on Feb 13, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.MetaDataObject;

/**
 * A Builder Pattern implementation to construct configuration parameter arrays for UIMA
 * {@link MetaDataObject MetaDataObjects} (ColectionReaders, AnalysisComponents, SharedResources,
 * etc.) used by the UIMAfit factories.
 * 
 * @author Florian Leitner
 */
public abstract class ConfigurationBuilder<T extends MetaDataObject> {
  /** Configuration parameter storage for a builder. */
  private final Map<String, Object> parameters;

  /** Create a new builder object. */
  protected ConfigurationBuilder() {
    parameters = new HashMap<String, Object>();
  }

  /** Set an optional parameter (name must not be null). */
  protected void setOptionalParameter(String name, Object value) {
    if (name == null) throw new IllegalArgumentException("paramter name cannot be null");
    parameters.put(name, value);
  }

  /** Set a required parameter (both name and value must not be null). */
  protected void setRequiredParameter(String name, Object value) {
    if (value == null) throw new IllegalArgumentException("paramter value cannot be null");
    setOptionalParameter(name, value);
  }

  /** Create a parameter array for the (descriptor) factory methods. */
  protected Object[] makeParameterArray() throws IOException {
    final List<Object> list = new LinkedList<Object>();
    for (final String key : parameters.keySet()) {
      Object value = parameters.get(key);
      if (value != null && !"".equals(value)) {
        list.add(key);
        if (value instanceof File) {
          value = ((File) value).getCanonicalPath();
        }
        list.add(value);
      }
    }
    return list.toArray(new Object[list.size()]);
  }

  /**
   * Create a descriptor meta-data object using the configured parameters.
   * 
   * @throws ResourceInitializationException if the descriptor cannot be created
   */
  public abstract T create() throws ResourceInitializationException;
}
