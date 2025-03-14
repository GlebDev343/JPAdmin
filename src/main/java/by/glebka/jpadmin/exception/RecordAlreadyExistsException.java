package by.glebka.jpadmin.exception;

/**
 * Exception thrown when attempting to create a record that already exists.
 */
public class RecordAlreadyExistsException extends RuntimeException {
    public RecordAlreadyExistsException(String message) {
        super(message);
    }
}