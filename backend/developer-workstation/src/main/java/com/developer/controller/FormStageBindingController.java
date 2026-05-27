package com.developer.controller;

import com.developer.component.FormDesignComponent;
import com.developer.dto.ApiResponse;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.repository.FormStageBindingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolve task form definition by BPMN userTask id (stageId), for use by user-portal {@code TaskFormComponent}.
 */
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
            @RequestParam(value = "stageId", required = false) String stageId) {
        if (stageId == null || stageId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(Map.of()));
        }
        return formStageBindingRepository.findByStageId(stageId.trim())
                .map(this::buildSuccess)
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(Map.of())));
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
