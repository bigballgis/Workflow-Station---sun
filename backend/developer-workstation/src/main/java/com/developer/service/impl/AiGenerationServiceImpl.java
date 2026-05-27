package com.developer.service.impl;

import com.developer.dto.AiChatSseEvent;
import com.developer.dto.AiMessageResponse;
import com.developer.dto.AiSessionResponse;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.AiDocument;
import com.developer.entity.AiMessage;
import com.developer.entity.AiSession;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ActionDefinition;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.entity.FormTableBinding;
import com.developer.entity.Icon;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.entity.TableRelation;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import com.developer.exception.AiGenerationException;
import com.platform.common.security.SsrfProtection;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.AiGenerationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * AI generation service implementation.
 */
@Service
@Slf4j
public class AiGenerationServiceImpl implements AiGenerationService {

    private final AiSessionRepository aiSessionRepository;
    private final AiMessageRepository aiMessageRepository;
    private final AiDocumentRepository aiDocumentRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final ObjectMapper objectMapper;
    private final int maxContextSizeBytes;

    @Value("${n8n.ai-generation.webhook-url:http://localhost:5678/webhook/ai-function-unit-gen}")
    private String n8nWebhookUrl;

    @Value("${n8n.ai-generation.timeout-seconds:120}")
    private int n8nTimeoutSeconds;

    @Value("${ssrf.allowed-hosts:localhost,n8n}")
    private List<String> ssrfAllowedHosts;

    /** Cached N8N RestTemplate (initialized at startup via @PostConstruct) */
    private RestTemplate n8nRestTemplate;

    /** Chat SSE emitters: key = "functionUnitId:userId" → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> chatEmitters = new ConcurrentHashMap<>();

    /** Event SSE emitters: key = functionUnitId → list of (userId, SseEmitter) pairs */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<EventEmitterEntry>> eventEmitters = new ConcurrentHashMap<>();

    /** Timestamp of the last successful N8N call, used for degradation info (requirements 45 linkage) */
    private volatile Instant lastN8NSuccessTime;

    public AiGenerationServiceImpl(
            AiSessionRepository aiSessionRepository,
            AiMessageRepository aiMessageRepository,
            AiDocumentRepository aiDocumentRepository,
            FunctionUnitRepository functionUnitRepository,
            ObjectMapper objectMapper,
            @Value("${ai-generation.context.max-size-bytes:102400}") int maxContextSizeBytes) {
        this.aiSessionRepository = aiSessionRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.aiDocumentRepository = aiDocumentRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.objectMapper = objectMapper;
        this.maxContextSizeBytes = maxContextSizeBytes;
    }

