package by.glebka.jpadmin.service.record;

import by.glebka.jpadmin.scanner.EntityInfo;
import by.glebka.jpadmin.service.EntityTableService;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Component responsible for validating entity records before persistence.
 */
@Component
public class RecordValidator {

    private static final Logger logger = LoggerFactory.getLogger(RecordValidator.class);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityTableService entityTableService;

    @Autowired
    private FieldUtils fieldUtils;

    @Autowired
    private FieldValueSetter fieldValueSetter;

    @Autowired
    private Validator validator;

    /**
     * Validates an existing record for updates.
     *
     * @param tableName    The name of the table.
     * @param id           The ID of the record to validate.
     * @param editedFields The fields with updated values.
     * @return A map of field names to validation error messages, empty if valid.
     */
    @Transactional
    public Map<String, String> validateRecord(String tableName, Long id, Map<String, String> editedFields) {
        Map<String, String> errors = new HashMap<>();
        EntityInfo entityInfo = findEntityInfo(tableName, errors);
        if (entityInfo == null) return errors;

        Class<?> entityClass = loadEntityClass(entityInfo, errors);
        if (entityClass == null) return errors;

        Object entity = entityManager.find(entityClass, id);
        if (entity == null) {
            logger.warn("Entity not found for table {} with id {}", tableName, id);
            errors.put("entity", "Entity not found for table " + tableName + " with id " + id);
            return errors;
        }

        Map<String, Object> fieldMetadata = collectFieldMetadata(entityClass);
        validateFields(entity, editedFields, fieldMetadata, errors);

        Set<ConstraintViolation<Object>> violations = validator.validate(entity);
        processViolations(violations, errors);

        return errors;
    }

    /**
     * Validates a new record before creation.
     *
     * @param tableName The name of the table.
     * @param newFields The fields with values for the new record.
     * @return A map of field names to validation error messages, empty if valid.
     */
    @Transactional
    public Map<String, String> validateNewRecord(String tableName, Map<String, String> newFields) {
        Map<String, String> errors = new HashMap<>();
        EntityInfo entityInfo = findEntityInfo(tableName, errors);
        if (entityInfo == null) return errors;

        Class<?> entityClass = loadEntityClass(entityInfo, errors);
        if (entityClass == null) return errors;

        Object entity = instantiateEntity(entityClass, tableName, errors);
        if (entity == null) return errors;

        Map<String, Object> fieldMetadata = collectFieldMetadata(entityClass);
        validateFields(entity, newFields, fieldMetadata, errors);

        Set<ConstraintViolation<Object>> violations = validator.validate(entity);
        processViolations(violations, errors);

        return errors;
    }

