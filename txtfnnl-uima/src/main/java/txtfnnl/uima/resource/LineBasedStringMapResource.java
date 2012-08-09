package txtfnnl.uima.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;

/**
 * A generic implementation of a StringMapResource that reads data from a
 * line-based stream with the data separated by some string.
 * 
 * @author Florian Leitner
 * @param <V> is the value type to map
 */
public abstract class LineBasedStringMapResource<V> implements
        SharedResourceObject, StringMapResource<V> {

	public static final String DEFAULT_SEPARATOR = "\t";
	
	protected Map<String, V> resourceMap = new HashMap<String, V>();
	protected String line;

	/**
	 * The separator used on each line to separate relevant items.
	 */
	String separator = DEFAULT_SEPARATOR;

	/* (non-Javadoc)
	 * 
	 * @see
	 * org.apache.uima.resource.SharedResourceObject#load(org.apache.uima
	 * .resource.DataResource) */
	public void load(DataResource data) throws ResourceInitializationException {
		InputStream inStr = null;
		
		try {
			// open input stream to data
			inStr = data.getInputStream();
			// read each line
			BufferedReader reader = new BufferedReader(new InputStreamReader(
			    inStr));

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
	 * Any implementing class must decide what to do with the parsed lines 
	 * and fill the resourceMap.
	 * 
	 * @param items to process
	 */
	abstract void parse(String[] items) throws ResourceInitializationException;

	/* (non-Javadoc)
	 * 
	 * @see txtfnnl.uima.resource.StringMapResource#get(java.lang.String) */
	public V get(String key) {
		return resourceMap.get(key);
	}

}
