package com.aetherauth.api;

/**
 * Result of a dialog shown to a player.
 *
 * <p>All values are raw strings exactly as typed by the player. Validation is
 * performed by the plugin, never by the dialog bridge.</p>
 */
public final class DialogOutcome {

    public enum Kind {
        /** The player pressed the primary (submit) button. */
        SUBMIT,
        /** The player dismissed the dialog or pressed the secondary button. */
        CANCEL
    }

    private final Kind kind;
    private final String email;
    private final String password;
    private final String confirmPassword;
    private final String value;

    private DialogOutcome(Kind kind, String email, String password, String confirmPassword, String value) {
        this.kind = kind;
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.value = value;
    }

    public static DialogOutcome submit(String email, String password, String confirmPassword) {
        return submit(email, password, confirmPassword, "");
    }

    public static DialogOutcome submit(String email, String password, String confirmPassword, String value) {
        return new DialogOutcome(Kind.SUBMIT, safe(email), safe(password), safe(confirmPassword), safe(value));
    }

    public static DialogOutcome cancel() {
        return new DialogOutcome(Kind.CANCEL, null, null, null, null);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isSubmit() {
        return kind == Kind.SUBMIT;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public String confirmPassword() {
        return confirmPassword;
    }

    /** Value of the generic change field, empty when the dialog has none. */
    public String value() {
        return value;
    }
}
