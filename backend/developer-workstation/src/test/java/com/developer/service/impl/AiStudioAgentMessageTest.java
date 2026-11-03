package com.developer.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStudioAgentMessageTest {

    @Test
    void proposalCarriesEverySectionInContractOrder() {
        String message = AiStudioAgentMessage.build("PROPOSE", "TABLE_DESIGN", "TABLES", "{\"tableDefinitions\":[]}",
                "{\"tableTypes\":[\"MAIN\"]}", "Generate data with the following JSON structure...",
                List.of(Map.of("documentType", "REQUIREMENTS", "content", "Leave requests need approval.")),
                "User: hi\nAssistant: hello", "Design the tables");

        List<String> headings = message.lines().filter(l -> l.startsWith("## ")).toList();
        assertEquals(List.of("## MODE: PROPOSE", "## PHASE: TABLE_DESIGN", "## SCOPE: TABLES", "## CURRENT DESIGN",
                "## SCHEMA METADATA", "## FORMAT SPECIFICATION", "## DOCUMENTS", "## CONVERSATION", "## USER REQUEST"),
                headings);
        assertTrue(message.contains("### REQUIREMENTS\nLeave requests need approval."));
        // 格式说明自带网关链路的输出顺序与语言要求：分节开头要声明以专家自己的 OUTPUT 规则为准
        assertTrue(message.contains("## FORMAT SPECIFICATION\n(Field names, enum values"));
        assertTrue(message.endsWith("## USER REQUEST\nDesign the tables"));
    }

    @Test
    void advisoryWithoutOptionalPartsOmitsTheirSections() {
        String message = AiStudioAgentMessage.build("ADVISORY", "VALIDATION", "NONE", "{}", "{}", null, List.of(),
                "", "  ready?  ");

        assertFalse(message.contains("## FORMAT SPECIFICATION"));
        assertFalse(message.contains("## DOCUMENTS"));
        assertFalse(message.contains("## CONVERSATION"));
        assertTrue(message.startsWith("## MODE: ADVISORY\n## PHASE: VALIDATION\n## SCOPE: NONE\n"));
        assertTrue(message.endsWith("## USER REQUEST\nready?"));
    }

    @Test
    void longDocumentsAreCappedLikeTheGatewayPath() {
        String content = "x".repeat(AiStudioAgentMessage.MAX_DOCUMENT_CHARS + 10);
        String message = AiStudioAgentMessage.build("PROPOSE", "PROCESS_DESIGN", "PROCESS", "{}", "{}", null,
                List.of(Map.of("documentType", "DESIGN", "content", content)), null, "go");

        assertTrue(message.contains("…(document truncated)"));
        assertFalse(message.contains("x".repeat(AiStudioAgentMessage.MAX_DOCUMENT_CHARS + 1)));
    }
}
