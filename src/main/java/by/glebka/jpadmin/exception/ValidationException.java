package by.glebka.jpadmin.exception;

import java.util.Map;

/**
 * Exception thrown when validation of a record fails, containing a map of field-specific errors.
 */
public class ValidationException extends RuntimeException {
    private final Map<String, String> validationErrors;

    public ValidationException(Map<String, String> validationErrors) {
        super("Validation failed");
        this.validationErrors = validationErrors;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}