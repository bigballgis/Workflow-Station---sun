package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.BatchStartContext;
import com.platform.common.exception.BusinessException;
import com.platform.common.enums.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A page must echo the same published batch design which the server will actually start. */
final class SubTableBatchStartContext {
    private SubTableBatchStartContext() { }

    static BatchStartContext capture(Map<String, Object> content,
            ProcessStartComponent.ActiveCatalogPin pin, ObjectMapper mapper) {
        var snapshot = SubTableBatchRuntimeSnapshot.capture(content, pin, mapper);
        if (snapshot.isEmpty()) return null; // Protocol: a legacy design has no new capability/context requirement.
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("snapshot", snapshot.get());
        definition.put("processes", ((List<?>) content.get("processes")).stream()
                .map(raw -> ((Map<?, ?>) raw).get("data")).toList());
        // Exclude live DW field enrichment; frozen snapshot already captures published PK/FK/fields.
        definition.put("forms", ((List<?>) content.get("forms")).stream().map(raw -> {
            Map<?, ?> form = (Map<?, ?>) raw;
            Map<String, Object> published = new LinkedHashMap<>();
            for (String key : List.of("sourceId", "formType", "scene", "data", "publishedFieldPermissions")) {
                published.put(key, form.get(key));
            }
            return published;
        }).toList());
        return new BatchStartContext(1, pin.catalogId(), SubTableBatchSnapshotDigest.compute(definition, mapper));
    }

    static Map<String, Object> forPage(Map<String, Object> content, String catalogId) {
        var mapper = com.platform.common.util.JsonUtils.getObjectMapper();
        var pin = new ProcessStartComponent.ActiveCatalogPin(catalogId,
                (String) content.get("code"), (String) content.get("version"));
        var context = capture(content, pin, mapper);
        Map<String, Object> response = new LinkedHashMap<>(content);
        if (context != null) response.put("batchStartContext", context);
        return response;
    }

    static void validate(BatchStartContext submitted, BatchStartContext current, boolean serverProduced, String changedMessage) {
        if (serverProduced || submitted == null && current == null) return;
        if (current == null || !current.equals(submitted)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, changedMessage);
        }
    }
}
