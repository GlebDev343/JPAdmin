package by.glebka.jpadmin.scanner;

import java.util.Map;

/**
 * Represents metamodel information about a JPA entity, including its type, identity, and attribute details.
 */
public class MetamodelInfo {
    private final String entityName;
    private final Class<?> javaType;
    private final Class<?> idType;
    private final boolean hasSingleId;
    private final boolean hasVersion;
    private final Map<String, AttributeInfo> attributes;
    private final String metamodelName;

    /**
     * Constructs a new MetamodelInfo instance with the specified entity metadata.
     *
     * @param entityName The name of the entity, typically derived from the @Entity annotation or class name.
     * @param javaType The Java class representing the entity.
     * @param idType The Java class of the entity's identifier, or null if no ID is defined.
     * @param hasSingleId Indicates whether the entity has a single ID field (true) or a composite ID (false).
     * @param hasVersion Indicates whether the entity has a version field for optimistic locking.
     * @param attributes A map of attribute names to their metadata.
     * @param metamodelName The name of the metamodel class, typically used for JPA metamodel generation.
     */
    public MetamodelInfo(String entityName, Class<?> javaType, Class<?> idType, boolean hasSingleId,
                         boolean hasVersion, Map<String, AttributeInfo> attributes, String metamodelName) {
        this.entityName = entityName;
        this.javaType = javaType;
        this.idType = idType;
        this.hasSingleId = hasSingleId;
        this.hasVersion = hasVersion;
        this.attributes = attributes;
        this.metamodelName = metamodelName;
    }

    /**
     * Returns the name of the entity.
     *
     * @return The entity name, or null if not explicitly defined.
     */
    public String getEntityName() { return entityName; }

    /**
     * Returns the Java class of the entity.
     *
     * @return The entity class.
     */
    public Class<?> getJavaType() { return javaType; }

    /**
     * Returns the Java class of the entity's identifier.
     *
     * @return The ID type, or null if no ID is defined.
     */
    public Class<?> getIdType() { return idType; }

    /**
     * Indicates whether the entity has a single ID field.
     *
     * @return True if the entity has a single ID, false if it uses a composite ID or no ID.
     */
    public boolean isHasSingleId() { return hasSingleId; }

    /**
     * Indicates whether the entity has a version field for optimistic locking.
     *
     * @return True if a version field exists, false otherwise.
     */
    public boolean isHasVersion() { return hasVersion; }

    /**
     * Returns a map of attribute names to their metadata.
     *
     * @return A map containing attribute information.
     */
    public Map<String, AttributeInfo> getAttributes() { return attributes; }

    /**
     * Returns the name of the metamodel class.
     *
     * @return The metamodel class name.
     */
    public String getMetamodelName() { return metamodelName; }
}