package com.aetherauth.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailValidatorTest {

    @Test
    void acceptsOrdinaryAddresses() {
        assertTrue(EmailValidator.isValid("ada@example.com"));
        assertTrue(EmailValidator.isValid("ada.lovelace+mc@sub.example.co.uk"));
        assertTrue(EmailValidator.isValid("a_b-c@example.io"));
    }

    @Test
    void rejectsMalformedAddresses() {
        assertFalse(EmailValidator.isValid(null));
        assertFalse(EmailValidator.isValid(""));
        assertFalse(EmailValidator.isValid("ada"));
        assertFalse(EmailValidator.isValid("@example.com"));
        assertFalse(EmailValidator.isValid("ada@"));
        assertFalse(EmailValidator.isValid("ada@@example.com"));
        assertFalse(EmailValidator.isValid("ada example@example.com"));
        assertFalse(EmailValidator.isValid("ada@example"));
    }

    @Test
    void normalisesCaseAndWhitespace() {
        assertEquals("ada@example.com", EmailValidator.normalize("  Ada@Example.COM "));
    }
}
