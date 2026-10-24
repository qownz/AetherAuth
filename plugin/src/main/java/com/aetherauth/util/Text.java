package com.aetherauth.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, dependency free text engine.
 *
 * <p>It understands a practical subset of MiniMessage ({@code <red>},
 * {@code <#rrggbb>}, {@code <gradient:#aabbcc:#ddeeff>}, decorations, reset,
 * newline) as well as classic ampersand codes. Templates are rendered either
 * to a legacy string (for chat, titles and action bars on every Minecraft
 * version) or back to MiniMessage (for Paper's dialog API).</p>
 */
public final class Text {

    private static final Map<String, Integer> NAMED = new LinkedHashMap<String, Integer>();
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static volatile boolean hexSupported = false;

    static {
        NAMED.put("black", 0x000000);
        NAMED.put("dark_blue", 0x0000AA);
        NAMED.put("dark_green", 0x00AA00);
        NAMED.put("dark_aqua", 0x00AAAA);
        NAMED.put("dark_red", 0xAA0000);
        NAMED.put("dark_purple", 0xAA00AA);
        NAMED.put("gold", 0xFFAA00);
        NAMED.put("gray", 0xAAAAAA);
        NAMED.put("grey", 0xAAAAAA);
        NAMED.put("dark_gray", 0x555555);
        NAMED.put("dark_grey", 0x555555);
        NAMED.put("blue", 0x5555FF);
        NAMED.put("green", 0x55FF55);
        NAMED.put("aqua", 0x55FFFF);
        NAMED.put("red", 0xFF5555);
        NAMED.put("light_purple", 0xFF55FF);
        NAMED.put("yellow", 0xFFFF55);
        NAMED.put("white", 0xFFFFFF);
    }

    private Text() {
    }

    public static void setHexSupported(boolean supported) {
        hexSupported = supported;
    }

    public static boolean isHexSupported() {
        return hexSupported;
    }

    /** Replaces {key} placeholders. */
    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty() || input.indexOf('{') < 0) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }

    public static String apply(String input, Object... pairs) {
        if (pairs == null || pairs.length < 2) {
            return input == null ? "" : input;
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return apply(input, map);
    }

    /** Renders a template to a legacy section-sign string. */
    public static String legacy(String template, Map<String, String> placeholders) {
        return LegacyRenderer.render(parse(apply(template, placeholders)), hexSupported);
    }

    public static String legacy(String template, Object... pairs) {
        return legacy(template, toMap(pairs));
    }

    /** Renders a template to MiniMessage (used by the Paper dialog bridge). */
    public static String mini(String template, Map<String, String> placeholders) {
        return MiniRenderer.render(parse(apply(template, placeholders)));
    }

    public static String mini(String template, Object... pairs) {
        return mini(template, toMap(pairs));
    }

    /** Removes every formatting tag and colour code from a template. */
    public static String plain(String template) {
        List<Segment> segments = parse(template == null ? "" : template);
        StringBuilder out = new StringBuilder();
        for (Segment segment : segments) {
            out.append(segment.text);
        }
        return out.toString();
    }

    /** Turns {@code &} codes into MiniMessage tags. */
    public static String normalize(String input) {
        return mini(input);
    }

    private static Map<String, String> toMap(Object... pairs) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                map.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
            }
        }
        return map;
    }

    static List<Segment> parse(String input) {
        List<Segment> out = new ArrayList<Segment>();
        if (input == null || input.isEmpty()) {
            return out;
        }
        String normalized = convertAmpersand(input);
        Style style = new Style();
        ArrayList<Style> styles = new ArrayList<Style>();
        ArrayList<String> names = new ArrayList<String>();
        int i = 0;
        int length = normalized.length();
        StringBuilder chunk = new StringBuilder();

        while (i < length) {
            char c = normalized.charAt(i);
            if (c == '\\' && i + 1 < length) {
                chunk.append(normalized.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c != '<') {
                chunk.append(c);
                i++;
                continue;
            }
            int end = normalized.indexOf('>', i + 1);
            if (end < 0) {
                chunk.append(c);
                i++;
                continue;
            }
            String tag = normalized.substring(i + 1, end).trim();
            if (tag.isEmpty() || tag.indexOf(' ') == 0) {
                chunk.append(c);
                i++;
                continue;
            }
            flush(out, chunk, style);

            if (tag.startsWith("/")) {
                String name = tag.substring(1).toLowerCase(Locale.ROOT);
                style = close(styles, names, name, style);
                i = end + 1;
                continue;
            }

            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.equals("reset")) {
                styles.clear();
                names.clear();
                style = new Style();
                i = end + 1;
                continue;
            }
            if (lower.equals("newline") || lower.equals("br")) {
                chunk.append('\n');
                i = end + 1;
                continue;
            }
            if (lower.startsWith("gradient:")) {
                int close = findGradientEnd(normalized, end + 1);
                if (close >= 0) {
                    String inner = normalized.substring(end + 1, close);
                    String[] parts = splitGradient(lower.substring("gradient:".length()));
                    int from = parseColor(parts[0], 0xFFFFFF);
                    int to = parseColor(parts.length > 1 ? parts[1] : parts[0], from);
                    appendGradient(out, inner, style, from, to);
                    i = close + "</gradient>".length();
                    continue;
                }
                i = end + 1;
                continue;
            }

            Style next = style.copy();
            boolean known = true;
            if (lower.startsWith("color:")) {
                next.color = parseColor(tag.substring(6), next.color);
            } else if (lower.startsWith("#") && isHex(lower.substring(1))) {
                next.color = parseColor(lower, next.color);
            } else if (NAMED.containsKey(lower)) {
                next.color = NAMED.get(lower);
            } else if (lower.equals("bold") || lower.equals("b")) {
                next.bold = true;
            } else if (lower.equals("italic") || lower.equals("i") || lower.equals("em")) {
                next.italic = true;
            } else if (lower.equals("underlined") || lower.equals("u")) {
                next.underlined = true;
            } else if (lower.equals("strikethrough") || lower.equals("st")) {
                next.strikethrough = true;
            } else if (lower.equals("obfuscated") || lower.equals("obf")) {
                next.obfuscated = true;
            } else {
                known = false;
            }

            if (known) {
                styles.add(style);
                names.add(lower);
                style = next;
            } else {
                // Unknown tags are kept as literal text instead of being lost.
                chunk.append('<').append(tag).append('>');
            }
            i = end + 1;
        }
        flush(out, chunk, style);
        return out;
    }

    private static Style close(List<Style> styles, List<String> names, String name, Style fallback) {
        if (names.isEmpty()) {
            return fallback;
        }
        for (int index = names.size() - 1; index >= 0; index--) {
            String candidate = names.get(index);
            if (candidate.equals(name) || family(candidate).equals(family(name))) {
                Style restored = styles.get(index);
                while (names.size() > index) {
                    names.remove(names.size() - 1);
                    styles.remove(styles.size() - 1);
                }
                return restored;
            }
        }
        return fallback;
    }

    private static String family(String name) {
        if (name.equals("b")) {
            return "bold";
        }
        if (name.equals("i") || name.equals("em")) {
            return "italic";
        }
        if (name.equals("u")) {
            return "underlined";
        }
        if (name.equals("st")) {
            return "strikethrough";
        }
        if (name.equals("obf")) {
            return "obfuscated";
        }
        return name;
    }

    private static void flush(List<Segment> out, StringBuilder chunk, Style style) {
        if (chunk.length() > 0) {
            out.add(new Segment(chunk.toString(), style.copy()));
            chunk.setLength(0);
        }
    }

    private static int findGradientEnd(String input, int from) {
        int index = input.indexOf("</gradient>", from);
        return index;
    }

    private static String[] splitGradient(String value) {
        String cleaned = value;
        if (cleaned.startsWith(":")) {
            cleaned = cleaned.substring(1);
        }
        // gradient:<from>:<to>[:<phase>]
        String[] raw = cleaned.split(":");
        List<String> colors = new ArrayList<String>();
        for (String part : raw) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String candidate = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
            if (isHex(candidate) || NAMED.containsKey(trimmed.toLowerCase(Locale.ROOT))) {
                colors.add(trimmed);
            }
        }
        if (colors.isEmpty()) {
            return new String[]{"#ffffff", "#ffffff"};
        }
        if (colors.size() == 1) {
            return new String[]{colors.get(0), colors.get(0)};
        }
        return new String[]{colors.get(0), colors.get(1)};
    }

    private static void appendGradient(List<Segment> out, String inner, Style base, int from, int to) {
        String text = stripTags(inner);
        if (text.isEmpty()) {
            return;
        }
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\n') {
                visible++;
            }
        }
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Style style = base.copy();
            if (c == '\n') {
                style.color = -1;
            } else {
                double ratio = visible <= 1 ? 0.0D : (double) index / (double) (visible - 1);
                style.color = interpolate(from, to, ratio);
                index++;
            }
            char[] single = {c};
            out.add(new Segment(new String(single), style));
        }
    }

    private static String stripTags(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean inTag = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<') {
                int end = value.indexOf('>', i + 1);
                if (end > 0) {
                    i = end;
                    continue;
                }
            }
            if (!inTag) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int interpolate(int from, int to, double ratio) {
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = (int) Math.round(fr + (tr - fr) * ratio);
        int g = (int) Math.round(fg + (tg - fg) * ratio);
        int b = (int) Math.round(fb + (tb - fb) * ratio);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (isHex(value)) {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        }
        Integer named = NAMED.get(value);
        return named == null ? fallback : named;
    }

    private static boolean isHex(String value) {
        if (value == null || value.length() != 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Converts classic ampersand codes to the tag syntax understood by the parser. */
    static String convertAmpersand(String input) {
        if (input.indexOf('&') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c != '&' || i + 1 >= input.length()) {
                out.append(c);
                i++;
                continue;
            }
            char next = Character.toLowerCase(input.charAt(i + 1));
            if (next == '&') {
                out.append('&');
                i += 2;
                continue;
            }
            if (next == '#' && i + 7 < input.length() && isHex(input.substring(i + 2, i + 8))) {
                out.append("<#").append(input, i + 2, i + 8).append('>');
                i += 8;
                continue;
            }
            if (next == 'x' && i + 13 < input.length()) {
                StringBuilder hex = new StringBuilder(6);
                int cursor = i + 2;
                boolean ok = true;
                for (int part = 0; part < 6; part++) {
                    if (cursor + 1 >= input.length() || input.charAt(cursor) != '&') {
                        ok = false;
                        break;
                    }
                    char digit = input.charAt(cursor + 1);
                    if (Character.digit(digit, 16) < 0) {
                        ok = false;
                        break;
                    }
                    hex.append(digit);
                    cursor += 2;
                }
                if (ok) {
                    out.append("<#").append(hex).append('>');
                    i = cursor;
                    continue;
                }
            }
            String tag = null;
            switch (next) {
                case '0': tag = "black"; break;
                case '1': tag = "dark_blue"; break;
                case '2': tag = "dark_green"; break;
                case '3': tag = "dark_aqua"; break;
                case '4': tag = "dark_red"; break;
                case '5': tag = "dark_purple"; break;
                case '6': tag = "gold"; break;
                case '7': tag = "gray"; break;
                case '8': tag = "dark_gray"; break;
                case '9': tag = "blue"; break;
                case 'a': tag = "green"; break;
                case 'b': tag = "aqua"; break;
                case 'c': tag = "red"; break;
                case 'd': tag = "light_purple"; break;
                case 'e': tag = "yellow"; break;
                case 'f': tag = "white"; break;
                case 'k': tag = "obfuscated"; break;
                case 'l': tag = "bold"; break;
                case 'm': tag = "strikethrough"; break;
                case 'n': tag = "underlined"; break;
                case 'o': tag = "italic"; break;
                case 'r': tag = "reset"; break;
                default: tag = null; break;
            }
            if (tag == null) {
                out.append(c);
                i++;
            } else {
                out.append('<').append(tag).append('>');
                i += 2;
            }
        }
        return out.toString();
    }

    public static final class Segment {
        final String text;
        final Style style;

        Segment(String text, Style style) {
            this.text = text;
            this.style = style;
        }
    }

    static final class Style {
        int color = -1;
        boolean bold;
        boolean italic;
        boolean underlined;
        boolean strikethrough;
        boolean obfuscated;

        Style copy() {
            Style copy = new Style();
            copy.color = color;
            copy.bold = bold;
            copy.italic = italic;
            copy.underlined = underlined;
            copy.strikethrough = strikethrough;
            copy.obfuscated = obfuscated;
            return copy;
        }

        boolean sameAs(Style other) {
            return color == other.color
                    && bold == other.bold
                    && italic == other.italic
                    && underlined == other.underlined
                    && strikethrough == other.strikethrough
                    && obfuscated == other.obfuscated;
        }
    }

    private static final class LegacyRenderer {

        static String render(List<Segment> segments, boolean hex) {
            StringBuilder out = new StringBuilder();
            Style current = new Style();
            boolean first = true;
            for (Segment segment : segments) {
                if (segment.text.isEmpty()) {
                    continue;
                }
                if (first || !segment.style.sameAs(current)) {
                    out.append('\u00A7').append('r');
                    appendColor(out, segment.style.color, hex);
                    if (segment.style.bold) out.append('\u00A7').append('l');
                    if (segment.style.italic) out.append('\u00A7').append('o');
                    if (segment.style.underlined) out.append('\u00A7').append('n');
                    if (segment.style.strikethrough) out.append('\u00A7').append('m');
                    if (segment.style.obfuscated) out.append('\u00A7').append('k');
                    current = segment.style.copy();
                    first = false;
                }
                out.append(segment.text);
            }
            return out.toString();
        }

        private static void appendColor(StringBuilder out, int color, boolean hex) {
            if (color < 0) {
                return;
            }
            if (hex) {
                out.append('\u00A7').append('x');
                String value = String.format(Locale.ROOT, "%06x", color & 0xFFFFFF);
                for (int i = 0; i < 6; i++) {
                    out.append('\u00A7').append(value.charAt(i));
                }
                return;
            }
            int index = nearest(color);
            out.append('\u00A7').append("0123456789abcdef".charAt(index));
        }

        private static int nearest(int color) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int best = 15;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < LEGACY_RGB.length; i++) {
                int candidate = LEGACY_RGB[i];
                int cr = (candidate >> 16) & 0xFF;
                int cg = (candidate >> 8) & 0xFF;
                int cb = candidate & 0xFF;
                long distance = (long) (r - cr) * (r - cr)
                        + (long) (g - cg) * (g - cg)
                        + (long) (b - cb) * (b - cb);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return best;
        }
    }

    private static final class MiniRenderer {

        static String render(List<Segment> segments) {
            StringBuilder out = new StringBuilder();
            Style current = new Style();
            boolean first = true;
            for (Segment segment : segments) {
                if (segment.text.isEmpty()) {
                    continue;
                }
                if (first || !segment.style.sameAs(current)) {
                    out.append("<reset>");
                    if (segment.style.color >= 0) {
                        out.append(String.format(Locale.ROOT, "<#%06x>", segment.style.color & 0xFFFFFF));
                    }
                    if (segment.style.bold) out.append("<bold>");
                    if (segment.style.italic) out.append("<italic>");
                    if (segment.style.underlined) out.append("<underlined>");
                    if (segment.style.strikethrough) out.append("<strikethrough>");
                    if (segment.style.obfuscated) out.append("<obfuscated>");
                    current = segment.style.copy();
                    first = false;
                }
                out.append(escape(segment.text));
            }
            return out.toString();
        }

        private static String escape(String value) {
            StringBuilder out = new StringBuilder(value.length() + 8);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\') {
                    out.append("\\\\");
                } else if (c == '<') {
                    out.append("\\<");
                } else if (c == '\n') {
                    out.append("<newline>");
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }
}
