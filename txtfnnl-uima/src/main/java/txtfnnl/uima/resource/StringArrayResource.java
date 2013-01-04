package txtfnnl.uima.resource;

import java.util.Iterator;

import org.apache.uima.resource.SharedResourceObject;

/**
 * A resource interface for loading row-based String arrays.
 * 
 * @author Florian Leitner
 */
public interface StringArrayResource extends SharedResourceObject, Iterable<String[]> {
  /**
   * Return the String values in row.
   * 
   * @param key to fetch the value V for
   * @return the value V mapped to the key
   */
  public String[] get(int row);

  /**
   * Return the number of rows.
   * 
   * @return the number of rows loaded from the file.
   */
  public int size();

  /**
   * Fetch an iterator over the rows.
   * 
   * @return a row iterator
   */
  public Iterator<String[]> iterator();

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
  public String getResourceUrl();
}
