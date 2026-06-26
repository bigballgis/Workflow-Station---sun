package com.admin.component;

import com.admin.entity.ActionDefinition;
import com.admin.repository.ActionDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Persists imported action definitions for a function unit.
 *
 * <p>This lives in its own bean (not inline in {@code FunctionUnitImportController}) so the
 * delete-then-insert runs inside a real transaction: {@code deleteByFunctionUnitId} is a Spring
 * Data <em>derived</em> delete (load entities, then {@code em.remove} each). {@code em.remove}
 * requires an active transaction, otherwise Spring throws
 * {@code TransactionRequiredException: cannot reliably process 'remove' call}. The import endpoint
 * is not transactional, and a derived delete only triggers {@code remove} when rows actually exist —
 * so the first import of a brand-new unit succeeded while re-deploying an existing unit (which has
 * stale action rows to clear) failed with HTTP 500 {@code admin.fu.import_unexpected_error}.
 *
 * <p>{@code @Transactional} only takes effect across a bean boundary (Spring proxy), which is why
 * the controller must delegate here rather than self-invoke.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDefinitionImportWriter {

    private final ActionDefinitionRepository actionDefinitionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Replace all action definitions of a function unit with the imported set, atomically.
     *
     * @param functionUnitId target function unit id
     * @param actions        raw action maps parsed from the import package (may be null/empty)
     */
    @Transactional
    public void replaceActions(String functionUnitId, List<Map<String, Object>> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        actionDefinitionRepository.deleteByFunctionUnitId(functionUnitId);
        // Force the DELETE to hit the DB before the INSERTs below. Hibernate orders INSERTs
        // ahead of DELETEs within a single flush, so without this the re-inserted rows collide
        // with the not-yet-deleted old rows on uk_sys_action_name_fu (function_unit_id, action_name)
        // -> 23505 duplicate key. Same delete-then-flush guard used elsewhere on re-import paths.
        actionDefinitionRepository.flush();

        for (Map<String, Object> actionData : actions) {
            try {
                String actionName = (String) actionData.get("actionName");
                String actionType = (String) actionData.get("actionType");

                if (actionName != null && actionType != null) {
                    Map<String, Object> configJson = parseActionConfigJson(actionData.get("configJson"));

                    ActionDefinition actionDef = ActionDefinition.builder()
                            .functionUnitId(functionUnitId)
                            .actionName(actionName)
                            .actionType(actionType)
                            .description((String) actionData.get("description"))
                            .configJson(configJson)
                            .icon((String) actionData.get("icon"))
                            .buttonColor((String) actionData.get("buttonColor"))
                            .isDefault(actionData.get("isDefault") instanceof Boolean
                                    ? (Boolean) actionData.get("isDefault") : false)
                            .build();

                    actionDefinitionRepository.save(actionDef);
                    log.info("Saved action definition: {} ({}) for function unit: {}",
                            actionName, actionType, functionUnitId);
                }
            } catch (Exception e) {
                log.warn("Failed to save action definition", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseActionConfigJson(Object configJsonObj) {
        if (configJsonObj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (configJsonObj instanceof String s && !s.isBlank()) {
            try {
                return objectMapper.readValue(s,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse action config_json: {}", e.getMessage());
            }
        }
        return Map.of();
    }
}
