package by.glebka.jpadmin.controller;

import by.glebka.jpadmin.service.EntityTableService;
import by.glebka.jpadmin.service.record.RecordDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for handling admin-related web requests, such as displaying tables, records, and editing data.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private EntityTableService entityTableService;

    @Autowired
    private RecordDetailsService recordDetailsService;

    /**
     * Displays the list of available entity tables.
     */
    @GetMapping
    public String showTables(Model model) {
        model.addAttribute("tables", entityTableService.getEntityTables());
        return "admin";
    }

    /**
     * Displays records of a specific table with optional filtering, sorting, and pagination.
     */
    @GetMapping("/table/{tableName}")
    public String showTableRecords(
            @PathVariable("tableName") String tableName,
            @RequestParam(value = "filterField", required = false) List<String> filterFields,
            @RequestParam(value = "filterOperation", required = false) List<String> filterOperations,
            @RequestParam(value = "filterValue", required = false) List<String> filterValues,
            @RequestParam(value = "filterTimeFormat", required = false) List<String> filterTimeFormats,
            @RequestParam(value = "allowNull", required = false) List<String> allowNulls,
            @RequestParam(value = "treatEmptyAsEmptyString", required = false) List<String> treatEmptyAsEmptyStrings,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortOrder", required = false) String sortOrder,
            @RequestParam(value = "nullsFirst", required = false) Boolean nullsFirst,
            Model model) {
        List<Map<String, String>> filters = recordDetailsService.buildFilters(filterFields, filterOperations, filterValues,
                filterTimeFormats, allowNulls, treatEmptyAsEmptyStrings, tableName);
        Map<String, Object> tableData = recordDetailsService.getTableRecords(tableName, filters, page, size, sortField,
                sortOrder, nullsFirst != null ? nullsFirst : false);

        model.addAttribute("fieldTypesJson", recordDetailsService.getFieldTypesJson(tableName));
        model.addAllAttributes(tableData);
        model.addAttribute("filters", filters);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        return "table-records";
    }

    /**
     * Displays details of a specific record in a table.
     */
    @GetMapping("/table/{tableName}/{id}")
    public String showRecordDetails(
            @PathVariable("tableName") String tableName,
            @PathVariable("id") Long id,
            Model model) {
        Map<String, Object> recordData = recordDetailsService.getRecordDetails(tableName, id);
        if (recordData == null || recordData.get("record") == null) {
            logger.warn("Record not found for table {} with id {}", tableName, id);
            throw new IllegalArgumentException("Record not found for table " + tableName + " with id " + id);
        }
        model.addAllAttributes(recordData);
        return "record-details";
    }

    /**
     * Displays the edit form for a specific record.
     */
    @GetMapping("/table/{tableName}/{id}/edit")
    public String showEditRecordForm(
            @PathVariable("tableName") String tableName,
            @PathVariable("id") Long id,
            Model model) {
        Map<String, Object> recordData = recordDetailsService.getRecordDetails(tableName, id);
        if (recordData == null) {
            logger.warn("Record not found for table {} with id {}", tableName, id);
            throw new IllegalArgumentException("Record not found for table " + tableName + " with id " + id);
        }
        logger.debug("Record data for edit form: {}", recordData);
        model.addAllAttributes(recordData);
        return "record-edit";
    }

    /**
     * Handles the submission of edited record data.
     */
    @PostMapping("/table/{tableName}/{id}/edit")
    public String saveEditedRecord(
            @PathVariable("tableName") String tableName,
            @PathVariable("id") Long id,
            @RequestParam Map<String, String> editedFields,
            RedirectAttributes redirectAttributes) {
        recordDetailsService.validateRecord(tableName, id, editedFields);
        boolean success = recordDetailsService.updateRecord(tableName, id, editedFields);
        if (!success) {
            throw new IllegalStateException("Failed to update record for table " + tableName + " with id " + id);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Record updated successfully!");
        return "redirect:/admin/table/" + tableName + "/" + id;
    }

    /**
     * Displays the form for creating a new record in a table.
     */
    @GetMapping("/table/{tableName}/create")
    public String showCreateRecordForm(
            @PathVariable("tableName") String tableName,
            Model model) {
        Map<String, Object> recordData = recordDetailsService.getEmptyRecordData(tableName);
        if (recordData == null) {
            throw new IllegalArgumentException("Table configuration not found for " + tableName);
        }
        model.addAllAttributes(recordData);
        model.addAttribute("tableName", tableName);
        model.addAttribute("displayNames", recordData.get("displayNames"));
        model.addAttribute("validationErrors", null);
        return "record-create";
    }

    /**
     * Handles the creation of a new record in a table.
     */
    @PostMapping("/table/{tableName}/create")
    public String createRecord(
            @PathVariable("tableName") String tableName,
            @RequestParam Map<String, String> newFields,
            RedirectAttributes redirectAttributes) {
        recordDetailsService.validateNewRecord(tableName, newFields);
        Long newId = recordDetailsService.createRecord(tableName, newFields);
        if (newId == null) {
            throw new IllegalStateException("Failed to create record for table " + tableName);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Record created successfully!");
        return "redirect:/admin/table/" + tableName + "/" + newId;
    }
}