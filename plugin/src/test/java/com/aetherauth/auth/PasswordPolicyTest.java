package com.aetherauth.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aetherauth.config.ConfigData;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static PasswordPolicy policy() {
        ConfigData.Auth config = new ConfigData.Auth();
        config.minPasswordLength = 8;
        config.blockCommonPasswords = true;
        return new PasswordPolicy(config);
    }

    @Test
    void acceptsAReasonablePassword() {
        assertNull(policy().validate("Kartal-42-Ucus", "Ada", "ada@example.com"));
    }

    @Test
    void rejectsEmptyAndShortPasswords() {
        assertNotNull(policy().validate("", "Ada", ""));
        assertNotNull(policy().validate("kisa", "Ada", ""));
    }

    @Test
    void rejectsSurroundingWhitespaceAndInnerSpaces() {
        assertNotNull(policy().validate(" sifre1234", "Ada", ""));
        assertNotNull(policy().validate("sifre 1234", "Ada", ""));
    }

    @Test
    void rejectsCommonPasswordsCaseInsensitively() {
        assertNotNull(policy().validate("PASSWORD", "Ada", ""));
        assertNotNull(policy().validate("12345678", "Ada", ""));
    }

    @Test
    void rejectsTheUsernameAsPassword() {
        assertNotNull(policy().validate("AetherPlayer", "aetherplayer", ""));
    }

    @Test
    void rejectsTheEmailLocalPartAsPassword() {
        assertNotNull(policy().validate("localpart123", "Ada", "localpart123@example.com"));
    }

    @Test
    void enforcesConfiguredCharacterClasses() {
        ConfigData.Auth config = new ConfigData.Auth();
        config.minPasswordLength = 6;
        config.blockCommonPasswords = false;
        config.requireDigit = true;
        config.requireUppercase = true;
        config.requireSpecial = true;
        PasswordPolicy policy = new PasswordPolicy(config);

        assertNotNull(policy.validate("kucukharf", "Ada", ""));
        assertNotNull(policy.validate("Kucukharf1", "Ada", ""));
        assertNull(policy.validate("Kucukharf1!", "Ada", ""));
    }

    @Test
    void rejectsOverlyLongPasswords() {
        ConfigData.Auth config = new ConfigData.Auth();
        config.maxPasswordLength = 20;
        PasswordPolicy policy = new PasswordPolicy(config);
        assertEquals("en fazla 20 karakter olabilir",
                policy.validate("a-very-long-password-that-exceeds", "Ada", ""));
    }
}
