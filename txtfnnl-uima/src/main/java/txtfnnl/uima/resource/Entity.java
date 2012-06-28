/**
 * 
 */
package txtfnnl.uima.resource;


/**
 *
 *
 * @author Florian Leitner
 */
public class Entity {

	private String type;
	private String namespace;
	private String identifier;

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
	
}
