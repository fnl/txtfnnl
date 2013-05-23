/* Created on Feb 4, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.descriptor.ConfigurationParameter;

/**
 * The LineBasedGazetteerResource uses an input stream from some URL to retrieve the ID, name
 * values used to populate its Gazetteer. The ID, name fields can be separated by anything that can
 * be recognized by a regular expression (<code>String.spit(regex, 2)</code>).
 * 
 * @author Florian Leitner
 */
public class LineBasedGazetteerResource extends AbstractGazetteerResource {
  /**
   * The field separator to use. May be a regular expression and defaults to a tab. Input lines are
   * split into ID, name pairs.
   */
  public static final String PARAM_SEPARATOR = "FieldSeparator";
  @ConfigurationParameter(name = PARAM_SEPARATOR, mandatory = false, defaultValue = "\t")
  protected String separator;

  public static class Builder extends AbstractGazetteerResource.Builder {
    public Builder(String url) {
      super(LineBasedGazetteerResource.class, url);
    }

    /** Any regular expression that can be used to split the input lines in two. */
    public Builder setSeparator(String regex) {
      setOptionalParameter(PARAM_SEPARATOR, regex);
      return this;
    }
  }

  /**
   * Configure a Gazetteer from a line-based data stream.
   * <p>
   * In the simplest case, this could be just a flat-file.
   * 
   * @param resourceUri where a line-based data stream can be fetched
   */
  public static Builder configure(String resourceUri) {
    return new Builder(resourceUri);
  }

  @Override
  public synchronized void load(DataResource dataResource) throws ResourceInitializationException {
    super.load(dataResource);
  }

  /**
   * Compile the DFA.
   * 
   * @throws ResourceInitializationException
   */
  @Override
  public void afterResourcesInitialized() {
    Pattern pattern = Pattern.compile(separator);
    String line = null;
    InputStream inStr = null;
    try {
      inStr = getInputStream();
      final BufferedReader reader = new BufferedReader(new InputStreamReader(inStr));
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0) continue;
        String[] keyVal = pattern.split(line, 2);
        put(keyVal[0], keyVal[1]);
      }
    } catch (final IOException e) {
      throw new RuntimeException(e.getLocalizedMessage() + " while loading " + resourceUri);
    } catch (IndexOutOfBoundsException e) {
      throw new RuntimeException("received an illegal line from " + resourceUri + ": '" + line +
          "'");
    } finally {
      if (inStr != null) {
        try {
          inStr.close();
        } catch (final IOException e) {}
      }
    }
  }
}
