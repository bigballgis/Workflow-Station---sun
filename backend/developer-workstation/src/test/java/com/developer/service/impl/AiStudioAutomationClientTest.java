package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** 共用的 webhook 行为由 {@link HermesMasterAutomationClientTest} 覆盖；这里只验 AI Studio 自己的契约与错误码前缀。 */
@ExtendWith(MockitoExtension.class)
class AiStudioAutomationClientTest {

    @Mock
    private AiAutomationFlowCatalogReader flowCatalog;

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status;
    private volatile String responseBody;

    private AiStudioAutomationClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : out.length);
            if (status != 204) {
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();

        client = new AiStudioAutomationClient(flowCatalog, new ObjectMapper());
        ReflectionTestUtils.setField(client, "flowKey", "ai-studio-co-agent");
        ReflectionTestUtils.setField(client, "webhookBaseUrl", "http://localhost:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(client, "ssrfAllowedHosts", List.of("localhost"));
        client.init();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void publishedFlow() {
        when(flowCatalog.findByKey("ai-studio-co-agent")).thenReturn(Optional.of(
                new AiAutomationFlowCatalogReader.FlowEntry("flow-9", "ai-studio-co-agent", "AI Studio CO Agent",
                        true, "hermes-main", "Public")));
    }

    @Test
    void postsMessageAndStaffIdAndReturnsTheAgentReply() throws IOException {
        publishedFlow();
        status = 200;
        responseBody = "{\"reply\":\"Done.\\n---GENERATED_DATA_START---\\n{}\\n---GENERATED_DATA_END---\",\"error\":\"\"}";

        assertEquals("Done.\n---GENERATED_DATA_START---\n{}\n---GENERATED_DATA_END---",
                client.chat("## MODE: PROPOSE", "45349679"));
        assertEquals("/api/v1/webhooks/flow-9/sync", requestPath.get());
        assertEquals(Map.of("message", "## MODE: PROPOSE", "staffId", "45349679"),
                new ObjectMapper().readValue(requestBody.get(), Map.class));
    }

    @Test
    void failuresCarryTheAiStudioCodePrefix() {
        when(flowCatalog.findByKey("ai-studio-co-agent")).thenReturn(Optional.empty());

        AiGenerationException e = assertThrows(AiGenerationException.class, () -> client.chat("m", "1"));
        assertEquals("AI_STUDIO_FLOW_NOT_FOUND", e.getErrorCode());
        assertEquals("AI Studio's Automation flow 'ai-studio-co-agent' is not imported or not published "
                + "in this environment", e.getMessage());
    }
}
