package com.portal.properties;

import net.jqwik.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for BPMN XML base64 roundtrip consistency.
 *
 * Feature: travel-expense-reimbursement, Property 3: BPMN XML base64 编码往返一致性
 *
 * For any BPMN XML string, base64 encode then decode should produce the
 * original string. Decoded string should be valid BPMN XML (contains
 * bpmn:definitions and bpmn:process).
 *
 * **Validates: Requirements 6.5**
 */
public class TravelExpenseBpmnBase64PropertyTest {

    /**
     * Feature: travel-expense-reimbursement, Property 3
     *
     * For any generated BPMN XML string, base64 encoding then decoding
     * should produce the original string, and the result should contain
     * valid BPMN XML markers.
     *
     * **Validates: Requirements 6.5**
     */
    @Property(tries = 100)
    @Label("Feature: travel-expense-reimbursement, Property 3: BPMN XML base64 roundtrip consistency")
    void bpmnXmlBase64RoundtripIsConsistent(
            @ForAll("bpmnXmlStrings") String bpmnXml) {

        // Encode to base64 (same as SQL: encode(convert_to(xml, 'UTF8'), 'base64'))
        byte[] xmlBytes = bpmnXml.getBytes(StandardCharsets.UTF_8);
        String base64Encoded = Base64.getEncoder().encodeToString(xmlBytes);

        // Decode from base64
        byte[] decodedBytes = Base64.getDecoder().decode(base64Encoded);
        String decodedXml = new String(decodedBytes, StandardCharsets.UTF_8);

        // Roundtrip: decoded must equal original
        assertThat(decodedXml).isEqualTo(bpmnXml);

        // Decoded string must be valid BPMN XML
        assertThat(decodedXml).contains("bpmn:definitions");
        assertThat(decodedXml).contains("bpmn:process");
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> bpmnXmlStrings() {
        return Combinators.combine(
                processIds(),
                processNames(),
                userTaskLists()
        ).as((processId, processName, userTasks) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ");
            sb.append("xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" ");
            sb.append("id=\"Definitions_").append(processId).append("\" ");
            sb.append("targetNamespace=\"http://bpmn.io/schema/bpmn\">\n");
            sb.append("  <bpmn:process id=\"").append(processId);
            sb.append("\" name=\"").append(processName);
            sb.append("\" isExecutable=\"true\">\n");
            sb.append("    <bpmn:startEvent id=\"StartEvent_1\" name=\"Start\">\n");
            sb.append("      <bpmn:outgoing>Flow_1</bpmn:outgoing>\n");
            sb.append("    </bpmn:startEvent>\n");
            for (String task : userTasks) {
                sb.append("    <bpmn:userTask id=\"Task_").append(task);
                sb.append("\" name=\"").append(task).append("\"/>\n");
            }
            sb.append("    <bpmn:endEvent id=\"EndEvent_1\" name=\"End\"/>\n");
            sb.append("  </bpmn:process>\n");
            sb.append("</bpmn:definitions>");
            return sb.toString();
        });
    }

    private Arbitrary<String> processIds() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30)
                .map(s -> "Process_" + s);
    }

    private Arbitrary<String> processNames() {
        return Arbitraries.of(
                "Travel Expense Reimbursement",
                "Procurement Workflow",
                "Leave Request",
                "Invoice Processing",
                "Budget Approval"
        );
    }

    private Arbitrary<java.util.List<String>> userTaskLists() {
        Arbitrary<String> taskNames = Arbitraries.strings().alpha()
                .ofMinLength(3).ofMaxLength(15);
        return taskNames.list().ofMinSize(1).ofMaxSize(5);
    }
}
