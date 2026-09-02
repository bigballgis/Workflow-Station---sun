package com.portal.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code _currentItem} identifies which MI collection row a sub-task owns. It is EXECUTION-scoped,
 * but a process-instance-level copy also exists — left by whichever participant wrote it last.
 *
 * <p>Every payload the portal hands the form must be re-scoped to the task being opened. Missing one
 * of them is not cosmetic: the form's submit carries the value back, so the write path saved rows
 * under a foreign participant while the (correctly scoped) read path filtered them straight out.
 * Measured on FU fu-20260422 — task aa9fd949 owns {@code Test-000001} while
 * {@code processFormRef.fieldValues} still said {@code Test-000002}, and People rows added on that
 * sub-task disappeared on reload.
 */
class EngineTaskMapperCurrentItemTest {

    private static Map<String, Object> engineBody(String rowId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("rowId", rowId);
        item.put("rowKey", Map.of("id_idw", rowId));
        Map<String, Object> vars = new HashMap<>();
        vars.put("_currentItem", item);
        Map<String, Object> data = new HashMap<>();
        data.put("variables", vars);
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        return body;
    }

    @Test
    @DisplayName("the task's own execution value overwrites an inherited instance-wide one")
    void overwritesInstanceWideCopy() {
        Map<String, Object> target = new HashMap<>();
        target.put("_currentItem", Map.of("rowId", "Test-000002"));   // another participant's

        EngineTaskMapper.applyTaskScopedMiCurrentItem(engineBody("Test-000001"), target);

        assertThat(target.get("_currentItem")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) target.get("_currentItem")).get("rowId")).isEqualTo("Test-000001");
    }

    @Test
    @DisplayName("a body without the data envelope is read directly")
    void acceptsUnwrappedBody() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("currentItem", Map.of("rowId", "Test-000007"));
        Map<String, Object> body = new HashMap<>();
        body.put("variables", vars);

        Map<String, Object> target = new HashMap<>();
        EngineTaskMapper.applyTaskScopedMiCurrentItem(body, target);

        assertThat(((Map<?, ?>) target.get("_currentItem")).get("rowId")).isEqualTo("Test-000007");
    }

    /**
     * When the engine cannot answer, the target keeps whatever it had. Callers that must not expose a
     * foreign participant clear the key BEFORE calling — leaving no identity is safe, inventing one
     * is not.
     */
    @Test
    @DisplayName("no engine answer leaves the target untouched")
    void leavesTargetAloneWhenEngineHasNothing() {
        Map<String, Object> target = new HashMap<>();
        target.put("keep", "me");

        EngineTaskMapper.applyTaskScopedMiCurrentItem(null, target);
        EngineTaskMapper.applyTaskScopedMiCurrentItem(Map.of(), target);
        EngineTaskMapper.applyTaskScopedMiCurrentItem(Map.of("data", "not-a-map"), target);
        EngineTaskMapper.applyTaskScopedMiCurrentItem(Map.of("data", Map.of("variables", "nope")), target);

        assertThat(target).containsEntry("keep", "me");
        assertThat(target).doesNotContainKey("_currentItem");
    }

    @Test
    @DisplayName("a null target is tolerated")
    void nullTargetIsSafe() {
        EngineTaskMapper.applyTaskScopedMiCurrentItem(engineBody("Test-000001"), null);
    }
}
