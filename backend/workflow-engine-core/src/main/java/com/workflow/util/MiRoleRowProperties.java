package com.workflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The BU + Role pool of an MI row assigned by role, as {@code MultiInstanceTaskWriter.handleRoleModeAssignment} stores
 * it in {@code wf_extended_task_info.extended_properties}: {@code assigneeMode = role}, {@code businessUnitCode},
 * {@code roleCodes}.
 */
public record MiRoleRowProperties(String businessUnitCode, List<String> roleCodes) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The row's pool, or empty when the row is not assigned by role. A role row without its BU or roles is corrupt. */
    public static Optional<MiRoleRowProperties> parse(String extendedPropertiesJson) {
        if (extendedPropertiesJson == null || extendedPropertiesJson.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JSON.readTree(extendedPropertiesJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable extended_properties on an MI row", e);
        }
        if (!"role".equalsIgnoreCase(root.path("assigneeMode").asText())) {
            return Optional.empty();
        }
        String bu = root.path("businessUnitCode").asText("").trim();
        List<String> roles = new ArrayList<>();
        root.path("roleCodes").forEach(r -> {
            if (!r.asText("").isBlank()) {
                roles.add(r.asText().trim());
            }
        });
        if (bu.isEmpty() || roles.isEmpty()) {
            throw new IllegalStateException("MI role row without businessUnitCode / roleCodes");
        }
        return Optional.of(new MiRoleRowProperties(bu, List.copyOf(roles)));
    }
}
