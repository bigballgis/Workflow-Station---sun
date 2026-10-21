package com.portal.component;

import com.portal.entity.ActionDefinition;
import com.portal.repository.ActionDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves action definitions by id: catalog ({@code sys_action_definitions}) first,
 * then designer rows ({@code dw_action_definitions}). BPMN actionIds use bigint designer ids.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDefinitionLookupComponent {

    private final ActionDefinitionRepository actionDefinitionRepository;

    public List<ActionDefinition> resolvePinned(String catalogId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        var definitions = actionDefinitionRepository.findPinnedActions(catalogId, ids);
        Map<String, ActionDefinition> byId = new LinkedHashMap<>();
        for (var action : definitions) {
            byId.put(action.getId(), action);
            if (action.getSourceId() != null) byId.put(action.getSourceId(), action);
        }
        List<ActionDefinition> result = new ArrayList<>();
        for (String id : ids) {
            var action = byId.get(id);
            if (action == null) throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_SNAPSHOT_INCOMPLETE", catalogId);
            result.add(action);
        }
        return result;
    }

    /**
     * Returns definitions in the same order as {@code actionIds}, skipping unresolved ids.
     */
    public List<ActionDefinition> resolveByIdsInOrder(List<String> actionIds) {
        if (actionIds == null || actionIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = actionIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }

        Map<String, ActionDefinition> byId = new LinkedHashMap<>();
        for (ActionDefinition action : actionDefinitionRepository.findAllById(normalized)) {
            if (action != null && action.getId() != null) {
                byId.put(action.getId(), action);
            }
        }

        List<String> missingIds = normalized.stream()
                .filter(id -> !byId.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            log.info("Catalog lookup missing {} action(s), falling back to dw_action_definitions: {}",
                    missingIds.size(), missingIds);
            for (ActionDefinition action : actionDefinitionRepository.findFromDwByIds(missingIds)) {
                if (action != null && action.getId() != null) {
                    byId.putIfAbsent(action.getId(), action);
                }
            }
        }

        if (byId.isEmpty()) {
            log.warn("No action definitions found for ids: {}", normalized);
            return List.of();
        }

        List<String> unresolved = normalized.stream()
                .filter(id -> !byId.containsKey(id))
                .toList();
        if (!unresolved.isEmpty()) {
            log.warn("Could not resolve action definitions for ids: {}", unresolved);
        }

        List<ActionDefinition> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : actionIds) {
            if (id == null) {
                continue;
            }
            String trimmed = id.trim();
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                continue;
            }
            ActionDefinition action = byId.get(trimmed);
            if (action != null) {
                ordered.add(action);
            }
        }
        return ordered;
    }
}
