package by.glebka.jpadmin.scanner;

import jakarta.persistence.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Component responsible for analyzing JPA metamodels and collecting entity metadata.
 */
@Component
public class MetamodelAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(MetamodelAnalyzer.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AnnotationCollector annotationCollector;

    /**
     * Analyzes the given class and constructs its entity metadata.
     *
     * @param clazz The class to analyze (must be a JPA entity or related type).
     * @return An EntityInfo object containing metadata about the class.
     */
    public EntityInfo analyze(Class<?> clazz) {
        Map<String, Annotation> classAnnotations = annotationCollector.collectClassAnnotations(clazz);
        Map<String, Map<String, Annotation>> fieldAnnotations = annotationCollector.collectFieldAnnotations(clazz);
        MetamodelInfo metamodelInfo;
        if (clazz.isAnnotationPresent(Entity.class)) {
            EntityType<?> entityType = entityManager.getMetamodel().entity(clazz);
            metamodelInfo = analyzeEntity(entityType);
        } else {
            metamodelInfo = analyzeNonEntity(clazz);
        }
        EntityInfo entityInfo = new EntityInfo(clazz.getName(), classAnnotations, fieldAnnotations, metamodelInfo);
        logger.info("Analysis finished for class: {}", clazz.getSimpleName());
        return entityInfo;
    }

    private MetamodelInfo analyzeEntity(EntityType<?> entityType) {
        Map<String, AttributeInfo> attributes = new HashMap<>();
        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            String databaseType = getDatabaseType(attribute);
            if (attribute instanceof SingularAttribute) {
                SingularAttribute<?, ?> singularAttr = (SingularAttribute<?, ?>) attribute;
                attributes.put(attribute.getName(), new SingularAttributeInfo(
                        attribute.getName(), attribute.getJavaType(), attribute.getPersistentAttributeType(),
                        singularAttr.isId(), singularAttr.isVersion(), attribute.isAssociation(),
                        attribute.getJavaMember().getName(), singularAttr.isOptional(),
                        singularAttr.getBindableType(), singularAttr.getBindableJavaType(),
                        databaseType
                ));
            } else if (attribute instanceof PluralAttribute) {
                PluralAttribute<?, ?, ?> pluralAttr = (PluralAttribute<?, ?, ?>) attribute;
                attributes.put(attribute.getName(), new PluralAttributeInfo(
                        attribute.getName(), attribute.getJavaType(), attribute.getPersistentAttributeType(),
                        attribute.isAssociation(), attribute.getJavaMember().getName(),
                        pluralAttr.getCollectionType(), pluralAttr.getElementType(),
                        databaseType
                ));
            }
        }
        return new MetamodelInfo(
                entityType.getName(), entityType.getJavaType(),
                entityType.getIdType() != null ? entityType.getIdType().getJavaType() : null,
                entityType.hasSingleIdAttribute(), entityType.hasVersionAttribute(),
                attributes, entityType.getJavaType().getSimpleName() + "_"
        );
    }

    private MetamodelInfo analyzeNonEntity(Class<?> clazz) {
        Map<String, AttributeInfo> attributes = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            Attribute.PersistentAttributeType persistentType = determinePersistentType(field);
            if (persistentType != null) {
                String databaseType = getDatabaseTypeFromField(field);
                attributes.put(field.getName(), new SingularAttributeInfo(
                        field.getName(), field.getType(), persistentType,
                        field.isAnnotationPresent(Id.class),
                        field.isAnnotationPresent(Version.class),
                        field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class),
                        field.getName(),
                        !field.isAnnotationPresent(Column.class) || field.getAnnotation(Column.class).nullable(),
                        null, field.getType(),
                        databaseType
                ));
            }
        }
        return new MetamodelInfo(clazz.getSimpleName(), clazz, null, false, false, attributes, "n/a");
    }

    private Attribute.PersistentAttributeType determinePersistentType(Field field) {
        if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(Column.class) ||
                field.isAnnotationPresent(Basic.class)) {
            return Attribute.PersistentAttributeType.BASIC;
        } else if (field.isAnnotationPresent(ManyToOne.class)) {
            return Attribute.PersistentAttributeType.MANY_TO_ONE;
        } else if (field.isAnnotationPresent(OneToOne.class)) {
            return Attribute.PersistentAttributeType.ONE_TO_ONE;
        } else if (field.isAnnotationPresent(OneToMany.class)) {
            return Attribute.PersistentAttributeType.ONE_TO_MANY;
        } else if (field.isAnnotationPresent(ManyToMany.class)) {
            return Attribute.PersistentAttributeType.MANY_TO_MANY;
        } else if (field.isAnnotationPresent(ElementCollection.class)) {
            return Attribute.PersistentAttributeType.ELEMENT_COLLECTION;
        } else if (field.isAnnotationPresent(Embedded.class)) {
            return Attribute.PersistentAttributeType.EMBEDDED;
        }
        return null;
    }

    /**
     * Retrieves the database type for the given attribute.
     *
     * @param attribute The attribute to analyze.
     * @return The database type (e.g., "VARCHAR", "BIT") or null if not explicitly defined.
     */
    private String getDatabaseType(Attribute<?, ?> attribute) {
        if (attribute.getJavaMember() instanceof Field field) {
            return getDatabaseTypeFromField(field);
        }
        return null;
    }

    /**
     * Retrieves the database type for a field based on its annotations.
     *
     * @param field The field to analyze.
     * @return The database type (e.g., "VARCHAR", "BIT") or null if not explicitly defined.
     */
    private String getDatabaseTypeFromField(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.columnDefinition().isEmpty()) {
            String columnDef = column.columnDefinition().toUpperCase();
            return columnDef.contains("(") ? columnDef.substring(0, columnDef.indexOf("(")) : columnDef;
        }
        // Fallback: если columnDefinition не указан, возвращаем null
        logger.debug("No explicit database type defined for field: {}", field.getName());
        return null;
    }

    /**
     * Retrieves fields suitable for filtering based on relationships.
     *
     * @param clazz The class to analyze.
     * @return A map of field names to their corresponding column names.
     */
    public Map<String, String> getFilterFields(Class<?> clazz) {
        Map<String, String> filterFields = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToMany.class)) {
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                Class<?> targetEntity = (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                String targetFieldName = targetEntity.getSimpleName().toLowerCase() + "_id";
                if (joinColumn != null) {
                    filterFields.put(field.getName(), joinColumn.name());
                } else {
                    filterFields.put(field.getName(), targetFieldName);
                }
            } else if (field.isAnnotationPresent(ManyToMany.class)) {
                JoinTable joinTable = field.getAnnotation(JoinTable.class);
                if (joinTable != null && joinTable.joinColumns().length > 0) {
                    filterFields.put(field.getName(), joinTable.joinColumns()[0].name());
                } else {
                    ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
                    String mappedBy = manyToMany.mappedBy();
                    if (!mappedBy.isEmpty()) {
                        try {
                            Class<?> targetEntity = (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                            Field ownerField = targetEntity.getDeclaredField(mappedBy);
                            JoinTable ownerJoinTable = ownerField.getAnnotation(JoinTable.class);
                            if (ownerJoinTable != null && ownerJoinTable.inverseJoinColumns().length > 0) {
                                filterFields.put(field.getName(), ownerJoinTable.inverseJoinColumns()[0].name());
                            } else {
                                filterFields.put(field.getName(), targetEntity.getSimpleName().toLowerCase() + "_id");
                            }
                        } catch (NoSuchFieldException e) {
                            filterFields.put(field.getName(), clazz.getSimpleName().toLowerCase() + "_id");
                        }
                    }
                }
            } else if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (joinColumn != null) {
                    filterFields.put(field.getName(), joinColumn.name());
                } else {
                    filterFields.put(field.getName(), field.getName() + "_id");
                }
            }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            filterFields.putAll(getFilterFields(superclass));
        }
        return filterFields;
    }

    /**
     * Retrieves metadata about ManyToMany relationships.
     *
     * @param clazz The class to analyze.
     * @return A map of field names to ManyToMany relationship details.
     */
    public Map<String, ManyToManyInfo> getManyToManyInfo(Class<?> clazz) {
        Map<String, ManyToManyInfo> manyToManyInfo = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ManyToMany.class)) {
                JoinTable joinTable = field.getAnnotation(JoinTable.class);
                Class<?> targetEntity = (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                String targetTableName = targetEntity.getAnnotation(Table.class) != null ? targetEntity.getAnnotation(Table.class).name() : targetEntity.getSimpleName().toLowerCase();

                if (joinTable != null) {
                    String joinTableName = joinTable.name();
                    String joinColumn = joinTable.joinColumns().length > 0 ? joinTable.joinColumns()[0].name() : "unknown";
                    String inverseJoinColumn = joinTable.inverseJoinColumns().length > 0 ? joinTable.inverseJoinColumns()[0].name() : "unknown";
                    manyToManyInfo.put(field.getName(), new ManyToManyInfo(joinTableName, joinColumn, inverseJoinColumn, targetTableName));
                } else {
                    ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
                    String mappedBy = manyToMany.mappedBy();
                    if (!mappedBy.isEmpty()) {
                        try {
                            Class<?> ownerClass = targetEntity;
                            Field ownerField = ownerClass.getDeclaredField(mappedBy);
                            JoinTable ownerJoinTable = ownerField.getAnnotation(JoinTable.class);
                            if (ownerJoinTable != null) {
                                String joinTableName = ownerJoinTable.name();
                                String joinColumn = ownerJoinTable.joinColumns().length > 0 ? ownerJoinTable.joinColumns()[0].name() : "unknown";
                                String inverseJoinColumn = ownerJoinTable.inverseJoinColumns().length > 0 ? ownerJoinTable.inverseJoinColumns()[0].name() : "unknown";
                                manyToManyInfo.put(field.getName(), new ManyToManyInfo(joinTableName, joinColumn, inverseJoinColumn, targetTableName));
                            }
                        } catch (NoSuchFieldException e) {
                            logger.warn("Failed to find mappedBy field {} in class {}", mappedBy, targetEntity.getSimpleName());
                        }
                    }
                }
            }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            manyToManyInfo.putAll(getManyToManyInfo(superclass));
        }
        return manyToManyInfo;
    }

    public static class ManyToManyInfo {
        private final String joinTableName;
        private final String joinColumn;
        private final String inverseJoinColumn;
        private final String targetTableName;

        public ManyToManyInfo(String joinTableName, String joinColumn, String inverseJoinColumn, String targetTableName) {
            this.joinTableName = joinTableName;
            this.joinColumn = joinColumn;
            this.inverseJoinColumn = inverseJoinColumn;
            this.targetTableName = targetTableName;
        }

        public String getJoinTableName() { return joinTableName; }
        public String getJoinColumn() { return joinColumn; }
        public String getInverseJoinColumn() { return inverseJoinColumn; }
        public String getTargetTableName() { return targetTableName; }
    }
}