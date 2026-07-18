package com.developer.service;

import com.developer.dto.AiChatSseEvent;
import com.developer.dto.AiMessageResponse;
import com.developer.dto.AiSessionResponse;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.AiDocument;
import com.developer.entity.AiMessage;
import com.developer.entity.AiSession;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 生成服务
 * 负责会话生命周期管理、模式判定、消息持久化、文档版本管理、N8N 调用与 SSE 事件流
 */
public interface AiGenerationService {

    // ==================== Session Management ====================

    /**
     * 创建新的 AI 会话
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     * @param mode           AI 模式（如果为 null，则自动判定）
     * @return 创建的会话实体
     */
    AiSession createSession(Long functionUnitId, String userId, AiMode mode);

    /**
     * 恢复已有会话
     *
     * @param sessionId 会话 ID（UUID 字符串）
     * @return 会话实体
     * @throws com.developer.exception.AiGenerationException 如果会话不存在
     */
    AiSession restoreSession(String sessionId);

    /**
     * 查询功能单元的会话列表（按创建时间降序）
     *
     * @param functionUnitId 功能单元 ID
     * @return 会话响应 DTO 列表
     */
    List<AiSessionResponse> getSessionsByFunctionUnitId(Long functionUnitId);

    /**
     * 更新会话当前阶段
     *
     * @param sessionId 会话 ID（UUID 字符串）
     * @param phase     新阶段
     */
    void updateSessionPhase(String sessionId, AiPhase phase);

    /**
     * 更新会话状态（仅允许 ACTIVE → COMPLETED 或 ACTIVE → CANCELLED）
     *
     * @param sessionId 会话 ID（UUID 字符串）
     * @param status    新状态
     * @throws com.developer.exception.AiGenerationException 如果状态转换非法
     */
    void updateSessionStatus(String sessionId, AiSessionStatus status);

    // ==================== Mode Detection ====================

    /**
     * 根据功能单元数据状态自动判定 AI 模式
     * 有任意组件数据 → MODIFY，无任何组件数据 → NEW
     *
     * @param functionUnitId 功能单元 ID
     * @return AI 模式
     */
    AiMode determineMode(Long functionUnitId);

    // ==================== Message Persistence ====================

    /**
     * 保存对话消息
     *
     * @param sessionId 会话 ID
     * @param role      消息角色
     * @param content   消息内容
     * @param phase     消息所属阶段
     * @return 保存的消息实体
     */
    AiMessage saveMessage(UUID sessionId, AiMessageRole role, String content, AiPhase phase);

    /**
     * 加载会话的所有消息（按创建时间升序）
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    List<AiMessage> loadMessages(UUID sessionId);

    /**
     * 分页查询会话消息
     *
     * @param sessionId 会话 ID（UUID 字符串）
     * @param pageable  分页参数
     * @return 消息响应 DTO 分页结果
     */
    Page<AiMessageResponse> getMessagesPaged(String sessionId, Pageable pageable);

    // ==================== Document Version Management ====================

    /**
     * 保存文档（自动递增版本号）
     *
     * @param functionUnitId 功能单元 ID
     * @param documentType   文档类型
     * @param content        文档内容
     * @param summary        版本摘要
     * @param userId         创建人 ID
     * @return 保存的文档实体
     */
    AiDocument saveDocument(Long functionUnitId, AiDocumentType documentType, String content, String summary, String userId);

    /**
     * 获取文档版本列表（按版本号降序）
     *
     * @param functionUnitId 功能单元 ID
     * @param documentType   文档类型
     * @return 文档版本列表
     */
    List<AiDocument> getDocumentVersions(Long functionUnitId, AiDocumentType documentType);

    /**
     * 获取指定版本的文档
     *
     * @param functionUnitId 功能单元 ID
     * @param documentType   文档类型
     * @param version        版本号
     * @return 文档实体
     * @throws com.developer.exception.AiGenerationException 如果版本不存在
     */
    AiDocument getDocumentByVersion(Long functionUnitId, AiDocumentType documentType, Integer version);

    // ==================== Context Serialization ====================

