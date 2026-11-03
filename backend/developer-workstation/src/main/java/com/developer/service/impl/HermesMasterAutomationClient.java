package com.developer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hermes Master 的 Automation 链路：DW → AP sync webhook → content-organizer 的 Chat 动作 → Content Organizer。
 *
 * <p>flow 默认业务键 {@code hermes-master-chat}，调用细节见 {@link AutomationSyncWebhook}。
 * 提示词不在这里：它是 CO 上的 prompt setting「Hermes Master」，DW 只送它的变量（页面 + 设计摘要 + 对话转写）。</p>
 *
 * <p>flow 契约：请求 {@code {message, userInput, staffId}}；响应 {@code {reply}} 或 {@code {error}}。</p>
 */
@Component
public class HermesMasterAutomationClient {

    @Value("${ai-generation.hermes-master.automation.flow-key:hermes-master-chat}")
    private String flowKey;

    @Value("${ai-generation.hermes-master.automation.webhook-base-url:http://localhost:8086}")
    private String webhookBaseUrl;

    /** 比前端 120s 短：让 DW 先给出可读的超时，而不是让浏览器自己掐断。 */
    @Value("${ai-generation.hermes-master.automation.timeout-seconds:110}")
    private int timeoutSeconds;

    @Value("${ssrf.allowed-hosts:localhost,activepieces}")
    private List<String> ssrfAllowedHosts;

    private final AiAutomationFlowCatalogReader flowCatalog;
    private final ObjectMapper objectMapper;

    private AutomationSyncWebhook webhook;

    public HermesMasterAutomationClient(AiAutomationFlowCatalogReader flowCatalog, ObjectMapper objectMapper) {
        this.flowCatalog = flowCatalog;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.webhook = new AutomationSyncWebhook("Hermes Master", "HERMES_MASTER", flowCatalog, objectMapper,
                flowKey, webhookBaseUrl, timeoutSeconds, ssrfAllowedHosts);
    }

    /**
     * @param message   本轮用户消息
     * @param userInput 填进 CO prompt setting 变量的整段上下文
     * @param staffId   工号，CO 用它审计
     * @return 模型回复（Markdown）
     */
    public String chat(String message, String userInput, String staffId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("userInput", userInput);
        body.put("staffId", staffId);
        return webhook.call(body);
    }
}
