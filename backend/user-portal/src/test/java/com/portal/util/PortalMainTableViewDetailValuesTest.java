package com.portal.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortalMainTableViewDetailValuesTest {

    @Test
    void storedMembersMissingFromTheListProjectionAreFilledIn() {
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("rebilled_date", "2026-09-04");
        Map<String, Object> stored = Map.of(
                "rebilled_date", "should-not-overwrite",
                "merchant_credit", "1",
                "merchant_credi_date", "2026-09-04",
                "temporary_refund", "1",
                "temporary_refund_date", "2026-09-04",
                "_processInstanceId", "hidden");

        Map<String, Object> out = PortalMainTableViewDetailValues.overlayStoredMembers(
                projected, stored);

        assertThat(out.get("rebilled_date"))
                .as("list / fk_display projection must keep winning")
                .isEqualTo("2026-09-04");
        assertThat(out)
                .containsEntry("merchant_credit", "1")
                .containsEntry("merchant_credi_date", "2026-09-04")
                .containsEntry("temporary_refund", "1")
                .containsEntry("temporary_refund_date", "2026-09-04")
                .doesNotContainKey("_processInstanceId");
    }
}
