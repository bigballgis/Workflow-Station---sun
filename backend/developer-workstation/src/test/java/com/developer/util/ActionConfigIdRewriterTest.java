package com.developer.util;

import com.developer.entity.ActionDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionConfigIdRewriterTest {

    @Test
    void rewritePostEmail_mapsTemplateAndConnection() {
        Map<String, Object> config = new HashMap<>();
        config.put("postEmail", new HashMap<>(Map.of(
                "enabled", true,
                "emailTemplateId", "12",
                "connectionId", "uid-old")));

        assertTrue(ActionConfigIdRewriter.rewritePostEmail(
                config, Map.of(12L, 99L), Map.of("uid-old", "uid-new")));

        @SuppressWarnings("unchecked")
        Map<String, Object> post = (Map<String, Object>) config.get("postEmail");
        assertEquals("99", post.get("emailTemplateId"));
        assertEquals("uid-new", post.get("connectionId"));
    }

    @Test
    void rewritePostEmail_skipsWhenDisabledOrMissing() {
        assertFalse(ActionConfigIdRewriter.rewritePostEmail(null, Map.of(), Map.of()));
        assertFalse(ActionConfigIdRewriter.rewritePostEmail(new HashMap<>(), Map.of(1L, 2L), Map.of()));
    }

    @Test
    void rewritePostEmailRules_mapsTemplateAndConnection() {
        Map<String, Object> config = new HashMap<>();
        config.put("postEmailRules", new ArrayList<>(List.of(new HashMap<>(Map.of(
                "id", "r1",
                "enabled", true,
                "emailTemplateId", "12",
                "connectionId", "uid-old")))));

        assertTrue(ActionConfigIdRewriter.rewritePostEmail(
                config, Map.of(12L, 99L), Map.of("uid-old", "uid-new")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) config.get("postEmailRules");
        assertEquals("99", rules.get(0).get("emailTemplateId"));
        assertEquals("uid-new", rules.get(0).get("connectionId"));
    }

    @Test
    void rewritePersistedActions_savesOnlyChanged() {
        ActionDefinition changed = ActionDefinition.builder()
                .configJson(new HashMap<>(Map.of("postEmail", new HashMap<>(Map.of("emailTemplateId", "3")))))
                .build();
        ActionDefinition untouched = ActionDefinition.builder()
                .configJson(new HashMap<>(Map.of("requireComment", true)))
                .build();
        AtomicInteger saves = new AtomicInteger();
        ActionConfigIdRewriter.rewritePersistedActions(
                List.of(changed, untouched),
                Map.of(),
                Map.of(3L, 8L),
                Map.of(),
                action -> saves.incrementAndGet());
        assertEquals(1, saves.get());
        assertEquals("8", ((Map<?, ?>) changed.getConfigJson().get("postEmail")).get("emailTemplateId"));
    }

    @Test void popupFormReferenceIsRemappedWithItsPackage() {
        ActionDefinition popup = ActionDefinition.builder().actionType(com.developer.enums.ActionType.FORM_POPUP)
                .configJson(new HashMap<>(Map.of("formId", "401"))).build();
        AtomicInteger saves = new AtomicInteger();
        ActionConfigIdRewriter.rewritePersistedActions(List.of(popup), Map.of(401L, 17L), Map.of(), Map.of(),
                action -> saves.incrementAndGet());
        assertEquals(17L, popup.getConfigJson().get("formId"));
        assertEquals(1, saves.get());
    }

    @Test void danglingPopupReferenceIsRejectedBeforePublishingBrokenContent() {
        ActionDefinition popup = ActionDefinition.builder().actionType(com.developer.enums.ActionType.FORM_POPUP)
                .configJson(new HashMap<>(Map.of("formId", 401))).build();
        org.junit.jupiter.api.Assertions.assertThrows(com.developer.exception.DeveloperBusinessException.class,
                () -> ActionConfigIdRewriter.rewritePersistedActions(List.of(popup), Map.of(), Map.of(), Map.of(),
                        action -> org.junit.jupiter.api.Assertions.fail("Invalid action must not be saved")));
    }
}
