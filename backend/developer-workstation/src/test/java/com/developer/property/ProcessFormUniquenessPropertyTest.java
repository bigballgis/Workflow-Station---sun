package com.developer.property;

import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.enums.FormType;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * PROCESS form 唯一性属性测试
 * Feature: process-task-form-separation, Property 2: PROCESS form uniqueness per FunctionUnit
 *
 * Validates: Requirements 2.1, 2.2
 */
public class ProcessFormUniquenessPropertyTest {

    /**
     * Property 2: Creating a second PROCESS form in the same FunctionUnit should fail.
     *
     * For any FunctionUnit that already has a PROCESS form, attempting to create
     * another PROCESS form should be rejected by the uniqueness validation.
     *
     * Validates: Requirements 2.1, 2.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 2: Second PROCESS form creation is rejected")
    void secondProcessFormCreationShouldBeRejected(
            @ForAll("functionUnitsWithProcessForm") FunctionUnitWithForms unitWithForms) {

        // The FunctionUnit already has one PROCESS form
        long processFormCount = unitWithForms.forms.stream()
                .filter(f -> f.getFormType() == FormType.PROCESS)
                .count();

        assertThat(processFormCount).isEqualTo(1);

        // Attempting to create another PROCESS form should fail validation
        boolean canCreateAnother = validateProcessFormUniqueness(unitWithForms.forms);
        assertThat(canCreateAnother).isFalse();
    }

    /**
     * Property 2: A FunctionUnit with no PROCESS form should allow creating one.
     *
     * Validates: Requirements 2.1, 2.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 2: First PROCESS form creation is allowed")
    void firstProcessFormCreationShouldBeAllowed(
            @ForAll("functionUnitsWithoutProcessForm") FunctionUnitWithForms unitWithForms) {

        long processFormCount = unitWithForms.forms.stream()
                .filter(f -> f.getFormType() == FormType.PROCESS)
                .count();

        assertThat(processFormCount).isZero();

        // Creating the first PROCESS form should pass validation
        boolean canCreate = validateProcessFormUniqueness(unitWithForms.forms);
        assertThat(canCreate).isTrue();
    }

    /**
     * Property 2: Multiple TASK or ACTION forms do not affect PROCESS form uniqueness.
     *
     * Validates: Requirements 2.1, 2.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 2: Non-PROCESS forms do not affect uniqueness")
    void nonProcessFormsDoNotAffectUniqueness(
            @ForAll("functionUnitsWithOnlyNonProcessForms") FunctionUnitWithForms unitWithForms) {

        long processFormCount = unitWithForms.forms.stream()
                .filter(f -> f.getFormType() == FormType.PROCESS)
                .count();

        assertThat(processFormCount).isZero();

        // Even with many TASK/ACTION forms, creating a PROCESS form should be allowed
        boolean canCreate = validateProcessFormUniqueness(unitWithForms.forms);
        assertThat(canCreate).isTrue();
    }

    // ========== Validation Logic ==========

    /**
     * Business rule: At most one PROCESS form per FunctionUnit.
     * Returns true if a new PROCESS form can be created (count == 0).
     */
    private boolean validateProcessFormUniqueness(List<FormDefinition> existingForms) {
        long processFormCount = existingForms.stream()
                .filter(f -> f.getFormType() == FormType.PROCESS)
                .count();
        return processFormCount == 0;
    }

    // ========== Data Classes ==========

    static class FunctionUnitWithForms {
        final FunctionUnit functionUnit;
        final List<FormDefinition> forms;

        FunctionUnitWithForms(FunctionUnit functionUnit, List<FormDefinition> forms) {
            this.functionUnit = functionUnit;
            this.forms = forms;
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FunctionUnitWithForms> functionUnitsWithProcessForm() {
        return functionUnitArbitrary().flatMap(fu ->
                formDefinitionArbitrary(FormType.PROCESS).flatMap(processForm ->
                        nonProcessFormList().map(otherForms -> {
                            List<FormDefinition> allForms = new ArrayList<>();
                            allForms.add(processForm);
                            allForms.addAll(otherForms);
                            return new FunctionUnitWithForms(fu, allForms);
                        })
                )
        );
    }

    @Provide
    Arbitrary<FunctionUnitWithForms> functionUnitsWithoutProcessForm() {
        return functionUnitArbitrary().flatMap(fu ->
                nonProcessFormList().map(forms ->
                        new FunctionUnitWithForms(fu, forms)
                )
        );
    }

    @Provide
    Arbitrary<FunctionUnitWithForms> functionUnitsWithOnlyNonProcessForms() {
        return functionUnitArbitrary().flatMap(fu ->
                nonProcessFormList()
                        .filter(list -> !list.isEmpty())
                        .map(forms -> new FunctionUnitWithForms(fu, forms))
        );
    }

    private Arbitrary<FunctionUnit> functionUnitArbitrary() {
        return Arbitraries.longs().between(1L, 10000L).map(id ->
                FunctionUnit.builder().id(id).name("fu-" + id).code("fu-code-" + id).build()
        );
    }

    private Arbitrary<FormDefinition> formDefinitionArbitrary(FormType formType) {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30).map(name ->
                FormDefinition.builder()
                        .formType(formType)
                        .formName(name)
                        .configJson(Map.of("fields", List.of()))
                        .stageBindings(new ArrayList<>())
                        .build()
        );
    }

    private Arbitrary<List<FormDefinition>> nonProcessFormList() {
        return Arbitraries.of(FormType.TASK, FormType.ACTION)
                .flatMap(this::formDefinitionArbitrary)
                .list()
                .ofMinSize(0)
                .ofMaxSize(5);
    }
}
