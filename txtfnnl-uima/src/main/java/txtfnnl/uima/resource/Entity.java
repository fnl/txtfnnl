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

	private volatile int hashCode;

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

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (!(o instanceof Entity))
			return false;

		Entity other = (Entity) o;

		return type.equals(other.getType()) &&
		       namespace.equals(other.getNamespace()) &&
		       identifier.equals(other.getIdentifier());
	}

	@Override
	public int hashCode() {
		int code = hashCode;

		if (code == 0) {
			code = 17 + type.hashCode();
			code *= 31 + namespace.hashCode();
			code *= 31 + identifier.hashCode();
			hashCode = code;
		}
		return code;
	}

}
