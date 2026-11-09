package com.developer.controller;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health probe standardization endpoints.
 * <p>
 * This controller keeps existing business APIs unchanged and answers with the Spring Boot Actuator health groups,
 * read in-process. It used to call its own actuator over HTTP at the request's Host and port, which fails wherever
 * the probe reaches the service through a different port than the one it listens on (from the host through the
 * mapped port 8083 it answered 503 while the service was up).
 */
@RestController
public class HealthAliasController {

    private final HealthEndpoint healthEndpoint;
    private final HttpCodeStatusMapper statusMapper;

    public HealthAliasController(HealthEndpoint healthEndpoint, HttpCodeStatusMapper statusMapper) {
        this.healthEndpoint = healthEndpoint;
        this.statusMapper = statusMapper;
    }

    @GetMapping("/health/live")
    public ResponseEntity<Map<String, String>> live() {
        return healthGroup("live");
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> ready() {
        return healthGroup("ready");
    }

    @GetMapping("/.well-known/health")
    public ResponseEntity<String> keepalive() {
        // Keepalive must be lightweight and never depends on external systems.
        return ResponseEntity.ok("{\"status\":\"ok\"}");
    }

    /** The group's status as /actuator/health/{group} reports it to an anonymous caller, with the same HTTP code. */
    private ResponseEntity<Map<String, String>> healthGroup(String group) {
        HealthComponent health = healthEndpoint.healthForPath(group);
        if (health == null) {
            throw new IllegalStateException("Health group '" + group + "' is not configured (management.endpoint.health.group)");
        }
        return ResponseEntity.status(statusMapper.getStatusCode(health.getStatus()))
                .body(Map.of("status", health.getStatus().getCode()));
    }
}
