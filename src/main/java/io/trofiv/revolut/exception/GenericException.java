package io.trofiv.revolut.exception;

/**
 * Base class for all application-related business exceptions
 */
public class GenericException extends Exception {
    public GenericException(final String message) {
        super(message);
    }

    public GenericException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
