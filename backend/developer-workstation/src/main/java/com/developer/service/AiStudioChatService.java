package com.developer.service;

import com.developer.dto.AiStudioChatRequest;

/**
 * AI Studio Copilot 对话服务。
 *
 * <p>模型链路与 AI Generate 完全同源：同一个 {@code AiGatewayClient}（集团 gateway +
 * 每用户 AMToken / dev 静态 key）、同一个 {@code AiResponseParser}。区别只在编排：
 * 无会话持久化、无锁、无文档与生成数据产出，单轮请求带历史直出回复。</p>
 */
public interface AiStudioChatService {

    /**
     * 发起一轮 Copilot 对话。
     *
     * @param amToken 该用户的 DSP AMToken，透传给 AI gateway 作 Bearer 凭证；
     *                缺失时以 {@code AI_GATEWAY_TOKEN_MISSING} 显式失败，不做匿名调用
     * @return 模型回复文本
     */
    String chat(AiStudioChatRequest request, String amToken);
}
