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
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.Icon;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import com.developer.exception.AiGenerationException;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.AiGenerationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * AI 生成服务实现
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

    /** Cached N8N RestTemplate (created lazily, timeout configured from properties) */
    private volatile RestTemplate n8nRestTemplate;

    /** Chat SSE emitters: key = "functionUnitId:userId" → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> chatEmitters = new ConcurrentHashMap<>();

    /** Event SSE emitters: key = functionUnitId → list of (userId, SseEmitter) pairs */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<EventEmitterEntry>> eventEmitters = new ConcurrentHashMap<>();

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
                .orElseThrow(() -> new AiGenerationException("AI_SESSION_NOT_FOUND", "会话不存在"));
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
                .orElseThrow(() -> new AiGenerationException("AI_SESSION_NOT_FOUND", "会话不存在"));

        session.setCurrentPhase(phase);
        aiSessionRepository.save(session);
        log.info("Updated session phase: sessionId={}, phase={}", sessionId, phase);
    }

    @Override
    @Transactional
    public void updateSessionStatus(String sessionId, AiSessionStatus status) {
        UUID uuid = parseSessionId(sessionId);
        AiSession session = aiSessionRepository.findBySessionId(uuid)
                .orElseThrow(() -> new AiGenerationException("AI_SESSION_NOT_FOUND", "会话不存在"));

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
                .orElseThrow(() -> new AiGenerationException("AI_FUNCTION_UNIT_NOT_FOUND", "功能单元不存在"));

        boolean hasProcessDefinition = functionUnit.getProcessDefinition() != null;
        boolean hasTableDefinitions = functionUnit.getTableDefinitions() != null && !functionUnit.getTableDefinitions().isEmpty();
        boolean hasFormDefinitions = functionUnit.getFormDefinitions() != null && !functionUnit.getFormDefinitions().isEmpty();
        boolean hasActionDefinitions = functionUnit.getActionDefinitions() != null && !functionUnit.getActionDefinitions().isEmpty();

        if (hasProcessDefinition || hasTableDefinitions || hasFormDefinitions || hasActionDefinitions) {
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
                        String.format("文档版本不存在: functionUnitId=%d, type=%s, version=%d", functionUnitId, documentType, version)));
    }

    // ==================== Context Serialization ====================

    @Override
    @Transactional(readOnly = true)
    public FunctionUnitContextDTO serializeFunctionUnitContext(Long functionUnitId) {
        // 使用 findById 而非 findByIdWithRelations，避免 MultipleBagFetchException
        // （Hibernate 不允许同时 fetch 多个 List 类型的关联）
        // 在 @Transactional 事务内，lazy loading 会逐个加载关联集合
        FunctionUnit fu = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AiGenerationException("AI_FUNCTION_UNIT_NOT_FOUND", "功能单元不存在"));

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
                String.format("功能单元上下文序列化后大小 %dB 超过限制 %dB", jsonBytes.length, maxContextSizeBytes));
    }

    private FunctionUnitContextDTO buildContextDTO(FunctionUnit fu) {
        // 显式触发 lazy loading（确保在 @Transactional 事务内加载所有关联）
        List<TableDefinition> tables = fu.getTableDefinitions();
        if (tables != null) tables.size();
        List<FormDefinition> forms = fu.getFormDefinitions();
        if (forms != null) forms.size();
        List<ActionDefinition> actions = fu.getActionDefinitions();
        if (actions != null) actions.size();
        ProcessDefinition pd = fu.getProcessDefinition();
        Icon icon = fu.getIcon();

        return FunctionUnitContextDTO.builder()
                .functionUnitId(fu.getId())
                .name(fu.getName())
                .description(fu.getDescription())
                .tableDefinitions(serializeTableDefinitions(tables))
                .formDefinitions(serializeFormDefinitions(forms))
                .actionDefinitions(serializeActionDefinitions(actions))
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
            throw new AiGenerationException("AI_CONTEXT_SERIALIZATION_ERROR", "功能单元上下文序列化失败: " + e.getMessage());
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
        Map<String, Object> truncatedConfig = Map.of("truncated", true);
        if (dto.getFormDefinitions() != null) {
            for (Map<String, Object> form : dto.getFormDefinitions()) {
                form.put("configJson", truncatedConfig);
            }
        }
        if (dto.getActionDefinitions() != null) {
            for (Map<String, Object> action : dto.getActionDefinitions()) {
                action.put("configJson", truncatedConfig);
            }
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
                // 无论 NEW 还是 MODIFY，都加载已有需求文档（如果存在）
                // 用户可能在当前会话中已生成文档，后续消息需要让 AI 看到
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
                                               List<Map<String, String>> existingDocuments) {
        Map<String, Object> requestBody = buildN8NRequestBody(sessionId, message, phase, mode,
                context, functionUnitId, existingDocuments, null);

        Map<String, Object> response = doCallN8NWebhook(requestBody);

        if (isSessionNotFoundError(response)) {
            log.warn("N8N session not found for sessionId={}, rebuilding", sessionId);
            List<Map<String, String>> conversationHistory = buildConversationHistory(sessionId);

            // 使用 functionUnitId 重新加载上下文和文档
            FunctionUnitContextDTO rebuiltContext = serializeFunctionUnitContext(functionUnitId);
            List<Map<String, String>> rebuiltDocs = getLatestDocuments(functionUnitId, phase, mode);

            Map<String, Object> retryBody = buildN8NRequestBody(sessionId, message, phase, mode,
                    rebuiltContext, functionUnitId, rebuiltDocs, conversationHistory);
            response = doCallN8NWebhook(retryBody);
        }

        return response;
    }

    private Map<String, Object> buildN8NRequestBody(UUID sessionId, String message, AiPhase phase, AiMode mode,
                                                     FunctionUnitContextDTO context, Long functionUnitId,
                                                     List<Map<String, String>> existingDocuments,
                                                     List<Map<String, String>> conversationHistory) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId.toString());
        body.put("message", message);
        body.put("phase", phase.name());
        body.put("mode", mode.name());

        // 始终传递 functionUnitId 给 N8N，供 Agent 工具节点查询数据库
        // 即使 context 为 null（新功能单元尚未生成），也要传递 functionUnitId
        if (functionUnitId != null) {
            body.put("functionUnitId", functionUnitId);
        } else if (context != null && context.getFunctionUnitId() != null) {
            body.put("functionUnitId", context.getFunctionUnitId());
        }

        if (context != null) {
            // 预序列化为 JSON 字符串，避免 N8N 表达式渲染为 [object Object]
            body.put("context", toJsonString(context));
        }

        if (existingDocuments != null && !existingDocuments.isEmpty()) {
            List<Map<String, String>> truncated = truncateDocuments(existingDocuments);
            body.put("existingDocuments", formatDocumentsForPrompt(truncated));
        }

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            body.put("conversationHistory", conversationHistory);
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doCallN8NWebhook(Map<String, Object> requestBody) {
        try {
            RestTemplate n8nClient = getOrCreateN8NRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> responseEntity = n8nClient.postForEntity(n8nWebhookUrl, entity, Map.class);
            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody == null) {
                throw new AiGenerationException("AI_N8N_EMPTY_RESPONSE", "N8N 返回空响应");
            }
            return responseBody;
        } catch (AiGenerationException e) {
            throw e;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new AiGenerationException("AI_N8N_TIMEOUT", "N8N Webhook 调用超时: " + e.getMessage());
        } catch (Exception e) {
            throw new AiGenerationException("AI_N8N_CALL_FAILED", "N8N Webhook 调用失败: " + e.getMessage());
        }
    }

    private RestTemplate getOrCreateN8NRestTemplate() {
        if (n8nRestTemplate == null) {
            synchronized (this) {
                if (n8nRestTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    int timeoutMs = n8nTimeoutSeconds * 1000;
                    factory.setConnectTimeout(timeoutMs);
                    factory.setReadTimeout(timeoutMs);
                    n8nRestTemplate = new RestTemplate(factory);
                    log.info("Created N8N RestTemplate with timeout={}ms", timeoutMs);
                }
            }
        }
        return n8nRestTemplate;
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

    private static final long CHAT_EMITTER_TIMEOUT = 180_000L; // 180 seconds (buffer beyond N8N's 120s timeout)
    private static final long EVENT_EMITTER_TIMEOUT = 300_000L; // 300 seconds
    private static final int MAX_DOCUMENT_CONTENT_LENGTH = 50000;

    @Override
    public SseEmitter createChatEmitter(Long functionUnitId, String userId) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        SseEmitter emitter = new SseEmitter(CHAT_EMITTER_TIMEOUT);

        // 如果已有活跃的 emitter，先完成它，防止覆盖导致响应丢失
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
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("Chat SSE error for functionUnit {}, userId {}: {}", functionUnitId, userId, ex.getMessage());
            chatEmitters.remove(key);
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
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("Event SSE error for functionUnit {}, userId {}: {}", functionUnitId, userId, ex.getMessage());
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
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
                sseEvent.data(event.getData(), MediaType.APPLICATION_JSON);
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
            emitter.complete();
            log.debug("Completed chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
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
        if (currentStatus == AiSessionStatus.ACTIVE
                && (newStatus == AiSessionStatus.COMPLETED || newStatus == AiSessionStatus.CANCELLED)) {
            return; // Valid transitions
        }
        throw new AiGenerationException("AI_SESSION_INVALID_STATUS_TRANSITION",
                String.format("非法状态转换: %s → %s", currentStatus, newStatus));
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    /**
     * 将文档列表格式化为人类可读的纯文本，便于 AI 在 systemMessage 中理解。
     * 避免使用 JSON 字符串（双重转义后 AI 难以解析）。
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
            sb.append("【").append(docType).append(" 文档】\n");
            sb.append(content);
        }
        return sb.toString();
    }

    private List<Map<String, String>> truncateDocuments(List<Map<String, String>> documents) {
        return documents.stream().map(doc -> {
            String content = doc.get("content");
            if (content != null && content.length() > MAX_DOCUMENT_CONTENT_LENGTH) {
                log.warn("文档内容超过 {} 字符，执行截断: documentType={}",
                        MAX_DOCUMENT_CONTENT_LENGTH, doc.get("documentType"));
                content = content.substring(0, MAX_DOCUMENT_CONTENT_LENGTH) + "[已截断]";
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
            throw new AiGenerationException("AI_SESSION_INVALID_ID", "无效的会话 ID 格式");
        }
    }
}
