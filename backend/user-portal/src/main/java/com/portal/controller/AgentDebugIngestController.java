package com.portal.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives browser NDJSON debug lines so they appear in container stdout
 * ({@code docker logs ... | grep AGENT_NDJSON}). Disabled unless
 * {@code portal.debug.agent-ingest.enabled=true}.
 */
@RestController
@RequestMapping("/debug")
@ConditionalOnProperty(prefix = "portal.debug.agent-ingest", name = "enabled", havingValue = "true")
public class AgentDebugIngestController {

    private static final Logger AGENT_LOG = LoggerFactory.getLogger("AGENT_NDJSON");

    private static final int MAX_BODY_CHARS = 8192;

    @PostMapping("/agent-ingest")
    public ResponseEntity<Void> ingest(@RequestBody(required = false) String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String line = body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) + "…" : body;
        String safe = line.replace('\r', ' ').replace('\n', ' ');
        AGENT_LOG.info("{}", safe);
        return ResponseEntity.accepted().build();
    }
}
