package com.portal.component;

import com.platform.common.util.JsonUtils;
import com.portal.exception.PortalException;
import java.util.Map;

/** Validates a Lookup envelope without coercing its display/event payload into a database scalar. */
final class BatchLookupValue {
    private BatchLookupValue() { }

    static Object identity(Map<?, ?> rule, Object value) {
        Map<?, ?> props = rule.get("props") instanceof Map<?, ?> map ? map : Map.of();
        Object raw = props.get("lookupConfig");
        Map<?, ?> config;
        try { config = raw instanceof String text ? JsonUtils.getObjectMapper().readValue(text, Map.class)
                : raw instanceof Map<?, ?> map ? map : props; }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw invalid(); }
        String key = config.get("primaryKeyField") instanceof String text && !text.isBlank() ? text : "id";
        if (!(value instanceof Map<?, ?> row) || !(config.get("tableId") instanceof Number)
                || Boolean.TRUE.equals(config.get("multiple")) || Boolean.TRUE.equals(props.get("multiple"))) throw invalid();
        Object id = row.get(key);
        if (!(id instanceof String text && !text.isBlank()) && !(id instanceof Number)) throw invalid();
        return id;
    }

    static void validate(Map<?, ?> rule, Object value) { identity(rule, value); }
    private static PortalException invalid() { return new PortalException("400", "Batch Lookup requires one configured record identity"); }
}
