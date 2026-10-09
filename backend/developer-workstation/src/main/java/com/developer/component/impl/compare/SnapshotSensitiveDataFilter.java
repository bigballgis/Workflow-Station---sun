package com.developer.component.impl.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Set;

/** Prevent version comparison responses from returning stored connection credentials. */
public final class SnapshotSensitiveDataFilter {

    private static final Set<String> SECRET_KEYS = Set.of(
            "credentialencrypted", "oauthrefreshtokenencrypted", "oauthaccesstokenencrypted",
            "password", "secret", "accesstoken", "refreshtoken", "privatekey",
            "authorization", "proxyauthorization", "cookie", "setcookie", "apikey", "xapikey");
    private static final Set<String> NAME_KEYS = Set.of("name", "field", "key", "propertyName");
    private static final Set<String> VALUE_KEYS = Set.of(
            "value", "values", "defaultValue", "default", "literal", "content");

    private SnapshotSensitiveDataFilter() {}

    public static JsonNode redactForLegacyCompare(JsonNode snapshot, ObjectMapper mapper) {
        return redact(new VersionActionSnapshotProjector(mapper).projectForLegacyCompare(snapshot));
    }

    public static JsonNode redact(JsonNode value) {
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            boolean secretNamedValue = namedAsSecret(value);
            value.properties().forEach(entry -> result.set(entry.getKey(),
                    isSecret(entry.getKey()) || (secretNamedValue && VALUE_KEYS.contains(entry.getKey()))
                            ? JsonNodeFactory.instance.textNode("[REDACTED]")
                            : redact(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(redact(item)));
            return result;
        }
        return value.deepCopy();
    }

    private static boolean namedAsSecret(JsonNode value) {
        for (String key : NAME_KEYS) {
            JsonNode name = value.get(key);
            if (name != null && name.isTextual() && isSecret(name.asText())) return true;
        }
        return false;
    }

    static boolean isSecret(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (normalized.equals("passwordenvkey") || normalized.equals("tokenexpiresat")) {
            return false;
        }
        return SECRET_KEYS.contains(normalized) || normalized.endsWith("secret")
                || normalized.endsWith("password") || normalized.endsWith("token")
                || normalized.endsWith("credentialencrypted");
    }
}
