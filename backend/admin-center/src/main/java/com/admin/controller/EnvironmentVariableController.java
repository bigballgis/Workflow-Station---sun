package com.admin.controller;

import com.admin.component.EnvironmentVariableComponent;
import com.admin.dto.EnvironmentVariableRequest;
import com.admin.dto.EnvironmentVariableResponse;
import com.admin.enums.EnvironmentValueKind;
import com.platform.common.dto.ApiResponse;
import com.platform.common.resource.AbstractBaseController;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/environment-variables")
@Tag(name = "Environment variables", description = "TEXT / VAULT environment catalog")
public class EnvironmentVariableController extends AbstractBaseController {

    private final EnvironmentVariableComponent environmentVariableComponent;

    public EnvironmentVariableController(EnvironmentVariableComponent environmentVariableComponent) {
        this.environmentVariableComponent = environmentVariableComponent;
    }

    @GetMapping
    @Operation(summary = "List environment variables for the current deploy_env")
    public ResponseEntity<ApiResponse<List<EnvironmentVariableResponse>>> list(
            @RequestParam(required = false) EnvironmentValueKind kind) {
        return handleRequest(() -> environmentVariableComponent.list(kind));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get environment variable")
    public ResponseEntity<ApiResponse<EnvironmentVariableResponse>> get(@PathVariable String id) {
        return handleRequest(() -> environmentVariableComponent.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create environment variable")
    public ResponseEntity<ApiResponse<EnvironmentVariableResponse>> create(
            @Valid @RequestBody EnvironmentVariableRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated"));
        return handleRequest(() -> environmentVariableComponent.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update environment variable")
    public ResponseEntity<ApiResponse<EnvironmentVariableResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody EnvironmentVariableRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated"));
        return handleRequest(() -> environmentVariableComponent.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete environment variable")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        return handleRequest(() -> {
            environmentVariableComponent.delete(id);
            return null;
        });
    }
}
