package txtfnnl.uima.resource;

/**
 * A resource specification that uses String keys to return (arbitrary) values.
 *
 * @author Florian Leitner
 */
public interface StringMapResource<V> {

	/**
	 * Return the mapped value V for a given key.
	 * 
	 * @param key to fetch the value V for
	 * @return the value V mapped to the key
	 */
	public V get(String key);
}
