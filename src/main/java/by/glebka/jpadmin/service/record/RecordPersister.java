package by.glebka.jpadmin.service.record;

import by.glebka.jpadmin.scanner.EntityInfo;
import by.glebka.jpadmin.service.EntityTableService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Component responsible for persisting entity records to the database.
 */
@Component
public class RecordPersister {

    private static final Logger logger = LoggerFactory.getLogger(RecordPersister.class);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityTableService entityTableService;

    @Autowired
    private FieldUtils fieldUtils;

    @Autowired
    private FieldValueSetter fieldValueSetter;

    /**
     * Updates an existing record in the database.
     *
     * @param tableName    The name of the table.
     * @param id           The ID of the record to update.
     * @param editedFields The fields with updated values.
     * @return True if the update was successful, false otherwise.
     */
    @Transactional
    public boolean updateRecord(String tableName, Long id, Map<String, String> editedFields) {
        logger.debug("Updating record for table {} with id {} and fields {}", tableName, id, editedFields);

        EntityInfo entityInfo = findEntityInfo(tableName);
        if (entityInfo == null) return false;

        Class<?> entityClass = loadEntityClass(entityInfo);
        if (entityClass == null) return false;

        Object entity = entityManager.find(entityClass, id);
        if (entity == null) {
            logger.warn("Entity not found for table {} with id {}", tableName, id);
            return false;
        }

        Map<String, Object> fieldMetadata = collectFieldMetadata(entityClass);
        if (!updateFields(entity, editedFields, fieldMetadata)) return false;

        return persistEntity(entity, tableName, id, "merge");
    }

    /**
     * Creates a new record in the database.
     *
     * @param tableName The name of the table.
     * @param newFields The fields with values for the new record.
     * @return The ID of the newly created record, or null if creation failed.
     */
    @Transactional
    public Long createRecord(String tableName, Map<String, String> newFields) {
        logger.debug("Creating record for table {} with fields {}", tableName, newFields);

        EntityInfo entityInfo = findEntityInfo(tableName);
        if (entityInfo == null) throw new IllegalArgumentException("Table not found: " + tableName);

        Class<?> entityClass = loadEntityClass(entityInfo);
        if (entityClass == null) throw new RuntimeException("Unable to load entity class: " + entityInfo.getClassName());

        Object entity = instantiateEntity(entityClass, tableName);
        if (entity == null) throw new RuntimeException("Unable to instantiate entity");

        Map<String, Object> fieldMetadata = collectFieldMetadata(entityClass);
        updateFields(entity, newFields, fieldMetadata, true);

        boolean success = persistEntity(entity, tableName, null, "persist");
        if (!success) {
            return null; // Если сохранение не удалось, возвращаем null
        }

        try {
            Field idField = fieldUtils.getFieldFromHierarchy(entityClass, "id");
            idField.setAccessible(true);
            Long newId = (Long) idField.get(entity);
            logger.info("Entity persisted successfully for table {} with new ID {}", tableName, newId);
            return newId;
        } catch (Exception e) {
            logger.error("Failed to retrieve ID after persisting entity for table {}: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve new ID", e);
        }
    }

