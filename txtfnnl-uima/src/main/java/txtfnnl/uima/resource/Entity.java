/**
 * 
 */
package txtfnnl.uima.resource;


/**
 * Entity objects produced by the EntityStringMapResource.
 *
 * @author Florian Leitner
 */
public class Entity {

	final String type;
	final String namespace;
	final String identifier;

	Entity(String type, String namespace, String id) {
		this.type = type;
		this.namespace = namespace;
		this.identifier = id;
	}
	
	public String getType() {
		return type;
	}
	
	public String getNamespace() {
		return namespace;
	}
	
	public String getIdentifier() {
		return identifier;
	}
	
	@Override
	public String toString() {
		return type + "(" + namespace + ":" + identifier + ")";
	}
	
}
