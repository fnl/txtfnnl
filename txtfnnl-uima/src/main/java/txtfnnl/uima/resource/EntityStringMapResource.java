package txtfnnl.uima.resource;

import java.util.HashSet;
import java.util.Set;

import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.ExternalResourceFactory;

/**
 * A resource providing a Set of {@link Entity Entities} mapped to a unique key (a String). An
 * Entity String Map should be a line-oriented data stream containing the following elements:
 * <ol>
 * <li>a unique key (String)</li>
 * <li>Entity type (String)</li>
 * <li>Entity namespace (String)</li>
 * <li>Entity identifier (String)</li>
 * </ol>
 * If more than one entity are to be associated with a unique key, multiple lines may be used, each
 * starting with the same key. Any additional fields after the identifier will be ignored.
 * 
 * @author Florian Leitner
 */
public class EntityStringMapResource extends LineBasedStringMapResource<Set<Entity>> {
    /**
     * Configure a new Entity String Map resource. In the case of a file, make sure that the (data)
     * resource URL is prefixed with the "file:" schema prefix.
     * 
     * @param resourceUrl the URL where this resource is located
     * @param separator the string used to separate the fields/elements (if <code>null</code>, use
     *        default - see {@link #PARAM_SEPARATOR} )
     */
    public static ExternalResourceDescription configure(String resourceUrl, String separator) {
        if (separator == null || "".equals(separator)) return ExternalResourceFactory
            .createExternalResourceDescription(EntityStringMapResource.class, resourceUrl);
        else return ExternalResourceFactory.createExternalResourceDescription(
            EntityStringMapResource.class, resourceUrl, PARAM_SEPARATOR, separator);
    }

    /**
     * Configure a new Entity String Map resource using the default separator.
     * 
     * @param resourceUrl the URL where this resource is located
     * @return a configured resource description
     */
    public static ExternalResourceDescription configure(String resourceUrl) {
        return EntityStringMapResource.configure(resourceUrl, null);
    }

    /** Place the separated String items on the input stream into a Map. */
    @Override
    void parse(String[] items) throws ResourceInitializationException {
        Entity entity;
        try {
            entity = new Entity(items[1], items[2], items[3]);
            if (!resourceMap.containsKey(items[0])) {
                resourceMap.put(items[0], new HashSet<Entity>());
            }
        } catch (final IndexOutOfBoundsException e) {
            throw new ResourceInitializationException(new RuntimeException("illegal line: '" +
                line + "' with " + items.length + " fields"));
        }
        resourceMap.get(items[0]).add(entity);
    }
}