    private EntityInfo findEntityInfo(String tableName) {
        EntityInfo entityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> tableName.equals(e.getTableName()))
                .findFirst()
                .orElse(null);
        if (entityInfo == null) logger.error("Table not found: {}", tableName);
        return entityInfo;
    }

    private Class<?> loadEntityClass(EntityInfo entityInfo) {
        try {
            return Class.forName(entityInfo.getClassName());
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load entity class: {}", e.getMessage());
            return null;
        }
    }

    private Object instantiateEntity(Class<?> entityClass, String tableName) {
        try {
            Object entity = entityClass.getDeclaredConstructor().newInstance();
            logger.debug("Entity instantiated for table {}", tableName);
            return entity;
        } catch (Exception e) {
            logger.error("Failed to instantiate entity for table {}: {}", tableName, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> collectFieldMetadata(Class<?> entityClass) {
        Set<String> displayFields = new LinkedHashSet<>();
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

    private boolean updateFields(Object entity, Map<String, String> fields, Map<String, Object> metadata) {
        return updateFields(entity, fields, metadata, false);
    }

    private boolean updateFields(Object entity, Map<String, String> fields, Map<String, Object> metadata, boolean isNewRecord) {
        @SuppressWarnings("unchecked")
        Map<String, String> embeddedFieldPaths = (Map<String, String>) metadata.get("embeddedFieldPaths");
        @SuppressWarnings("unchecked")
        Map<String, String> foreignKeyFields = (Map<String, String>) metadata.get("foreignKeyFields");
        @SuppressWarnings("unchecked")
        Map<String, String> oneToManyFields = (Map<String, String>) metadata.get("oneToManyFields");
        @SuppressWarnings("unchecked")
        Map<String, String> manyToManyFields = (Map<String, String>) metadata.get("manyToManyFields");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> nullableFields = (Map<String, Boolean>) metadata.get("nullableFields");

        for (String field : fields.keySet()) {
            if (oneToManyFields.containsKey(field) || manyToManyFields.containsKey(field) || (!isNewRecord && "id".equals(field))) {
                logger.debug("Skipping collection or id field: {}", field);
                continue;
            }
            try {
                if (foreignKeyFields.containsKey(field) && isNewRecord) {
                    updateForeignKeyField(entity, field, fields.get(field), foreignKeyFields.get(field), nullableFields.getOrDefault(field, true));
                } else if (embeddedFieldPaths.containsKey(field)) {
                    updateEmbeddedField(entity, field, fields.get(field), embeddedFieldPaths.get(field));
                } else {
                    updateBasicField(entity, field, fields.get(field));
                }
            } catch (Exception e) {
                logger.error("Failed to set field {}: {}", field, e.getMessage(), e);
                if (!isNewRecord) return false;
                throw new RuntimeException("Failed to set field " + field + ": " + e.getMessage(), e);
            }
        }
        return true;
    }

    private void updateForeignKeyField(Object entity, String field, String value, String targetTable, boolean nullable) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            if (!nullable) throw new IllegalArgumentException("Field " + field + " cannot be null");
            return;
        }
        Long foreignId = Long.parseLong(value);
        EntityInfo targetEntityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> targetTable.equals(e.getTableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target table not found: " + targetTable));
        Class<?> targetClass = Class.forName(targetEntityInfo.getClassName());
        Object targetEntity = entityManager.find(targetClass, foreignId);
        if (targetEntity == null) {
            throw new IllegalArgumentException("Referenced entity not found for " + field + " with id " + foreignId);
        }
        Field fkField = fieldUtils.getFieldFromHierarchy(entity.getClass(), field.replace("_id", ""));
        fkField.setAccessible(true);
        logger.debug("Setting foreign key field {} with entity: {}", field, targetEntity);
        fkField.set(entity, targetEntity);
    }

    private void updateEmbeddedField(Object entity, String field, String value, String path) throws Exception {
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
        logger.debug("Setting embedded field {} (type: {}) with value: {}", field, fieldType.getSimpleName(), value);
        fieldValueSetter.setFieldValue(lastField, currentObj, value, fieldType);
    }

    private void updateBasicField(Object entity, String field, String value) throws Exception {
        Field f = fieldUtils.getFieldFromHierarchy(entity.getClass(), field);
        f.setAccessible(true);
        Class<?> fieldType = f.getType();
        logger.debug("Setting field {} (type: {}) with value: {}", field, fieldType.getSimpleName(), value);
        fieldValueSetter.setFieldValue(f, entity, value, fieldType);
    }

    private boolean persistEntity(Object entity, String tableName, Long id, String operation) {
        try {
            if ("merge".equals(operation)) {
                entityManager.merge(entity);
                logger.info("Entity merged successfully for table {} with id {}", tableName, id);
                return true;
            } else {
                entityManager.persist(entity);
                entityManager.flush();
                return true; // Успех сохранения, ID извлечём позже
            }
        } catch (PersistenceException e) {
            logger.error("Failed to {} entity for table {}: {}", operation, tableName, e.getMessage(), e);
            if ("merge".equals(operation)) return false;
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error {}ing entity for table {}: {}", operation, tableName, e.getMessage(), e);
            if ("merge".equals(operation)) return false;
            throw new RuntimeException("Unexpected error during " + operation, e);
        }
    }
}