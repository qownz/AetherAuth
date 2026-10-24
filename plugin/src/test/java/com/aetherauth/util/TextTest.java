package com.aetherauth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TextTest {

    @AfterEach
    void resetHex() {
        Text.setHexSupported(false);
    }

    @Test
    void replacesPlaceholders() {
        assertEquals("Merhaba Ada, hoş geldin!",
                Text.plain(Text.apply("Merhaba {player}, hoş geldin!", "player", "Ada")));
    }

    @Test
    void stripsTagsAndKeepsPlainText() {
        assertEquals("AetherAuth hazır", Text.plain("<gradient:#22d3ee:#a855f7>AetherAuth</gradient> <bold>hazır</bold>"));
    }

    @Test
    void keepsUnknownTagsAsText() {
        assertEquals("a <notatag> b", Text.plain("a <notatag> b"));
    }

    @Test
    void rendersNamedColourAsLegacyCode() {
        String rendered = Text.legacy("<red>dur</red>");
        assertTrue(rendered.contains("\u00A7c"));
        assertTrue(rendered.contains("dur"));
    }

    @Test
    void rendersDecorations() {
        String rendered = Text.legacy("<bold>kalın</bold> <underlined>altı</underlined>");
        assertTrue(rendered.contains("\u00A7l"));
        assertTrue(rendered.contains("\u00A7n"));
    }

    @Test
    void downgradesHexColourToNearestLegacyColour() {
        Text.setHexSupported(false);
        String rendered = Text.legacy("<#ff0000>kırmızı</#ff0000>");
        assertTrue(rendered.contains("\u00A74"), rendered);
        assertFalse(rendered.contains("\u00A7x"));
    }

    @Test
    void keepsHexWhenTheServerSupportsIt() {
        Text.setHexSupported(true);
        String rendered = Text.legacy("<#12ab34>yeşil</#12ab34>");
        assertTrue(rendered.contains("\u00A7x\u00A71\u00A72\u00A7a\u00A7b\u00A73\u00A74"), rendered);
    }

    @Test
    void convertsAmpersandCodes() {
        String rendered = Text.legacy("&a&lAether&r &cAuth");
        assertTrue(rendered.contains("\u00A7a"));
        assertTrue(rendered.contains("\u00A7l"));
        assertTrue(rendered.contains("\u00A7c"));
        assertTrue(rendered.contains("Aether"));
    }

    @Test
    void producesMiniMessageForDialogs() {
        String mini = Text.mini("<gradient:#22d3ee:#a855f7>Merhaba</gradient>");
        assertTrue(mini.contains("<#"), mini);
        assertEquals("Merhaba", Text.plain("<gradient:#22d3ee:#a855f7>Merhaba</gradient>"));
        assertFalse(mini.contains("gradient"), mini);
    }

    @Test
    void escapesMiniMessageSpecialCharacters() {
        String mini = Text.mini("1 < 2 & 3 > 2");
        assertTrue(mini.contains("\\<"), mini);
    }

    @Test
    void gradientProducesMultipleColours() {
        String mini = Text.mini("<gradient:#000000:#ffffff>abc</gradient>");
        assertTrue(mini.split("<#").length >= 3, mini);
    }
}
