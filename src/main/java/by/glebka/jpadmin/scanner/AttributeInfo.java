package by.glebka.jpadmin.scanner;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.Type;

/**
 * Interface representing metadata about an entity attribute.
 */
public interface AttributeInfo {
    String getName();
    Class<?> getJavaType();
    String getJavaTypeSimpleName();
    Attribute.PersistentAttributeType getPersistentType();
    boolean isAssociation();
    String getJavaMember();
    String getDatabaseType(); // Returns the database column type (e.g., "VARCHAR", "BIT")
}

/**
 * Represents metadata for a singular attribute of an entity (e.g., a simple field or a relationship).
 */
class SingularAttributeInfo implements AttributeInfo {
    private final String name;
    private final Class<?> javaType;
    private final Attribute.PersistentAttributeType persistentType;
    private final boolean isId;
    private final boolean isVersion;
    private final boolean isAssociation;
    private final String javaMember;
    private final boolean isOptional;
    private final Bindable.BindableType bindableType;
    private final Class<?> bindableJavaType;
    private final String databaseType;

    public SingularAttributeInfo(String name, Class<?> javaType, Attribute.PersistentAttributeType persistentType,
                                 boolean isId, boolean isVersion, boolean isAssociation, String javaMember,
                                 boolean isOptional, Bindable.BindableType bindableType, Class<?> bindableJavaType,
                                 String databaseType) {
        this.name = name;
        this.javaType = javaType;
        this.persistentType = persistentType;
        this.isId = isId;
        this.isVersion = isVersion;
        this.isAssociation = isAssociation;
        this.javaMember = javaMember;
        this.isOptional = isOptional;
        this.bindableType = bindableType;
        this.bindableJavaType = bindableJavaType;
        this.databaseType = databaseType;
    }

    @Override public String getName() { return name; }
    @Override public Class<?> getJavaType() { return javaType; }
    @Override public String getJavaTypeSimpleName() { return javaType.getSimpleName(); }
    @Override public Attribute.PersistentAttributeType getPersistentType() { return persistentType; }
    @Override public boolean isAssociation() { return isAssociation; }
    @Override public String getJavaMember() { return javaMember; }
    @Override public String getDatabaseType() { return databaseType; }
    public boolean isId() { return isId; }
    public boolean isVersion() { return isVersion; }
    public boolean isOptional() { return isOptional; }
    public Bindable.BindableType getBindableType() { return bindableType; }
    public Class<?> getBindableJavaType() { return bindableJavaType; }
}

/**
 * Represents metadata for a plural attribute of an entity (e.g., a collection).
 */
class PluralAttributeInfo implements AttributeInfo {
    private final String name;
    private final Class<?> javaType;
    private final Attribute.PersistentAttributeType persistentType;
    private final boolean isAssociation;
    private final String javaMember;
    private final Object collectionType;
    private final Type<?> elementType;
    private final String databaseType;

    public PluralAttributeInfo(String name, Class<?> javaType, Attribute.PersistentAttributeType persistentType,
                               boolean isAssociation, String javaMember, Object collectionType, Type<?> elementType,
                               String databaseType) {
        this.name = name;
        this.javaType = javaType;
        this.persistentType = persistentType;
        this.isAssociation = isAssociation;
        this.javaMember = javaMember;
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.databaseType = databaseType;
    }

    @Override public String getName() { return name; }
    @Override public Class<?> getJavaType() { return javaType; }
    @Override public String getJavaTypeSimpleName() { return javaType.getSimpleName(); }
    @Override public Attribute.PersistentAttributeType getPersistentType() { return persistentType; }
    @Override public boolean isAssociation() { return isAssociation; }
    @Override public String getJavaMember() { return javaMember; }
    @Override public String getDatabaseType() { return databaseType; }
    public Object getCollectionType() { return collectionType; }
    public Type<?> getElementType() { return elementType; }
}