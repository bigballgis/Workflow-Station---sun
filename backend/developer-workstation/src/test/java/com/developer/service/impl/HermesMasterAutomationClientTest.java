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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HermesMasterAutomationClientTest {

    @Mock
    private AiAutomationFlowCatalogReader flowCatalog;

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status;
    private volatile String responseBody;

    private HermesMasterAutomationClient client;

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

        client = new HermesMasterAutomationClient(flowCatalog, new ObjectMapper());
        ReflectionTestUtils.setField(client, "flowKey", "hermes-master-chat");
        ReflectionTestUtils.setField(client, "webhookBaseUrl", "http://localhost:" + server.getAddress().getPort() + "/");
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(client, "ssrfAllowedHosts", List.of("localhost"));
        client.init();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void publishedFlow() {
        when(flowCatalog.findByKey("hermes-master-chat")).thenReturn(Optional.of(
                new AiAutomationFlowCatalogReader.FlowEntry("flow-1", "hermes-master-chat", "Hermes Master",
                        true, "hermes-main", "Public")));
    }

    @Test
    void postsTheContractToTheSyncWebhookAndReturnsTheReply() throws IOException {
        publishedFlow();
        status = 200;
        responseBody = "{\"reply\":\"1. Open **Function Units**.\",\"error\":\"\"}";

        assertEquals("1. Open **Function Units**.", client.chat("How?", "DW page: X\nUser: How?", "45349679"));
        assertEquals("/api/v1/webhooks/flow-1/sync", requestPath.get());
        assertEquals(Map.of("message", "How?", "userInput", "DW page: X\nUser: How?", "staffId", "45349679"),
                new ObjectMapper().readValue(requestBody.get(), Map.class));
    }

    @Test
    void surfacesTheContentOrganizerErrorTheFlowCarriesBack() {
        publishedFlow();
        status = 200;
        // the shape AP actually returns (dev, 2026-10-30): the piece error JSON nested as a string in {message}
        responseBody = "{\"reply\":\"\",\"error\":{\"message\":\"{\\\"__apErrorVersion\\\":1,\\\"message\\\":"
                + "\\\"Chat completion failed (HTTP 400): Prompt setting not found\\\",\\\"raw\\\":\\\"Error: at chat\\\"}\"}}";

        AiGenerationException e = assertThrows(AiGenerationException.class, () -> client.chat("m", "u", "1"));
        assertEquals("HERMES_MASTER_FLOW_FAILED", e.getErrorCode());
        assertEquals("Content Organizer failed: Chat completion failed (HTTP 400): Prompt setting not found", e.getMessage());
    }

    @Test
    void aRunWithoutReturnResponseIsNotASuccess() {
        publishedFlow();
        status = 204;
        responseBody = null;

        AiGenerationException e = assertThrows(AiGenerationException.class, () -> client.chat("m", "u", "1"));
        assertEquals("HERMES_MASTER_FLOW_NO_RESPONSE", e.getErrorCode());
    }

    @Test
    void anUnreachableAutomationIsNotReportedAsATimeout() {
        publishedFlow();
        server.stop(0);

        AiGenerationException e = assertThrows(AiGenerationException.class, () -> client.chat("m", "u", "1"));
        assertEquals("HERMES_MASTER_UNREACHABLE", e.getErrorCode());
    }

    @Test
    void anUnpublishedFlowFailsBeforeAnyCall() {
        when(flowCatalog.findByKey("hermes-master-chat")).thenReturn(Optional.of(
                new AiAutomationFlowCatalogReader.FlowEntry("flow-1", "hermes-master-chat", "Hermes Master",
                        false, "hermes-main", "Public")));

        AiGenerationException e = assertThrows(AiGenerationException.class, () -> client.chat("m", "u", "1"));
        assertEquals("HERMES_MASTER_FLOW_NOT_FOUND", e.getErrorCode());
        assertEquals(null, requestPath.get());
    }
}
