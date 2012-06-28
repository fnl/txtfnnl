package txtfnnl.uima.resource;

/**
 * A resource specification that uses String keys to return arbitrary values.
 *
 * @author Florian Leitner
 */
public interface StringMapResource<V> {

	public V get(String key);
}
