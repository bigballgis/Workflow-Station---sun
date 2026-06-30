package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessStartComponentCatalogVariablesTest {

    @Test
    void applyCatalogContextToVariables_injectsInitiatorAndFunctionUnitKeys() {
        Map<String, Object> variables = new HashMap<>();

        ProcessStartComponent.applyCatalogContextToVariables(variables, "user-dev", "cat-48", "FU-MCY");

        assertThat(variables)
                .containsEntry("initiator", "user-dev")
                .containsEntry("functionUnitId", "cat-48")
                .containsEntry("functionUnitCode", "FU-MCY");
    }
}
