package com.developer.util;

import com.developer.entity.ActionDefinition;
import org.junit.jupiter.api.Test;

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
                Map.of(3L, 8L),
                Map.of(),
                action -> saves.incrementAndGet());
        assertEquals(1, saves.get());
        assertEquals("8", ((Map<?, ?>) changed.getConfigJson().get("postEmail")).get("emailTemplateId"));
    }
}