    @PostConstruct
    void initN8NRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = n8nTimeoutSeconds * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.n8nRestTemplate = new RestTemplate(factory);
        Set<String> allowedHosts = ssrfAllowedHosts.stream()
                .map(h -> h.trim().toLowerCase())
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toSet());
        SsrfProtection.validate(n8nWebhookUrl, allowedHosts);
        log.info("Initialized N8N RestTemplate with timeout={}ms, webhookUrl validated (allowedHosts={})",
                timeoutMs, allowedHosts);
    }

    // ==================== Session Management ====================

    @Override
    @Transactional
    public AiSession createSession(Long functionUnitId, String userId, AiMode mode) {
        AiMode resolvedMode = mode != null ? mode : determineMode(functionUnitId);

        AiSession session = AiSession.builder()
                .sessionId(UUID.randomUUID())
                .functionUnitId(functionUnitId)
                .userId(userId)
                .currentPhase(AiPhase.REQUIREMENTS)
                .mode(resolvedMode)
                .status(AiSessionStatus.ACTIVE)
                .build();

        AiSession saved = aiSessionRepository.save(session);
        log.info("Created AI session: sessionId={}, functionUnitId={}, userId={}, mode={}",
                saved.getSessionId(), functionUnitId, userId, resolvedMode);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AiSession restoreSession(String sessionId) {
        UUID uuid = parseSessionId(sessionId);
        return aiSessionRepository.findBySessionId(uuid)
                .orElseThrow(() -> new AiGenerationException("AI_SESSION_NOT_FOUND", "Session not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiSessionResponse> getSessionsByFunctionUnitId(Long functionUnitId) {
        List<AiSession> sessions = aiSessionRepository.findByFunctionUnitIdOrderByCreatedAtDesc(functionUnitId);
        return sessions.stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateSessionPhase(String sessionId, AiPhase phase) {
        UUID uuid = parseSessionId(sessionId);
        AiSession session = aiSessionRepository.findBySessionId(uuid)
                .orElseThrow(() -> new AiGenerationException("AI_SESSION_NOT_FOUND", "Session not found"));

        session.setCurrentPhase(phase);
        aiSessionRepository.save(session);
        log.info("Updated session phase: sessionId={}, phase={}", sessionId, phase);
    }

    @Override
    @Transactional
    public void updateSessionStatus(String sessionId, AiSessionStatus status) {
        UUID uuid = parseSessionId(sessionId);
        AiSession session = aiSessionRepository.findBySessionId(uuid)
                .orElseThrow(() -> new AiGenerationException("AI_SESSION_NOT_FOUND", "Session not found"));

        validateStatusTransition(session.getStatus(), status);

        session.setStatus(status);
        aiSessionRepository.save(session);
        log.info("Updated session status: sessionId={}, status={}", sessionId, status);
    }

    // ==================== Mode Detection ====================

    @Override
    @Transactional(readOnly = true)
    public AiMode determineMode(Long functionUnitId) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AiGenerationException("AI_FUNCTION_UNIT_NOT_FOUND", "Function unit not found"));

        boolean hasProcessDefinition = functionUnit.getProcessDefinition() != null;
        boolean hasTableDefinitions = functionUnit.getTableDefinitions() != null && !functionUnit.getTableDefinitions().isEmpty();
        boolean hasFormDefinitions = functionUnit.getFormDefinitions() != null && !functionUnit.getFormDefinitions().isEmpty();
        boolean hasActionDefinitions = functionUnit.getActionDefinitions() != null && !functionUnit.getActionDefinitions().isEmpty();
        boolean hasDecisionDefinitions = functionUnit.getDecisionDefinitions() != null && !functionUnit.getDecisionDefinitions().isEmpty();

        if (hasProcessDefinition || hasTableDefinitions || hasFormDefinitions || hasActionDefinitions || hasDecisionDefinitions) {
            log.debug("FunctionUnit {} has existing component data, mode=MODIFY", functionUnitId);
            return AiMode.MODIFY;
        }

        log.debug("FunctionUnit {} has no component data, mode=NEW", functionUnitId);
        return AiMode.NEW;
    }

    // ==================== Message Persistence ====================

    @Override
    @Transactional
    public AiMessage saveMessage(UUID sessionId, AiMessageRole role, String content, AiPhase phase) {
        AiMessage message = AiMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .phase(phase)
                .build();

        AiMessage saved = aiMessageRepository.save(message);
        log.debug("Saved message: sessionId={}, role={}, phase={}", sessionId, role, phase);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiMessage> loadMessages(UUID sessionId) {
        return aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AiMessageResponse> getMessagesPaged(String sessionId, Pageable pageable) {
        UUID uuid = parseSessionId(sessionId);
        Page<AiMessage> messagePage = aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(uuid, pageable);
        return messagePage.map(this::toMessageResponse);
    }

    // ==================== Document Version Management ====================

    @Override
    @Transactional
    public AiDocument saveDocument(Long functionUnitId, AiDocumentType documentType, String content, String summary, String userId) {
        Integer maxVersion = aiDocumentRepository
                .findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(functionUnitId, documentType)
                .map(AiDocument::getVersion)
                .orElse(0);

        int newVersion = maxVersion + 1;

        AiDocument document = AiDocument.builder()
                .functionUnitId(functionUnitId)
                .documentType(documentType)
                .version(newVersion)
                .content(content)
                .summary(summary)
                .createdBy(userId)
                .build();

        AiDocument saved = aiDocumentRepository.save(document);
        log.info("Saved document: functionUnitId={}, type={}, version={}", functionUnitId, documentType, newVersion);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiDocument> getDocumentVersions(Long functionUnitId, AiDocumentType documentType) {
        return aiDocumentRepository.findByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(functionUnitId, documentType);
    }

    @Override
    @Transactional(readOnly = true)
    public AiDocument getDocumentByVersion(Long functionUnitId, AiDocumentType documentType, Integer version) {
        return aiDocumentRepository.findByFunctionUnitIdAndDocumentTypeAndVersion(functionUnitId, documentType, version)
                .orElseThrow(() -> new AiGenerationException("AI_DOCUMENT_NOT_FOUND",
                        String.format("Document version not found: functionUnitId=%d, type=%s, version=%d", functionUnitId, documentType, version)));
    }

    // ==================== Context Serialization ====================

    @Override
    @Transactional(readOnly = true)
    public FunctionUnitContextDTO serializeFunctionUnitContext(Long functionUnitId) {
        // Use findById instead of findByIdWithRelations to avoid MultipleBagFetchException
        // (Hibernate does not allow fetching multiple List-type associations simultaneously)
        // Within @Transactional, lazy loading will load associated collections one by one
        FunctionUnit fu = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AiGenerationException("AI_FUNCTION_UNIT_NOT_FOUND", "Function unit not found"));

        FunctionUnitContextDTO dto = buildContextDTO(fu);

        // Check size and truncate if needed
        byte[] jsonBytes = toJsonBytes(dto);
        if (jsonBytes.length <= maxContextSizeBytes) {
            return dto;
        }

        // First pass: truncate bpmnXml
        log.info("Context size {}B exceeds limit {}B for functionUnitId={}, truncating bpmnXml",
                jsonBytes.length, maxContextSizeBytes, functionUnitId);
        truncateBpmnXml(dto);
        jsonBytes = toJsonBytes(dto);
        if (jsonBytes.length <= maxContextSizeBytes) {
            return dto;
        }

        // Second pass: truncate all configJson
        log.info("Context still {}B after bpmnXml truncation, truncating configJson", jsonBytes.length);
        truncateConfigJson(dto);
        jsonBytes = toJsonBytes(dto);
        if (jsonBytes.length <= maxContextSizeBytes) {
            return dto;
        }

        throw new AiGenerationException("AI_CONTEXT_TOO_LARGE",
                String.format("Serialized context size %dB exceeds limit %dB", jsonBytes.length, maxContextSizeBytes));
    }

    private FunctionUnitContextDTO buildContextDTO(FunctionUnit fu) {
        // Explicitly trigger lazy loading (ensure all associations are loaded within @Transactional)
        List<TableDefinition> tables = fu.getTableDefinitions();
        if (tables != null) tables.size();
        List<FormDefinition> forms = fu.getFormDefinitions();
        if (forms != null) forms.size();
        List<ActionDefinition> actions = fu.getActionDefinitions();
        if (actions != null) actions.size();
        List<DecisionDefinition> decisions = fu.getDecisionDefinitions();
        if (decisions != null) decisions.size();
        List<TableRelation> relations = fu.getTableRelations();
        if (relations != null) relations.size();
        ProcessDefinition pd = fu.getProcessDefinition();
        Icon icon = fu.getIcon();

        return FunctionUnitContextDTO.builder()
                .functionUnitId(fu.getId())
                .name(fu.getName())
                .description(fu.getDescription())
                .tableDefinitions(serializeTableDefinitions(tables))
                .formDefinitions(serializeFormDefinitions(forms))
                .actionDefinitions(serializeActionDefinitions(actions))
                .decisionDefinitions(serializeDecisionDefinitions(decisions))
                .tableRelations(serializeTableRelations(relations, tables))
                .processDefinition(serializeProcessDefinition(pd))
                .icon(serializeIcon(icon))
                .build();
    }

    private List<Map<String, Object>> serializeTableDefinitions(List<TableDefinition> tables) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        return tables.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tableName", t.getTableName());
            map.put("tableType", t.getTableType() != null ? t.getTableType().name() : null);
            map.put("tableDisplayName", t.getTableDisplayName());
            map.put("description", t.getDescription());
            map.put("fieldDefinitions", serializeFieldDefinitions(t.getFieldDefinitions()));
            map.put("foreignKeys", serializeForeignKeys(t.getForeignKeys()));
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeFieldDefinitions(List<FieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fieldName", f.getFieldName());
            map.put("dataType", f.getDataType() != null ? f.getDataType().name() : null);
            map.put("length", f.getLength());
            map.put("precision", f.getPrecision());
            map.put("scale", f.getScale());
            map.put("nullable", f.getNullable());
            map.put("defaultValue", f.getDefaultValue());
            map.put("isPrimaryKey", f.getIsPrimaryKey());
            map.put("isUnique", f.getIsUnique());
            map.put("description", f.getDescription());
            map.put("sortOrder", f.getSortOrder());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeForeignKeys(List<ForeignKey> foreignKeys) {
        if (foreignKeys == null || foreignKeys.isEmpty()) {
            return List.of();
        }
        return foreignKeys.stream().map(fk -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fieldName", fk.getFieldDefinition() != null ? fk.getFieldDefinition().getFieldName() : null);
            map.put("refTableName", fk.getRefTableDefinition() != null ? fk.getRefTableDefinition().getTableName() : null);
            map.put("refFieldName", fk.getRefFieldDefinition() != null ? fk.getRefFieldDefinition().getFieldName() : null);
            map.put("onDelete", fk.getOnDelete());
            map.put("onUpdate", fk.getOnUpdate());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeFormDefinitions(List<FormDefinition> forms) {
        if (forms == null || forms.isEmpty()) {
            return List.of();
        }
        return forms.stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("formName", f.getFormName());
            map.put("formType", f.getFormType() != null ? f.getFormType().name() : null);
            map.put("configJson", f.getConfigJson());
            map.put("description", f.getDescription());
            map.put("tableBindings", serializeTableBindings(f.getTableBindings()));
            map.put("fieldPermissions", f.getFieldPermissions() != null ? f.getFieldPermissions() : Map.of());
            map.put("showLiveValues", f.getShowLiveValues());
            map.put("stageBindings", serializeStageBindings(f.getStageBindings()));
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeStageBindings(List<FormStageBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("stageId", b.getStageId());
            map.put("stageName", b.getStageName());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeTableBindings(List<FormTableBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tableName", b.getTableName());
            map.put("bindingType", b.getBindingType() != null ? b.getBindingType().name() : null);
            map.put("bindingMode", b.getBindingMode() != null ? b.getBindingMode().name() : null);
            map.put("foreignKeyField", b.getForeignKeyField());
            map.put("sortOrder", b.getSortOrder());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeActionDefinitions(List<ActionDefinition> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        return actions.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("actionName", a.getActionName());
            map.put("actionType", a.getActionType() != null ? a.getActionType().name() : null);
            map.put("configJson", a.getConfigJson());
            map.put("icon", a.getIcon());
            map.put("buttonColor", a.getButtonColor());
            map.put("description", a.getDescription());
            map.put("isDefault", a.getIsDefault());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeDecisionDefinitions(List<DecisionDefinition> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return List.of();
        }
        return decisions.stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("decisionKey", d.getDecisionKey());
            map.put("decisionName", d.getDecisionName());
            map.put("dmnXml", d.getDmnXml());
            map.put("hitPolicy", d.getHitPolicy());
            map.put("description", d.getDescription());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Serialize table relations, resolving sourceTableId/targetTableId to corresponding tableName
     * (AI does not know internal IDs).
     */
    private List<Map<String, Object>> serializeTableRelations(
            List<TableRelation> relations, List<TableDefinition> tables) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }

        // Build ID → tableName lookup table
        Map<Long, String> idToName = new HashMap<>();
        if (tables != null) {
            for (TableDefinition t : tables) {
                if (t.getId() != null) {
                    idToName.put(t.getId(), t.getTableName());
                }
            }
        }

        return relations.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceTableName", idToName.getOrDefault(r.getSourceTableId(), "unknown_" + r.getSourceTableId()));
            map.put("sourceFieldName", r.getSourceFieldName());
            map.put("relationType", r.getRelationType());
            map.put("targetTableName", idToName.getOrDefault(r.getTargetTableId(), "unknown_" + r.getTargetTableId()));
            map.put("targetFieldName", r.getTargetFieldName());
            return map;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> serializeProcessDefinition(ProcessDefinition pd) {
        if (pd == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bpmnXml", pd.getBpmnXml());
        return map;
    }

    private Map<String, Object> serializeIcon(Icon icon) {
        if (icon == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", icon.getName());
        map.put("category", icon.getCategory() != null ? icon.getCategory().name() : null);
        map.put("svgContent", icon.getSvgContent());
        map.put("description", icon.getDescription());
        return map;
    }

    private byte[] toJsonBytes(FunctionUnitContextDTO dto) {
        try {
            return objectMapper.writeValueAsBytes(dto);
        } catch (JsonProcessingException e) {
            throw new AiGenerationException("AI_CONTEXT_SERIALIZATION_ERROR", "Function unit context serialization failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void truncateBpmnXml(FunctionUnitContextDTO dto) {
        Map<String, Object> pd = dto.getProcessDefinition();
        if (pd != null && pd.get("bpmnXml") instanceof String bpmnXml) {
            if (bpmnXml.length() > 200) {
                pd.put("bpmnXml", bpmnXml.substring(0, 200) + "...[truncated]");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void truncateConfigJson(FunctionUnitContextDTO dto) {
        // Only truncate configJson on formDefinitions, not actionDefinitions
        if (dto.getFormDefinitions() == null) return;

        // Level 1 truncation: only truncate the rule array in configJson, keep business logic extension fields
        for (Map<String, Object> form : dto.getFormDefinitions()) {
            Object configObj = form.get("configJson");
            if (configObj instanceof Map) {
                Map<String, Object> config = (Map<String, Object>) configObj;
                if (config.containsKey("rule") && config.get("rule") instanceof List) {
                    List<?> rules = (List<?>) config.get("rule");
                    int originalCount = rules.size();
                    Map<String, Object> truncatedConfig = new LinkedHashMap<>(config);
                    truncatedConfig.put("rule", List.of(
                            new LinkedHashMap<>(Map.of("truncated", true, "originalCount", originalCount))
                    ));
                    form.put("configJson", truncatedConfig);
                    log.info("Tier-1 truncation: form '{}' rule array truncated ({} rules removed)",
                            form.get("formName"), originalCount);
                }
            }
        }

        // Check if still over limit
        byte[] jsonBytes = toJsonBytes(dto);
        if (jsonBytes.length <= maxContextSizeBytes) return;

        // Level 2 truncation: replace entire configJson on formDefinitions
        for (Map<String, Object> form : dto.getFormDefinitions()) {
            form.put("configJson", new LinkedHashMap<>(Map.of("truncated", true)));
            log.info("Tier-2 truncation: form '{}' entire configJson truncated", form.get("formName"));
        }
    }

    // ==================== N8N Session Memory Rebuild ====================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, String>> buildConversationHistory(UUID sessionId) {
        List<AiMessage> messages = aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return messages.stream()
                .map(msg -> Map.of(
                        "role", msg.getRole().name().toLowerCase(),
                        "content", msg.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, String>> getLatestDocuments(Long functionUnitId, AiPhase phase, AiMode mode) {
        List<Map<String, String>> documents = new ArrayList<>();

        switch (phase) {
            case REQUIREMENTS:
        // Whether NEW or MODIFY, always load existing requirements documents if present.
        // The user may have generated docs in the current session; subsequent messages need the AI to see them.
                aiDocumentRepository.findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(
                        functionUnitId, AiDocumentType.REQUIREMENTS)
                    .ifPresent(doc -> documents.add(Map.of(
                        "documentType", doc.getDocumentType().name(),
                        "content", doc.getContent())));
                break;
            case DESIGN:
                aiDocumentRepository.findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(
                        functionUnitId, AiDocumentType.REQUIREMENTS)
                    .ifPresent(doc -> documents.add(Map.of(
                        "documentType", doc.getDocumentType().name(),
                        "content", doc.getContent())));
                aiDocumentRepository.findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(
                        functionUnitId, AiDocumentType.DESIGN)
                    .ifPresent(doc -> documents.add(Map.of(
                        "documentType", doc.getDocumentType().name(),
                        "content", doc.getContent())));
                break;
            case GENERATION:
                aiDocumentRepository.findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(
                        functionUnitId, AiDocumentType.REQUIREMENTS)
                    .ifPresent(doc -> documents.add(Map.of(
                        "documentType", doc.getDocumentType().name(),
                        "content", doc.getContent())));
                aiDocumentRepository.findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(
                        functionUnitId, AiDocumentType.DESIGN)
                    .ifPresent(doc -> documents.add(Map.of(
                        "documentType", doc.getDocumentType().name(),
                        "content", doc.getContent())));
                break;
        }

        return documents;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> callN8NWebhook(UUID sessionId, String message, AiPhase phase, AiMode mode,
                                               FunctionUnitContextDTO context, Long functionUnitId,
                                               List<Map<String, String>> existingDocuments,
                                               String regenerateScope) {
        Map<String, Object> requestBody = buildN8NRequestBody(sessionId, message, phase, mode,
                context, functionUnitId, existingDocuments, null, regenerateScope);

        Map<String, Object> response = doCallN8NWebhookWithRetry(requestBody);

        if (isSessionNotFoundError(response)) {
            log.warn("N8N session not found for sessionId={}, rebuilding", sessionId);
            List<Map<String, String>> conversationHistory = buildConversationHistory(sessionId);

            // Reload context and documents using functionUnitId
            FunctionUnitContextDTO rebuiltContext = serializeFunctionUnitContext(functionUnitId);
            List<Map<String, String>> rebuiltDocs = getLatestDocuments(functionUnitId, phase, mode);

            Map<String, Object> retryBody = buildN8NRequestBody(sessionId, message, phase, mode,
                    rebuiltContext, functionUnitId, rebuiltDocs, conversationHistory, regenerateScope);
            response = doCallN8NWebhookWithRetry(retryBody);
        }

        return response;
    }

    private Map<String, Object> buildN8NRequestBody(UUID sessionId, String message, AiPhase phase, AiMode mode,
                                                     FunctionUnitContextDTO context, Long functionUnitId,
                                                     List<Map<String, String>> existingDocuments,
                                                     List<Map<String, String>> conversationHistory,
                                                     String regenerateScope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId.toString());
        body.put("message", message);
        body.put("phase", phase.name());
        body.put("mode", mode.name());

        // Always pass functionUnitId to N8N for the Agent tool node to query the database.
        // Even if context is null (new function unit not yet generated), pass functionUnitId.
        if (functionUnitId != null) {
            body.put("functionUnitId", functionUnitId);
        } else if (context != null && context.getFunctionUnitId() != null) {
            body.put("functionUnitId", context.getFunctionUnitId());
        }

        if (context != null) {
            // Pre-serialize to JSON string to avoid N8N expression rendering as [object Object]
            body.put("context", toJsonString(context));
        }

        if (existingDocuments != null && !existingDocuments.isEmpty()) {
            List<Map<String, String>> truncated = truncateDocuments(existingDocuments);
            body.put("existingDocuments", formatDocumentsForPrompt(truncated));
        }

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            body.put("conversationHistory", conversationHistory);
        }

        // Inject new architecture metadata to help AI generate data structures that conform to the new architecture (requirement 15)
        body.put("schemaMetadata", buildSchemaMetadata());

        // Instruct AI to include explanations when generating data (requirement 50)
        body.put("includeExplanations", true);

        // Incremental regeneration scope (requirement 42)
        body.put("regenerateScope", regenerateScope != null ? regenerateScope : "ALL");

        return body;
    }

    /**
     * Build new architecture metadata, including enum value lists, configJson extension
     * structure descriptions, ConditionExpression format, and new entity structures.
     * Provides N8N/AI with understanding of the current system architecture to generate
     * standard-compliant data.
     */
    private Map<String, Object> buildSchemaMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();

        // Enum value list
        metadata.put("formTypes", java.util.Arrays.stream(com.developer.enums.FormType.values())
                .map(Enum::name).collect(Collectors.toList()));
        metadata.put("tableTypes", java.util.Arrays.stream(com.developer.enums.TableType.values())
                .map(Enum::name).collect(Collectors.toList()));
        metadata.put("actionTypes", java.util.Arrays.stream(com.developer.enums.ActionType.values())
                .map(Enum::name).collect(Collectors.toList()));

        // configJson extension field structure description
        Map<String, Object> configJsonExtensions = new LinkedHashMap<>();
        configJsonExtensions.put("formulas", "Array of { targetField, expression, dependsOn[] }");
        configJsonExtensions.put("linkages", "Array of { sourceField, targetField, linkageType: option-filtering|value-auto-fill|field-state-change }");
        configJsonExtensions.put("crossFieldRules", "Array of { fields[], operator, message, targetField }");
        configJsonExtensions.put("summaryRules", "Array of { sourceColumn, targetField, aggregation: SUM|AVG|COUNT|MIN|MAX }");
        configJsonExtensions.put("subTableValidation", "Object with sub-table validation rules");
        metadata.put("configJsonExtensions", configJsonExtensions);

        // visibilityCondition format description
        Map<String, Object> visibilityConditionFormat = new LinkedHashMap<>();
        visibilityConditionFormat.put("type", "ConditionExpression object (not a string)");
        visibilityConditionFormat.put("structure", "{ field, operator, value }");
        visibilityConditionFormat.put("validOperators", List.of(
                "equals", "not-equals", "contains", "greater-than", "less-than", "is-empty", "is-not-empty"));
        metadata.put("visibilityConditionFormat", visibilityConditionFormat);

        // New entity structures
        Map<String, Object> newEntities = new LinkedHashMap<>();
        newEntities.put("decisionDefinitions", "Array of { decisionKey, decisionName, dmnXml, hitPolicy: FIRST|UNIQUE|PRIORITY|ANY|COLLECT|RULE_ORDER|OUTPUT_ORDER, description }");
        newEntities.put("tableRelations", "Array of { sourceTableName, sourceFieldName, relationType: ONE_TO_ONE|ONE_TO_MANY|MANY_TO_MANY, targetTableName, targetFieldName }");
        newEntities.put("formStageBindings", "Array of { stageId, stageName } within formDefinitions[].stageBindings");
        metadata.put("newEntities", newEntities);

        return metadata;
    }

    /**
     * Wraps doCallN8NWebhook with an automatic retry (2-second delay) for AI_N8N_TIMEOUT
     * and AI_N8N_CALL_FAILED exceptions.
     * On retry failure, builds degradation info and passes it to the caller via
     * AiGenerationException extraData (requirements 23 + 45 linkage).
     */
    private Map<String, Object> doCallN8NWebhookWithRetry(Map<String, Object> requestBody) {
        try {
            Map<String, Object> response = doCallN8NWebhook(requestBody);
            lastN8NSuccessTime = Instant.now();
            return response;
        } catch (AiGenerationException e) {
            if ("AI_N8N_TIMEOUT".equals(e.getErrorCode()) || "AI_N8N_CALL_FAILED".equals(e.getErrorCode())) {
                log.warn("N8N call failed with {}, retrying in 2 seconds...", e.getErrorCode());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                try {
                    Map<String, Object> response = doCallN8NWebhook(requestBody);
                    lastN8NSuccessTime = Instant.now();
                    return response;
                } catch (AiGenerationException retryEx) {
                    log.warn("N8N retry also failed with {}: {}", retryEx.getErrorCode(), retryEx.getMessage());
                    // Build degradation info (requirements 45 linkage)
                    Map<String, Object> degradationInfo = new LinkedHashMap<>();
                    degradationInfo.put("lastSuccessTime",
                            lastN8NSuccessTime != null ? lastN8NSuccessTime.toString() : null);
                    degradationInfo.put("degradationOptions", List.of("SAVE_DRAFT", "MANUAL_CREATE"));
                    throw new AiGenerationException(e.getErrorCode(), e.getMessage(), degradationInfo);
                }
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doCallN8NWebhook(Map<String, Object> requestBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> responseEntity = n8nRestTemplate.postForEntity(n8nWebhookUrl, entity, Map.class);
            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody == null) {
                throw new AiGenerationException("AI_N8N_EMPTY_RESPONSE", "N8N returned empty response");
            }
            return responseBody;
        } catch (AiGenerationException e) {
            throw e;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new AiGenerationException("AI_N8N_TIMEOUT", "N8N Webhook call timed out: " + e.getMessage());
        } catch (Exception e) {
            throw new AiGenerationException("AI_N8N_CALL_FAILED", "N8N Webhook call failed: " + e.getMessage());
        }
    }

    private boolean isSessionNotFoundError(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        // Check for error field indicating session not found
        Object error = response.get("error");
        if (error instanceof String errorStr) {
            String lower = errorStr.toLowerCase();
            return lower.contains("session") && lower.contains("not found");
        }
        // Check for error code
        Object errorCode = response.get("errorCode");
        if (errorCode instanceof String codeStr) {
            return "SESSION_NOT_FOUND".equalsIgnoreCase(codeStr);
        }
        // Check message field as fallback
        Object msg = response.get("message");
        if (msg instanceof String msgStr) {
            String lower = msgStr.toLowerCase();
            return lower.contains("session") && (lower.contains("not found") || lower.contains("not exist") || lower.contains("does not exist"));
        }
        return false;
    }

    // ==================== SSE Emitter Management ====================

    private static final long EVENT_EMITTER_TIMEOUT = 300_000L; // 300 seconds
    private static final int MAX_DOCUMENT_CONTENT_LENGTH = 50000;

    @Override
    public SseEmitter createChatEmitter(Long functionUnitId, String userId) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        long chatEmitterTimeout = (long) n8nTimeoutSeconds * 2 * 1000 + 60_000L;
        SseEmitter emitter = new SseEmitter(chatEmitterTimeout);

        // If there's already an active emitter, complete it first to prevent overwriting
        SseEmitter existingEmitter = chatEmitters.get(key);
        if (existingEmitter != null) {
            log.warn("Existing chat SSE emitter found for key={}, completing it before creating new one", key);
            try {
                existingEmitter.complete();
            } catch (Exception e) {
                log.debug("Failed to complete existing emitter: {}", e.getMessage());
            }
            chatEmitters.remove(key);
        }

        emitter.onCompletion(() -> {
            chatEmitters.remove(key);
            log.debug("Chat SSE completed: functionUnitId={}, userId={}", functionUnitId, userId);
        });
        emitter.onTimeout(() -> {
            chatEmitters.remove(key);
            log.debug("Chat SSE timed out: functionUnitId={}, userId={}", functionUnitId, userId);
            safeComplete(emitter);
        });
        emitter.onError(ex -> {
            log.warn("Chat SSE error for functionUnit {}, userId {}: {}", functionUnitId, userId, ex.getMessage());
            chatEmitters.remove(key);
            safeComplete(emitter);
        });

        chatEmitters.put(key, emitter);
        log.info("Created chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        return emitter;
    }

    @Override
    public SseEmitter createEventEmitter(Long functionUnitId, String userId) {
        SseEmitter emitter = new SseEmitter(EVENT_EMITTER_TIMEOUT);
        EventEmitterEntry entry = new EventEmitterEntry(userId, emitter);

        CopyOnWriteArrayList<EventEmitterEntry> entries = eventEmitters.computeIfAbsent(
                functionUnitId, k -> new CopyOnWriteArrayList<>());

        emitter.onCompletion(() -> {
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            log.debug("Event SSE completed: functionUnitId={}, userId={}", functionUnitId, userId);
        });
        emitter.onTimeout(() -> {
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            log.debug("Event SSE timed out: functionUnitId={}, userId={}", functionUnitId, userId);
            safeComplete(emitter);
        });
        emitter.onError(ex -> {
            log.warn("Event SSE error for functionUnit {}, userId {}: {}", functionUnitId, userId, ex.getMessage());
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            safeComplete(emitter);
        });

        entries.add(entry);
        log.info("Created event SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        return emitter;
    }

    @Override
    public void sendChatEvent(Long functionUnitId, String userId, AiChatSseEvent event) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        SseEmitter emitter = chatEmitters.get(key);
        if (emitter == null) {
            log.warn("No chat SSE emitter found for functionUnitId={}, userId={}", functionUnitId, userId);
            return;
        }
        try {
            SseEmitter.SseEventBuilder sseEvent = SseEmitter.event().name(event.getEventType());
            if (event.getData() != null) {
                Object data = event.getData();
                // Plain strings (token stream, phase name) as text — avoids JSON quoting edge cases
                // on very large markdown payloads when frontends concatenate raw data lines.
                if (data instanceof String str) {
                    sseEvent.data(str, MediaType.TEXT_PLAIN);
                } else {
                    sseEvent.data(data, MediaType.APPLICATION_JSON);
                }
            } else {
                sseEvent.data("", MediaType.TEXT_PLAIN);
            }
            emitter.send(sseEvent);
        } catch (IOException e) {
            log.warn("Failed to send chat SSE event: functionUnitId={}, userId={}, error={}",
                    functionUnitId, userId, e.getMessage());
            chatEmitters.remove(key);
        } catch (IllegalStateException e) {
            log.warn("Chat SSE emitter already completed: functionUnitId={}, userId={}, error={}",
                    functionUnitId, userId, e.getMessage());
            chatEmitters.remove(key);
        }
    }

    @Override
    public void sendEventNotification(Long functionUnitId, AiChatSseEvent event) {
        CopyOnWriteArrayList<EventEmitterEntry> entries = eventEmitters.get(functionUnitId);
        if (entries == null || entries.isEmpty()) {
            log.debug("No event SSE emitters for functionUnitId={}", functionUnitId);
            return;
        }

        Iterator<EventEmitterEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            EventEmitterEntry entry = iterator.next();
            try {
                entry.emitter().send(SseEmitter.event()
                        .name(event.getEventType())
                        .data(event.getData(), MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.warn("Failed to send event SSE notification to userId={}: {}", entry.userId(), e.getMessage());
                entries.remove(entry);
            }
        }

        if (entries.isEmpty()) {
            eventEmitters.remove(functionUnitId, entries);
        }
    }

    @Override
    public void completeChatEmitter(Long functionUnitId, String userId) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        SseEmitter emitter = chatEmitters.remove(key);
        if (emitter != null) {
            safeComplete(emitter);
            log.debug("Completed chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        }
    }

    /**
     * Safely complete an SseEmitter, suppressing IllegalStateException caused by
     * Spring's async response finalization after the OutputStream was already committed by SSE.
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("Emitter already completed or response committed: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Failed to complete emitter: {}", e.getMessage());
        }
    }

    @Override
    public void removeChatEmitter(Long functionUnitId, String userId) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        chatEmitters.remove(key);
        log.debug("Removed chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
    }

    @Override
    public void removeEventEmitter(Long functionUnitId, String userId) {
        CopyOnWriteArrayList<EventEmitterEntry> entries = eventEmitters.get(functionUnitId);
        if (entries != null) {
            entries.removeIf(entry -> entry.userId().equals(userId));
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            log.debug("Removed event SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        }
    }

    private String buildChatEmitterKey(Long functionUnitId, String userId) {
        return functionUnitId + ":" + userId;
    }

    /** Internal record to track event emitter ownership */
    private record EventEmitterEntry(String userId, SseEmitter emitter) {}

    // ==================== Private Helpers ====================

    private AiSessionResponse toSessionResponse(AiSession session) {
        return AiSessionResponse.builder()
                .sessionId(session.getSessionId().toString())
                .functionUnitId(session.getFunctionUnitId())
                .currentPhase(session.getCurrentPhase())
                .mode(session.getMode())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private AiMessageResponse toMessageResponse(AiMessage message) {
        return AiMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSessionId().toString())
                .role(message.getRole())
                .content(message.getContent())
                .phase(message.getPhase())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void validateStatusTransition(AiSessionStatus currentStatus, AiSessionStatus newStatus) {
        // Idempotent transition: setting the same status again is a no-op.
        if (currentStatus == newStatus) {
            return;
        }

        if (currentStatus == AiSessionStatus.ACTIVE
                && (newStatus == AiSessionStatus.COMPLETED || newStatus == AiSessionStatus.CANCELLED)) {
            return; // Valid transitions
        }
        throw new AiGenerationException("AI_SESSION_INVALID_STATUS_TRANSITION",
                String.format("Invalid status transition: %s -> %s", currentStatus, newStatus));
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    /**
     * Format document list as human-readable plain text for AI to understand in systemMessage.
     * Avoids JSON strings (double-escaping makes it hard for AI to parse).
     */
    private String formatDocumentsForPrompt(List<Map<String, String>> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Map<String, String> doc = documents.get(i);
            String docType = doc.getOrDefault("documentType", "UNKNOWN");
            String content = doc.getOrDefault("content", "");
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("[").append(docType).append(" Document]\n");
            sb.append(content);
        }
        return sb.toString();
    }

    private List<Map<String, String>> truncateDocuments(List<Map<String, String>> documents) {
        return documents.stream().map(doc -> {
            String content = doc.get("content");
            if (content != null && content.length() > MAX_DOCUMENT_CONTENT_LENGTH) {
                log.warn("Document content exceeds {} chars, truncating: documentType={}",
                        MAX_DOCUMENT_CONTENT_LENGTH, doc.get("documentType"));
                content = content.substring(0, MAX_DOCUMENT_CONTENT_LENGTH) + "[truncated]";
            }
            return Map.of(
                    "documentType", doc.getOrDefault("documentType", ""),
                    "content", content != null ? content : "");
        }).collect(Collectors.toList());
    }

    private UUID parseSessionId(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            throw new AiGenerationException("AI_SESSION_INVALID_ID", "Invalid session ID format");
        }
    }
}
