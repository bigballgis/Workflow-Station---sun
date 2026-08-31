package com.admin.controller;

import com.admin.component.ForceUnclaimAuthorizationComponent;
import com.admin.dto.request.ForceUnclaimEvaluateRequest;
import com.admin.dto.response.ForceUnclaimEvaluateResponse;
import com.admin.dto.response.RoleLeaderGroup;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Force Unclaim", description = "Claim Hold force-unclaim authorization and role leaders")
public class ForceUnclaimController {

    private final ForceUnclaimAuthorizationComponent forceUnclaimAuthorizationComponent;

    @PostMapping("/force-unclaim/evaluate")
    @Operation(summary = "Evaluate force-unclaim flags for claim-pool tasks")
    public ApiResponse<ForceUnclaimEvaluateResponse> evaluate(
            @RequestBody @Valid ForceUnclaimEvaluateRequest request) {
        List<ForceUnclaimAuthorizationComponent.ForceUnclaimItem> items = new ArrayList<>();
        for (ForceUnclaimEvaluateRequest.Item item : request.getItems()) {
            items.add(new ForceUnclaimAuthorizationComponent.ForceUnclaimItem(
                    item.getTaskId(),
                    item.getBusinessUnitId(),
                    item.getRoleIds() != null ? item.getRoleIds() : List.of()));
        }
        return ApiResponse.success(ForceUnclaimEvaluateResponse.builder()
                .flags(forceUnclaimAuthorizationComponent.evaluate(request.getUserId(), items))
                .build());
    }

    @GetMapping("/business-units/{businessUnitId}/role-leaders")
    @Operation(summary = "List Leaders of each role in a business unit")
    public ApiResponse<List<RoleLeaderGroup>> listRoleLeaders(@PathVariable String businessUnitId) {
        return ApiResponse.success(forceUnclaimAuthorizationComponent.listLeaders(businessUnitId));
    }
}
