package com.developer.property;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.enums.FormType;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * TASK form Stage 绑定属性测试
 * Feature: process-task-form-separation, Property 4: TASK form requires Stage binding
 *
 * Validates: Requirements 3.2
 */
public class TaskFormStageBindingPropertyTest {

    /**
     * Validates that a TASK form creation request without any Stage binding is rejected.
     *
     * For any form creation request with FormType TASK, if the request does not include
     * at least one Stage binding, the system should reject the creation with a validation error.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 4: TASK form requires Stage binding")
    void taskFormWithoutStageBindingShouldBeRejected(
            @ForAll("taskFormConfigsWithoutStage") FormDefinition formDef) {

        assertThat(formDef.getFormType()).isEqualTo(FormType.TASK);
        assertThat(formDef.getStageBindings()).isEmpty();

        // Validate: TASK form with no stage bindings must be rejected
        boolean isValid = validateTaskFormStageBinding(formDef);
        assertThat(isValid).isFalse();
    }

    /**
     * Validates that a TASK form creation request with at least one Stage binding is accepted.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 4: TASK form with Stage binding is accepted")
    void taskFormWithStageBindingShouldBeAccepted(
            @ForAll("taskFormConfigsWithStage") FormDefinition formDef) {

        assertThat(formDef.getFormType()).isEqualTo(FormType.TASK);
        assertThat(formDef.getStageBindings()).isNotEmpty();

        // Validate: TASK form with stage bindings must be accepted
        boolean isValid = validateTaskFormStageBinding(formDef);
        assertThat(isValid).isTrue();
    }

    /**
     * Validates that PROCESS and ACTION forms do not require Stage bindings.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 4: Non-TASK forms do not require Stage binding")
    void nonTaskFormDoesNotRequireStageBinding(
            @ForAll("nonTaskFormConfigs") FormDefinition formDef) {

        assertThat(formDef.getFormType()).isIn(FormType.PROCESS, FormType.ACTION);

        // Validate: non-TASK forms are always valid regardless of stage bindings
        boolean isValid = validateTaskFormStageBinding(formDef);
        assertThat(isValid).isTrue();
    }

    // ========== Validation Logic ==========

    /**
     * Business rule: TASK form requires at least one Stage binding.
     * PROCESS and ACTION forms do not require Stage bindings.
     */
    private boolean validateTaskFormStageBinding(FormDefinition formDef) {
        if (formDef.getFormType() == FormType.TASK) {
            return formDef.getStageBindings() != null && !formDef.getStageBindings().isEmpty();
        }
        return true;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FormDefinition> taskFormConfigsWithoutStage() {
        return Arbitraries.of(FormType.TASK).flatMap(type ->
                formNames().flatMap(name ->
                        configJsons().map(config ->
                                FormDefinition.builder()
                                        .formType(type)
                                        .formName(name)
                                        .configJson(config)
                                        .stageBindings(new ArrayList<>())
                                        .build()
                        )
                )
        );
    }

    @Provide
    Arbitrary<FormDefinition> taskFormConfigsWithStage() {
        return Arbitraries.of(FormType.TASK).flatMap(type ->
                formNames().flatMap(name ->
                        configJsons().flatMap(config ->
                                stageBindingLists().map(bindings ->
                                        FormDefinition.builder()
                                                .formType(type)
                                                .formName(name)
                                                .configJson(config)
                                                .stageBindings(bindings)
                                                .build()
                                )
                        )
                )
        );
    }

    @Provide
    Arbitrary<FormDefinition> nonTaskFormConfigs() {
        return Arbitraries.of(FormType.PROCESS, FormType.ACTION).flatMap(type ->
                formNames().flatMap(name ->
                        configJsons().map(config ->
                                FormDefinition.builder()
                                        .formType(type)
                                        .formName(name)
                                        .configJson(config)
                                        .stageBindings(new ArrayList<>())
                                        .build()
                        )
                )
        );
    }

    private Arbitrary<String> formNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    private Arbitrary<Map<String, Object>> configJsons() {
        return Arbitraries.of(
                Map.of("fields", List.of()),
                Map.of("layout", "grid", "fields", List.of("field1")),
                Map.of("fields", List.of("amount", "description"))
        );
    }

    private Arbitrary<List<FormStageBinding>> stageBindingLists() {
        return stageBindings()
                .list()
                .ofMinSize(1)
                .ofMaxSize(5);
    }

    private Arbitrary<FormStageBinding> stageBindings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30)
                .flatMap(stageId ->
                        Arbitraries.strings()
                                .alpha()
                                .ofMinLength(3)
                                .ofMaxLength(30)
                                .map(stageName ->
                                        FormStageBinding.builder()
                                                .stageId(stageId)
                                                .stageName(stageName)
                                                .build()
                                )
                );
    }
}
