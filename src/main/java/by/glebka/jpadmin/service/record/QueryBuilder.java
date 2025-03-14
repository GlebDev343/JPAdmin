package by.glebka.jpadmin.service.record;

import by.glebka.jpadmin.scanner.MetamodelAnalyzer;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/**
 * Component for building JPA Criteria API predicates based on filter conditions.
 */
@Component
public class QueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(QueryBuilder.class);
    private static final DateTimeFormatter FRONTEND_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_OPERATION = "equals";

    @Autowired
    private MetamodelAnalyzer metamodelAnalyzer;

    private static final Map<Class<?>, Function<String, Object>> PARSERS = initializeParsers();
    private static final Map<Class<?>, Function<Object, Object>> NORMALIZERS = initializeNormalizers();
    private static final Map<String, TriFunction<CriteriaBuilder, Path<?>, Object, Predicate>> OPERATION_HANDLERS = initializeOperationHandlers();

    /**
     * Builds a predicate for filtering records based on field and filter conditions.
     *
     * @param cb              The CriteriaBuilder instance.
     * @param root            The root entity for the query.
     * @param field           The field to filter on.
     * @param filters         The list of filter conditions.
     * @param entityClass     The entity class.
     * @param embeddedFieldPaths Map of embedded field paths.
     * @param joins           Map of existing joins.
     * @return A predicate representing the filter conditions, or null if invalid.
     */
    public <T> Predicate buildPredicate(CriteriaBuilder cb, Root<T> root, String field, List<Map<String, String>> filters,
                                        Class<T> entityClass, Map<String, String> embeddedFieldPaths,
                                        Map<String, Join<T, ?>> joins) {
        try {
            Path<?> path = getPath(root, field, entityClass, embeddedFieldPaths);
            if (path == null) {
                logger.warn("Path could not be resolved for field: {}", field);
                return null;
            }

            List<Predicate> fieldPredicates = new ArrayList<>();
            Map<String, List<Map<String, String>>> operationGroups = groupFiltersByOperation(filters);

            for (Map.Entry<String, List<Map<String, String>>> entry : operationGroups.entrySet()) {
                String[] keyParts = entry.getKey().split("\\|");
                String operation = keyParts[0];
                boolean allowNull = "true".equals(keyParts[1]);
                boolean treatEmptyAsEmptyString = "true".equals(keyParts[2]);
                List<Map<String, String>> opFilters = entry.getValue();
                List<String> values = opFilters.stream()
                        .map(f -> treatEmptyAsEmptyString ? "" : f.get("value"))
                        .toList();

                logger.debug("Applying filter: field={}, operation={}, values={}, allowNull={}, treatEmptyAsEmptyString={}",
                        field, operation, values, allowNull, treatEmptyAsEmptyString);
                Predicate predicate = applyFilter(cb, root, path, field, operation, values, treatEmptyAsEmptyString, allowNull);
                if (predicate != null) {
                    fieldPredicates.add(predicate);
                }
            }

            return fieldPredicates.isEmpty() ? null :
                    fieldPredicates.size() == 1 ? fieldPredicates.get(0) :
                            cb.and(fieldPredicates.toArray(new Predicate[0]));
        } catch (Exception e) {
            logger.warn("Invalid filter field: {} - {}", field, e.getMessage());
            return null;
        }
    }

    private static Map<Class<?>, Function<String, Object>> initializeParsers() {
        Map<Class<?>, Function<String, Object>> parsers = new HashMap<>();
        parsers.put(String.class, v -> v);
        parsers.put(Long.class, Long::parseLong);
        parsers.put(long.class, Long::parseLong);
        parsers.put(Integer.class, Integer::parseInt);
        parsers.put(int.class, Integer::parseInt);
        parsers.put(Short.class, Short::parseShort);
        parsers.put(short.class, Short::parseShort);
        parsers.put(Float.class, Float::parseFloat);
        parsers.put(float.class, Float::parseFloat);
        parsers.put(Double.class, Double::parseDouble);
        parsers.put(double.class, Double::parseDouble);
        parsers.put(BigDecimal.class, BigDecimal::new);
        parsers.put(LocalTime.class, QueryBuilder::parseLocalTime);
        parsers.put(OffsetTime.class, QueryBuilder::parseOffsetTime);
        parsers.put(LocalDateTime.class, QueryBuilder::parseLocalDateTime);
        parsers.put(OffsetDateTime.class, QueryBuilder::parseOffsetDateTime);
        parsers.put(Instant.class, QueryBuilder::parseInstant);
        parsers.put(ZonedDateTime.class, QueryBuilder::parseZonedDateTime);
        parsers.put(UUID.class, UUID::fromString);
        return parsers;
    }

    private static Map<Class<?>, Function<Object, Object>> initializeNormalizers() {
        Map<Class<?>, Function<Object, Object>> normalizers = new HashMap<>();
        normalizers.put(LocalTime.class, QueryBuilder::normalizeLocalTime);
        normalizers.put(OffsetTime.class, QueryBuilder::normalizeOffsetTime);
        normalizers.put(LocalDateTime.class, QueryBuilder::normalizeLocalDateTime);
        normalizers.put(OffsetDateTime.class, QueryBuilder::normalizeOffsetDateTime);
        normalizers.put(Instant.class, QueryBuilder::normalizeInstant);
        normalizers.put(ZonedDateTime.class, QueryBuilder::normalizeZonedDateTime);
        return normalizers;
    }

    private static Map<String, TriFunction<CriteriaBuilder, Path<?>, Object, Predicate>> initializeOperationHandlers() {
        Map<String, TriFunction<CriteriaBuilder, Path<?>, Object, Predicate>> handlers = new HashMap<>();
        handlers.put("equals", (cb, path, value) -> cb.equal(path, value));
        handlers.put("not equal", (cb, path, value) -> cb.notEqual(path, value));
        handlers.put("greater than", (cb, path, value) -> cb.greaterThan((Expression) path, (Comparable) value));
        handlers.put("less than", (cb, path, value) -> cb.lessThan((Expression) path, (Comparable) value));
        handlers.put("greater than or equal", (cb, path, value) -> cb.greaterThanOrEqualTo((Expression) path, (Comparable) value));
        handlers.put("less than or equal", (cb, path, value) -> cb.lessThanOrEqualTo((Expression) path, (Comparable) value));
        handlers.put("contains", (cb, path, value) -> cb.like(cb.lower((Expression<String>) path), "%" + value.toString().toLowerCase() + "%"));
        handlers.put("starts with", (cb, path, value) -> cb.like(cb.lower((Expression<String>) path), value.toString().toLowerCase() + "%"));
        handlers.put("ends with", (cb, path, value) -> cb.like(cb.lower((Expression<String>) path), "%" + value.toString().toLowerCase()));
        handlers.put("true", (cb, path, value) -> cb.isTrue((Expression<Boolean>) path));
        handlers.put("false", (cb, path, value) -> cb.isFalse((Expression<Boolean>) path));
        handlers.put("is null", (cb, path, value) -> cb.isNull(path));
        handlers.put("is not null", (cb, path, value) -> cb.isNotNull(path));
        return handlers;
    }

    @FunctionalInterface
    interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    private Map<String, List<Map<String, String>>> groupFiltersByOperation(List<Map<String, String>> filters) {
        Map<String, List<Map<String, String>>> operationGroups = new HashMap<>();
        for (Map<String, String> filter : filters) {
            String operation = filter.get("operation");
            String value = filter.get("value");
            String allowNull = filter.get("allowNull");
            String treatEmptyAsEmptyString = filter.get("treatEmptyAsEmptyString");
            if (operation != null) {
                String key = operation + "|" + (allowNull != null ? allowNull : "true") + "|" +
                        (treatEmptyAsEmptyString != null ? treatEmptyAsEmptyString : "false");
                if (isValueIndependentOperation(operation)) {
                    operationGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(filter);
                } else if (value != null || "true".equals(treatEmptyAsEmptyString)) {
                    operationGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(filter);
                }
            }
        }
        return operationGroups;
    }

    private boolean isValueIndependentOperation(String operation) {
        return "true".equals(operation) || "false".equals(operation) || "is null".equals(operation) || "is not null".equals(operation);
    }

    private <T> Path<?> getPath(Root<T> root, String field, Class<T> entityClass, Map<String, String> embeddedFieldPaths) {
        Path<?> path = null;
        try {
            Field f = entityClass.getDeclaredField(field);
            logger.debug("Field {} found in entity {}", field, entityClass.getSimpleName());
            if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class)) {
                Join<T, ?> join = root.join(field, JoinType.LEFT);
                path = join.get("id");
            } else if (f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(jakarta.persistence.ManyToMany.class)) {
                Join<T, ?> join = root.join(field, JoinType.LEFT);
                path = join.get("id");
            } else {
                path = root.get(field);
                logger.debug("Using direct path for simple field: {}", field);
            }
        } catch (NoSuchFieldException e) {
            if (embeddedFieldPaths.containsKey(field)) {
                String[] parts = embeddedFieldPaths.get(field).split("\\.");
                path = root.get(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    path = path.get(parts[i]);
                }
                logger.debug("Using embedded path for field: {}", field);
            } else {
                logger.warn("Field {} not found in entity or embedded paths", field);
            }
        }
        return path;
    }

    private Predicate applyFilter(CriteriaBuilder cb, Root<?> root, Path<?> path, String field, String operation,
                                  List<String> values, boolean treatEmptyAsEmptyString, boolean allowNull) {
        Class<?> fieldType = path.getJavaType();

        if ("is null".equals(operation)) return cb.isNull(path);
        if ("is not null".equals(operation)) return cb.isNotNull(path);
        if ("true".equals(operation) && isBooleanType(fieldType)) return cb.isTrue((Expression<Boolean>) path);
        if ("false".equals(operation) && isBooleanType(fieldType)) return cb.isFalse((Expression<Boolean>) path);

        if (values.isEmpty() && !treatEmptyAsEmptyString) {
            logger.warn("No values provided for operation {} on field {}", operation, field);
            return null;
        }

        if (values.size() > 1 && !DEFAULT_OPERATION.equalsIgnoreCase(operation)) {
            logger.warn("Multiple values not supported for operation {}", operation);
            return null;
        }

        String value = values.get(0);
        Object parsedValue = parseAndNormalizeValue(value, fieldType, treatEmptyAsEmptyString);
        if (parsedValue == null && !treatEmptyAsEmptyString && !isEqualityOperation(operation)) {
            logger.warn("Parsed value is null for operation {} on field {}", operation, field);
            return null;
        }

        if (DEFAULT_OPERATION.equalsIgnoreCase(operation) && values.size() > 1) {
            List<Object> parsedValues = values.stream()
                    .map(v -> parseAndNormalizeValue(v, fieldType, treatEmptyAsEmptyString))
                    .filter(Objects::nonNull)
                    .toList();
            return path.in(parsedValues);
        }

        TriFunction<CriteriaBuilder, Path<?>, Object, Predicate> handler = OPERATION_HANDLERS.get(operation.toLowerCase());
        if (handler == null) {
            logger.warn("Unsupported operation {} for field type {}", operation, fieldType.getSimpleName());
            return null;
        }

        try {
            Predicate predicate = handler.apply(cb, path, parsedValue);
            if (allowNull && !isNullOrEqualityOperation(operation)) {
                return cb.or(predicate, cb.isNull(path));
            }
            return predicate;
        } catch (Exception e) {
            logger.warn("Failed to apply filter for operation {} on field type {}: {}", operation, fieldType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private boolean isBooleanType(Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    private boolean isEqualityOperation(String operation) {
        return "equals".equalsIgnoreCase(operation) || "not equal".equalsIgnoreCase(operation);
    }

    private boolean isNullOrEqualityOperation(String operation) {
        return "equals".equalsIgnoreCase(operation) || "is null".equalsIgnoreCase(operation) || "is not null".equalsIgnoreCase(operation);
    }

    private Object parseAndNormalizeValue(String value, Class<?> fieldType, boolean treatEmptyAsEmptyString) {
        if (value == null && !treatEmptyAsEmptyString) return null;
        if (treatEmptyAsEmptyString && (value == null || value.isEmpty())) return "";

        Function<String, Object> parser = PARSERS.get(fieldType);
        if (parser == null) {
            logger.warn("No parser defined for field type: {}", fieldType.getSimpleName());
            return null;
        }

        try {
            Object parsed = parser.apply(value);
            Function<Object, Object> normalizer = NORMALIZERS.get(fieldType);
            return normalizer != null ? normalizer.apply(parsed) : parsed;
        } catch (Exception e) {
            logger.warn("Failed to parse/normalize value '{}' for type {}: {}", value, fieldType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static OffsetTime normalizeOffsetTime(Object value) {
        return value instanceof OffsetTime ot ? ot.truncatedTo(ChronoUnit.MINUTES) : null;
    }

    private static LocalTime normalizeLocalTime(Object value) {
        return value instanceof LocalTime lt ? lt.truncatedTo(ChronoUnit.MINUTES) : null;
    }

    private static OffsetDateTime normalizeOffsetDateTime(Object value) {
        return value instanceof OffsetDateTime odt ? odt.truncatedTo(ChronoUnit.MINUTES) : null;
    }

    private static LocalDateTime normalizeLocalDateTime(Object value) {
        return value instanceof LocalDateTime ldt ? ldt.truncatedTo(ChronoUnit.MINUTES) : null;
    }

    private static Instant normalizeInstant(Object value) {
        return value instanceof Instant i ? i.truncatedTo(ChronoUnit.MINUTES) : null;
    }

    private static ZonedDateTime normalizeZonedDateTime(Object value) {
        return value instanceof ZonedDateTime zdt ? zdt.truncatedTo(ChronoUnit.MINUTES) : null;
    }

    private static LocalTime parseLocalTime(String value) {
        if (value == null) return null;
        String cleanValue = cleanTimeValue(value);
        try {
            return LocalTime.parse(cleanValue, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid LocalTime format: " + value, e);
        }
    }

    private static OffsetTime parseOffsetTime(String value) {
        if (value == null) return null;
        try {
            return OffsetTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid OffsetTime format: " + value, e);
        }
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value, FRONTEND_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e1) {
            try {
                String cleanValue = cleanTimeValue(value);
                return LocalDateTime.parse(cleanValue, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Invalid LocalDateTime format: " + value, e2);
            }
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid OffsetDateTime format: " + value, e);
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid Instant format: " + value, e);
        }
    }

    private static ZonedDateTime parseZonedDateTime(String value) {
        if (value == null) return null;
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ZonedDateTime format: " + value, e);
        }
    }

    private static String cleanTimeValue(String value) {
        return value.contains("+") ? value.split("\\+")[0] :
                value.contains("-") ? value.split("-")[0] :
                        value.endsWith("Z") ? value.substring(0, value.length() - 1) : value;
    }
}