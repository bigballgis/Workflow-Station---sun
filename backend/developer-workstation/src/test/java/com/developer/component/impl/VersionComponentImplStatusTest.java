package com.developer.component.impl;

import com.developer.enums.FunctionUnitStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VersionComponentImplStatusTest {

    @Test
    void resolveStatusFromSnapshot_restoresPublished() {
        assertThat(VersionComponentImpl.resolveStatusFromSnapshot(Map.of("status", "PUBLISHED")))
                .isEqualTo(FunctionUnitStatus.PUBLISHED);
    }

    @Test
    void resolveStatusFromSnapshot_legacyMissingStatusDefaultsDraft() {
        assertThat(VersionComponentImpl.resolveStatusFromSnapshot(Map.of("tableDefinitions", java.util.List.of())))
                .isEqualTo(FunctionUnitStatus.DRAFT);
    }
}
