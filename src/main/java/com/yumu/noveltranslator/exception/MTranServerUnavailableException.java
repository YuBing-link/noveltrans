package com.yumu.noveltranslator.exception;

/**
 * Thrown when the MTranServer circuit breaker is open or the service is unavailable.
 * Enables upstream round-robin logic (UserLevelThrottledTranslationClient) to switch to the Python fallback service.
 */
public class MTranServerUnavailableException extends RuntimeException {

    public MTranServerUnavailableException(String message) {
        super(message);
    }

    public MTranServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
