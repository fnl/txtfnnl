package txtfnnl.uima.resource;

import java.util.HashSet;
import java.util.Set;

import org.apache.uima.resource.ResourceInitializationException;

/**
 * An EntityStringMapResource provides one Set of Entities mapped to a unique
 * key (String).
 * 
 * An entity string map should be a line-oriented data stream containing the
 * following elements, separated by tabs:
 * <ol>
 * <li>unique string ("key")</li>
 * <li>Entity type string</li>
 * <li>Entity namespace string</li>
 * <li>Entity identifier string</li>
 * </ol>
 * If there is more than one entity associated to a key string, multiple lines
 * may be used, each starting with the same key.
 * 
 * In the case of a TSV file, make sure that the data resource URL is prefixed
 * with the "file:" schema prefix.
 * 
 * @author Florian Leitner
 */
public class EntityStringMapResource extends
        LineBasedStringMapResource<Set<Entity>> {

	@Override
	void parse(String[] items) throws ResourceInitializationException {
		Entity entity;

		try {
			entity = new Entity(items[1], items[2], items[3]);

			if (!resourceMap.containsKey(items[0]))
				resourceMap.put(items[0], new HashSet<Entity>());
		} catch (IndexOutOfBoundsException e) {
			throw new ResourceInitializationException(new RuntimeException(
			    "illegal line: '" + line + "' with " + items.length +
			            " fields"));
		}
		resourceMap.get(items[0]).add(entity);
	}
}
