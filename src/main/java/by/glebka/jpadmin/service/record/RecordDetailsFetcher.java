package by.glebka.jpadmin.service.record;

import by.glebka.jpadmin.scanner.EntityInfo;
import by.glebka.jpadmin.service.EntityTableService;
import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Component responsible for fetching detailed record data and metadata from entities.
 */
@Component
public class RecordDetailsFetcher {

    private static final Logger logger = LoggerFactory.getLogger(RecordDetailsFetcher.class);
    private static final String DEFAULT_PACKAGE_PREFIX = "by.glebka.jpadmin.entity.";
    private static final String ADMIN_TABLE_PATH = "/admin/table/";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityTableService entityTableService;

    @Autowired
    private FieldUtils fieldUtils;

    /**
     * Fetches detailed information about a specific record with configurable table checking.
     *
     * @param tableName       The name of the table.
     * @param id              The ID of the record.
     * @param strictTableCheck Whether to enforce strict table registration checks.
     * @return A map containing record details, or null if the record is not found.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRecordDetails(String tableName, Long id, boolean strictTableCheck) {
        if (tableName == null || id == null) {
            throw new IllegalArgumentException("Table name and ID cannot be null");
        }
        logger.debug("Fetching details for table {} with id {}, strictTableCheck={}", tableName, id, strictTableCheck);

        EntityInfo entityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> tableName.equals(e.getTableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));

        Class<?> entityClass = loadEntityClass(entityInfo);
        Object entity = entityManager.find(entityClass, id);
        if (entity == null) {
            logger.warn("Entity not found for table {} with id {}", tableName, id);
            return null;
        }

        Map<String, Object> metadata = collectFieldMetadata(entityClass);
        Map<String, Object> recordMap = buildRecordMap(entity, entityClass, metadata);
        Map<String, Map<String, String>> childTables = buildChildTables(entityClass, recordMap, metadata, strictTableCheck);

        List<String> simpleFields = buildSimpleFields(metadata);
        return assembleResult(tableName, entityClass, recordMap, metadata, simpleFields, childTables);
    }

    /**
     * Fetches detailed information about a specific record with default table checking.
     *
     * @param tableName The name of the table.
     * @param id        The ID of the record.
     * @return A map containing record details, or null if the record is not found.
     */
    public Map<String, Object> getRecordDetails(String tableName, Long id) {
        return getRecordDetails(tableName, id, false);
    }

    /**
     * Fetches metadata for an empty record for a given table.
     *
     * @param tableName The name of the table.
     * @return A map containing empty record metadata.
     */
    public Map<String, Object> getEmptyRecordData(String tableName) {
        logger.debug("Fetching empty record data for table {}", tableName);

        EntityInfo entityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> tableName.equals(e.getTableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));

