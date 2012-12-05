package txtfnnl.uima.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.ExternalResourceAware;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.ExternalResourceFactory;

/**
 * A generic implementation of a StringMapResource that reads data from a
 * line-based stream with the data separated by some string.
 * 
 * @author Florian Leitner
 * @param <V> is the value type to map
 */
public abstract class LineBasedStringMapResource<V> implements StringMapResource<V>,
        ExternalResourceAware {

	@ConfigurationParameter(name = ExternalResourceFactory.PARAM_RESOURCE_NAME)
	private String resourceName;

	public static final String DEFAULT_SEPARATOR = "\t";

	/** The (optional) field separator to use (defaults to tab). */
	public static final String PARAM_SEPARATOR = "FieldSeparator";
	@ExternalResource(key = PARAM_SEPARATOR, mandatory = false)
	protected String separator = DEFAULT_SEPARATOR;

	protected Map<String, V> resourceMap = new HashMap<String, V>();
	protected String line;

	public void load(DataResource data) throws ResourceInitializationException {
		ConfigurationParameterInitializer.initialize(this, data);
		InputStream inStr = null;

		try {
			// open input stream to data
			inStr = data.getInputStream();
			// read each line
			BufferedReader reader = new BufferedReader(new InputStreamReader(inStr));

			while ((line = reader.readLine()) != null) {
				line = line.trim();

				if (line.length() == 0)
					continue;

				String[] items = line.split(separator);

				parse(items);
			}
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		} finally {
			if (inStr != null) {
				try {
					inStr.close();
				} catch (IOException e) {}
			}
		}
	}

	/**
	 * Any implementing class must decide what to do with the parsed lines and
	 * how to fill the resourceMap.
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

	public void afterResourcesInitialized() {
		// nothing to do here...
	}
}
