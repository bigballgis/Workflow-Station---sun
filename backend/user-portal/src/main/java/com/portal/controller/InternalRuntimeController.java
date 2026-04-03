package com.portal.controller;

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
