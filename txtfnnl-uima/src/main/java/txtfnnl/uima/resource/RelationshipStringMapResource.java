package txtfnnl.uima.resource;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.ExternalResourceFactory;

/**
 * A RelationshipStringMapResource provides Sets of {@link Entity Entities} mapped to a unique key
 * (String). A relationship string map should be a line-oriented data stream containing <i>at
 * least</i> the following elements:
 * <ol>
 * <li>unique string ("key")</li>
 * <li>Entity type string</li>
 * <li>Entity namespace string</li>
 * <li>Entity identifier string</li>
 * </ol>
 * The last three items (the Entity) may be repeated as often as necessary on a single line to
 * create relationship sets. If there is more than one relationship set associated to the unique
 * key (string), multiple lines may be used, each starting with the same key. In the case of a
 * file, make sure that the data resource URL is prefixed with the "file:" schema prefix.
 * 
 * @author Florian Leitner
 */
public class RelationshipStringMapResource extends LineBasedStringMapResource<List<Set<Entity>>> {
    public static ExternalResourceDescription
            configure(String resourceUrl, final String separator) throws IOException {
        if (separator == null || "".equals(separator)) return ExternalResourceFactory
            .createExternalResourceDescription(RelationshipStringMapResource.class, resourceUrl);
        else return ExternalResourceFactory.createExternalResourceDescription(
            RelationshipStringMapResource.class, resourceUrl, PARAM_SEPARATOR, separator);
    }

    public static ExternalResourceDescription configure(String resourceUrl) throws IOException {
        return RelationshipStringMapResource.configure(resourceUrl, null);
    }

    @Override
    void parse(String[] items) throws ResourceInitializationException {
        if ((items.length - 1) % 3 != 0) {
            new ResourceInitializationException(new RuntimeException("illegal line: '" + line +
                "' with " + items.length + " fields"));
        }
        final int numEntities = (items.length - 1) / 3;
        final Set<Entity> entities = new HashSet<Entity>();
        for (int idx = 0; idx < numEntities; ++idx) {
            entities.add(new Entity(items[3 * idx + 1], items[3 * idx + 2], items[3 * idx + 3]));
        }
        if (!resourceMap.containsKey(items[0])) {
            resourceMap.put(items[0], new LinkedList<Set<Entity>>());
        }
        resourceMap.get(items[0]).add(entities);
    }
}
