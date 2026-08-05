package com.portal.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemAuditFieldFillerTest {

    @Test
    void fillOnInsertWritesAllFourEvenWhenKeysAbsentFromFormPayload() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("case_number", "C-1");

        SystemAuditFieldFiller.fillOnInsert(variables, "Alice");

        assertThat(variables.get("case_number")).isEqualTo("C-1");
        assertThat(variables.get(SystemAuditFieldFiller.CREATED_AT)).asString()
                .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        assertThat(variables.get(SystemAuditFieldFiller.CREATED_BY)).isEqualTo("Alice");
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_AT))
                .isEqualTo(variables.get(SystemAuditFieldFiller.CREATED_AT));
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_BY)).isEqualTo("Alice");
    }

    @Test
    void fillOnInsertOverwritesClientForgedAuditValues() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(SystemAuditFieldFiller.CREATED_AT, "2000-01-01 00:00:00");
        variables.put(SystemAuditFieldFiller.CREATED_BY, "forged");
        variables.put(SystemAuditFieldFiller.UPDATED_AT, "2000-01-01 00:00:00");
        variables.put(SystemAuditFieldFiller.UPDATED_BY, "forged");

        SystemAuditFieldFiller.fillOnInsert(variables, "Bob");

        assertThat(variables.get(SystemAuditFieldFiller.CREATED_AT)).asString()
                .isNotEqualTo("2000-01-01 00:00:00");
        assertThat(variables.get(SystemAuditFieldFiller.CREATED_BY)).isEqualTo("Bob");
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_BY)).isEqualTo("Bob");
    }

    @Test
    void fillOnUpdateRefreshesOnlyUpdatedFamily() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(SystemAuditFieldFiller.CREATED_AT, "2020-01-01 00:00:00");
        variables.put(SystemAuditFieldFiller.CREATED_BY, "Alice");
        variables.put(SystemAuditFieldFiller.UPDATED_AT, "2020-01-01 00:00:00");
        variables.put(SystemAuditFieldFiller.UPDATED_BY, "Alice");

        SystemAuditFieldFiller.fillOnUpdate(variables, "Carol");

        assertThat(variables.get(SystemAuditFieldFiller.CREATED_AT)).isEqualTo("2020-01-01 00:00:00");
        assertThat(variables.get(SystemAuditFieldFiller.CREATED_BY)).isEqualTo("Alice");
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_AT)).asString()
                .isNotEqualTo("2020-01-01 00:00:00");
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_BY)).isEqualTo("Carol");
    }

    @Test
    void fillOnUpdateInjectsUpdatedFieldsWhenAbsent() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", "x");

        SystemAuditFieldFiller.fillOnUpdate(variables, "Dana");

        assertThat(variables).containsKeys(
                SystemAuditFieldFiller.UPDATED_AT, SystemAuditFieldFiller.UPDATED_BY);
        assertThat(variables).doesNotContainKeys(
                SystemAuditFieldFiller.CREATED_AT, SystemAuditFieldFiller.CREATED_BY);
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_BY)).isEqualTo("Dana");
    }

    @Test
    void nullVariablesAreNoOp() {
        SystemAuditFieldFiller.fillOnInsert(null, "x");
        SystemAuditFieldFiller.fillOnUpdate(null, "x");
    }

    @Test
    void blankDisplayNameDoesNotWriteUserFields() {
        Map<String, Object> variables = new HashMap<>();
        SystemAuditFieldFiller.fillOnInsert(variables, "  ");
        assertThat(variables).containsKeys(
                SystemAuditFieldFiller.CREATED_AT, SystemAuditFieldFiller.UPDATED_AT);
        assertThat(variables).doesNotContainKeys(
                SystemAuditFieldFiller.CREATED_BY, SystemAuditFieldFiller.UPDATED_BY);
    }

    @Test
    void stripClientAuditKeysRemovesExactAndCaseVariants() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "ok");
        payload.put("created_at", "forged");
        payload.put("CREATED_BY", "forged");
        payload.put("Updated_At", "forged");
        payload.put("updated_by", "forged");

        SystemAuditFieldFiller.stripClientAuditKeys(payload);

        assertThat(payload).containsOnlyKeys("title");
        assertThat(payload.get("title")).isEqualTo("ok");
    }

    @Test
    void stripThenFillOnUpdatePreservesCreatedFamily() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(SystemAuditFieldFiller.CREATED_AT, "2020-01-01 00:00:00");
        variables.put(SystemAuditFieldFiller.CREATED_BY, "Alice");
        variables.put(SystemAuditFieldFiller.UPDATED_AT, "2020-01-01 00:00:00");
        variables.put(SystemAuditFieldFiller.UPDATED_BY, "Alice");

        Map<String, Object> inbound = new HashMap<>();
        inbound.put("title", "changed");
        inbound.put(SystemAuditFieldFiller.CREATED_AT, "1999-01-01 00:00:00");
        inbound.put(SystemAuditFieldFiller.CREATED_BY, "attacker");
        inbound.put(SystemAuditFieldFiller.UPDATED_AT, "1999-01-01 00:00:00");
        inbound.put(SystemAuditFieldFiller.UPDATED_BY, "attacker");

        SystemAuditFieldFiller.stripClientAuditKeys(inbound);
        variables.putAll(inbound);
        SystemAuditFieldFiller.fillOnUpdate(variables, "Carol");

        assertThat(variables.get("title")).isEqualTo("changed");
        assertThat(variables.get(SystemAuditFieldFiller.CREATED_AT)).isEqualTo("2020-01-01 00:00:00");
        assertThat(variables.get(SystemAuditFieldFiller.CREATED_BY)).isEqualTo("Alice");
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_AT)).asString()
                .isNotEqualTo("2020-01-01 00:00:00")
                .isNotEqualTo("1999-01-01 00:00:00");
        assertThat(variables.get(SystemAuditFieldFiller.UPDATED_BY)).isEqualTo("Carol");
    }
}
