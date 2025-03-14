package by.glebka.jpadmin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Service for formatting time-related fields in records and UI values.
 */
@Service
public class TimeFormatService {

    private static final Logger logger = LoggerFactory.getLogger(TimeFormatService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_WITH_TZ_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ssXXX");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_WITH_TZ_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    /**
     * Formats time fields in a list of records to their string representations.
     *
     * @param records List of records to format.
     * @param fieldTypes Map of field names to their types.
     */
    public void formatRecords(List<Map<String, Object>> records, Map<String, String> fieldTypes) {
        if (records == null || fieldTypes == null) {
            logger.warn("Records or fieldTypes are null, skipping formatting");
            return;
        }

        for (Map<String, Object> record : records) {
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                String fieldType = fieldTypes.get(fieldName);
                if (fieldType != null && value != null) {
                    formatField(record, fieldName, value, fieldType);
                }
            }
        }
    }

    /**
     * Formats a temporal value for display based on its type and format.
     *
     * @param value Temporal value to format.
     * @param timeFormat Desired format ("time", "time_tz", "datetime", "datetime_tz").
     * @return Formatted string representation.
     */
    public String formatValueForDisplay(LocalTime time, String timeFormat) {
        return time.format(TIME_FORMATTER);
    }

    /** Overloaded method for OffsetTime. */
    public String formatValueForDisplay(OffsetTime offsetTime, String timeFormat) {
        return offsetTime.format(TIME_WITH_TZ_FORMATTER);
    }

    /** Overloaded method for LocalDateTime. */
    public String formatValueForDisplay(LocalDateTime dateTime, String timeFormat) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /** Overloaded method for OffsetDateTime. */
    public String formatValueForDisplay(OffsetDateTime offsetDateTime, String timeFormat) {
        return offsetDateTime.format(DATE_TIME_WITH_TZ_FORMATTER);
    }

    /** Overloaded method for Instant. */
    public String formatValueForDisplay(Instant instant, String timeFormat) {
        return instant.atOffset(ZoneOffset.UTC).format(DATE_TIME_WITH_TZ_FORMATTER);
    }

    /** Overloaded method for ZonedDateTime. */
    public String formatValueForDisplay(ZonedDateTime zonedDateTime, String timeFormat) {
        return zonedDateTime.format(DATE_TIME_WITH_TZ_FORMATTER);
    }

    /**
     * Formats a string value for display based on its type and format.
     *
     * @param value String value to format.
     * @param timeFormat Desired format ("time", "time_tz", "datetime", "datetime_tz").
     * @param fieldType Type of the field (e.g., "LocalTime", "OffsetDateTime").
     * @return Formatted string representation.
     */
    public String formatValueForDisplay(String value, String timeFormat, String fieldType) {
        if (value == null || value.isEmpty()) return value;

        try {
            return switch (timeFormat) {
                case "time" -> formatTime(value, fieldType);
                case "time_tz" -> formatTimeWithTz(value, fieldType);
                case "datetime" -> "LocalDateTime".equals(fieldType) ? LocalDateTime.parse(value, DATE_TIME_FORMATTER).format(DATE_TIME_FORMATTER) : value;
                case "datetime_tz" -> "OffsetDateTime".equals(fieldType) ? OffsetDateTime.parse(value).format(DATE_TIME_WITH_TZ_FORMATTER) : value;
                default -> value;
            };
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse time value: {}, format: {}, type: {}", value, timeFormat, fieldType);
            return value;
        }
    }

    /**
     * Formats a filter value for Flatpickr compatibility.
     *
     * @param value String value to format.
     * @param timeFormat Desired format ("time", "time_tz", "datetime", "datetime_tz").
     * @param fieldType Type of the field.
     * @return Formatted string for Flatpickr.
     */
    public String formatFilterValueForFlatpickr(String value, String timeFormat, String fieldType) {
        if (value == null || value.isEmpty()) return value;

        try {
            LocalDate currentDate = LocalDate.now();
            return switch (timeFormat) {
                case "time" -> formatTimeForFlatpickr(value, fieldType, currentDate);
                case "time_tz" -> formatTimeTzForFlatpickr(value, fieldType, currentDate);
                case "datetime" -> "LocalDateTime".equals(fieldType) ? LocalDateTime.parse(value, DATE_TIME_FORMATTER).format(DATE_TIME_FORMATTER) : value;
                case "datetime_tz" -> "OffsetDateTime".equals(fieldType) ? OffsetDateTime.parse(value).format(DATE_TIME_WITH_TZ_FORMATTER) : value;
                default -> value;
            };
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse time value for Flatpickr: {}, format: {}, type: {}", value, timeFormat, fieldType);
            return value;
        }
    }