    private EntityInfo findEntityInfo(String tableName, Map<String, String> errors) {
        EntityInfo entityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> tableName.equals(e.getTableName()))
                .findFirst()
                .orElse(null);
        if (entityInfo == null) {
            logger.error("Table not found: {}", tableName);
            errors.put("table", "Table not found: " + tableName);
        }
        return entityInfo;
    }

    private Class<?> loadEntityClass(EntityInfo entityInfo, Map<String, String> errors) {
        try {
            return Class.forName(entityInfo.getClassName());
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load entity class: {}", e.getMessage());
            errors.put("entity", "Unable to load entity class: " + entityInfo.getClassName());
            return null;
        }
    }

    private Object instantiateEntity(Class<?> entityClass, String tableName, Map<String, String> errors) {
        try {
            Object entity = entityClass.getDeclaredConstructor().newInstance();
            logger.debug("Entity instantiated for validation in table {}", tableName);
            return entity;
        } catch (Exception e) {
            logger.error("Failed to instantiate entity for table {}: {}", tableName, e.getMessage());
            errors.put("entity", "Failed to create entity for table " + tableName);
            return null;
        }
    }

    private Map<String, Object> collectFieldMetadata(Class<?> entityClass) {
        Set<String> displayFields = new HashSet<>();
        Map<String, Boolean> isCollectionField = new HashMap<>();
        Map<String, String> embeddedFieldPaths = new HashMap<>();
        Map<String, String> fieldTypes = new HashMap<>();
        Map<String, Boolean> nullableFields = new HashMap<>();
        Map<String, String> foreignKeyFields = new HashMap<>();
        Map<String, String> foreignKeyColumnNames = new HashMap<>();
        Map<String, String> oneToManyFields = new HashMap<>();
        Map<String, String> manyToManyFields = new HashMap<>();
        fieldUtils.collectFieldTypes(entityClass, displayFields, isCollectionField, embeddedFieldPaths, fieldTypes, nullableFields, foreignKeyFields, foreignKeyColumnNames, oneToManyFields, manyToManyFields);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("displayFields", displayFields);
        metadata.put("isCollectionField", isCollectionField);
        metadata.put("embeddedFieldPaths", embeddedFieldPaths);
        metadata.put("fieldTypes", fieldTypes);
        metadata.put("nullableFields", nullableFields);
        metadata.put("foreignKeyFields", foreignKeyFields);
        metadata.put("foreignKeyColumnNames", foreignKeyColumnNames);
        metadata.put("oneToManyFields", oneToManyFields);
        metadata.put("manyToManyFields", manyToManyFields);
        return metadata;
    }

    private void validateFields(Object entity, Map<String, String> fields, Map<String, Object> metadata, Map<String, String> errors) {
        @SuppressWarnings("unchecked")
        Map<String, String> embeddedFieldPaths = (Map<String, String>) metadata.get("embeddedFieldPaths");
        @SuppressWarnings("unchecked")
        Map<String, String> foreignKeyFields = (Map<String, String>) metadata.get("foreignKeyFields");
        @SuppressWarnings("unchecked")
        Map<String, String> oneToManyFields = (Map<String, String>) metadata.get("oneToManyFields");
        @SuppressWarnings("unchecked")
        Map<String, String> manyToManyFields = (Map<String, String>) metadata.get("manyToManyFields");

        for (String field : fields.keySet()) {
            if (foreignKeyFields.containsKey(field) || oneToManyFields.containsKey(field) || manyToManyFields.containsKey(field) || "id".equals(field)) {
                logger.debug("Skipping relationship or id field: {}", field);
                continue;
            }
            try {
                if (embeddedFieldPaths.containsKey(field)) {
                    validateEmbeddedField(entity, field, fields.get(field), embeddedFieldPaths.get(field));
                } else {
                    validateBasicField(entity, field, fields.get(field));
                }
            } catch (Exception e) {
                handleValidationError(field, e, errors);
            }
        }
    }

    private void validateEmbeddedField(Object entity, String field, String value, String path) throws Exception {
        String[] parts = path.split("\\.");
        Object currentObj = entity;
        for (int i = 0; i < parts.length - 1; i++) {
            Field subField = fieldUtils.getFieldFromHierarchy(currentObj.getClass(), parts[i]);
            subField.setAccessible(true);
            Object nextObj = subField.get(currentObj);
            if (nextObj == null) {
                nextObj = subField.getType().getDeclaredConstructor().newInstance();
                subField.set(currentObj, nextObj);
            }
            currentObj = nextObj;
        }
        Field lastField = fieldUtils.getFieldFromHierarchy(currentObj.getClass(), parts[parts.length - 1]);
        lastField.setAccessible(true);
        Class<?> fieldType = lastField.getType();
        logger.debug("Validating embedded field {} (type: {}) with value: {}", field, fieldType.getSimpleName(), value);
        fieldValueSetter.setFieldValue(lastField, currentObj, value, fieldType);
    }

    private void validateBasicField(Object entity, String field, String value) throws Exception {
        Field f = fieldUtils.getFieldFromHierarchy(entity.getClass(), field);
        f.setAccessible(true);
        Class<?> fieldType = f.getType();
        logger.debug("Validating field {} (type: {}) with value: {}", field, fieldType.getSimpleName(), value);
        fieldValueSetter.setFieldValue(f, entity, value, fieldType);
    }

    private void handleValidationError(String field, Exception e, Map<String, String> errors) {
        if (e instanceof NoSuchFieldException) {
            logger.warn("Field {} not found in entity: {}", field, e.getMessage());
            errors.put(field, "Field not found: " + field);
        } else if (e instanceof IllegalAccessException) {
            logger.warn("Cannot access field {}: {}", field, e.getMessage());
            errors.put(field, "Cannot access field: " + field);
        } else if (e instanceof NumberFormatException) {
            logger.warn("Invalid number format for field {}: {}", field, e.getMessage());
            errors.put(field, "Invalid number format for field: " + field);
        } else {
            logger.error("Unexpected error validating field {}: {}", field, e.getMessage(), e);
            errors.put(field, "Unexpected error: " + e.getMessage());
        }
    }

    private void processViolations(Set<ConstraintViolation<Object>> violations, Map<String, String> errors) {
        if (!violations.isEmpty()) {
            for (ConstraintViolation<Object> violation : violations) {
                String fieldName = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                errors.put(fieldName, message);
                logger.debug("Validation error for field {}: {}", fieldName, message);
            }
        }
    }
}