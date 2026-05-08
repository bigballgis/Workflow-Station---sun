package com.developer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinimalBpmnTemplateTest {

    @Test
    void build_embedsProcessIdFromFunctionUnitCode() {
        String code = "fu-20260508-a1b2c3";
        String xml = MinimalBpmnTemplate.build(code);

        assertThat(xml).contains("<bpmn:process id=\"" + code + "\"");
        assertThat(xml).contains("bpmnElement=\"" + code + "\"");
    }

    @Test
    void build_rejectsInvalidId() {
        assertThatThrownBy(() -> MinimalBpmnTemplate.build("123bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MinimalBpmnTemplate.build(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
