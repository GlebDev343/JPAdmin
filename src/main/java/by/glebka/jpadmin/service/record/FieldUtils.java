package by.glebka.jpadmin.service.record;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for collecting and analyzing field metadata from JPA entities.
 */
@Component
public class FieldUtils {

    private static final Logger logger = LoggerFactory.getLogger(FieldUtils.class);

    /**
     * Collects field metadata for the given entity class, including types, relationships, and constraints.
     *
     * @param clazz               The entity class to analyze.
     * @param fields              Set to store field names in declaration order.
     * @param isCollectionField   Map to indicate if a field is a collection.
     * @param embeddedFieldPaths  Map of embedded field paths (e.g., "address.street").
     * @param fieldTypes          Map of field names to their Java types.
     * @param nullableFields      Map of field names to their nullable status.
     * @param foreignKeyFields    Map of foreign key fields to their target table names.
     * @param foreignKeyColumnNames Map of foreign key fields to their column names.
     * @param oneToManyFields     Map of OneToMany fields to their target table names.
     * @param manyToManyFields    Map of ManyToMany fields to their target table names.
     */
    public <T> void collectFieldTypes(Class<T> clazz, Set<String> fields, Map<String, Boolean> isCollectionField,
                                      Map<String, String> embeddedFieldPaths, Map<String, String> fieldTypes,
                                      Map<String, Boolean> nullableFields,
                                      Map<String, String> foreignKeyFields, Map<String, String> foreignKeyColumnNames,
                                      Map<String, String> oneToManyFields, Map<String, String> manyToManyFields) {
        try {
            Field idField = getFieldFromHierarchy(clazz, "id");
            if (idField != null && (idField.isAnnotationPresent(Id.class) || idField.isAnnotationPresent(Column.class))) {
                fields.add("id"); // Ensure ID is always first
                isCollectionField.put("id", false);
                String typeName = normalizeTypeName(idField.getType().getSimpleName());
                fieldTypes.put("id", typeName);
                Column column = idField.getAnnotation(Column.class);
                nullableFields.put("id", column != null && column.nullable());
            }
        } catch (NoSuchFieldException e) {
            logger.debug("No id field found in class {}", clazz.getSimpleName());
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Embedded.class)) {
                collectEmbeddedFieldTypes(field.getType(), field.getName() + ".", fields, embeddedFieldPaths, fieldTypes, nullableFields, foreignKeyFields, foreignKeyColumnNames, oneToManyFields, manyToManyFields);
            } else if (field.isAnnotationPresent(OneToMany.class)) {
                processOneToManyField(field, fields, isCollectionField, fieldTypes, oneToManyFields);
            } else if (field.isAnnotationPresent(ManyToMany.class)) {
                processManyToManyField(field, fields, isCollectionField, fieldTypes, manyToManyFields);
            } else if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
                processToOneField(field, fields, isCollectionField, fieldTypes, nullableFields, foreignKeyFields, foreignKeyColumnNames);
            } else if (field.isAnnotationPresent(Column.class) && !field.getName().equals("id")) {
                processBasicField(field, fields, isCollectionField, fieldTypes, nullableFields);
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            collectFieldTypes(superclass, fields, isCollectionField, embeddedFieldPaths, fieldTypes, nullableFields, foreignKeyFields, foreignKeyColumnNames, oneToManyFields, manyToManyFields);
        }
    }

    /**
     * Retrieves a field from the class hierarchy, including superclasses.
     *
     * @param clazz     The class to start searching from.
     * @param fieldName The name of the field to find.
     * @return The Field object if found, otherwise throws NoSuchFieldException.
     */
    public Field getFieldFromHierarchy(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && !superclass.equals(Object.class)) {
                return getFieldFromHierarchy(superclass, fieldName);
            }
            throw e;
        }
    }

    /**
     * Collects field types for embedded entities.
     */
    public void collectEmbeddedFieldTypes(Class<?> embeddedClass, String prefix, Set<String> fields,
                                          Map<String, String> embeddedFieldPaths, Map<String, String> fieldTypes,
                                          Map<String, Boolean> nullableFields,
                                          Map<String, String> foreignKeyFields, Map<String, String> foreignKeyColumnNames,
                                          Map<String, String> oneToManyFields, Map<String, String> manyToManyFields) {
        for (Field embeddedField : embeddedClass.getDeclaredFields()) {
            if (embeddedField.isAnnotationPresent(Column.class)) {
                String fieldName = embeddedField.getName();
                fields.add(fieldName);
                embeddedFieldPaths.put(fieldName, prefix + fieldName);
                String typeName = normalizeTypeName(embeddedField.getType().getSimpleName());
                fieldTypes.put(fieldName, typeName);
                Column column = embeddedField.getAnnotation(Column.class);
                nullableFields.put(fieldName, column.nullable());
            }
        }
    }

    private void processOneToManyField(Field field, Set<String> fields, Map<String, Boolean> isCollectionField,
                                       Map<String, String> fieldTypes, Map<String, String> oneToManyFields) {
        fields.add(field.getName());
        isCollectionField.put(field.getName(), true);
        fieldTypes.put(field.getName(), "Collection");
        Class<?> targetEntity = (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
        String targetTableName = getTableName(targetEntity);
        oneToManyFields.put(field.getName(), targetTableName);
        logger.debug("Added OneToMany field {} with target table {}", field.getName(), targetTableName);
    }

    private void processManyToManyField(Field field, Set<String> fields, Map<String, Boolean> isCollectionField,
                                        Map<String, String> fieldTypes, Map<String, String> manyToManyFields) {
        fields.add(field.getName());
        isCollectionField.put(field.getName(), true);
        fieldTypes.put(field.getName(), "Collection");
        Class<?> targetEntity = field.getType().isArray() ? field.getType().getComponentType()
                : (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
        String targetTableName = getTableName(targetEntity);
        manyToManyFields.put(field.getName(), targetTableName);
        logger.debug("Added ManyToMany field {} with target table {}", field.getName(), targetTableName);
    }

    private void processToOneField(Field field, Set<String> fields, Map<String, Boolean> isCollectionField,
                                   Map<String, String> fieldTypes, Map<String, Boolean> nullableFields,
                                   Map<String, String> foreignKeyFields, Map<String, String> foreignKeyColumnNames) {
        fields.add(field.getName());
        isCollectionField.put(field.getName(), false);
        fieldTypes.put(field.getName(), field.getType().getSimpleName());
        Class<?> targetEntity = field.getType();
        String targetTableName = getTableName(targetEntity);
        foreignKeyFields.put(field.getName(), targetTableName);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        String columnName = joinColumn != null ? joinColumn.name() : field.getName() + "_id";
        foreignKeyColumnNames.put(field.getName(), columnName);
        nullableFields.put(field.getName(), joinColumn != null && joinColumn.nullable());
        logger.debug("Added ManyToOne/OneToOne field {} with target table {} and column {}", field.getName(), targetTableName, columnName);
    }

    private void processBasicField(Field field, Set<String> fields, Map<String, Boolean> isCollectionField,
                                   Map<String, String> fieldTypes, Map<String, Boolean> nullableFields) {
        fields.add(field.getName());
        isCollectionField.put(field.getName(), false);
        String typeName = normalizeTypeName(field.getType().getSimpleName());
        fieldTypes.put(field.getName(), typeName);
        Column column = field.getAnnotation(Column.class);
        nullableFields.put(field.getName(), column.nullable());
    }

    private String normalizeTypeName(String typeName) {
        return "boolean".equals(typeName) ? "Boolean" : typeName;
    }

    private String getTableName(Class<?> entityClass) {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        return tableAnnotation != null ? tableAnnotation.name() : entityClass.getSimpleName().toLowerCase();
    }
}