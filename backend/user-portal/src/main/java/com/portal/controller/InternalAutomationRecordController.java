package com.portal.controller;

import com.platform.common.dto.ApiResponse;
import com.portal.automation.AutomationProcessStartComponent;
import com.portal.automation.AutomationRecordComponent;
import com.portal.config.PortalInternalApiProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Automation-engine access to Function Unit records. The token proves the engine, not a person. */
@RestController
@RequestMapping("/internal/automation")
public class InternalAutomationRecordController {

    private final PortalInternalApiProperties tokens;
    private final AutomationRecordComponent records;
    private final AutomationProcessStartComponent processStart;

    public InternalAutomationRecordController(PortalInternalApiProperties tokens, AutomationRecordComponent records,
                                              AutomationProcessStartComponent processStart) {
        this.tokens = tokens;
        this.records = records;
        this.processStart = processStart;
    }

    @GetMapping("/function-units/{functionUnitCode}/schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schemaByCode(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable String functionUnitCode, @RequestParam String externalProjectId, @RequestParam String flowId) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(records.schemaByCode(functionUnitCode, externalProjectId, flowId)));
    }

    @GetMapping("/records/{processInstanceId}/schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schemaByRecord(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable String processInstanceId, @RequestParam String externalProjectId, @RequestParam String flowId) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(records.schemaByRecord(processInstanceId, externalProjectId, flowId)));
    }

    @GetMapping("/records/{processInstanceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> read(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable String processInstanceId, @RequestParam String externalProjectId, @RequestParam String flowId) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(records.read(processInstanceId, externalProjectId, flowId)));
    }

    @PostMapping("/processes/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(processStart.start(body, idempotencyKey)));
    }

    @PostMapping("/records/query")
    public ResponseEntity<ApiResponse<Map<String, Object>>> query(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(records.query(body)));
    }

    @PatchMapping("/records/{processInstanceId}/fields")
    public ResponseEntity<ApiResponse<Map<String, Object>>> patch(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String processInstanceId, @RequestBody Map<String, Object> body) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(records.patchFields(processInstanceId, body, idempotencyKey)));
    }

    @PostMapping("/records/{processInstanceId}/sub-tables/{bindingId}/rows")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addRow(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String processInstanceId, @PathVariable long bindingId, @RequestBody Map<String, Object> body) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(records.addRow(processInstanceId, bindingId, body, idempotencyKey)));
    }

    @PutMapping("/records/{processInstanceId}/sub-tables/{bindingId}/rows/{rowId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> replaceRow(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String processInstanceId, @PathVariable long bindingId, @PathVariable String rowId,
            @RequestBody Map<String, Object> body) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(
                records.replaceRow(processInstanceId, bindingId, rowId, body, idempotencyKey)));
    }

    @DeleteMapping("/records/{processInstanceId}/sub-tables/{bindingId}/rows/{rowId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteRow(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String processInstanceId, @PathVariable long bindingId, @PathVariable String rowId,
            @RequestBody Map<String, Object> body) {
        tokens.requireValidToken(token);
        return ResponseEntity.ok(ApiResponse.success(
                records.deleteRow(processInstanceId, bindingId, rowId, body, idempotencyKey)));
    }
}
