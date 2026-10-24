package com.aetherauth.crypto;

/**
 * Checked exception for the cryptographic layer, so callers can degrade
 * gracefully instead of leaking stack traces to players.
 */
public class CryptoException extends Exception {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
