package io.trofiv.revolut.exception;

/**
 * Should be raised when user requested operation over account which doesn't exist
 */
public class NoSuchAccountException extends GenericException {
    public NoSuchAccountException(final String message) {
        super(message);
    }
}
