package com.portal.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Health probe standardization endpoints.
 * <p>
 * This controller keeps existing business APIs unchanged, and only proxies to Spring Boot Actuator health groups.
 */
@RestController
public class HealthAliasController {

    private final RestTemplate restTemplate;

    public HealthAliasController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/health/live")
    public ResponseEntity<String> live(HttpServletRequest request) {
        return proxyToActuatorHealthGroup(request, "live");
    }

    @GetMapping("/health/ready")
    public ResponseEntity<String> ready(HttpServletRequest request) {
        return proxyToActuatorHealthGroup(request, "ready");
    }

    @GetMapping("/.well-known/health")
    public ResponseEntity<String> keepalive() {
        // Keepalive must be lightweight and never depends on external systems.
        return ResponseEntity.ok("{\"status\":\"ok\"}");
    }

    private ResponseEntity<String> proxyToActuatorHealthGroup(HttpServletRequest request, String group) {
        String scheme = Optional.ofNullable(request.getScheme()).orElse("http");
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = Optional.ofNullable(request.getContextPath()).orElse("");

        String url = scheme + "://" + host + ":" + port + contextPath + "/actuator/health/" + group;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"status\":\"unavailable\"}");
        }
    }
}

