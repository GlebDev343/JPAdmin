package by.glebka.jpadmin.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * Global exception handler for the admin interface, managing various error scenarios and rendering error pages.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles cases where a record already exists during creation.
     */
    @ExceptionHandler(RecordAlreadyExistsException.class)
    public String handleRecordAlreadyExistsException(RecordAlreadyExistsException ex, Model model) {
        logger.error("Record already exists: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    /**
     * Handles validation errors, formatting them into a readable message.
     */
    @ExceptionHandler(ValidationException.class)
    public String handleValidationException(ValidationException ex, Model model) {
        logger.warn("Validation failed: {}", ex.getValidationErrors());
        String errorMessage = formatValidationErrors(ex.getValidationErrors());
        model.addAttribute("errorMessage", errorMessage);
        return "error";
    }

    /**
     * Handles invalid argument exceptions, typically due to bad user input.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex, Model model) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    /**
     * Handles illegal state exceptions, indicating an unexpected application state.
     */
    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalStateException(IllegalStateException ex, Model model) {
        logger.error("Illegal state: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    /**
     * Catch-all handler for unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Unexpected error: " + ex.getMessage());
        return "error";
    }

    private String formatValidationErrors(Map<String, String> validationErrors) {
        StringBuilder errorMessage = new StringBuilder("Validation failed: ");
        validationErrors.forEach((field, error) -> errorMessage.append(field).append(" - ").append(error).append("; "));
        return errorMessage.toString();
    }
}