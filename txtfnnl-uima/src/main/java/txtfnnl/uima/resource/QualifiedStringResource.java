/* Created on May 14, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;

/**
 * A QualifiedStringResource consists of two-column String tables where the first column contains
 * the key of the to which the value in the second column will be assigned. 
 *
 * @author Florian Leitner
 */
public class QualifiedStringResource extends LineBasedStringMapResource<String> {
  public static class Builder extends LineBasedStringMapResource.Builder {
    protected Builder(Class<? extends SharedResourceObject> klass, String url) {
      super(klass, url);
    }

    public Builder(String resourceUrl) {
      this(QualifiedStringResource.class, resourceUrl);
    }
  }

  /**
   * Configure a new resource of String-to-String mappings ("Map<String, String>").
   * 
   * @param resourceUrl the URL where this resource is located (In the case of a file, make sure
   *        that the (data) resource URL is prefixed with the "file:" schema prefix.)
   */
  public static Builder configure(String resourceUrl) {
    return new Builder(resourceUrl);
  }


  @Override
  void parse(String[] items) throws ResourceInitializationException {
    try {
      if (items.length > 2) throw new IndexOutOfBoundsException();
      resourceMap.put(items[0], items[1]);
    } catch (final IndexOutOfBoundsException e) {
      throw new ResourceInitializationException(new RuntimeException("illegal line: '" + line +
          "' with " + items.length + " fields"));
    }
  }}
