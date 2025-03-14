package by.glebka.jpadmin.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for managing table settings in the admin interface.
 */
public class AdminConfig {
    private Map<Class<?>, TableConfig> tableConfigs = new HashMap<>();
    private boolean showAllTablesByDefault = true;

    /**
     * Registers a table configuration for a specific entity class.
     */
    public AdminConfig registerTable(Class<?> entityClass, TableConfig config) {
        tableConfigs.put(entityClass, config);
        this.showAllTablesByDefault = false;
        return this;
    }

    /**
     * Retrieves the table configuration for a given entity class.
     */
    public TableConfig getTableConfig(Class<?> entityClass) {
        return tableConfigs.get(entityClass);
    }

    public boolean isShowAllTablesByDefault() {
        return showAllTablesByDefault;
    }

    public Map<Class<?>, TableConfig> getRegisteredTables() {
        return new HashMap<>(tableConfigs);
    }
}