        Class<?> entityClass = loadEntityClass(entityInfo);
        Map<String, Object> metadata = collectFieldMetadata(entityClass);
        Map<String, Object> recordMap = initializeEmptyRecord(metadata);
        List<String> simpleFields = buildSimpleFields(metadata);

        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        result.put("entityClass", entityClass.getSimpleName());
        result.put("record", recordMap);
        result.put("fields", metadata.get("displayFields"));
        result.put("simpleFields", simpleFields);
        result.put("isCollectionField", metadata.get("isCollectionField"));
        result.put("fieldTypes", metadata.get("fieldTypes"));
        result.put("nullableFields", metadata.get("nullableFields"));
        result.put("foreignKeyFields", metadata.get("foreignKeyFields"));
        result.put("oneToManyFields", metadata.get("oneToManyFields"));
        result.put("manyToManyFields", metadata.get("manyToManyFields"));
        return result;
    }

    private Class<?> loadEntityClass(EntityInfo entityInfo) {
        try {
            return Class.forName(entityInfo.getClassName());
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load entity class: {}", e.getMessage());
            throw new RuntimeException("Unable to load entity class: " + entityInfo.getClassName(), e);
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
        metadata.put("oneToManyFields", oneToManyFields);
        metadata.put("manyToManyFields", manyToManyFields);
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRecordMap(Object entity, Class<?> entityClass, Map<String, Object> metadata) {
        Map<String, Object> recordMap = new HashMap<>();
        Set<String> displayFields = (Set<String>) metadata.get("displayFields");
        Map<String, String> embeddedFieldPaths = (Map<String, String>) metadata.get("embeddedFieldPaths");
        Map<String, String> foreignKeyFields = (Map<String, String>) metadata.get("foreignKeyFields");
        Map<String, String> fieldTypes = (Map<String, String>) metadata.get("fieldTypes");
        Map<String, Boolean> isCollectionField = (Map<String, Boolean>) metadata.get("isCollectionField");

        for (String field : displayFields) {
            try {
                if (embeddedFieldPaths.containsKey(field)) {
                    recordMap.put(field, getEmbeddedFieldValue(entity, embeddedFieldPaths.get(field)));
                } else if (foreignKeyFields.containsKey(field)) {
                    String link = buildForeignKeyLink(field, foreignKeyFields, fieldTypes, entityClass, entity);
                    recordMap.put(field, link);
                    recordMap.put(field + "_link", link);
                } else if (!isCollectionField.getOrDefault(field, false)) {
                    Field f = fieldUtils.getFieldFromHierarchy(entityClass, field);
                    f.setAccessible(true);
                    recordMap.put(field, f.get(entity));
                }
            } catch (Exception e) {
                logger.warn("Failed to process field {}: {}", field, e.getMessage());
                recordMap.put(field, null);
            }
        }
        return recordMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> buildChildTables(Class<?> entityClass, Map<String, Object> recordMap, Map<String, Object> metadata, boolean strictTableCheck) {
        Map<String, String> oneToManyFields = (Map<String, String>) metadata.get("oneToManyFields");
        Map<String, String> manyToManyFields = (Map<String, String>) metadata.get("manyToManyFields");
        Set<String> displayFields = (Set<String>) metadata.get("displayFields");
        Map<String, Map<String, String>> childTables = new HashMap<>();
        Object idValue = recordMap.get("id");
        List<EntityInfo> availableTables = entityTableService.getEntityTables();

        for (String field : displayFields) {
            if (oneToManyFields.containsKey(field) && idValue != null) {
                processChildTable(field, oneToManyFields.get(field), entityClass, idValue, strictTableCheck, availableTables, childTables, OneToMany.class);
            } else if (manyToManyFields.containsKey(field) && idValue != null) {
                processChildTable(field, manyToManyFields.get(field), entityClass, idValue, strictTableCheck, availableTables, childTables, ManyToMany.class);
            }
        }
        return childTables;
    }

    private void processChildTable(String field, String targetTable, Class<?> entityClass, Object idValue, boolean strictTableCheck,
                                   List<EntityInfo> availableTables, Map<String, Map<String, String>> childTables,
                                   Class<? extends java.lang.annotation.Annotation> relationType) {
        boolean isRegistered = isTableRegistered(targetTable, availableTables);
        if (strictTableCheck && !isRegistered) {
            logger.warn("Skipping {} field {}: Table '{}' is not registered", relationType.getSimpleName(), field, targetTable);
            return;
        }
        if (isRegistered) {
            try {
                Field f = fieldUtils.getFieldFromHierarchy(entityClass, field);
                Class<?> targetClass = (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                String filterField = getInverseFieldName(targetClass, entityClass, relationType);
                String link = buildLink(targetTable, filterField, idValue);
                childTables.put(field, Map.of("targetTable", targetTable, "link", link));
            } catch (NoSuchFieldException e) {
                logger.warn("Field {} not found for {} relationship", field, relationType.getSimpleName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> buildSimpleFields(Map<String, Object> metadata) {
        Set<String> displayFields = (Set<String>) metadata.get("displayFields");
        Map<String, String> oneToManyFields = (Map<String, String>) metadata.get("oneToManyFields");
        Map<String, String> manyToManyFields = (Map<String, String>) metadata.get("manyToManyFields");
        Map<String, String> foreignKeyFields = (Map<String, String>) metadata.get("foreignKeyFields");

        List<String> simpleFields = new ArrayList<>();
        for (String field : displayFields) {
            if (!"id".equals(field) && !oneToManyFields.containsKey(field) && !manyToManyFields.containsKey(field) && !foreignKeyFields.containsKey(field)) {
                simpleFields.add(field);
            }
        }
        return simpleFields;
    }

    private Map<String, Object> assembleResult(String tableName, Class<?> entityClass, Map<String, Object> recordMap,
                                               Map<String, Object> metadata, List<String> simpleFields,
                                               Map<String, Map<String, String>> childTables) {
        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        result.put("entityClass", entityClass.getSimpleName());
        result.put("record", recordMap);
        result.put("fields", metadata.get("displayFields"));
        result.put("simpleFields", simpleFields);
        result.put("isCollectionField", metadata.get("isCollectionField"));
        result.put("fieldTypes", metadata.get("fieldTypes"));
        result.put("nullableFields", metadata.get("nullableFields"));
        result.put("foreignKeyFields", metadata.get("foreignKeyFields"));
        result.put("oneToManyFields", metadata.get("oneToManyFields"));
        result.put("manyToManyFields", metadata.get("manyToManyFields"));
        result.put("childTables", childTables);
        logger.debug("Fetched record details: {}", result);
        return result;
    }

    private Object getEmbeddedFieldValue(Object entity, String path) throws Exception {
        String[] parts = path.split("\\.");
        Object embeddedObj = entity;
        for (String part : parts) {
            Field f = embeddedObj.getClass().getDeclaredField(part);
            f.setAccessible(true);
            embeddedObj = f.get(embeddedObj);
        }
        return embeddedObj;
    }

    private String buildForeignKeyLink(String field, Map<String, String> foreignKeyFields, Map<String, String> fieldTypes,
                                       Class<?> entityClass, Object entity) throws Exception {
        String targetTable = foreignKeyFields.get(field);
        String targetType = fieldTypes.get(field);
        if (targetType == null) return null;

        String fullTargetType = targetType.contains(".") ? targetType : DEFAULT_PACKAGE_PREFIX + targetType;
        Class<?> targetClass = Class.forName(fullTargetType);
        String filterField = getInverseFieldName(targetClass, entityClass, OneToOne.class);
        Field f = fieldUtils.getFieldFromHierarchy(entityClass, field);
        f.setAccessible(true);
        Object id = f.get(entity);
        return id != null ? buildLink(targetTable, filterField, id) : null;
    }

    private String buildLink(String targetTable, String filterField, Object idValue) {
        return ADMIN_TABLE_PATH + targetTable + "?filterField=" + filterField + "&filterOperation=equals&filterValue=" + idValue;
    }

    private boolean isTableRegistered(String tableName, List<EntityInfo> availableTables) {
        return availableTables.stream().anyMatch(t -> t.getTableName().equals(tableName));
    }

    private String getInverseFieldName(Class<?> targetClass, Class<?> parentClass, Class<? extends java.lang.annotation.Annotation> relationType) {
        for (Field field : targetClass.getDeclaredFields()) {
            if (relationType.equals(OneToOne.class) && field.isAnnotationPresent(OneToOne.class)) {
                OneToOne oneToOne = field.getAnnotation(OneToOne.class);
                if (field.getType().equals(parentClass) && oneToOne.mappedBy().isEmpty()) {
                    return field.getName();
                }
            } else if (relationType.equals(OneToMany.class) && field.isAnnotationPresent(ManyToOne.class)) {
                if (field.getType().equals(parentClass)) {
                    return field.getName();
                }
            } else if (relationType.equals(ManyToMany.class) && field.isAnnotationPresent(ManyToMany.class)) {
                ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
                if (field.getType().equals(List.class)) {
                    Class<?> elementType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    if (elementType.equals(parentClass) && !manyToMany.mappedBy().isEmpty()) {
                        return field.getName();
                    }
                }
            }
        }
        logger.warn("Could not find inverse field in {} for {} with relation {}", targetClass.getSimpleName(), parentClass.getSimpleName(), relationType.getSimpleName());
        return "id";
    }

    private Map<String, Object> initializeEmptyRecord(Map<String, Object> metadata) {
        Map<String, Object> recordMap = new HashMap<>();
        @SuppressWarnings("unchecked")
        Set<String> displayFields = (Set<String>) metadata.get("displayFields");
        for (String field : displayFields) {
            recordMap.put(field, null);
        }
        return recordMap;
    }
}