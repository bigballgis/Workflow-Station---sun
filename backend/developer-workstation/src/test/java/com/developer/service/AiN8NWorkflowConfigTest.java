package com.developer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N8N Workflow systemMessage 配置验证测试
 *
 * 验证三个 Agent 的 systemMessage 包含 context 和 existingDocuments 表达式引用。
 * Validates: Requirements 4.2, 4.3, 4.4
 */
class AiN8NWorkflowConfigTest {

    private static JsonNode workflowRoot;

    @BeforeAll
    static void loadWorkflow() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // Try multiple paths: from module dir and from project root
        File workflowFile = new File("deploy/n8n-workflows/ai-function-unit-gen-workflow.json");
        if (!workflowFile.exists()) {
            workflowFile = new File("../../deploy/n8n-workflows/ai-function-unit-gen-workflow.json");
        }
        assertTrue(workflowFile.exists(), "N8N workflow JSON file should exist at deploy/n8n-workflows/ai-function-unit-gen-workflow.json");
        workflowRoot = mapper.readTree(workflowFile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Requirements Agent", "Design Agent", "Generation Agent"})
    void agentSystemMessageContainsContextExpression(String agentName) {
        String systemMessage = getSystemMessage(agentName);
        assertNotNull(systemMessage, agentName + " should have a systemMessage");
        assertTrue(systemMessage.contains("$json.body.context"),
                agentName + " systemMessage should reference $json.body.context");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Requirements Agent", "Design Agent", "Generation Agent"})
    void agentSystemMessageContainsExistingDocumentsExpression(String agentName) {
        String systemMessage = getSystemMessage(agentName);
        assertNotNull(systemMessage, agentName + " should have a systemMessage");
        assertTrue(systemMessage.contains("$json.body.existingDocuments"),
                agentName + " systemMessage should reference $json.body.existingDocuments");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Requirements Agent", "Design Agent", "Generation Agent"})
    void agentSystemMessageContainsModeExpression(String agentName) {
        String systemMessage = getSystemMessage(agentName);
        assertNotNull(systemMessage, agentName + " should have a systemMessage");
        assertTrue(systemMessage.contains("$json.body.mode"),
                agentName + " systemMessage should reference $json.body.mode");
    }

    @Test
    void agentSystemMessageContainsJsonFormatHint() {
        for (String agentName : List.of("Requirements Agent", "Design Agent", "Generation Agent")) {
            String systemMessage = getSystemMessage(agentName);
            assertNotNull(systemMessage, agentName + " should have a systemMessage");
            assertTrue(systemMessage.contains("JSON 格式字符串"),
                    agentName + " systemMessage should contain 'JSON 格式字符串' hint for double-serialization");
        }
    }

    private String getSystemMessage(String agentName) {
        JsonNode nodes = workflowRoot.get("nodes");
        assertNotNull(nodes, "Workflow should have nodes");
        for (JsonNode node : nodes) {
            if (agentName.equals(node.get("name").asText())) {
                JsonNode params = node.get("parameters");
                if (params != null && params.has("options")) {
                    JsonNode options = params.get("options");
                    if (options.has("systemMessage")) {
                        return options.get("systemMessage").asText();
                    }
                }
            }
        }
        return null;
    }
}
