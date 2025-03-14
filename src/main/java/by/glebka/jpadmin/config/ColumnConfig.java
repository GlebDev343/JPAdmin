package by.glebka.jpadmin.config;

import java.util.function.Function;

/**
 * Configuration class for defining column settings in a table.
 */
public class ColumnConfig {
    private final String fieldName;
    private final String displayName;
    private final Function<Object, Object> computedValue; // null for regular columns

    public ColumnConfig(String fieldName, String displayName) {
        this(fieldName, displayName, null);
    }

    public ColumnConfig(String fieldName, String displayName, Function<Object, Object> computedValue) {
        this.fieldName = fieldName;
        this.displayName = displayName != null ? displayName : fieldName;
        this.computedValue = computedValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Function<Object, Object> getComputedValue() {
        return computedValue;
    }

    public boolean isComputed() {
        return computedValue != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnConfig that = (ColumnConfig) o;
        return fieldName.equals(that.fieldName);
    }

    @Override
    public int hashCode() {
        return fieldName.hashCode();
    }
}