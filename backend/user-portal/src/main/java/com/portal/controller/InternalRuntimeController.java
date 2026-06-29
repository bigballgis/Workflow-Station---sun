package com.portal.controller;

import com.portal.component.ProcessInstanceHydrationComponent;
import com.portal.component.ProcessRuntimePurgeComponent;
import com.portal.config.PortalInternalApiProperties;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 供 admin-center 编排调用的内部 API（X-Internal-Token）
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/internal/runtime")
@RequiredArgsConstructor
public class InternalRuntimeController {

    private final PortalInternalApiProperties portalInternalApiProperties;
    private final ProcessRuntimePurgeComponent processRuntimePurgeComponent;
    private final ProcessInstanceHydrationComponent processInstanceHydrationComponent;

    @PostMapping("/hydrate-process-instance")
    public ApiResponse<Map<String, Object>> hydrateProcessInstance(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        portalInternalApiProperties.requireValidToken(token);
        String rawId = body != null ? body.get("processInstanceId") : null;
        if (!StringUtils.hasText(rawId)) {
            return ApiResponse.error("BAD_REQUEST", "processInstanceId 不能为空");
        }
        String processInstanceId = rawId.trim();
        processInstanceHydrationComponent.requireProcessInstance(processInstanceId);
        return ApiResponse.success(Map.of("processInstanceId", processInstanceId, "hydrated", true));
    }

    @PostMapping("/purge-by-catalog")
    public ApiResponse<Map<String, Object>> purgeByCatalog(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        portalInternalApiProperties.requireValidToken(token);
        String rawCatalogId = body != null ? body.get("catalogId") : null;
        if (!StringUtils.hasText(rawCatalogId)) {
            return ApiResponse.error("BAD_REQUEST", "catalogId 不能为空");
        }
        String catalogId = rawCatalogId.trim();
        return ApiResponse.success(processRuntimePurgeComponent.purgeByCatalogId(catalogId));
    }
}
