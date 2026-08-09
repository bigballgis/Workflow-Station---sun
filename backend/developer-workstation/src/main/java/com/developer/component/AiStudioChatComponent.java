package com.developer.component;

import com.developer.dto.AiStudioChatRequest;
import com.developer.dto.AiStudioChatResponse;

/**
 * AI Studio Copilot 组件接口。
 *
 * <p>与 {@link AiGenerationComponent} 分开而不是往里加方法：那个接口的实现类被多处单测以
 * 构造函数直接 new，扩参会连坐改一串测试；Copilot 编排也确实是另一件事（无锁、无会话、无写入）。</p>
 */
public interface AiStudioChatComponent {

    /** 单轮 Copilot 对话；amToken 透传给 AI gateway 作 Bearer 凭证。 */
    AiStudioChatResponse chat(AiStudioChatRequest request, String userId, String amToken);
}
