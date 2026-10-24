package com.aetherauth.auth;

public enum AuthState {
    /** Account lookup is running. */
    LOADING,
    /** No account exists; the player must register. */
    REGISTER,
    /** Account exists; the player must log in. */
    LOGIN,
    /** A password check or registration write is in flight. */
    PROCESSING,
    /** Fully authenticated. */
    AUTHENTICATED
}
