package com.developer.service.impl;

import com.developer.dto.AiPromptTemplateResponse;
import com.developer.entity.AiPromptTemplate;
import com.developer.exception.AiGenerationException;
import com.developer.repository.AiPromptTemplateRepository;
import com.developer.service.AiPromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三段 AI 提示词的内置默认值 + 运行时覆盖值。
 *
 * <p>内置默认值在启动时从 classpath 一次性读入并校验——缺文件/空文件直接启动失败，不留到调用时才发现
 * （这是本类从 {@link AiPromptBuilder} 接管过来的原有行为）。覆盖值存 {@code dw_ai_prompt_templates}，
 * 每轮对话现取，因此在 UI 里改完立即对所有实例生效，不需要重启或重新部署。</p>
 */
@Slf4j
@Service
public class AiPromptTemplateServiceImpl implements AiPromptTemplateService {

    private static final String RESOURCE_DIR = "ai-prompts/";
    private static final List<String> PHASES = List.of("REQUIREMENTS", "DESIGN", "GENERATION");
    private static final String SOURCE_BUILT_IN = "BUILT_IN";
    private static final String SOURCE_CUSTOM = "CUSTOM";

    /** phase → 内置默认提示词全文 */
    private final Map<String, String> defaults;

    private final AiPromptTemplateRepository repository;

    public AiPromptTemplateServiceImpl(AiPromptTemplateRepository repository) {
        this.repository = repository;
        this.defaults = loadDefaults();
    }

    /** 启动即加载并校验三段提示词——缺文件直接启动失败，不留到调用时才发现。 */
    private static Map<String, String> loadDefaults() {
        Map<String, String> loaded = new LinkedHashMap<>();
        for (String phase : PHASES) {
            String path = RESOURCE_DIR + phase.toLowerCase() + ".txt";
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (text.isBlank()) {
                    throw new IllegalStateException("AI prompt resource is empty: " + path);
                }
                loaded.put(phase, text);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load AI prompt resource: " + path, e);
            }
        }
        log.info("Loaded built-in AI prompt templates: {}", loaded.keySet());
        return Map.copyOf(loaded);
    }

    @Override
    public List<String> phases() {
        return PHASES;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiPromptTemplateResponse> list() {
        List<AiPromptTemplateResponse> result = new ArrayList<>(PHASES.size());
        for (String phase : PHASES) {
            result.add(get(phase));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AiPromptTemplateResponse get(String phase) {
        String normalized = requireKnownPhase(phase);
        return repository.findByPhase(normalized)
                .map(override -> toResponse(normalized, override))
                .orElseGet(() -> builtInResponse(normalized));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolve(String phase) {
        String normalized = requireKnownPhase(phase);
        return repository.findByPhase(normalized)
                .map(AiPromptTemplate::getContent)
                .orElseGet(() -> defaults.get(normalized));
    }

    @Override
    @Transactional
    public AiPromptTemplateResponse save(String phase, String content) {
        String normalized = requireKnownPhase(phase);
        if (content == null || content.isBlank()) {
            throw new AiGenerationException("AI_PROMPT_CONTENT_EMPTY",
                    "AI prompt content must not be empty: " + normalized);
        }
        AiPromptTemplate entity = repository.findByPhase(normalized)
                .orElseGet(() -> AiPromptTemplate.builder().phase(normalized).build());
        entity.setContent(content);
        AiPromptTemplate saved = repository.save(entity);
        log.info("AI prompt template overridden: phase={} chars={} by={}",
                normalized, content.length(), saved.getUpdatedBy());
        return toResponse(normalized, saved);
    }

    @Override
    @Transactional
    public AiPromptTemplateResponse reset(String phase) {
        String normalized = requireKnownPhase(phase);
        repository.deleteByPhase(normalized);
        log.info("AI prompt template override removed, reverting to built-in: phase={}", normalized);
        return builtInResponse(normalized);
    }

    /** phase 白名单校验：非三者之一直接拒，不做就近匹配也不落默认相位。 */
    private String requireKnownPhase(String phase) {
        String normalized = phase != null ? phase.trim().toUpperCase() : "";
        if (!defaults.containsKey(normalized)) {
            throw new AiGenerationException("AI_PROMPT_PHASE_UNKNOWN",
                    "Unknown AI prompt phase: " + phase + " (expected one of " + PHASES + ")");
        }
        return normalized;
    }

    private AiPromptTemplateResponse builtInResponse(String phase) {
        String builtIn = defaults.get(phase);
        return AiPromptTemplateResponse.builder()
                .phase(phase)
                .content(builtIn)
                .source(SOURCE_BUILT_IN)
                .defaultContent(builtIn)
                .build();
    }

    private AiPromptTemplateResponse toResponse(String phase, AiPromptTemplate override) {
        return AiPromptTemplateResponse.builder()
                .phase(phase)
                .content(override.getContent())
                .source(SOURCE_CUSTOM)
                .defaultContent(defaults.get(phase))
                .updatedBy(override.getUpdatedBy())
                .updatedAt(override.getUpdatedAt())
                .build();
    }
}
