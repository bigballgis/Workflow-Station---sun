package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeHistoryValueEqualityTest {

    @Test
    void lookupMapMatchesDisplayScalarAndIgnoresCategoryMetadata() {
        Map<String, Object> urge = new LinkedHashMap<>();
        urge.put("id", "hmdc-dd-urge-normal");
        urge.put("enabled", true);
        urge.put("dropdown_name", "Normal");
        urge.put("dropdown_category", "Urge Type");

        assertThat(ChangeHistoryValueEquality.equal(urge, "Normal")).isTrue();
        assertThat(ChangeHistoryValueEquality.equal(urge, "Urge Type")).isFalse();
        assertThat(ChangeHistoryValueEquality.equal("", "Open")).isFalse();
        assertThat(ChangeHistoryValueEquality.equal(urge, "High")).isFalse();
    }

    @Test
    void jsonStringMatchesDisplayScalar() {
        String stored = "{\"id\":\"hmdc-st-cs-open\",\"status_name\":\"Open\",\"enabled\":true}";
        assertThat(ChangeHistoryValueEquality.equal(stored, "Open")).isTrue();
        assertThat(ChangeHistoryValueEquality.equal(stored, "Closed")).isFalse();
    }

    @Test
    void numericScalarMatchesStringForm() {
        assertThat(ChangeHistoryValueEquality.equal(1, "1")).isTrue();
        assertThat(ChangeHistoryValueEquality.equal(1, "2")).isFalse();
    }
}
