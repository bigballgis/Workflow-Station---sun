package com.developer.controller;

import com.developer.component.FormDesignComponent;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.entity.FunctionUnit;
import com.developer.enums.FormScene;
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
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-leave", SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of(leaveBinding));
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-expense", SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of(expenseBinding));
        stubFormDefinition(leaveBinding);
        stubFormDefinition(expenseBinding);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, "fu-leave", null))).isEqualTo("Leave Approval");
        assertThat(formName(controller.getByStageId(SHARED_STAGE, "fu-expense", null))).isEqualTo("Expense Approval");
    }

    /**
     * The same node holds one design for To Do and another for My Requests; each
     * scene must resolve its own, never the other's.
     */
    @Test
    void getByStageId_withBothScenesBound_returnsTheDesignOfTheRequestedScene() {
        FormStageBinding todo = binding(11L, "fu-leave", "Leave Approval", FormScene.TASK);
        FormStageBinding myRequest = binding(12L, "fu-leave", "Leave Approval (read-only)", FormScene.REQUEST);
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-leave", SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of(todo));
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-leave", SHARED_STAGE, FormScene.REQUEST))
                .thenReturn(List.of(myRequest));
        stubFormDefinition(todo);
        stubFormDefinition(myRequest);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, "fu-leave", FormScene.TASK)))
                .isEqualTo("Leave Approval");
        assertThat(formName(controller.getByStageId(SHARED_STAGE, "fu-leave", FormScene.REQUEST)))
                .isEqualTo("Leave Approval (read-only)");
    }

    /**
     * A node with only a To Do design must render nothing in My Requests rather
     * than silently borrowing the To Do form.
     */
    @Test
    void getByStageId_withRequestSceneUnbound_returnsEmptyInsteadOfTheTaskDesign() {
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-leave", SHARED_STAGE, FormScene.REQUEST))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getByStageId(SHARED_STAGE, "fu-leave", FormScene.REQUEST);

        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void getByStageId_withFunctionUnitThatBindsNoForm_returnsEmptyRatherThanAnotherUnitsForm() {
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-unbound", SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getByStageId(SHARED_STAGE, "fu-unbound", null);

        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void getByStageId_withoutFunctionUnit_resolvesDeterministicallyInsteadOfThrowing() {
        // Repository returns candidates ordered by form id desc; the ambiguity must not surface as
        // IncorrectResultSizeDataAccessException (HTTP 500) the way the Optional-returning query did.
        FormStageBinding newest = binding(22L, "fu-expense", "Expense Approval");
        when(formStageBindingRepository.findByStageIdAndSceneOrderByFormIdDesc(SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of(newest, binding(11L, "fu-leave", "Leave Approval")));
        stubFormDefinition(newest);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, null, null))).isEqualTo("Expense Approval");
    }

    @Test
    void getByStageId_trimsBothParameters() {
        FormStageBinding leaveBinding = binding(11L, "fu-leave", "Leave Approval");
        when(formStageBindingRepository
                .findByFunctionUnitCodeAndStageIdAndScene("fu-leave", SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of(leaveBinding));
        stubFormDefinition(leaveBinding);

        assertThat(formName(controller.getByStageId("  " + SHARED_STAGE + " ", " fu-leave  ", null)))
                .isEqualTo("Leave Approval");
    }

    @Test
    void getByStageId_withBlankStageId_returnsEmpty() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getByStageId("  ", "fu-leave", null);

        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void getByStageId_withBlankFunctionUnitCode_fallsBackToUnscopedLookup() {
        FormStageBinding leaveBinding = binding(11L, "fu-leave", "Leave Approval");
        when(formStageBindingRepository.findByStageIdAndSceneOrderByFormIdDesc(SHARED_STAGE, FormScene.TASK))
                .thenReturn(List.of(leaveBinding));
        stubFormDefinition(leaveBinding);

        assertThat(formName(controller.getByStageId(SHARED_STAGE, "   ", null))).isEqualTo("Leave Approval");
    }

    private FormStageBinding binding(Long formId, String functionUnitCode, String formName) {
        return binding(formId, functionUnitCode, formName, FormScene.TASK);
    }

    private FormStageBinding binding(Long formId, String functionUnitCode, String formName, FormScene scene) {
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
                .scene(scene)
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
