package com.developer.controller;

import com.developer.component.EmailTemplateComponent;
import com.developer.dto.EmailTemplateResponse;
import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal API for workflow-engine to resolve Send Task email content from templates.
 */
@RestController
@RequestMapping("/internal/function-units")
@RequiredArgsConstructor
@Tag(name = "内部-邮件模板", description = "工作流引擎内部调用")
public class InternalEmailTemplateController {

    private final EmailTemplateComponent emailTemplateComponent;
    private final FunctionUnitRepository functionUnitRepository;

    @GetMapping("/{functionUnitRef}/email-templates/{templateId}")
    @Operation(summary = "获取邮件模板内容（内部）")
    public ResponseEntity<Map<String, Object>> getTemplate(
            @PathVariable String functionUnitRef,
            @PathVariable Long templateId) {
        Long functionUnitId = resolveFunctionUnitId(functionUnitRef);
        EmailTemplateResponse template = emailTemplateComponent.getById(functionUnitId, templateId);
        if (Boolean.FALSE.equals(template.getEnabled())) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("id", template.getId());
        body.put("name", template.getName());
        body.put("subject", template.getSubject() != null ? template.getSubject() : "");
        body.put("bodyHtml", template.getBodyHtml() != null ? template.getBodyHtml() : "");
        body.put("enabled", template.getEnabled());
        return ResponseEntity.ok(body);
    }

    /**
     * Accepts DW Long id or function-unit {@code code}. Admin-center UUIDs are not valid here.
     */
    private Long resolveFunctionUnitId(String functionUnitRef) {
        if (!StringUtils.hasText(functionUnitRef)) {
            throw new ResourceNotFoundException("FunctionUnit", functionUnitRef);
        }
        String ref = functionUnitRef.trim();
        if (ref.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(ref);
        }
        return functionUnitRepository.findByCode(ref)
                .map(FunctionUnit::getId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", ref));
    }
}
