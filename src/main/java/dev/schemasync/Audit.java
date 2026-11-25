package dev.schemasync;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;

final class Audit {
    static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    record Field(String path, Set<String> observed, String target, List<String> findings, String suggestion) {}
    record Report(long documents, List<Field> fields) {}

    static Report compare(BufferedReader rows, JsonNode mapping) throws IOException {
        if (mapping == null || !mapping.isObject() || mapping.size() != 1)
            throw new IOException("Expected the _mapping response for one index");
        JsonNode properties = mapping.elements().next().path("mappings").path("properties");
        if (!properties.isObject()) throw new IOException("Missing mappings.properties");
        Map<String, String> targets = new TreeMap<>();
        flatten(properties, "", targets);
        Map<String, Set<String>> seen = new TreeMap<>();
        long documents = 0;
        String line;
        while ((line = rows.readLine()) != null) {
            if (line.isBlank()) continue;
            JsonNode doc = JSON.readTree(line);
            if (!doc.isObject()) throw new IOException("Expected an object at record " + (documents + 1));
            doc.fields().forEachRemaining(e -> {
                if (!e.getKey().equals("_id")) inspect(key(e.getKey()), e.getValue(), seen);
            });
            documents++;
        }
        List<Field> fields = new ArrayList<>();
        seen.forEach((path, types) -> {
            String target = targets.get(path);
            List<String> findings = new ArrayList<>();
            var concrete = new TreeSet<>(types);
            concrete.removeAll(Set.of("null", "empty_array"));
            if (concrete.size() > 1) findings.add("mixed_types");
            if (target == null) findings.add("unmapped");
            else if (!concrete.isEmpty()) {
                Set<String> allowed = switch (target) {
                    case "keyword", "text" -> Set.of("string");
                    case "long" -> Set.of("integer");
                    case "double" -> Set.of("integer", "decimal");
                    case "boolean" -> Set.of("boolean");
                    case "object", "nested" -> Set.of("object");
                    default -> Set.of();
                };
                if (allowed.isEmpty()) findings.add("unsupported_mapping");
                else if (!allowed.containsAll(concrete)) findings.add("mapping_conflict");
            }
            String hint = findings.contains("mapping_conflict") && "long".equals(target) && concrete.contains("string")
                ? "Validate numeric strings and signed 64-bit range before converting to long." : null;
            fields.add(new Field(path, types, target, findings, hint));
        });
        return new Report(documents, fields);
    }

    private static String key(String name) {
        if (name.isEmpty() || name.contains(".") || name.startsWith("$"))
            throw new IllegalArgumentException("Unsupported field name or Extended JSON wrapper: " + name);
        return name;
    }

    private static void flatten(JsonNode properties, String prefix, Map<String, String> targets) {
        properties.fields().forEachRemaining(e -> {
            String path = prefix + key(e.getKey());
            targets.put(path, e.getValue().path("type").asText("object"));
            flatten(e.getValue().path("properties"), path + ".", targets);
        });
    }

    private static void inspect(String path, JsonNode value, Map<String, Set<String>> seen) {
        var types = seen.computeIfAbsent(path, ignored -> new TreeSet<>());
        if (value.isArray()) {
            if (value.isEmpty()) types.add("empty_array");
            value.forEach(element -> inspect(path, element, seen));
            return;
        }
        String type = switch (value.getNodeType()) {
            case OBJECT -> "object";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case NULL -> "null";
            case NUMBER -> value.isIntegralNumber() ? (value.canConvertToLong() ? "integer" : "oversized_integer") : "decimal";
            default -> throw new IllegalArgumentException("Unsupported JSON value");
        };
        types.add(type);
        if (value.isObject()) value.fields().forEachRemaining(e -> inspect(path + "." + key(e.getKey()), e.getValue(), seen));
    }
}
