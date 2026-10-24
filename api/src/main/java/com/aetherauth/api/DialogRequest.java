package com.aetherauth.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable description of an authentication dialog.
 *
 * <p>Every textual value is a MiniMessage template that has already been
 * processed by the plugin (placeholders replaced, legacy colour codes
 * normalised). The bridge only has to deserialize it.</p>
 */
public final class DialogRequest {

    private final String title;
    private final List<String> body;
    private final boolean emailField;
    private final boolean passwordField;
    private final boolean confirmField;
    private final boolean valueField;
    private final boolean canClose;
    private final String emailLabel;
    private final String emailPlaceholder;
    private final String passwordLabel;
    private final String passwordPlaceholder;
    private final String confirmLabel;
    private final String confirmPlaceholder;
    private final String valueLabel;
    private final String submitLabel;
    private final String cancelLabel;
    private final String initialEmail;
    private final String initialPassword;
    private final String initialValue;
    private final int width;
    private final int buttonWidth;
    private final int maxPasswordLength;
    private final int maxEmailLength;
    private final int maxValueLength;

    private DialogRequest(Builder builder) {
        this.title = builder.title == null ? "" : builder.title;
        this.body = Collections.unmodifiableList(new ArrayList<String>(builder.body));
        this.emailField = builder.emailField;
        this.passwordField = builder.passwordField;
        this.confirmField = builder.confirmField;
        this.valueField = builder.valueField;
        this.canClose = builder.canClose;
        this.emailLabel = builder.emailLabel;
        this.emailPlaceholder = builder.emailPlaceholder;
        this.passwordLabel = builder.passwordLabel;
        this.passwordPlaceholder = builder.passwordPlaceholder;
        this.confirmLabel = builder.confirmLabel;
        this.confirmPlaceholder = builder.confirmPlaceholder;
        this.valueLabel = builder.valueLabel;
        this.submitLabel = builder.submitLabel;
        this.cancelLabel = builder.cancelLabel;
        this.initialEmail = builder.initialEmail == null ? "" : builder.initialEmail;
        this.initialPassword = builder.initialPassword == null ? "" : builder.initialPassword;
        this.initialValue = builder.initialValue == null ? "" : builder.initialValue;
        this.width = builder.width;
        this.buttonWidth = builder.buttonWidth;
        this.maxPasswordLength = builder.maxPasswordLength;
        this.maxEmailLength = builder.maxEmailLength;
        this.maxValueLength = builder.maxValueLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String title() { return title; }

    public List<String> body() { return body; }

    public boolean emailField() { return emailField; }

    public boolean passwordField() { return passwordField; }

    public boolean confirmField() { return confirmField; }

    public boolean valueField() { return valueField; }

    public boolean canClose() { return canClose; }

    public String emailLabel() { return emailLabel; }

    public String emailPlaceholder() { return emailPlaceholder; }

    public String passwordLabel() { return passwordLabel; }

    public String passwordPlaceholder() { return passwordPlaceholder; }

    public String confirmLabel() { return confirmLabel; }

    public String confirmPlaceholder() { return confirmPlaceholder; }

    public String valueLabel() { return valueLabel; }

    public String submitLabel() { return submitLabel; }

    public String cancelLabel() { return cancelLabel; }

    public String initialEmail() { return initialEmail; }

    public String initialPassword() { return initialPassword; }

    public String initialValue() { return initialValue; }

    public int width() { return width; }

    public int buttonWidth() { return buttonWidth; }

    public int maxPasswordLength() { return maxPasswordLength; }

    public int maxEmailLength() { return maxEmailLength; }

    public int maxValueLength() { return maxValueLength; }

    public static final class Builder {

        private String title;
        private final List<String> body = new ArrayList<String>();
        private boolean emailField = true;
        private boolean passwordField = true;
        private boolean confirmField = true;
        private boolean valueField = false;
        private boolean canClose;
        private String emailLabel = "Mail";
        private String emailPlaceholder = "";
        private String passwordLabel = "Şifre";
        private String passwordPlaceholder = "";
        private String confirmLabel = "Şifre (tekrar)";
        private String confirmPlaceholder = "";
        private String valueLabel = "Yeni değer";
        private String submitLabel = "Onayla";
        private String cancelLabel = "Vazgeç";
        private String initialEmail = "";
        private String initialPassword = "";
        private String initialValue = "";
        private int width = 320;
        private int buttonWidth = 150;
        private int maxPasswordLength = 64;
        private int maxEmailLength = 254;
        private int maxValueLength = 254;

        public Builder title(String title) { this.title = title; return this; }

        public Builder bodyLine(String line) { this.body.add(line); return this; }

        public Builder body(List<String> lines) {
            this.body.clear();
            if (lines != null) {
                this.body.addAll(lines);
            }
            return this;
        }

        public Builder emailField(boolean value) { this.emailField = value; return this; }

        public Builder passwordField(boolean value) { this.passwordField = value; return this; }

        public Builder confirmField(boolean value) { this.confirmField = value; return this; }

        public Builder valueField(boolean value) { this.valueField = value; return this; }

        public Builder canClose(boolean value) { this.canClose = value; return this; }

        public Builder emailLabel(String value) { this.emailLabel = value; return this; }

        public Builder emailPlaceholder(String value) { this.emailPlaceholder = value; return this; }

        public Builder passwordLabel(String value) { this.passwordLabel = value; return this; }

        public Builder passwordPlaceholder(String value) { this.passwordPlaceholder = value; return this; }

        public Builder confirmLabel(String value) { this.confirmLabel = value; return this; }

        public Builder confirmPlaceholder(String value) { this.confirmPlaceholder = value; return this; }

        public Builder valueLabel(String value) { this.valueLabel = value; return this; }

        public Builder submitLabel(String value) { this.submitLabel = value; return this; }

        public Builder cancelLabel(String value) { this.cancelLabel = value; return this; }

        public Builder initialEmail(String value) { this.initialEmail = value; return this; }

        public Builder initialPassword(String value) { this.initialPassword = value; return this; }

        public Builder initialValue(String value) { this.initialValue = value; return this; }

        public Builder width(int value) { this.width = value; return this; }

        public Builder buttonWidth(int value) { this.buttonWidth = value; return this; }

        public Builder maxPasswordLength(int value) { this.maxPasswordLength = value; return this; }

        public Builder maxEmailLength(int value) { this.maxEmailLength = value; return this; }

        public Builder maxValueLength(int value) { this.maxValueLength = value; return this; }

        public DialogRequest build() {
            return new DialogRequest(this);
        }
    }
}
