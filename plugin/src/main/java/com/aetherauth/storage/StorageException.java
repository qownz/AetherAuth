package com.aetherauth.storage;

/**
 * Unchecked wrapper for database failures.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
