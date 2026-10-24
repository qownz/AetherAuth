package com.aetherauth.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Practical e-mail validation (not full RFC 5322 — nobody wants that in a
 * Minecraft plugin). Rejects the mistakes players actually make.
 */
public final class EmailValidator {

    private static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9._%+-]{0,62}[A-Za-z0-9])?@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,24}$"
    );

    private EmailValidator() {
    }

    public static boolean isValid(String email) {
        if (email == null) {
            return false;
        }
        String value = email.trim();
        if (value.length() < 5 || value.length() > 254) {
            return false;
        }
        if (value.contains("..") || value.startsWith(".") || value.endsWith(".")) {
            return false;
        }
        return PATTERN.matcher(value).matches();
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
