package com.admin.controller;

import com.admin.automation.AutomationConnectionComponent;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Portal-only lookup of runtime identity and published scope. Activepieces cannot call this. */
@RestController
@RequestMapping("/internal")
public class InternalAutomationConnectionController {

    private final AutomationConnectionComponent component;
    private final String serviceToken;

    public InternalAutomationConnectionController(AutomationConnectionComponent component,
                                                  @Value("${service.internal-token:}") String serviceToken) {
        this.component = component;
        this.serviceToken = serviceToken;
    }

    @GetMapping("/automation-runtime")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runtime(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String token,
            @RequestParam String externalProjectId,
            @RequestParam(required = false) String flowId) {
        requireToken(token);
        return ResponseEntity.ok(ApiResponse.success(component.runtimeForService(externalProjectId, flowId)));
    }

    @GetMapping("/automation-scopes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scope(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String token,
            @RequestParam String externalProjectId, @RequestParam String flowId) {
        requireToken(token);
        return ResponseEntity.ok(ApiResponse.success(component.scopeForService(externalProjectId, flowId)));
    }

    private void requireToken(String provided) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Service token is not configured");
        }
        byte[] expected = serviceToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "valid X-Service-Token required");
        }
    }
}
