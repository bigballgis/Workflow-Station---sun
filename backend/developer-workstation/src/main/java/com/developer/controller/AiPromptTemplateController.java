package com.developer.controller;

import com.developer.component.AiPromptTemplateComponent;
import com.developer.dto.AiPromptTemplateResponse;
import com.developer.dto.AiPromptTemplateUpdateRequest;
import com.developer.security.RequireDeveloperPermission;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * AI 提示词管理：读取 / 覆盖 / 还原三段系统提示词（REQUIREMENTS、DESIGN、GENERATION）。
 *
 * <p>存在的意义是让提示词能在运行时改，不必为了调一句话重新构建镜像重新部署。写入的是
 * {@code dw_ai_prompt_templates}，{@code AiPromptBuilder} 每轮对话现取，所以保存后下一轮就生效。
 * 没有覆盖行的相位继续用镜像里的 {@code resources/ai-prompts/<phase>.txt}，从而跟随代码仓库演进。</p>
 *
 * <p>导入/导出是纯前端行为（读本地 .txt → 调本控制器的 PUT；导出 = 把 GET 到的正文存成 .txt），
 * 因此这里不提供 multipart 上传或文件下载端点。</p>
 *
 * <p>与 {@link AiGenerationController} 共用 {@code ai-generation.enabled} 开关：关掉时整组端点返回 404。
 * 提示词是平台级配置（改动影响所有人使用 AI 生成），权限沿用写侧的 {@code FUNCTION_UNIT_UPDATE}。</p>
 */
@RestController
@RequestMapping("/ai-generation/prompt-templates")
@ConditionalOnProperty(prefix = "ai-generation", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Prompt Templates", description = "Runtime editing of the AI generation system prompts")
public class AiPromptTemplateController extends BaseController {

    /** 路径参数白名单——非三者之一直接 400，不做就近匹配（防参数篡改类告警）。 */
    private static final Set<String> ALLOWED_PHASES = Set.of("REQUIREMENTS", "DESIGN", "GENERATION");

    private final AiPromptTemplateComponent aiPromptTemplateComponent;

    @GetMapping
    @Operation(summary = "List the three AI prompts with their current content and source")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<AiPromptTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(aiPromptTemplateComponent.list()));
    }

    @GetMapping("/{phase}")
    @Operation(summary = "Get one AI prompt with its current content and source")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<AiPromptTemplateResponse>> get(@PathVariable("phase") String phase) {
        String normalized = normalizePhase(phase);
        if (normalized == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ApiResponse.success(aiPromptTemplateComponent.get(normalized)));
    }

    @PutMapping("/{phase}")
    @Operation(summary = "Override one AI prompt (used by both the editor and file import)")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<AiPromptTemplateResponse>> update(
            @PathVariable("phase") String phase,
            @Valid @RequestBody AiPromptTemplateUpdateRequest request) {
        String normalized = normalizePhase(phase);
        if (normalized == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ApiResponse.success(
                aiPromptTemplateComponent.save(normalized, request.getContent())));
    }

    @DeleteMapping("/{phase}")
    @Operation(summary = "Drop the override and revert this prompt to the built-in default")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<AiPromptTemplateResponse>> reset(@PathVariable("phase") String phase) {
        String normalized = normalizePhase(phase);
        if (normalized == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ApiResponse.success(aiPromptTemplateComponent.reset(normalized)));
    }

    /** 白名单归一化；不在白名单返回 null，由调用点转 400。 */
    private static String normalizePhase(String phase) {
        String normalized = phase != null ? phase.trim().toUpperCase() : "";
        return ALLOWED_PHASES.contains(normalized) ? normalized : null;
    }
}
