package txtfnnl.uima.resource;

import java.util.Iterator;

import org.apache.uima.resource.SharedResourceObject;

/**
 * A resource specification that uses String keys to return (generic) values.
 * 
 * @author Florian Leitner
 */
public interface StringMapResource<V> extends SharedResourceObject, Iterable<String> {
  /**
   * Return the mapped value V for a given key.
   * 
   * @param key to fetch the value V for
   * @return the value V mapped to the key
   */
  public V get(String key);

  /**
   * Check if the given key exists.
   * 
   * @param key to check
   * @return <code>true</code> if the key is known to the resource
   */
  public boolean containsKey(String key);
  
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

  /**
   * Get the name of this resource.
   * 
   * @return resource name
   */
  public String getResourceName();

  /**
   * Get the URL or URI of this resource.
   * 
   * @return the resource URL string or <code>null</code>
   */
  public String getUrl();
}