    /**
     * Parses a UI-provided string value into a formatted string based on its type and format.
     *
     * @param value String value from UI.
     * @param timeFormat Desired format ("time", "time_tz", "datetime", "datetime_tz").
     * @param fieldType Type of the field.
     * @return Parsed and formatted string.
     */
    public String parseValueFromUI(String value, String timeFormat, String fieldType) {
        if (value == null || value.isEmpty()) return value;

        try {
            return switch (timeFormat) {
                case "time" -> formatTime(value, fieldType);
                case "time_tz" -> formatTimeWithTz(value, fieldType);
                case "datetime" -> "LocalDateTime".equals(fieldType) ? LocalDateTime.parse(value, DATE_TIME_FORMATTER).format(DATE_TIME_FORMATTER) : value;
                case "datetime_tz" -> "OffsetDateTime".equals(fieldType) ? OffsetDateTime.parse(value).format(DATE_TIME_WITH_TZ_FORMATTER) : value;
                default -> value;
            };
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse UI time value: {}, format: {}, type: {}", value, timeFormat, fieldType);
            return value;
        }
    }

    private void formatField(Map<String, Object> record, String fieldName, Object value, String fieldType) {
        switch (fieldType) {
            case "LocalTime" -> record.put(fieldName, formatValueForDisplay((LocalTime) value, "time"));
            case "OffsetTime" -> record.put(fieldName, formatValueForDisplay((OffsetTime) value, "time_tz"));
            case "LocalDateTime" -> record.put(fieldName, formatValueForDisplay((LocalDateTime) value, "datetime"));
            case "OffsetDateTime" -> record.put(fieldName, formatValueForDisplay((OffsetDateTime) value, "datetime_tz"));
            case "Instant" -> record.put(fieldName, formatValueForDisplay((Instant) value, "datetime_tz"));
            case "ZonedDateTime" -> record.put(fieldName, formatValueForDisplay((ZonedDateTime) value, "datetime_tz"));
        }
    }

    private String formatTime(String value, String fieldType) {
        if ("LocalTime".equals(fieldType) || "OffsetTime".equals(fieldType)) {
            return LocalTime.parse(value, TIME_FORMATTER).format(TIME_FORMATTER);
        }
        return value;
    }

    private String formatTimeWithTz(String value, String fieldType) {
        if ("OffsetTime".equals(fieldType)) {
            return OffsetTime.parse(value).format(TIME_WITH_TZ_FORMATTER);
        } else if ("LocalTime".equals(fieldType)) {
            return LocalTime.parse(value, TIME_FORMATTER).format(TIME_FORMATTER);
        }
        return value;
    }

    private String formatTimeForFlatpickr(String value, String fieldType, LocalDate currentDate) {
        if ("LocalTime".equals(fieldType) || "OffsetTime".equals(fieldType)) {
            LocalTime time = LocalTime.parse(value, TIME_FORMATTER);
            return LocalDateTime.of(currentDate, time).format(DATE_TIME_FORMATTER);
        }
        return value;
    }

    private String formatTimeTzForFlatpickr(String value, String fieldType, LocalDate currentDate) {
        if ("OffsetTime".equals(fieldType) || "LocalTime".equals(fieldType)) {
            OffsetTime offsetTime = parseOffsetTime(value);
            LocalDateTime dateTime = LocalDateTime.of(currentDate, offsetTime.toLocalTime());
            return dateTime.format(DATE_TIME_FORMATTER) + offsetTime.getOffset();
        }
        return value;
    }

    private OffsetTime parseOffsetTime(String value) {
        try {
            return OffsetTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid OffsetTime format: " + value);
        }
    }
}