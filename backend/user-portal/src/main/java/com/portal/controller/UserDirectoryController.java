package com.portal.controller;

import com.platform.common.dto.ApiResponse;
import com.portal.component.UserDirectoryComponent;
import com.portal.dto.UserSummariesRequest;
import com.portal.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Same authenticated Portal directory surface as the existing people search. */
@RestController
@RequestMapping("/tasks/users")
@RequiredArgsConstructor
public class UserDirectoryController {
    private final UserDirectoryComponent userDirectoryComponent;

    @PostMapping("/summaries")
    public ApiResponse<List<UserSummaryResponse>> summaries(
            @RequestBody @Valid UserSummariesRequest request) {
        return ApiResponse.success(userDirectoryComponent.summaries(request.userIds()));
    }
}
