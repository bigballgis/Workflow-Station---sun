package com.platform.common.functionunit;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RuntimeVersionContextTest {
    @Test void preservesAPinAndRejectsAChangeToAnotherRelease() {
        var pin = new RuntimeVersionContext("catalog-1", "purchase", "1.0.0", "definition-1");
        var values = new HashMap<String, Object>();
        pin.applyTo(values);
        assertThat(values).containsEntry("functionUnitId", "catalog-1");
        assertThatThrownBy(() -> new RuntimeVersionContext("catalog-2", "purchase", "2.0.0", "definition-2")
                .applyTo(values)).isInstanceOf(FunctionUnitVersionException.class);
        assertThat(values).containsEntry("functionUnitId", "catalog-1");
    }

    @Test void clientCannotReassignVersionButNestedBusinessFieldsAreNotReserved() {
        assertThatThrownBy(() -> RuntimeVersionContext.rejectClientMetadata(Map.of("functionUnitId", "forged")))
                .isInstanceOf(FunctionUnitVersionException.class);
        RuntimeVersionContext.rejectClientMetadata(Map.of("selectedBusinessRow", Map.of("functionUnitId", "business-value")));
    }

    @Test void absentAssociationIsAnExplicitRecoveryFailure() {
        assertThatThrownBy(() -> new RuntimeVersionContext(null, "purchase", null, null))
                .isInstanceOf(FunctionUnitVersionException.class);
    }
}
