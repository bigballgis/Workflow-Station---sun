package com.developer.controller;

import com.developer.component.FormDesignComponent;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FormStageBindingRepository;
import com.platform.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BPMN node ids are unique only within one process and {@code dw_form_stage_bindings} constrains only
 * {@code UNIQUE(form_id, stage_id)}, so the same stage id legitimately exists in several function units
 * (AI-generated processes reuse readable ids such as {@code UserTask_Approve}). These tests pin the
 * resolution down to the requested function unit.
 */
@ExtendWith(MockitoExtension.class)
class FormStageBindingControllerTest {

    private static final String SHARED_STAGE = "UserTask_Approve";

    @Mock
    private FormStageBindingRepository formStageBindingRepository;

    @Mock
    private FormDesignComponent formDesignComponent;

    @InjectMocks
    private FormStageBindingController controller;

    @Test
    void getByStageId_withTwoFunctionUnitsSharingStageId_returnsTheRequestedUnitsForm() {
        FormStageBinding leaveBinding = binding(11L, "fu-leave", "Leave Approval");
        FormStageBinding expenseBinding = binding(22L, "fu-expense", "Expense Approval");
        when(formStageBindingRepository.findByFunctionUnitCodeAndStageId("fu-leave", SHARED_STAGE))
                .thenReturn(List.of(leaveBinding));
        when(formStageBindingRepository.findByFunctionUnitCodeAndStageId("fu-expense", SHARED_STAGE))
                .thenReturn(List.of(expenseBinding));
        stubFormDefinition(leaveBinding);
        stubFormDefinition(expenseBinding);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, "fu-leave"))).isEqualTo("Leave Approval");
        assertThat(formName(controller.getByStageId(SHARED_STAGE, "fu-expense"))).isEqualTo("Expense Approval");
    }

    @Test
    void getByStageId_withFunctionUnitThatBindsNoForm_returnsEmptyRatherThanAnotherUnitsForm() {
        when(formStageBindingRepository.findByFunctionUnitCodeAndStageId("fu-unbound", SHARED_STAGE))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getByStageId(SHARED_STAGE, "fu-unbound");

        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void getByStageId_withoutFunctionUnit_resolvesDeterministicallyInsteadOfThrowing() {
        // Repository returns candidates ordered by form id desc; the ambiguity must not surface as
        // IncorrectResultSizeDataAccessException (HTTP 500) the way the Optional-returning query did.
        FormStageBinding newest = binding(22L, "fu-expense", "Expense Approval");
        when(formStageBindingRepository.findByStageIdOrderByFormIdDesc(SHARED_STAGE))
                .thenReturn(List.of(newest, binding(11L, "fu-leave", "Leave Approval")));
        stubFormDefinition(newest);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, null))).isEqualTo("Expense Approval");
    }

    @Test
    void getByStageId_trimsBothParameters() {
        FormStageBinding leaveBinding = binding(11L, "fu-leave", "Leave Approval");
        when(formStageBindingRepository.findByFunctionUnitCodeAndStageId("fu-leave", SHARED_STAGE))
                .thenReturn(List.of(leaveBinding));
        stubFormDefinition(leaveBinding);

        assertThat(formName(controller.getByStageId("  " + SHARED_STAGE + " ", " fu-leave  ")))
                .isEqualTo("Leave Approval");
    }

    @Test
    void getByStageId_withBlankStageId_returnsEmpty() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getByStageId("  ", "fu-leave");

        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void getByStageId_withBlankFunctionUnitCode_fallsBackToUnscopedLookup() {
        FormStageBinding leaveBinding = binding(11L, "fu-leave", "Leave Approval");
        when(formStageBindingRepository.findByStageIdOrderByFormIdDesc(SHARED_STAGE))
                .thenReturn(List.of(leaveBinding));
        stubFormDefinition(leaveBinding);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, "   "))).isEqualTo("Leave Approval");
    }

    private FormStageBinding binding(Long formId, String functionUnitCode, String formName) {
        FormDefinition form = FormDefinition.builder()
                .id(formId)
                .formName(formName)
                .functionUnit(FunctionUnit.builder().id(formId).code(functionUnitCode).build())
                .build();
        return FormStageBinding.builder()
                .id(formId)
                .form(form)
                .stageId(SHARED_STAGE)
                .readOnly(false)
                .build();
    }

    private void stubFormDefinition(FormStageBinding binding) {
        when(formDesignComponent.getById(binding.getForm().getId())).thenReturn(binding.getForm());
    }

    @SuppressWarnings("unchecked")
    private String formName(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        Map<String, Object> form = (Map<String, Object>) response.getBody().getData().get("form");
        return (String) form.get("formName");
    }
}
