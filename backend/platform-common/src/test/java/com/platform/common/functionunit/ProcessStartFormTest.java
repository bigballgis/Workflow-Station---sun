package com.platform.common.functionunit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessStartFormTest {

    @Test
    void onlyTheTaskSceneProcessFormStartsAProcess() {
        assertThat(ProcessStartForm.matches(Map.of("formType", "PROCESS", "scene", "TASK"))).isTrue();
        assertThat(ProcessStartForm.matches(Map.of("formType", "PROCESS", "scene", "REQUEST"))).isFalse();
        assertThat(ProcessStartForm.matches(Map.of("formType", "TASK", "scene", "TASK"))).isFalse();
    }

    @Test
    void preScenePackagesAreTaskScene() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("formType", "PROCESS");
        legacy.put("scene", null);
        assertThat(ProcessStartForm.matches(legacy)).isTrue();
        assertThat(ProcessStartForm.matches(Map.of("formType", "PROCESS"))).isTrue();
    }
}
