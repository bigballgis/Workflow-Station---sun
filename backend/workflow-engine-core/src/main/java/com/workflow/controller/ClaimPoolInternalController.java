package com.workflow.controller;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.security.util.ServiceTokenVerifier;
import com.workflow.component.ClaimPoolMembershipSync;
import com.workflow.dto.request.ClaimPoolResyncRequest;
import com.workflow.dto.response.ClaimPoolResyncResult;
import com.workflow.exception.AdminCenterUnavailableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Center → engine after a UBR change (platform access model WP8b-2). Service token only: no user acts here.
 * Open in {@code SecurityConfig}; the token is checked below.
 */
@RestController
@RequestMapping("/api/v1/internal/claim-pools")
@RequiredArgsConstructor
public class ClaimPoolInternalController {

    private final ServiceTokenVerifier serviceTokenVerifier;
    private final ClaimPoolMembershipSync claimPoolMembershipSync;

    @PostMapping("/resync")
    public ApiResponse<ClaimPoolResyncResult> resync(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody ClaimPoolResyncRequest request) {
        if (!serviceTokenVerifier.matches(serviceToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return ApiResponse.success(claimPoolMembershipSync.resync(request.pools()));
        } catch (AdminCenterUnavailableException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Claim pools not resynced: Admin Center unavailable", e);
        }
    }
}
