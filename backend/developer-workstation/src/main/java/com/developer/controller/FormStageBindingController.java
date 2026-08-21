package com.developer.controller;

import com.developer.component.FormDesignComponent;
import com.platform.common.dto.ApiResponse;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.enums.FormScene;
import com.developer.repository.FormStageBindingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolve task form definition by BPMN userTask id (stageId), for use by user-portal {@code TaskFormComponent}.
 *
 * <p>{@code stageId} alone does not identify a binding: BPMN node ids are unique only within one process,
 * and {@code dw_form_stage_bindings} constrains only {@code UNIQUE(form_id, stage_id)}. Callers that can
 * determine the function unit must pass {@code functionUnitCode}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/form-stage-bindings")
@RequiredArgsConstructor
@Tag(name = "Form Stage Bindings", description = "Task form resolution by BPMN stage id")
public class FormStageBindingController {

    private final FormStageBindingRepository formStageBindingRepository;
    private final FormDesignComponent formDesignComponent;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Get task form definition by BPMN user task id (taskDefinitionKey)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getByStageId(
            @Parameter(description = "BPMN user task id (taskDefinitionKey)")
            @RequestParam(value = "stageId", required = false) String stageId,
            @Parameter(description = "Owning function unit code. Optional only because a caller may fail to "
                    + "resolve it; without it the same stage id may match another function unit's form.")
            @RequestParam(value = "functionUnitCode", required = false) String functionUnitCode,
            @Parameter(description = "Rendering scene: TASK (To Do/Completed) or REQUEST (My Requests/audit). "
                    + "Defaults to TASK so existing callers keep resolving the To Do design.")
            @RequestParam(value = "scene", required = false) FormScene scene) {
        if (stageId == null || stageId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(Map.of()));
        }
        String stage = stageId.trim();
        FormScene resolvedScene = scene == null ? FormScene.TASK : scene;

        if (functionUnitCode != null && !functionUnitCode.isBlank()) {
            // Scoped resolution: a miss here is a definitive negative — the function unit is
            // known and simply has no form bound to this stage in this scene. Falling through
            // to the global lookup would serve another unit's form, and falling back to the
            // other scene would silently render the To Do design inside My Requests.
            return firstBinding(formStageBindingRepository
                    .findByFunctionUnitCodeAndStageIdAndScene(functionUnitCode.trim(), stage, resolvedScene));
        }

        // No function unit resolvable by the caller: stay deterministic (highest form id
        // wins) instead of throwing on the legitimately ambiguous multi-unit case.
        List<FormStageBinding> candidates =
                formStageBindingRepository.findByStageIdAndSceneOrderByFormIdDesc(stage, resolvedScene);
        if (candidates.size() > 1) {
            log.warn("Stage id '{}' is bound in {} function units for scene {} and no functionUnitCode was "
                    + "supplied; resolving to form {}. Pass functionUnitCode to disambiguate.",
                    stage, candidates.size(), resolvedScene, candidates.get(0).getForm().getId());
        }
        return firstBinding(candidates);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> firstBinding(List<FormStageBinding> candidates) {
        return candidates.isEmpty()
                ? ResponseEntity.ok(ApiResponse.success(Map.of()))
                : buildSuccess(candidates.get(0));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildSuccess(FormStageBinding binding) {
        FormDefinition def = formDesignComponent.getById(binding.getForm().getId());
        Map<String, Object> form = new HashMap<>();
        form.put("formName", def.getFormName());
        form.put("configJson", def.getConfigJson() != null ? def.getConfigJson() : Map.of());
        form.put("fieldPermissions", def.getFieldPermissions() != null ? def.getFieldPermissions() : Map.of());
        form.put("readOnly", binding.getReadOnly() != null ? binding.getReadOnly() : false);
        Map<String, Object> data = new HashMap<>();
        data.put("form", form);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
