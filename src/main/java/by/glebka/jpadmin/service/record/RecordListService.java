package by.glebka.jpadmin.service.record;

import by.glebka.jpadmin.annotation.ComputedColumn;
import by.glebka.jpadmin.config.AdminConfig;
import by.glebka.jpadmin.config.ColumnConfig;
import by.glebka.jpadmin.config.TableConfig;
import by.glebka.jpadmin.scanner.AttributeInfo;
import by.glebka.jpadmin.scanner.EntityInfo;
import by.glebka.jpadmin.scanner.MetamodelAnalyzer;
import by.glebka.jpadmin.service.EntityTableService;
import by.glebka.jpadmin.service.TimeFormatService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for retrieving and processing table records in the admin interface.
 */
@Service
public class RecordListService {

    private static final Logger logger = LoggerFactory.getLogger(RecordListService.class);
    private static final String BASE_URL = "/admin/table/";
    private static final String DEFAULT_SORT_FIELD = "id";
    private static final String DEFAULT_SORT_ORDER = "DESC";
    private static final String DEFAULT_FILTER_OPERATION = "equals";
    private static final Set<Class<?>> ALLOWED_JAVA_TYPES = initializeAllowedJavaTypes();

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private EntityTableService entityTableService;

    @Autowired
    private QueryBuilder queryBuilder;

    @Autowired
    private MetamodelAnalyzer metamodelAnalyzer;

    @Autowired
    private FieldUtils fieldUtils;

    @Autowired
    private TimeFormatService timeFormatService;

    @Autowired(required = false)
    private AdminConfig adminConfig;

