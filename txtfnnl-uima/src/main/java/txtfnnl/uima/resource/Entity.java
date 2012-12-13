package txtfnnl.uima.resource;

/**
 * A comparable, hashable Entity object.
 * 
 * @author Florian Leitner
 */
public class Entity {
    final String type;
    final String namespace;
    final String id;
    // make entity objects thread-safe
    private volatile int hashCode;

    /**
     * Generate a new entity instance.
     * 
     * @param type the Entity type
     * @param namespace the Entity's namespace
     * @param id the Entity's identifier
     */
    public Entity(String type, String namespace, String id) {
        assert type != null;
        assert namespace != null;
        assert id != null;
        this.type = type;
        this.namespace = namespace;
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getIdentifier() {
        return id;
    }

    /**
     * A simple representation using "type(namespace:identifier)".
     */
    @Override
    public String toString() {
        return String.format("%s(%s:%s)", type, namespace, id);
    }

    /**
     * Entities with the same type, namespace and identifier are equal.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        else if (!(o instanceof Entity)) return false;
        final Entity other = (Entity) o;
        return type.equals(other.getType()) && namespace.equals(other.getNamespace()) &&
            id.equals(other.getIdentifier());
    }

    @Override
    public int hashCode() {
        int code = hashCode;
        if (code == 0) {
            code = 17 + type.hashCode();
            code *= 31 + namespace.hashCode();
            code *= 31 + id.hashCode();
            hashCode = code;
        }
        return code;
    }
}
