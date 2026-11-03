package com.developer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Studio 的 Automation 链路：DW → AP sync webhook → content-organizer 的 Agent Chat 动作 → Content Organizer 的
 * agent setting「Function Unit」（多智能体，按 PHASE 交给一个专家，专家直接回答）。
 *
 * <p>flow 默认业务键 {@code ai-studio-co-agent}，调用细节见 {@link AutomationSyncWebhook}。agent setting 没有变量：
 * 整段分节请求（见 docs/design/developer-workstation-ai-studio-co-output-contract.md）就是消息。</p>
 *
 * <p>flow 契约：请求 {@code {message, staffId}}；响应 {@code {reply}} 或 {@code {error}}。</p>
 */
@Component
public class AiStudioAutomationClient {

    @Value("${ai-generation.ai-studio.automation.flow-key:ai-studio-co-agent}")
    private String flowKey;

    @Value("${ai-generation.ai-studio.automation.webhook-base-url:http://localhost:8086}")
    private String webhookBaseUrl;

    /** 提案是后台作业，不受浏览器超时约束；略长于 AP sync webhook 的 300s，让 AP 自己的超时先说话。 */
    @Value("${ai-generation.ai-studio.automation.timeout-seconds:310}")
    private int timeoutSeconds;

    @Value("${ssrf.allowed-hosts:localhost,activepieces}")
    private List<String> ssrfAllowedHosts;

    private final AiAutomationFlowCatalogReader flowCatalog;
    private final ObjectMapper objectMapper;

    private AutomationSyncWebhook webhook;

    public AiStudioAutomationClient(AiAutomationFlowCatalogReader flowCatalog, ObjectMapper objectMapper) {
        this.flowCatalog = flowCatalog;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.webhook = new AutomationSyncWebhook("AI Studio", "AI_STUDIO", flowCatalog, objectMapper,
                flowKey, webhookBaseUrl, timeoutSeconds, ssrfAllowedHosts);
    }

    /**
     * @param message 按契约分节拼好的整段请求
     * @param staffId 工号，CO 用它审计
     * @return 回答请求的那个专家的原文（PROPOSE 时含 GENERATED_DATA 标记块）
     */
    public String chat(String message, String staffId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("staffId", staffId);
        return webhook.call(body);
    }
}
