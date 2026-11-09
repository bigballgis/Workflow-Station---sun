package com.workflow.controller;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.security.util.ServiceTokenVerifier;
import com.workflow.component.DepartedHolderReassignment;
import com.workflow.dto.request.DepartedUsersRequest;
import com.workflow.dto.response.DepartedReassignResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Center → engine after a user is set INACTIVE or deleted (platform access model D-42): their tasks go to their
 * manager. Service token only: no user acts here. Open in {@code SecurityConfig}; the token is checked below.
 */
@RestController
@RequestMapping("/api/v1/internal/departed-users")
@RequiredArgsConstructor
public class DepartedUserTasksInternalController {

    private final ServiceTokenVerifier serviceTokenVerifier;
    private final DepartedHolderReassignment departedHolderReassignment;

    @PostMapping("/tasks/reassign")
    public ApiResponse<DepartedReassignResult> reassign(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody DepartedUsersRequest request) {
        if (!serviceTokenVerifier.matches(serviceToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(departedHolderReassignment.reassign(request.userIds()));
    }
}
