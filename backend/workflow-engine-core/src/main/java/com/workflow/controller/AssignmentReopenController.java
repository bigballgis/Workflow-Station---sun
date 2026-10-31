package com.workflow.controller;

import com.platform.common.assignment.AssignmentReopenReceipt;
import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentReopenComponent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Portal owns public access and local mirrors; accepted round snapshots are exposed only to authenticated services. */
@RestController
@RequestMapping("/api/v1/internal/assignment/reopens")
public class AssignmentReopenController {
    private final AssignmentReopenComponent component;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentReopenController(AssignmentReopenComponent component) { this.component = component; }

    @PostMapping("/{source}") public ApiResponse<AssignmentReopenReceipt> reopen(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable String source,
            @Valid @RequestBody AssignmentReopenReceipt.Request request) {
        AssignmentServiceAuthentication.require(serviceToken, token);
        return ApiResponse.success(component.reopen(source, request));
    }

    @GetMapping public ApiResponse<List<AssignmentReopenReceipt>> pending(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @RequestParam(defaultValue="50") int limit) {
        AssignmentServiceAuthentication.require(serviceToken, token);
        return ApiResponse.success(component.pending(limit));
    }

    @PostMapping("/{operation}/mirrored") public ApiResponse<Boolean> mirrored(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable UUID operation) {
        AssignmentServiceAuthentication.require(serviceToken, token);
        component.mirrored(operation);
        return ApiResponse.success(true);
    }

    @PostMapping("/{operation}/retry") public ApiResponse<Boolean> retry(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable UUID operation, @RequestBody Retry request) {
        AssignmentServiceAuthentication.require(serviceToken, token);
        component.retry(operation, request.conflict());
        return ApiResponse.success(true);
    }
    public record Retry(boolean conflict) { }
}
