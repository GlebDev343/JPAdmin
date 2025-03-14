package by.glebka.jpadmin.service.record;

import jakarta.persistence.Column;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for setting field values on entities based on their types and annotations.
 */
@Component
public class FieldValueSetter {

    private static final Logger logger = LoggerFactory.getLogger(FieldValueSetter.class);
    private static final Pattern BIT_PATTERN = Pattern.compile("BIT\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final String NULL_VALUE = "null";

    /**
     * Sets the value of a field on a target object, handling various data types and constraints.
     *
     * @param field     The field to set.
     * @param target    The object to modify.
     * @param value     The string value to set.
     * @param fieldType The type of the field.
     * @throws IllegalAccessException If the field cannot be accessed.
     * @throws IllegalArgumentException If the value is invalid for the field type.
     */
    public void setFieldValue(Field field, Object target, String value, Class<?> fieldType) throws IllegalAccessException {
        if (isNullOrEmpty(value)) {
            field.set(target, null);
            return;
        }

        try {
            if (field.isAnnotationPresent(Type.class)) {
                field.set(target, value); // Delegate to Hibernate UserType
                return;
            }

            Column column = field.getAnnotation(Column.class);
            String columnDefinition = column != null ? column.columnDefinition().toUpperCase() : "";

            if (columnDefinition.contains("BIT")) {
                handleBitField(field, target, value, columnDefinition);
            } else {
                setTypedFieldValue(field, target, value, fieldType);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid value '{}' for field {}: {}", value, field.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to set field {} with value {}: {}", field.getName(), value, e.getMessage());
            throw new IllegalArgumentException("Failed to set field " + field.getName() + ": " + e.getMessage(), e);
        }
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty() || NULL_VALUE.equalsIgnoreCase(value);
    }

    private void handleBitField(Field field, Object target, String value, String columnDefinition) throws IllegalAccessException {
        Matcher bitMatcher = BIT_PATTERN.matcher(columnDefinition);
        if (bitMatcher.find()) {
            int bitLength = Integer.parseInt(bitMatcher.group(1));
            if (value.length() > bitLength) {
                throw new IllegalArgumentException("Value '" + value + "' exceeds maximum length of " + bitLength + " bits");
            }
            field.set(target, value);
        } else if (columnDefinition.contains("VARYING") || columnDefinition.contains("VARBIT")) {
            field.set(target, value);
        } else {
            if ("0".equals(value) || "1".equals(value)) {
                field.set(target, value);
            } else if ("true".equalsIgnoreCase(value)) {
                field.set(target, "1");
            } else if ("false".equalsIgnoreCase(value)) {
                field.set(target, "0");
            } else {
                throw new IllegalArgumentException("Value for BIT must be '0', '1', 'true', or 'false'");
            }
        }
    }

    private void setTypedFieldValue(Field field, Object target, String value, Class<?> fieldType) throws IllegalAccessException {
        if (fieldType == String.class) field.set(target, value);
        else if (fieldType == Long.class || fieldType == long.class) field.set(target, Long.parseLong(value));
        else if (fieldType == Integer.class || fieldType == int.class) field.set(target, Integer.parseInt(value));
        else if (fieldType == Short.class || fieldType == short.class) field.set(target, Short.parseShort(value));
        else if (fieldType == Boolean.class || fieldType == boolean.class) field.set(target, "true".equalsIgnoreCase(value));
        else if (fieldType == byte[].class) field.set(target, hexStringToByteArray(value));
        else if (fieldType == LocalDate.class) field.set(target, LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE));
        else if (fieldType == LocalDateTime.class) field.set(target, LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        else if (fieldType == LocalTime.class) field.set(target, LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME));
        else if (fieldType == OffsetTime.class) field.set(target, OffsetTime.parse(value, DateTimeFormatter.ISO_OFFSET_TIME));
        else if (fieldType == OffsetDateTime.class) field.set(target, OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        else if (fieldType == Double.class || fieldType == double.class) field.set(target, Double.parseDouble(value));
        else if (fieldType == Float.class || fieldType == float.class) field.set(target, Float.parseFloat(value));
        else if (fieldType == BigDecimal.class) field.set(target, new BigDecimal(value));
        else if (fieldType == UUID.class) field.set(target, UUID.fromString(value));
        else {
            logger.warn("Unsupported field type {} for field {}", fieldType.getSimpleName(), field.getName());
            throw new IllegalArgumentException("Unsupported field type: " + fieldType.getName());
        }
    }

    private byte[] hexStringToByteArray(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }
}