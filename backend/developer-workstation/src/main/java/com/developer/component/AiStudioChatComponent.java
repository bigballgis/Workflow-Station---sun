package com.developer.component;

import com.developer.dto.AiStudioApplyRequest;
import com.developer.dto.AiStudioChatRequest;
import com.developer.dto.AiStudioChatResponse;

/**
 * AI Studio Copilot 组件接口。
 *
 * <p>与 {@link AiGenerationComponent} 分开而不是往里加方法：那个接口的实现类被多处单测以
 * 构造函数直接 new，扩参会连坐改一串测试；Copilot 编排也确实是另一件事（无会话、无锁的轻对话 +
 * 显式 propose/apply）。</p>
 */
public interface AiStudioChatComponent {

    /** 单轮 Copilot 对话（propose=true 时产出可 Apply 的结构化提案）；amToken 透传给 AI gateway。 */
    AiStudioChatResponse chat(AiStudioChatRequest request, String userId, String amToken);

    /**
     * 应用改动提案：工作区访问校验 → 抢 AI 锁（与 AI Generate 同一把，冲突 409）→
     * 归一化 + 平台校验（失败 422，不落库）→ 按 scope 写入 → 释放锁。
     */
    void applyProposal(AiStudioApplyRequest request, String userId);
}
