package com.admin.controller;

import com.admin.automation.AutomationConnectionComponent;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Logged-in management of automation runtime identity and published flow scope. */
@RestController
@RequestMapping
@Tag(name = "Automation connection", description = "Runtime identity and published data scope")
public class AutomationConnectionController {

    private final AutomationConnectionComponent component;

    public AutomationConnectionController(AutomationConnectionComponent component) {
        this.component = component;
    }

    @GetMapping("/automation-service-accounts")
    @Operation(summary = "List service accounts that can be bound to a workspace")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAccounts(
            @RequestHeader(value = "X-Dev-Group-Id", required = false) String workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(component.listAccounts(workspaceId)));
    }

    @PostMapping("/automation-service-accounts")
    @Operation(summary = "Register an existing user as a service account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @RequestHeader(value = "X-Dev-Group-Id", required = false) String workspaceId,
            @RequestBody Map<String, Object> body) {
        String userId = text(body, "userId");
        String displayName = text(body, "displayName");
        boolean platformDefault = Boolean.TRUE.equals(body.get("platformDefault"));
        return ResponseEntity.ok(ApiResponse.success(
                component.register(workspaceId, userId, displayName, platformDefault)));
    }

    @GetMapping("/automation-runtime-connections/{workspaceId}")
    @Operation(summary = "Runtime identity of a workspace")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runtime(@PathVariable String workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(component.runtimeForCaller(workspaceId)));
    }

    @PutMapping("/automation-runtime-connections/{workspaceId}")
    @Operation(summary = "Bind a workspace to a registered service account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bind(@PathVariable String workspaceId,
                                                                  @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(component.bind(workspaceId, text(body, "serviceAccountUserId"))));
    }

    @DeleteMapping("/automation-runtime-connections/{workspaceId}")
    @Operation(summary = "Clear a workspace binding and use the platform default account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unbind(@PathVariable String workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(component.unbind(workspaceId)));
    }

    @GetMapping("/automation/flows/{flowId}/scope")
    @Operation(summary = "Published data scope of a flow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readScope(@PathVariable String flowId) {
        return ResponseEntity.ok(ApiResponse.success(component.readScope(flowId)));
    }

    @PutMapping("/automation/flows/{flowId}/runtime-identity")
    @Operation(summary = "Set a published flow's runtime identity without republishing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateFlowIdentity(
            @PathVariable String flowId,
            @RequestHeader(value = "X-Dev-Group-Id", required = false) String workspaceId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(
                component.updateFlowIdentity(flowId, workspaceId, text(body, "serviceAccountUserId"))));
    }

    @PutMapping("/automation/flows/{flowId}/scope")
    @Operation(summary = "Publish the data scope of a flow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishScope(
            @PathVariable String flowId,
            @RequestHeader(value = "X-Dev-Group-Id", required = false) String workspaceId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(component.publishScope(
                flowId, longValue(body.get("functionUnitId")), stringList(body.get("catalogIds")),
                stringList(body.get("capabilities")), workspaceId, text(body, "serviceAccountUserId"))));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text.trim());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
