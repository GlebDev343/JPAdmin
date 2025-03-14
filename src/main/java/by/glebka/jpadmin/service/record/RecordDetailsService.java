package by.glebka.jpadmin.service.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing record details, validation, and persistence operations.
 */
@Service
public class RecordDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(RecordDetailsService.class);
    private static final String DEFAULT_JSON_RESPONSE = "{}";

    @Autowired
    private RecordDetailsFetcher recordDetailsFetcher;

    @Autowired
    private RecordValidator recordValidator;

    @Autowired
    private RecordPersister recordPersister;

    @Autowired
    private RecordListService recordListService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Retrieves detailed information about a specific record with strict table checking option.
     *
     * @param tableName       The name of the table.
     * @param id              The ID of the record.
     * @param strictTableCheck Whether to enforce strict table registration checks.
     * @return A map containing record details.
     */
    public Map<String, Object> getRecordDetails(String tableName, Long id, boolean strictTableCheck) {
        return recordDetailsFetcher.getRecordDetails(tableName, id, strictTableCheck);
    }

    /**
     * Retrieves detailed information about a specific record with default table checking.
     *
     * @param tableName The name of the table.
     * @param id        The ID of the record.
     * @return A map containing record details.
     */
    public Map<String, Object> getRecordDetails(String tableName, Long id) {
        return recordDetailsFetcher.getRecordDetails(tableName, id);
    }

    /**
     * Retrieves metadata for an empty record for a given table.
     *
     * @param tableName The name of the table.
     * @return A map containing empty record metadata.
     */
    public Map<String, Object> getEmptyRecordData(String tableName) {
        return recordDetailsFetcher.getEmptyRecordData(tableName);
    }

    /**
     * Updates an existing record in the specified table.
     *
     * @param tableName    The name of the table.
     * @param id           The ID of the record to update.
     * @param editedFields The fields with updated values.
     * @return True if the update was successful, false otherwise.
     */
    @Transactional
    public boolean updateRecord(String tableName, Long id, Map<String, String> editedFields) {
        return recordPersister.updateRecord(tableName, id, editedFields);
    }

    /**
     * Creates a new record in the specified table.
     *
     * @param tableName The name of the table.
     * @param newFields The fields with values for the new record.
     * @return The ID of the newly created record, or null if creation failed.
     */
    @Transactional
    public Long createRecord(String tableName, Map<String, String> newFields) {
        return recordPersister.createRecord(tableName, newFields);
    }

    /**
     * Validates an existing record for updates.
     *
     * @param tableName    The name of the table.
     * @param id           The ID of the record to validate.
     * @param editedFields The fields with updated values.
     */
    @Transactional
    public void validateRecord(String tableName, Long id, Map<String, String> editedFields) {
        recordValidator.validateRecord(tableName, id, editedFields);
    }

    /**
     * Validates a new record before creation.
     *
     * @param tableName The name of the table.
     * @param newFields The fields with values for the new record.
     */
    @Transactional
    public void validateNewRecord(String tableName, Map<String, String> newFields) {
        recordValidator.validateNewRecord(tableName, newFields);
    }

    /**
     * Retrieves the field types for a given table.
     *
     * @param tableName The name of the table.
     * @return A map of field names to their types.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getFieldTypes(String tableName) {
        Map<String, Object> metadata = recordListService.getFieldTypesMetadata(tableName);
        return (Map<String, String>) metadata.get("fieldTypes");
    }

    /**
     * Builds a list of filters based on provided parameters.
     *
     * @param filterFields            The fields to filter on.
     * @param filterOperations        The operations to apply.
     * @param filterValues            The values for the filters.
     * @param filterTimeFormats       The time formats for temporal fields.
     * @param allowNulls              Whether null values are allowed.
     * @param treatEmptyAsEmptyStrings Whether to treat empty values as empty strings.
     * @param tableName               The name of the table.
     * @return A list of filter maps.
     */
    public List<Map<String, String>> buildFilters(List<String> filterFields, List<String> filterOperations,
                                                  List<String> filterValues, List<String> filterTimeFormats,
                                                  List<String> allowNulls, List<String> treatEmptyAsEmptyStrings,
                                                  String tableName) {
        logger.debug("Building filters for table: {}", tableName);
        Map<String, String> fieldTypes = getFieldTypes(tableName);
        return recordListService.buildFilters(filterFields, filterOperations, filterValues, filterTimeFormats,
                allowNulls, treatEmptyAsEmptyStrings, tableName, fieldTypes);
    }

    /**
     * Retrieves paginated and filtered records from a table.
     *
     * @param tableName  The name of the table.
     * @param filters    The list of filters to apply.
     * @param page       The page number (zero-based).
     * @param size       The number of records per page.
     * @param sortField  The field to sort by.
     * @param sortOrder  The sort order ("ASC" or "DESC").
     * @param nullsFirst Whether to place null values first in sorting.
     * @return A map containing the records and metadata.
     */
    public Map<String, Object> getTableRecords(String tableName, List<Map<String, String>> filters, int page, int size,
                                               String sortField, String sortOrder, boolean nullsFirst) {
        return recordListService.getTableRecords(tableName, filters, page, size, sortField, sortOrder, nullsFirst);
    }

    /**
     * Serializes field types for a table into JSON format.
     *
     * @param tableName The name of the table.
     * @return A JSON string representing the field types, or "{}" if serialization fails.
     */
    public String getFieldTypesJson(String tableName) {
        Map<String, String> fieldTypes = getFieldTypes(tableName);
        try {
            return objectMapper.writeValueAsString(fieldTypes);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize fieldTypes to JSON for table {}: {}", tableName, e.getMessage());
            return DEFAULT_JSON_RESPONSE;
        }
    }
}