package com.aetherauth.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aetherauth.config.ConfigData;
import com.aetherauth.util.Json;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void newerPatchIsDetected() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    void missingPartsCountAsZero() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.0.0.1", "1.0.0"));
    }

    @Test
    void prefixesAndSuffixesAreIgnored() {
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.9"));
        assertTrue(UpdateChecker.isNewer("1.2.0-SNAPSHOT", "1.1.9"));
    }

    @Test
    void updateCheckConfigKeysAreMapped() {
        ConfigData data = Json.gson().fromJson(
                "{\"update-check\":{\"enabled\":false,"
                        + "\"version-url\":\"https://example.test/version.txt\","
                        + "\"download-url\":\"https://example.test/download\"}}",
                ConfigData.class);
        assertFalse(data.update.enabled);
        assertEquals("https://example.test/version.txt", data.update.versionUrl);
        assertEquals("https://example.test/download", data.update.downloadUrl);
    }
}
