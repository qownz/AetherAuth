package com.aetherauth.storage;

/**
 * Thrown when a unique column (player name or e-mail) already exists.
 */
public class DuplicateEntryException extends Exception {

    private final String field;

    public DuplicateEntryException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
