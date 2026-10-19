package com.developer.util;

import com.developer.entity.ActionDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Rewrites Action {@code config_json.postEmailRules} template / connection ids after clone, import, or rollback.
 */
public final class ActionConfigIdRewriter {

    public static final String POST_EMAIL = "postEmail";
    public static final String POST_EMAIL_RULES = "postEmailRules";
    public static final String EMAIL_TEMPLATE_ID = "emailTemplateId";
    public static final String CONNECTION_ID = "connectionId";

    private ActionConfigIdRewriter() {}

    /**
     * @return {@code true} if {@code configJson} was mutated
     */
    public static boolean rewritePostEmail(
            Map<String, Object> configJson,
            Map<Long, Long> emailTemplateIdMapping,
            Map<String, String> connectionUidMapping) {
        if (configJson == null) {
            return false;
        }
        boolean changed = false;
        if (configJson.get(POST_EMAIL_RULES) instanceof List<?> rules) {
            changed = rewriteRulesList(rules, emailTemplateIdMapping, connectionUidMapping);
            if (changed) {
                configJson.put(POST_EMAIL_RULES, rules);
            }
            return changed;
        }
        return rewriteLegacyPostEmail(configJson, emailTemplateIdMapping, connectionUidMapping);
    }

    @SuppressWarnings("unchecked")
    private static boolean rewriteLegacyPostEmail(
            Map<String, Object> configJson,
            Map<Long, Long> emailTemplateIdMapping,
            Map<String, String> connectionUidMapping) {
        Object raw = configJson.get(POST_EMAIL);
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return false;
        }
        Map<String, Object> postEmail = new HashMap<>();
        rawMap.forEach((k, v) -> postEmail.put(String.valueOf(k), v));
        boolean changed = rewriteEmailIds(postEmail, emailTemplateIdMapping, connectionUidMapping);
        if (changed) {
            configJson.put(POST_EMAIL, postEmail);
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean rewriteRulesList(
            List<?> rules,
            Map<Long, Long> emailTemplateIdMapping,
            Map<String, String> connectionUidMapping) {
        boolean changed = false;
        for (int i = 0; i < rules.size(); i++) {
            if (!(rules.get(i) instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> rule = new HashMap<>();
            rawMap.forEach((k, v) -> rule.put(String.valueOf(k), v));
            if (rewriteEmailIds(rule, emailTemplateIdMapping, connectionUidMapping)) {
                ((List<Object>) rules).set(i, rule);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteEmailIds(
            Map<String, Object> emailBlock,
            Map<Long, Long> emailTemplateIdMapping,
            Map<String, String> connectionUidMapping) {
        boolean changed = false;
        Object templateId = emailBlock.get(EMAIL_TEMPLATE_ID);
        if (templateId != null && emailTemplateIdMapping != null && !emailTemplateIdMapping.isEmpty()) {
            Long mapped = emailTemplateIdMapping.get(parseLong(templateId));
            if (mapped != null && !String.valueOf(mapped).equals(String.valueOf(templateId))) {
                emailBlock.put(EMAIL_TEMPLATE_ID, String.valueOf(mapped));
                changed = true;
            }
        }
        Object connectionId = emailBlock.get(CONNECTION_ID);
        if (connectionId != null && connectionUidMapping != null && !connectionUidMapping.isEmpty()) {
            String mappedUid = connectionUidMapping.get(connectionId.toString());
            if (mappedUid != null && !mappedUid.equals(connectionId.toString())) {
                emailBlock.put(CONNECTION_ID, mappedUid);
                changed = true;
            }
        }
        return changed;
    }

    public static void rewritePersistedActions(
            List<ActionDefinition> actions,
            Map<Long, Long> emailTemplateIdMapping,
            Map<String, String> connectionUidMapping,
            Consumer<ActionDefinition> saver) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (ActionDefinition action : actions) {
            Map<String, Object> config = action.getConfigJson();
            if (config == null) {
                continue;
            }
            Map<String, Object> copy = new HashMap<>(config);
            if (rewritePostEmail(copy, emailTemplateIdMapping, connectionUidMapping)) {
                action.setConfigJson(copy);
                saver.accept(action);
            }
        }
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
