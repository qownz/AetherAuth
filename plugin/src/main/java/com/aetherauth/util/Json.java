package com.aetherauth.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON helpers built around a single, consistently configured Gson instance.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private static final Gson COMPACT = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private Json() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static Gson compact() {
        return COMPACT;
    }

    public static JsonObject readObject(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    public static <T> T read(Path path, Class<T> type) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static void write(Path path, Object value) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
            writer.write(System.lineSeparator());
        }
    }

    public static String toJson(Object value) {
        return COMPACT.toJson(value);
    }

    public static <T> T fromJson(String value, Class<T> type) {
        return COMPACT.fromJson(value, type);
    }

    public static <T> T fromJson(String value, Type type) {
        return COMPACT.fromJson(value, type);
    }

    /**
     * Recursively merges {@code source} into {@code target}. Values from the
     * source win; missing object keys are kept from the target. Used to add
     * new configuration keys without destroying an existing file.
     */
    public static JsonObject merge(JsonObject target, JsonObject source) {
        JsonObject result = target.deepCopy();
        for (java.util.Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement existing = result.get(entry.getKey());
            JsonElement incoming = entry.getValue();
            if (existing != null && existing.isJsonObject() && incoming.isJsonObject()) {
                result.add(entry.getKey(), merge(existing.getAsJsonObject(), incoming.getAsJsonObject()));
            } else {
                result.add(entry.getKey(), incoming.deepCopy());
            }
        }
        return result;
    }
}
