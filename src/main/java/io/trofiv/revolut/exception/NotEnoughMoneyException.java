package io.trofiv.revolut.exception;

/**
 * Should be raised when user requested credit-related operation over account which doesn't have sufficient funds
 */
public class NotEnoughMoneyException extends GenericException {
    public NotEnoughMoneyException(final String message) {
        super(message);
    }
}