    /**
     * Builds a list of filters based on input parameters.
     *
     * @param filterFields            Fields to filter on.
     * @param filterOperations        Operations to apply.
     * @param filterValues            Values for the filters.
     * @param filterTimeFormats       Time formats for temporal fields.
     * @param allowNulls              Whether null values are allowed.
     * @param treatEmptyAsEmptyStrings Whether to treat empty values as empty strings.
     * @param tableName               Name of the table.
     * @param fieldTypes              Map of field types.
     * @return List of filter maps.
     */
    public List<Map<String, String>> buildFilters(List<String> filterFields, List<String> filterOperations,
                                                  List<String> filterValues, List<String> filterTimeFormats,
                                                  List<String> allowNulls, List<String> treatEmptyAsEmptyStrings,
                                                  String tableName, Map<String, String> fieldTypes) {
        logger.debug("Building filters for table '{}'", tableName);

        if (filterFields == null || filterFields.isEmpty()) {
            return new ArrayList<>();
        }

        int size = filterFields.size();
        List<String> operations = normalizeList(filterOperations, size, DEFAULT_FILTER_OPERATION);
        List<String> values = normalizeList(filterValues, size, "");
        List<String> timeFormats = normalizeList(filterTimeFormats, size, "");
        List<String> allowNullList = normalizeList(allowNulls, size, "true");
        List<String> treatEmptyList = normalizeList(treatEmptyAsEmptyStrings, size, "false");

        List<Map<String, String>> filters = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String field = filterFields.get(i);
            String fieldType = fieldTypes.getOrDefault(field, "String");
            String operation = operations.get(i);
            String value = values.get(i);

            if (DEFAULT_FILTER_OPERATION.equals(operation) && "Integer".equals(fieldType) && !value.matches("-?\\d+")) {
                logger.warn("Invalid value '{}' for field '{}' of type '{}'", value, field, fieldType);
                continue;
            }

            Map<String, String> filter = new HashMap<>();
            filter.put("field", field);
            filter.put("operation", operation);
            filter.put("value", value);
            filter.put("timeFormat", timeFormats.get(i));
            filter.put("allowNull", allowNullList.get(i));
            filter.put("treatEmptyAsEmptyString", treatEmptyList.get(i));
            filters.add(filter);
        }
        return filters;
    }

    /**
     * Retrieves field type metadata for a given table.
     *
     * @param tableName Name of the table.
     * @return Map containing field metadata.
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, Object> getFieldTypesMetadata(String tableName) {
        EntityInfo entityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> tableName.equals(e.getTableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));

        Class<T> entityClass = loadEntityClass(entityInfo);
        return prepareEntityMetadata(entityClass);
    }

    /**
     * Retrieves paginated and filtered records from a table.
     *
     * @param tableName  Name of the table.
     * @param filters    List of filters to apply.
     * @param page       Page number (zero-based).
     * @param size       Number of records per page.
     * @param sortField  Field to sort by.
     * @param sortOrder  Sort order ("ASC" or "DESC").
     * @param nullsFirst Whether to place null values first in sorting.
     * @return Map containing records and metadata.
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, Object> getTableRecords(String tableName, List<Map<String, String>> filters, int page, int size,
                                                   String sortField, String sortOrder, boolean nullsFirst) {
        if (tableName == null) {
            throw new IllegalArgumentException("Table name cannot be null");
        }
        logger.debug("Retrieving records for table '{}'", tableName);

        EntityInfo entityInfo = entityTableService.getEntityTables().stream()
                .filter(e -> tableName.equals(e.getTableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));

        Class<T> entityClass = loadEntityClass(entityInfo);
        String entityClassName = entityClass.getName();
        Map<String, Object> metadata = prepareEntityMetadata(entityClass);
        Set<String> filterableFields = getFilterableFields(entityClass);
        Map<String, String> filterFields = buildFilterFields(entityClass, metadata);
        List<Map<String, String>> adjustedFilters = adjustFilters(filters, filterFields);

        String effectiveSortField = determineSortField(sortField, entityClass, metadata);
        String effectiveSortOrder = determineSortOrder(sortOrder, entityClass);
        boolean effectiveNullsFirst = determineNullsFirst(sortField, nullsFirst, entityClass);

        CriteriaQuery<T> query = buildQuery(entityClass, adjustedFilters, metadata, effectiveSortField, effectiveSortOrder);
        List<T> results = executeQuery(query, page, size);
        List<Map<String, Object>> recordMaps = buildRecordMaps(results, entityClass, metadata, entityClassName);

        timeFormatService.formatRecords(recordMaps, (Map<String, String>) metadata.get("fieldTypes"));

        long totalElements = getTotalCount(entityClass, adjustedFilters, (Map<String, String>) metadata.get("embeddedFieldPaths"));
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return assembleResult(tableName, entityClass, recordMaps, metadata, filterFields, totalPages, totalElements,
                effectiveSortField, effectiveSortOrder, effectiveNullsFirst, adjustedFilters);
    }

    private static Set<Class<?>> initializeAllowedJavaTypes() {
        return Set.of(
                Short.class, short.class, Integer.class, int.class, Long.class, long.class,
                Float.class, float.class, Double.class, double.class, BigDecimal.class,
                String.class, Boolean.class, boolean.class,
                LocalDate.class, LocalTime.class, LocalDateTime.class,
                ZonedDateTime.class, Instant.class, UUID.class
        );
    }

    private <T> Class<T> loadEntityClass(EntityInfo entityInfo) {
        try {
            @SuppressWarnings("unchecked")
            Class<T> entityClass = (Class<T>) Class.forName(entityInfo.getClassName());
            return entityClass;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load entity class: " + entityInfo.getClassName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, Object> prepareEntityMetadata(Class<T> entityClass) {
        Set<String> displayFields = new LinkedHashSet<>();
        Map<String, Boolean> isCollectionField = new HashMap<>();
        Map<String, String> embeddedFieldPaths = new HashMap<>();
        Map<String, String> fieldTypes = new HashMap<>();
        Map<String, Boolean> nullableFields = new HashMap<>();
        Map<String, String> foreignKeyFields = new HashMap<>();
        Map<String, String> foreignKeyColumnNames = new HashMap<>();
        Map<String, String> oneToManyFields = new HashMap<>();
        Map<String, String> manyToManyFields = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        Map<String, Function<Object, Object>> computedColumns = new HashMap<>();

        fieldUtils.collectFieldTypes(entityClass, displayFields, isCollectionField, embeddedFieldPaths, fieldTypes,
                nullableFields, foreignKeyFields, foreignKeyColumnNames, oneToManyFields, manyToManyFields);

        TableConfig tableConfig = adminConfig != null ? adminConfig.getTableConfig(entityClass) : null;
        displayFields.clear();
        if (tableConfig != null && tableConfig.getColumns() != null) {
            for (ColumnConfig column : tableConfig.getColumns()) {
                String fieldName = column.getFieldName();
                if (!isRelationField(entityClass, fieldName)) {
                    displayFields.add(fieldName);
                    displayNames.put(fieldName, column.getDisplayName() != null ? column.getDisplayName() : fieldName);
                    updateFieldType(entityClass, fieldName, fieldTypes);
                }
                if (column.isComputed()) {
                    computedColumns.put(fieldName, column.getComputedValue());
                }
            }
        } else {
            processFieldsAndMethods(entityClass, displayFields, displayNames, computedColumns, embeddedFieldPaths, fieldTypes);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fieldTypes", fieldTypes);
        metadata.put("displayFields", displayFields);
        metadata.put("embeddedFieldPaths", embeddedFieldPaths);
        metadata.put("displayNames", displayNames);
        metadata.put("isCollectionField", isCollectionField);
        metadata.put("foreignKeyFields", foreignKeyFields);
        metadata.put("oneToManyFields", oneToManyFields);
        metadata.put("manyToManyFields", manyToManyFields);
        metadata.put("nullableFields", nullableFields);
        metadata.put("computedColumns", computedColumns);
        return metadata;
    }

    private void updateFieldType(Class<?> entityClass, String fieldName, Map<String, String> fieldTypes) {
        try {
            Field field = entityClass.getDeclaredField(fieldName);
            fieldTypes.putIfAbsent(fieldName, field.getType().getSimpleName());
        } catch (NoSuchFieldException e) {
            logger.warn("Field {} not found in entity {}", fieldName, entityClass.getSimpleName());
        }
    }

    private void processFieldsAndMethods(Class<?> entityClass, Set<String> displayFields, Map<String, String> displayNames,
                                         Map<String, Function<Object, Object>> computedColumns, Map<String, String> embeddedFieldPaths,
                                         Map<String, String> fieldTypes) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                processEmbeddedFields(field, displayFields, displayNames, embeddedFieldPaths, fieldTypes);
            } else if (!isRelationField(entityClass, field.getName())) {
                String fieldName = field.getName();
                displayFields.add(fieldName);
                displayNames.put(fieldName, fieldName);
                fieldTypes.putIfAbsent(fieldName, field.getType().getSimpleName());
            }
        }
        for (Method method : entityClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ComputedColumn.class)) {
                ComputedColumn annotation = method.getAnnotation(ComputedColumn.class);
                String fieldName = method.getName();
                displayFields.add(fieldName);
                displayNames.put(fieldName, annotation.displayName());
                computedColumns.put(fieldName, entity -> {
                    try {
                        method.setAccessible(true);
                        return method.invoke(entity);
                    } catch (Exception e) {
                        logger.warn("Failed to compute column {}: {}", fieldName, e.getMessage());
                        return null;
                    }
                });
            }
        }
    }

    private void processEmbeddedFields(Field field, Set<String> displayFields, Map<String, String> displayNames,
                                       Map<String, String> embeddedFieldPaths, Map<String, String> fieldTypes) {
        Class<?> embeddedType = field.getType();
        for (Field embeddedField : embeddedType.getDeclaredFields()) {
            String embeddedFieldName = field.getName() + "." + embeddedField.getName();
            displayFields.add(embeddedFieldName);
            embeddedFieldPaths.put(embeddedFieldName, embeddedFieldName);
            fieldTypes.putIfAbsent(embeddedFieldName, embeddedField.getType().getSimpleName());
            displayNames.put(embeddedFieldName, embeddedField.getName());
        }
    }

    private <T> boolean isRelationField(Class<T> entityClass, String fieldName) {
        try {
            Field field = entityClass.getDeclaredField(fieldName);
            return field.isAnnotationPresent(jakarta.persistence.OneToMany.class) ||
                    field.isAnnotationPresent(jakarta.persistence.ManyToOne.class) ||
                    field.isAnnotationPresent(jakarta.persistence.ManyToMany.class) ||
                    field.isAnnotationPresent(jakarta.persistence.OneToOne.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private List<String> normalizeList(List<String> list, int expectedSize, String defaultValue) {
        if (list == null) {
            return Collections.nCopies(expectedSize, defaultValue);
        }
        List<String> normalized = new ArrayList<>(list);
        while (normalized.size() < expectedSize) {
            normalized.add(defaultValue);
        }
        return normalized;
    }

    private <T> Set<String> getFilterableFields(Class<T> entityClass) {
        EntityInfo entityInfo = metamodelAnalyzer.analyze(entityClass);
        Set<String> filterableFields = new LinkedHashSet<>();
        Set<String> displayedFields = new LinkedHashSet<>();
        Map<String, Function<Object, Object>> computedColumns = new HashMap<>();
        TableConfig tableConfig = adminConfig != null ? adminConfig.getTableConfig(entityClass) : null;

        if (tableConfig != null && tableConfig.getColumns() != null) {
            for (ColumnConfig column : tableConfig.getColumns()) {
                if (!column.isComputed()) {
                    displayedFields.add(column.getFieldName());
                } else {
                    computedColumns.put(column.getFieldName(), column.getComputedValue());
                }
            }
        } else {
            processDefaultFieldsAndMethods(entityClass, displayedFields, computedColumns);
        }

        Map<String, AttributeInfo> attributes = entityInfo.getMetamodelInfo().getAttributes();
        for (Field field : entityClass.getDeclaredFields()) {
            String fieldName = field.getName();
            if (field.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                processEmbeddedFilterableFields(field, displayedFields, computedColumns, filterableFields);
            } else if (displayedFields.contains(fieldName) && !computedColumns.containsKey(fieldName)) {
                AttributeInfo attrInfo = attributes.get(fieldName);
                if (attrInfo != null && ALLOWED_JAVA_TYPES.contains(getJavaTypeFromString(attrInfo.getJavaTypeSimpleName()))) {
                    filterableFields.add(fieldName);
                }
            }
        }
        return filterableFields;
    }

    private void processDefaultFieldsAndMethods(Class<?> entityClass, Set<String> displayedFields, Map<String, Function<Object, Object>> computedColumns) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                Class<?> embeddedType = field.getType();
                for (Field embeddedField : embeddedType.getDeclaredFields()) {
                    displayedFields.add(field.getName() + "." + embeddedField.getName());
                }
            } else {
                displayedFields.add(field.getName());
            }
        }
        for (Method method : entityClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ComputedColumn.class)) {
                String fieldName = method.getName();
                displayedFields.add(fieldName);
                computedColumns.put(fieldName, entity -> {
                    try {
                        method.setAccessible(true);
                        return method.invoke(entity);
                    } catch (Exception e) {
                        logger.warn("Failed to compute column {}: {}", fieldName, e.getMessage());
                        return null;
                    }
                });
            }
        }
    }

    private void processEmbeddedFilterableFields(Field field, Set<String> displayedFields, Map<String, Function<Object, Object>> computedColumns,
                                                 Set<String> filterableFields) {
        Class<?> embeddedType = field.getType();
        for (Field embeddedField : embeddedType.getDeclaredFields()) {
            String embeddedFieldName = field.getName() + "." + embeddedField.getName();
            if (displayedFields.contains(embeddedFieldName) && !computedColumns.containsKey(embeddedFieldName) &&
                    ALLOWED_JAVA_TYPES.contains(embeddedField.getType())) {
                filterableFields.add(embeddedFieldName);
            }
        }
    }

    private Class<?> getJavaTypeFromString(String typeName) {
        return switch (typeName) {
            case "String" -> String.class;
            case "Integer" -> Integer.class;
            case "Long" -> Long.class;
            case "Short" -> Short.class;
            case "Float" -> Float.class;
            case "Double" -> Double.class;
            case "BigDecimal" -> BigDecimal.class;
            case "Boolean" -> Boolean.class;
            case "LocalTime" -> LocalTime.class;
            case "OffsetTime" -> OffsetTime.class;
            case "LocalDateTime" -> LocalDateTime.class;
            case "OffsetDateTime" -> OffsetDateTime.class;
            case "Instant" -> Instant.class;
            case "ZonedDateTime" -> ZonedDateTime.class;
            case "UUID" -> UUID.class;
            case "byte[]" -> byte[].class;
            default -> Object.class;
        };
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, String> buildFilterFields(Class<T> entityClass, Map<String, Object> metadata) {
        Set<String> filterableFields = getFilterableFields(entityClass);
        Map<String, String> displayNames = (Map<String, String>) metadata.get("displayNames");
        Map<String, String> filterFields = new LinkedHashMap<>();
        for (String fieldName : filterableFields) {
            String displayName = displayNames.getOrDefault(fieldName, fieldName);
            filterFields.put(displayName, fieldName);
        }
        return filterFields;
    }

    private List<Map<String, String>> adjustFilters(List<Map<String, String>> filters, Map<String, String> filterFields) {
        if (filters == null || filters.isEmpty()) return filters;
        return filters.stream().map(filter -> {
            Map<String, String> adjustedFilter = new HashMap<>(filter);
            String displayField = filter.get("field");
            String realField = filterFields.getOrDefault(displayField, displayField);
            adjustedFilter.put("field", realField);
            return adjustedFilter;
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T> String determineSortField(String sortField, Class<T> entityClass, Map<String, Object> metadata) {
        Map<String, String> filterFields = buildFilterFields(entityClass, metadata);
        Map<String, Function<Object, Object>> computedColumns = (Map<String, Function<Object, Object>>) metadata.get("computedColumns");
        String defaultSortField = adminConfig != null && adminConfig.getTableConfig(entityClass) != null ?
                adminConfig.getTableConfig(entityClass).getDefaultSortField() : DEFAULT_SORT_FIELD;

        String effectiveSortField = sortField != null ? filterFields.getOrDefault(sortField, sortField) : defaultSortField;
        if (effectiveSortField != null && computedColumns.containsKey(effectiveSortField)) {
            logger.warn("Sort field '{}' is computed, falling back to '{}'", effectiveSortField, DEFAULT_SORT_FIELD);
            return DEFAULT_SORT_FIELD;
        }
        return effectiveSortField;
    }

    private <T> String determineSortOrder(String sortOrder, Class<T> entityClass) {
        return sortOrder != null ? sortOrder :
                (adminConfig != null && adminConfig.getTableConfig(entityClass) != null ?
                        adminConfig.getTableConfig(entityClass).getDefaultSortOrder() : DEFAULT_SORT_ORDER);
    }

    private <T> boolean determineNullsFirst(String sortField, boolean nullsFirst, Class<T> entityClass) {
        return sortField != null ? nullsFirst :
                (adminConfig != null && adminConfig.getTableConfig(entityClass) != null ?
                        adminConfig.getTableConfig(entityClass).isDefaultNullsFirst() : false);
    }

    @SuppressWarnings("unchecked")
    private <T> CriteriaQuery<T> buildQuery(Class<T> entityClass, List<Map<String, String>> filters, Map<String, Object> metadata,
                                            String sortField, String sortOrder) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);

        Map<String, String> foreignKeyFields = (Map<String, String>) metadata.get("foreignKeyFields");
        Set<String> displayFields = (Set<String>) metadata.get("displayFields");
        for (String field : displayFields) {
            if (foreignKeyFields.containsKey(field)) {
                root.fetch(field, JoinType.LEFT);
            }
        }

        query.select(root);

        if (filters != null && !filters.isEmpty()) {
            List<Predicate> predicates = new ArrayList<>();
            Map<String, Join<T, ?>> joins = new HashMap<>();
            for (Map<String, String> filter : filters) {
                Predicate predicate = queryBuilder.buildPredicate(cb, root, filter.get("field"), List.of(filter), entityClass,
                        (Map<String, String>) metadata.get("embeddedFieldPaths"), joins);
                if (predicate != null) {
                    predicates.add(predicate);
                }
            }
            if (!predicates.isEmpty()) {
                query.where(cb.and(predicates.toArray(new Predicate[0])));
            }
        }

        if (sortField != null && getFilterableFields(entityClass).contains(sortField)) {
            Path<?> sortPath = buildSortPath(root, sortField);
            query.orderBy("ASC".equalsIgnoreCase(sortOrder) ? cb.asc(sortPath) : cb.desc(sortPath));
        }

        return query;
    }

    private Path<?> buildSortPath(Root<?> root, String sortField) {
        Path<?> sortPath = root;
        for (String part : sortField.split("\\.")) {
            sortPath = sortPath.get(part);
        }
        return sortPath;
    }

    private <T> List<T> executeQuery(CriteriaQuery<T> query, int page, int size) {
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);
        return typedQuery.getResultList();
    }

    @SuppressWarnings("unchecked")
    private <T> List<Map<String, Object>> buildRecordMaps(List<T> results, Class<T> entityClass, Map<String, Object> metadata, String entityClassName) {
        List<Map<String, Object>> recordMaps = new ArrayList<>();
        Set<String> displayFields = (Set<String>) metadata.get("displayFields");
        Map<String, String> embeddedFieldPaths = (Map<String, String>) metadata.get("embeddedFieldPaths");
        Map<String, Function<Object, Object>> computedColumns = (Map<String, Function<Object, Object>>) metadata.get("computedColumns");
        Map<String, String> foreignKeyFields = (Map<String, String>) metadata.get("foreignKeyFields");
        Map<String, String> oneToManyFields = (Map<String, String>) metadata.get("oneToManyFields");
        Map<String, String> manyToManyFields = (Map<String, String>) metadata.get("manyToManyFields");

        for (T entity : results) {
            Map<String, Object> recordMap = new LinkedHashMap<>();
            for (String field : displayFields) {
                try {
                    if (embeddedFieldPaths.containsKey(field)) {
                        recordMap.put(field, getEmbeddedFieldValue(entity, embeddedFieldPaths.get(field)));
                    } else if (computedColumns.containsKey(field)) {
                        recordMap.put(field, computedColumns.get(field).apply(entity));
                    } else if (oneToManyFields.containsKey(field) || manyToManyFields.containsKey(field)) {
                        Field f = entityClass.getDeclaredField(field);
                        f.setAccessible(true);
                        Collection<?> relatedEntities = (Collection<?>) f.get(entity);
                        recordMap.put(field, relatedEntities != null ? relatedEntities.size() : 0);
                    } else {
                        Field f = entityClass.getDeclaredField(field);
                        f.setAccessible(true);
                        Object value = f.get(entity);
                        if (value instanceof byte[]) {
                            value = bytesToHex((byte[]) value);
                        }
                        recordMap.put(field, value);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to access field {}: {}", field, e.getMessage());
                    recordMap.put(field, null);
                }
            }
            addRelationLinks(recordMap, displayFields, foreignKeyFields, oneToManyFields, manyToManyFields, entityClassName);
            recordMaps.add(recordMap);
        }
        return recordMaps;
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

    private void addRelationLinks(Map<String, Object> recordMap, Set<String> displayFields, Map<String, String> foreignKeyFields,
                                  Map<String, String> oneToManyFields, Map<String, String> manyToManyFields, String entityClassName) {
        Object idValue = recordMap.get("id");
        for (String field : displayFields) {
            if (foreignKeyFields.containsKey(field) && recordMap.get(field) != null) {
                String targetTable = foreignKeyFields.get(field);
                recordMap.put(field + "_link", BASE_URL + targetTable + "/" + recordMap.get(field));
            } else if (oneToManyFields.containsKey(field) && idValue != null) {
                String targetTable = oneToManyFields.get(field);
                recordMap.put(field + "_link", buildRelationLink(targetTable, "OneToMany", idValue, entityClassName, field));
            } else if (manyToManyFields.containsKey(field) && idValue != null) {
                String targetTable = manyToManyFields.get(field);
                recordMap.put(field + "_link", buildRelationLink(targetTable, "ManyToMany", idValue, entityClassName, field));
            }
        }
    }

    private String buildRelationLink(String targetTable, String relationType, Object filterValue, String entityClassName, String fieldName) {
        String filterField = inferFilterFieldForRelation(targetTable, relationType, entityClassName, fieldName);
        return UriComponentsBuilder.fromPath(BASE_URL + targetTable)
                .queryParam("filterField", filterField)
                .queryParam("filterOperation", DEFAULT_FILTER_OPERATION)
                .queryParam("filterValue", filterValue)
                .build()
                .toUriString();
    }

    private String inferFilterFieldForRelation(String targetTable, String relationType, String entityClassName, String fieldName) {
        try {
            Class<?> entityClass = Class.forName(entityClassName);
            Map<String, String> filterFields = metamodelAnalyzer.getFilterFields(entityClass);
            if (filterFields.containsKey(fieldName)) {
                return filterFields.get(fieldName);
            }
        } catch (ClassNotFoundException e) {
            logger.warn("Could not load class {} for determining filterField: {}", entityClassName, e.getMessage());
        }
        String inferredField = entityClassName.substring(entityClassName.lastIndexOf('.') + 1).toLowerCase();
        if ("ManyToMany".equals(relationType) || "OneToMany".equals(relationType)) {
            inferredField += "s";
        }
        return inferredField;
    }

    private <T> long getTotalCount(Class<T> entityClass, List<Map<String, String>> filters, Map<String, String> embeddedFieldPaths) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        cq.select(cb.count(root));

        if (filters != null && !filters.isEmpty()) {
            List<Predicate> predicates = new ArrayList<>();
            Map<String, Join<T, ?>> joins = new HashMap<>();
            for (Map<String, String> filter : filters) {
                Predicate predicate = queryBuilder.buildPredicate(cb, root, filter.get("field"), List.of(filter), entityClass, embeddedFieldPaths, joins);
                if (predicate != null) {
                    predicates.add(predicate);
                }
            }
            if (!predicates.isEmpty()) {
                cq.where(cb.and(predicates.toArray(new Predicate[0])));
            }
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, Object> assembleResult(String tableName, Class<T> entityClass, List<Map<String, Object>> recordMaps,
                                                   Map<String, Object> metadata, Map<String, String> filterFields, int totalPages,
                                                   long totalElements, String sortField, String sortOrder, boolean nullsFirst,
                                                   List<Map<String, String>> filters) {
        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        result.put("entityClass", entityClass.getSimpleName());
        result.put("records", recordMaps);
        result.put("fields", metadata.get("displayFields"));
        result.put("simpleFields", metadata.get("displayFields"));
        result.put("displayNames", metadata.get("displayNames"));
        result.put("isCollectionField", metadata.get("isCollectionField"));
        result.put("embeddedFieldPaths", metadata.get("embeddedFieldPaths"));
        result.put("foreignKeyFields", metadata.get("foreignKeyFields"));
        result.put("foreignKeyColumnNames", metadata.get("foreignKeyColumnNames"));
        result.put("oneToManyFields", metadata.get("oneToManyFields"));
        result.put("manyToManyFields", metadata.get("manyToManyFields"));
        result.put("filterFields", filterFields);
        result.put("filterFieldsSet", new LinkedHashSet<>(filterFields.keySet()));
        result.put("inverseFilterFields", buildInverseFilterFields(filterFields));
        result.put("totalPages", totalPages);
        result.put("totalElements", totalElements);
        result.put("sortField", sortField);
        result.put("sortOrder", sortOrder);
        result.put("nullsFirst", nullsFirst);
        result.put("fieldTypes", metadata.get("fieldTypes"));
        result.put("nullableFields", metadata.get("nullableFields"));
        result.put("filters", filters);
        return result;
    }

    private Map<String, String> buildInverseFilterFields(Map<String, String> filterFields) {
        Map<String, String> inverse = new HashMap<>();
        filterFields.forEach((key, value) -> inverse.put(value, key));
        return inverse;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}