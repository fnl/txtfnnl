package txtfnnl.uima.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.utils.UIMAUtils;

/**
 * A RelationshipStringMapResource provides Sets of Entities mapped to a
 * unique key (String).
 * 
 * A relationship string map should be a line-oriented data stream containing
 * the following elements, separated by tabs:
 * <ol>
 * <li>unique string ("key")</li>
 * <li>Entity type string</li>
 * <li>Entity namespace string</li>
 * <li>Entity identifier string</li>
 * </ol>
 * The last three items (the entity) may be repeated as often as necessary on
 * a single line to create sets of relationships. Therefore, a line should
 * always have <code>1 + 3n</code> items. If there is more than one
 * relationship associated to a key string, multiple lines may be used, each
 * starting with the same key.
 * 
 * In the case of a TSV file, make sure that the data resource URL is prefixed
 * with the "file:" schema prefix.
 * 
 * @author Florian Leitner
 */
public class RelationshipStringMapResource extends LineBasedStringMapResource<List<Set<Entity>>> {

	@SuppressWarnings("serial")
	public static ExternalResourceDescription configure(String connectionUrl,
	                                                    final String separator) throws IOException {
		if (separator == null || "".equals(separator))
			return ExternalResourceFactory.createExternalResourceDescription(
			    RelationshipStringMapResource.class, connectionUrl);
		else
			return ExternalResourceFactory.createExternalResourceDescription(
			    RelationshipStringMapResource.class, connectionUrl,
			    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

				    {
					    put(PARAM_SEPARATOR, separator);
				    }
			    }));
	}

	public static ExternalResourceDescription configure(String connectionUrl) throws IOException {
		return configure(connectionUrl, null);
	}

	@Override
	void parse(String[] items) throws ResourceInitializationException {
		if ((items.length - 1) % 3 != 0)
			new ResourceInitializationException(new RuntimeException("illegal line: '" + line +
			                                                         "' with " + items.length +
			                                                         " fields"));

		int numEntities = (items.length - 1) / 3;
		Set<Entity> entities = new HashSet<Entity>();

		for (int idx = 0; idx < numEntities; ++idx)
			entities.add(new Entity(items[3 * idx + 1], items[3 * idx + 2], items[3 * idx + 3]));

		if (!resourceMap.containsKey(items[0]))
			resourceMap.put(items[0], new LinkedList<Set<Entity>>());

		resourceMap.get(items[0]).add(entities);

	}
}
