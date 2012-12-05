package txtfnnl.uima.resource;

import java.util.Iterator;

import org.apache.uima.resource.SharedResourceObject;

/**
 * A resource specification that uses String keys to return (arbitrary) values.
 *
 * @author Florian Leitner
 */
public interface StringMapResource<V> extends SharedResourceObject {

	/**
	 * Return the mapped value V for a given key.
	 * 
	 * @param key to fetch the value V for
	 * @return the value V mapped to the key
	 */
	public V get(String key);
	
	/**
	 * Return the number of keys.
	 * 
	 * @return the number of keys known by this resource.
	 */
	public int size();

	
	/**
	 * Fetch an iterator for the keys.
	 * 
	 * @return a key iterator
	 */
	public Iterator<String> iterator();
	
}
