package com.aetherauth.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class CryptoEngineTest {

    private static final byte[] MASTER =
            "unit-test-master-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static CryptoEngine engine(String variant, int iterations) {
        return new CryptoEngine(MASTER, iterations, variant, 32);
    }

    @Test
    void hashesAndVerifiesWithGcm() throws Exception {
        CryptoEngine crypto = engine("GCM", 1000);
        String blob = crypto.hash("Doğru Şifre 42!");
        assertTrue(blob.startsWith("$aether$1$GCM$"));
        assertTrue(crypto.verify("Doğru Şifre 42!", blob).ok());
    }

    @Test
    void hashesAndVerifiesWithCbcHmac() throws Exception {
        CryptoEngine crypto = engine("CBC_HMAC", 1000);
        String blob = crypto.hash("Cbc-Mode-Password");
        assertTrue(blob.startsWith("$aether$1$CBC_HMAC$"));
        assertTrue(crypto.verify("Cbc-Mode-Password", blob).ok());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        CryptoEngine crypto = engine("GCM", 1000);
        String blob = crypto.hash("correct-horse");
        CryptoEngine.Result result = crypto.verify("correct-horsE", blob);
        assertFalse(result.ok());
        assertEquals("mismatch", result.reason());
    }

    @Test
    void rejectsTamperedCiphertextInGcm() throws Exception {
        CryptoEngine crypto = engine("GCM", 1000);
        assertFalse(crypto.verify("secret", tamper(crypto.hash("secret"), 8)).ok());
    }

    @Test
    void rejectsTamperedMacInCbc() throws Exception {
        CryptoEngine crypto = engine("CBC_HMAC", 1000);
        assertFalse(crypto.verify("secret", tamper(crypto.hash("secret"), 9)).ok());
    }

    @Test
    void differentAccountsUseDifferentSalts() throws Exception {
        CryptoEngine crypto = engine("GCM", 1000);
        assertNotEquals(crypto.hash("same-password"), crypto.hash("same-password"));
    }

    @Test
    void rehashIsRequestedWhenIterationsIncrease() throws Exception {
        String oldBlob = engine("GCM", 1000).hash("rehash-me");
        CryptoEngine.Result result = engine("GCM", 2000).verify("rehash-me", oldBlob);
        assertTrue(result.ok());
        assertTrue(result.needsRehash());
    }

    @Test
    void rehashIsRequestedWhenVariantChanges() throws Exception {
        String oldBlob = engine("CBC_HMAC", 1000).hash("variant-change");
        CryptoEngine.Result result = engine("GCM", 1000).verify("variant-change", oldBlob);
        assertTrue(result.ok());
        assertTrue(result.needsRehash());
    }

    @Test
    void malformedBlobsAreRejectedWithoutThrowing() {
        CryptoEngine crypto = engine("GCM", 1000);
        assertFalse(crypto.verify("x", null).ok());
        assertFalse(crypto.verify("x", "").ok());
        assertFalse(crypto.verify("x", "plain-text-password").ok());
        assertFalse(crypto.verify("x", "$aether$1$ROT13$1000$256$aaaa$bbbb$cccc").ok());
    }

    @Test
    void emailRoundTripsThroughBothModes() throws Exception {
        for (String variant : new String[]{"GCM", "CBC_HMAC"}) {
            CryptoEngine crypto = engine(variant, 1000);
            String stored = crypto.encryptEmail("Oyuncu@Example.COM");
            assertTrue(stored.startsWith("e1:" + variant + ":"));
            assertEquals("Oyuncu@Example.COM", crypto.decryptEmail(stored));
        }
    }

    @Test
    void legacyPlainEmailIsStillReadable() throws Exception {
        assertEquals("a@b.c", engine("GCM", 1000).decryptEmail("p:a@b.c"));
    }

    @Test
    void emailLookupIsNormalisedAndStable() {
        CryptoEngine crypto = engine("GCM", 1000);
        String first = crypto.emailLookup("  Player@Example.com ");
        assertEquals(first, crypto.emailLookup("player@example.com"));
        assertNotEquals(first, crypto.emailLookup("other@example.com"));
    }

    @Test
    void hidesMostOfTheAddress() {
        assertEquals("a***@example.com", CryptoEngine.maskEmail("alice@example.com"));
        assertEquals("-", CryptoEngine.maskEmail(""));
        assertNotNull(CryptoEngine.maskEmail("no-at-sign"));
    }

    /** Flips one byte inside the base64 field at {@code index} of a blob. */
    private static String tamper(String blob, int index) {
        String[] parts = blob.split("\\$", -1);
        byte[] raw = Base64.getUrlDecoder().decode(parts[index]);
        raw[0] ^= 0x01;
        parts[index] = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('$');
            }
            out.append(parts[i]);
        }
        return out.toString();
    }
}
