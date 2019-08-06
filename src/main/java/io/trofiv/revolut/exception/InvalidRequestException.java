package io.trofiv.revolut.exception;

/**
 * Should be raised when user request is invalid both in terms of JSON validity and schema validation
 */
public class InvalidRequestException extends GenericException {
    public InvalidRequestException(final String message) {
        super(message);
    }

    public InvalidRequestException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
