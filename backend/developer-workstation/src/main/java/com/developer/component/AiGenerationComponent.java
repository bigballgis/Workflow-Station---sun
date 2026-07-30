package com.developer.component;

import com.developer.dto.AiChatRequest;
import com.developer.dto.AiMessageResponse;
import com.developer.dto.AiSessionResponse;
import com.developer.dto.ApplyGeneratedDataRequest;
import com.developer.dto.LockInfoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 生成功能组件接口
 * 编排锁管理、会话管理、AI webhook 调用、SSE 事件流、数据校验与写入等服务
 */
public interface AiGenerationComponent {

    /**
     * 对话流式传输
     * 编排: 锁续期 → 消息持久化 → AI gateway 调用 → SSE 事件流 → 文档保存
     *
     * @param amToken 该用户的 DSP AMToken，透传给 AI gateway 作 Bearer 凭证；缺失时本轮以
     *                {@code AI_GATEWAY_TOKEN_MISSING} 失败（不做匿名调用）
     */
    SseEmitter chatStream(AiChatRequest request, String userId, String amToken);

    /**
     * 注册独立事件 SSE emitter（长连接）
     */
    SseEmitter registerEventEmitter(Long functionUnitId, String userId);

    /**
     * 获取锁
     */
    LockInfoResponse acquireLock(Long functionUnitId, String userId);

    /**
     * 释放锁
     */
    void releaseLock(Long functionUnitId, String userId);

    /**
     * 请求强制解锁
     */
    void requestForceUnlock(Long functionUnitId, String requesterId);

    /**
     * 响应强制解锁请求
     */
    void respondForceUnlock(Long functionUnitId, String userId, boolean accept);

    /**
     * 获取会话列表
     */
    List<AiSessionResponse> getSessions(Long functionUnitId);

    /**
     * 分页获取消息
     */
    Page<AiMessageResponse> getMessages(String sessionId, Pageable pageable);

    /**
     * 应用 AI 生成的数据
     * 编排: 锁续期 → 校验 → 写入 → 更新会话状态 → SSE 通知
     */
    void applyGeneratedData(Long functionUnitId, ApplyGeneratedDataRequest request, String userId);

    /**
     * 获取文档版本列表
     */
    List<com.developer.entity.AiDocument> getDocumentVersions(Long functionUnitId, com.developer.enums.AiDocumentType documentType);

    /**
     * 按版本获取文档
     */
    com.developer.entity.AiDocument getDocumentByVersion(Long functionUnitId, com.developer.enums.AiDocumentType documentType, Integer version);

    /**
     * 保存用户手动编辑的文档为新版本
     */
    com.developer.entity.AiDocument saveDocument(Long functionUnitId, com.developer.enums.AiDocumentType documentType, String content, String userId);

    /**
     * 更新会话当前阶段
     */
    void updateSessionPhase(String sessionId, com.developer.enums.AiPhase phase);

    /**
     * 撤销上次应用操作
     * 从内存快照缓存恢复数据，30 秒 TTL 过期后不可撤销
     *
     * @param functionUnitId 功能单元 ID
     */
    void undoLastApply(Long functionUnitId);
}
