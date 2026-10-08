package com.portal.controller;

import com.platform.common.dto.ApiResponse;
import com.portal.component.SlaRecalcJobService;
import com.portal.config.PortalInternalApiProperties;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Internal API for admin-center (X-Internal-Token): start an SLA due date recalculation job. */
@Hidden
@RestController
@RequestMapping("/internal/sla")
@RequiredArgsConstructor
public class InternalSlaRecalcController {

    private final PortalInternalApiProperties portalInternalApiProperties;
    private final SlaRecalcJobService slaRecalcJobService;

    @PostMapping("/recalc-jobs")
    public ApiResponse<Map<String, String>> submit(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        portalInternalApiProperties.requireValidToken(token);
        Object rawCode = body != null ? body.get("functionUnitCode") : null;
        String functionUnitCode = rawCode != null ? String.valueOf(rawCode).trim() : null;
        if (!StringUtils.hasText(functionUnitCode)) {
            return ApiResponse.error("BAD_REQUEST", "functionUnitCode is required");
        }
        Integer policyVersion = body.get("policyVersion") instanceof Number n ? n.intValue() : null;
        Object triggeredBy = body.get("triggeredBy");
        String jobId = slaRecalcJobService.submit(functionUnitCode, policyVersion,
                triggeredBy != null ? String.valueOf(triggeredBy) : null);
        return ApiResponse.success(Map.of("jobId", jobId));
    }
}
