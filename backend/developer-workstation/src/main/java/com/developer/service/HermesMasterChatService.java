package com.developer.service;

import com.developer.dto.HermesMasterChatRequest;

/**
 * Hermes Master（HM）对话服务：DW 全局助手的单轮顾问式对话。
 *
 * <p>默认经 Automation 走 Content Organizer，可切回直连集团 AI gateway（见 {@code HermesMasterChatServiceImpl}）。
 * HM 只回答与引导，不产出结构化提案、不写设计、不落库。</p>
 */
public interface HermesMasterChatService {

    /**
     * @param userId  当前用户，Automation 链路用它查工号
     * @param amToken 仅直连 gateway 链路用
     * @return 模型回复（Markdown）；失败抛 {@code AiGenerationException}
     */
    String chat(HermesMasterChatRequest request, String userId, String amToken);
}
