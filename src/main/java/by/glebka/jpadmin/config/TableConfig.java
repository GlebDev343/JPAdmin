package by.glebka.jpadmin.config;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Configuration class for defining table display settings and column configurations.
 */
public class TableConfig {
    private final Class<?> entityClass;
    private String displayName;
    private Set<ColumnConfig> columns = null;
    private String defaultSortField;
    private String defaultSortOrder;
    private boolean defaultNullsFirst;

    public TableConfig(Class<?> entityClass) {
        this.entityClass = entityClass;
        this.displayName = entityClass.getSimpleName();
        this.defaultSortOrder = "DESC"; // Default value
        this.defaultNullsFirst = false; // Default value
    }

    /**
     * Sets the display name for the table.
     */
    public TableConfig setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Adds a simple column to the table configuration.
     */
    public TableConfig addColumn(String fieldName, String displayName) {
        if (columns == null) {
            columns = new LinkedHashSet<>();
        }
        columns.add(new ColumnConfig(fieldName, displayName));
        return this;
    }

    /**
     * Adds a computed column with a custom value function to the table configuration.
     */
    public TableConfig addComputedColumn(String fieldName, String displayName, Function<Object, Object> computedValue) {
        if (columns == null) {
            columns = new LinkedHashSet<>();
        }
        columns.add(new ColumnConfig(fieldName, displayName, computedValue));
        return this;
    }

    public TableConfig setDefaultSortField(String defaultSortField) {
        this.defaultSortField = defaultSortField;
        return this;
    }

    public TableConfig setDefaultSortOrder(String defaultSortOrder) {
        this.defaultSortOrder = defaultSortOrder;
        return this;
    }

    public TableConfig setDefaultNullsFirst(boolean defaultNullsFirst) {
        this.defaultNullsFirst = defaultNullsFirst;
        return this;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<ColumnConfig> getColumns() {
        return columns != null ? new LinkedHashSet<>(columns) : null;
    }

    public String getDefaultSortField() {
        return defaultSortField;
    }

    public String getDefaultSortOrder() {
        return defaultSortOrder;
    }

    public boolean isDefaultNullsFirst() {
        return defaultNullsFirst;
    }
}