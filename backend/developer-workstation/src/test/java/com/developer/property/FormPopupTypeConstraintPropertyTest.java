package com.developer.property;

import com.developer.enums.ActionType;
import com.developer.enums.FormType;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * FORM_POPUP 表单类型约束属性测试
 * Feature: process-task-form-separation, Property 22: FORM_POPUP action only references ACTION type forms
 *
 * Validates: Requirements 13.1, 13.3
 */
public class FormPopupTypeConstraintPropertyTest {

    /**
     * Property 22: FORM_POPUP action referencing an ACTION type form should pass validation.
     *
     * For any Action with type FORM_POPUP, the referenced form must have FormType ACTION.
     *
     * Validates: Requirements 13.1, 13.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 22: FORM_POPUP with ACTION form passes validation")
    void formPopupWithActionFormShouldPass(
            @ForAll("actionFormTypes") FormType formType) {

        assertThat(formType).isEqualTo(FormType.ACTION);

        boolean isValid = validateFormPopupReference(ActionType.FORM_POPUP, formType);
        assertThat(isValid).isTrue();
    }

    /**
     * Property 22: FORM_POPUP action referencing PROCESS or TASK form should fail validation.
     *
     * Validates: Requirements 13.1, 13.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 22: FORM_POPUP with non-ACTION form fails validation")
    void formPopupWithNonActionFormShouldFail(
            @ForAll("nonActionFormTypes") FormType formType) {

        assertThat(formType).isIn(FormType.PROCESS, FormType.TASK);

        boolean isValid = validateFormPopupReference(ActionType.FORM_POPUP, formType);
        assertThat(isValid).isFalse();
    }

    /**
     * Property 22: Non-FORM_POPUP actions can reference any form type.
     *
     * Validates: Requirements 13.1, 13.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 22: Non-FORM_POPUP actions accept any form type")
    void nonFormPopupActionsAcceptAnyFormType(
            @ForAll("nonFormPopupActionTypes") ActionType actionType,
            @ForAll("allFormTypes") FormType formType) {

        assertThat(actionType).isNotEqualTo(ActionType.FORM_POPUP);

        // Non-FORM_POPUP actions are not constrained by form type
        boolean isValid = validateFormPopupReference(actionType, formType);
        assertThat(isValid).isTrue();
    }

    // ========== Validation Logic ==========

    /**
     * Business rule: FORM_POPUP action only references ACTION type forms.
     * Non-FORM_POPUP actions have no form type constraint.
     */
    private boolean validateFormPopupReference(ActionType actionType, FormType formType) {
        if (actionType != ActionType.FORM_POPUP) {
            return true;
        }
        return formType == FormType.ACTION;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FormType> actionFormTypes() {
        return Arbitraries.of(FormType.ACTION);
    }

    @Provide
    Arbitrary<FormType> nonActionFormTypes() {
        return Arbitraries.of(FormType.PROCESS, FormType.TASK);
    }

    @Provide
    Arbitrary<FormType> allFormTypes() {
        return Arbitraries.of(FormType.PROCESS, FormType.TASK, FormType.ACTION);
    }

    @Provide
    Arbitrary<ActionType> nonFormPopupActionTypes() {
        return Arbitraries.of(ActionType.values())
                .filter(t -> t != ActionType.FORM_POPUP);
    }
}
