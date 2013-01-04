package txtfnnl.uima.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ExternalResourceFactory;

/**
 * A generic implementation of a StringMapResource that reads data from a line-based stream with
 * the data separated by some separator. By default, tab is used as separator. Implementing
 * classes must define a protocol to parse the values into generic types.
 * 
 * @author Florian Leitner
 * @param <V> is the value type returned by the resource for the String keys
 */
public abstract class LineBasedStringMapResource<V> implements StringMapResource<V>,
    ExternalResourceAware {
  @ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
  private String resourceName;
  protected String resourceUrl;
  /**
   * The field separator to use. May be a regular expression and defaults to a tab. Input lines are
   * split on this regular expression.
   */
  public static final String PARAM_SEPARATOR = "FieldSeparator";
  @ConfigurationParameter(name = PARAM_SEPARATOR, mandatory = false, defaultValue = "\t")
  protected String separator;
  protected Map<String, V> resourceMap = new HashMap<String, V>();
  protected String line;

  public void load(DataResource data) throws ResourceInitializationException {
    ConfigurationParameterInitializer.initialize(this, data);
    final URI uri = data.getUri();
    if (uri != null) {
      resourceUrl = uri.toString();
    }
    InputStream inStr = null;
    try {
      // open input stream to data
      inStr = data.getInputStream();
      // read each line
      final BufferedReader reader = new BufferedReader(new InputStreamReader(inStr));
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0) {
          continue;
        }
        parse(line.split(separator));
      }
    } catch (final IOException e) {
      throw new ResourceInitializationException(e);
    } finally {
      if (inStr != null) {
        try {
          inStr.close();
        } catch (final IOException e) {}
      }
    }
  }

  /**
   * Any implementing class must decide what to do with the parsed lines and how to fill the
   * resourceMap.
   * 
   * @param items to process
   */
  abstract void parse(String[] items) throws ResourceInitializationException;

  public V get(String key) {
    return resourceMap.get(key);
  }

  public int size() {
    return resourceMap.size();
  }

  public Iterator<String> iterator() {
    return resourceMap.keySet().iterator();
  }

  public String getResourceName() {
    return resourceName;
  }

  public String getResourceUrl() {
    return resourceUrl;
  }

  public void afterResourcesInitialized() {
    // nothing to do here...
  }
}