    /**
     * 序列化功能单元上下文（发送给 N8N AI Agent）
     * 加载功能单元及所有关联数据，序列化为 FunctionUnitContextDTO。
     * 如果序列化后 JSON 超过配置的最大字节数（默认 100KB），
     * 则依次截断 bpmnXml 和 configJson 字段。
     *
     * @param functionUnitId 功能单元 ID
     * @return 功能单元上下文 DTO
     * @throws com.developer.exception.AiGenerationException 如果功能单元不存在或上下文过大
     */
    FunctionUnitContextDTO serializeFunctionUnitContext(Long functionUnitId);

    // ==================== N8N Session Memory Rebuild ====================

    /**
     * 从 dw_ai_messages 加载完整对话历史，按 (role, content) 格式组装为对话历史数组。
     * 用于 N8N 会话记忆丢失时重建会话上下文。
     *
     * @param sessionId 会话 ID
     * @return 对话历史数组，每个元素包含 "role" 和 "content" 键
     */
    List<Map<String, String>> buildConversationHistory(UUID sessionId);

    /**
     * 根据当前阶段和模式获取前序文档列表
     *
     * @param functionUnitId 功能单元 ID
     * @param phase          当前阶段
     * @param mode           AI 模式
     * @return 文档列表，每个元素包含 "documentType" 和 "content" 键
     */
    List<Map<String, String>> getLatestDocuments(Long functionUnitId, AiPhase phase, AiMode mode);

    /**
     * 调用 N8N Webhook，包含会话不存在错误检测与自动重建逻辑。
     * 如果 N8N 返回会话不存在错误，自动从数据库加载对话历史并重新发送请求。
     *
     * @param sessionId          会话 ID
     * @param message            用户消息
     * @param phase              当前阶段
     * @param mode               AI 模式
     * @param context            功能单元上下文（首次请求时提供，后续为 null）
     * @param functionUnitId     功能单元 ID（用于会话重建时重新加载上下文）
     * @param existingDocuments  前序文档列表（首次请求时提供，后续为空列表）
     * @param regenerateScope    增量重新生成范围（ALL/TABLES/FORMS/ACTIONS/DECISIONS/PROCESS/TABLE_RELATIONS，null 等同于 ALL）
     * @return N8N 响应体（Map 格式）
     */
    Map<String, Object> callN8NWebhook(UUID sessionId, String message, AiPhase phase, AiMode mode,
                                        FunctionUnitContextDTO context, Long functionUnitId,
                                        List<Map<String, String>> existingDocuments,
                                        String regenerateScope);

    // ==================== SSE Emitter Management ====================

    /**
     * 创建对话 SSE emitter（120 秒超时）
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     * @return SseEmitter 实例
     */
    SseEmitter createChatEmitter(Long functionUnitId, String userId);

    /**
     * 创建独立事件 SSE emitter（长连接，300 秒超时）
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     * @return SseEmitter 实例
     */
    SseEmitter createEventEmitter(Long functionUnitId, String userId);

    /**
     * 向对话 SSE 发送事件
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     * @param event          SSE 事件
     */
    void sendChatEvent(Long functionUnitId, String userId, AiChatSseEvent event);

    /**
     * 判断给定 emitter 是否已被同一 (functionUnitId, userId) 的新对话请求取代。
     * 异步任务在发送事件前用它判活：用户「停止后立刻重发」时，被取代的旧任务
     * 不得把过期 reply/done 注入新请求的 SSE 流，也不得关闭新 emitter。
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     * @param emitter        发起方持有的 emitter 实例
     * @return true 表示已被取代（或已完成移除）
     */
    boolean isChatEmitterSuperseded(Long functionUnitId, String userId, SseEmitter emitter);

    /**
     * 向指定 functionUnitId 的所有独立事件 SSE 发送通知
     *
     * @param functionUnitId 功能单元 ID
     * @param event          SSE 事件
     */
    void sendEventNotification(Long functionUnitId, AiChatSseEvent event);

    /**
     * 完成并清理对话 SSE emitter
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     */
    void completeChatEmitter(Long functionUnitId, String userId);

    /**
     * 从注册表中移除对话 emitter
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     */
    void removeChatEmitter(Long functionUnitId, String userId);

    /**
     * 从注册表中移除独立事件 emitter
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     */
    void removeEventEmitter(Long functionUnitId, String userId);
}
