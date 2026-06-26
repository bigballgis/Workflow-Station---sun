package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.admin.component.ActionDefinitionImportWriter;
import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.ImportResult;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.DeploymentEnvironment;
import com.admin.enums.DeploymentStrategy;
import com.admin.enums.FunctionUnitStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;

/**
 * Accepts workstation-exported ZIP archives and exposes fast-path deployment hooks for admins.
 */
@Slf4j
@RestController
@RequestMapping("/function-units-import")
@RequiredArgsConstructor
@Tag(name = "Function unit import", description = "ZIP-based import bundles and streamlined deployment endpoints")
public class FunctionUnitImportController {
    
    private final FunctionUnitManagerComponent functionUnitManager;
    private final DeploymentManagerComponent deploymentManager;
    private final ProcessDeploymentComponent processDeploymentComponent;
    private final ActionDefinitionImportWriter actionDefinitionImportWriter;
    private final ObjectMapper objectMapper;
    private final I18nService i18nService;
    
    /**
     * Import a workstation ZIP artifact into repository metadata/content tables.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a function unit", description = "Upload a workstation ZIP archive")
    public ResponseEntity<Map<String, Object>> importFunctionUnit(
            @Parameter(description = "Function unit ZIP") @RequestParam("file") MultipartFile file) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        
        log.info("Importing function unit from file: {}", file.getOriginalFilename());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Parse uploaded archive
            Map<String, Object> packageData = parseZipFile(file);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = (Map<String, Object>) packageData.get("manifest");
            if (manifest == null) {
                manifest = (Map<String, Object>) packageData.get("metadata");
            }
            
            if (manifest == null) {
                result.put("status", "FAILED");
                result.put("message", i18nService.getMessage("admin.fu.import_invalid_package_manifest"));
                return ResponseEntity.badRequest().body(result);
            }
            
            String name = trimToNull((String) manifest.get("name"));
            String code = trimToNull((String) manifest.get("code"));
            String version = trimToNull((String) manifest.get("version"));
            String description = trimToNull((String) manifest.get("description"));
            
            FunctionUnitImportRequest importRequest = FunctionUnitImportRequest.builder()
                    .fileName(file.getOriginalFilename())
                    .name(name)
                    .code(code)
                    .version(version != null ? version : "1.0.0")
                    .description(description)
                    .fileContent((String) packageData.get("process"))
                    .iconSvg(extractIconSvg(manifest))
                    .build();
            
            ImportResult importResult = functionUnitManager.importFunctionPackage(importRequest, userId);
            
            if (importResult.isSuccess()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> forms = (List<Map<String, Object>>) packageData.get("forms");
                if (forms != null && !forms.isEmpty()) {
                    for (Map<String, Object> formData : forms) {
                        try {
                            String formName = (String) formData.get("formName");
                            Object formIdObj = formData.get("formId");
                            String sourceId = formIdObj != null ? String.valueOf(formIdObj) : null;
                            
                            @SuppressWarnings("unchecked")
                            Map<String, Object> configJson = (Map<String, Object>) formData.get("configJson");
                            
                            if (formName != null && configJson != null) {
                                String formConfigStr = objectMapper.writeValueAsString(configJson);
                                functionUnitManager.addFunctionUnitContent(
                                        importResult.getFunctionUnit().getId(),
                                        com.admin.enums.ContentType.FORM,
                                        formName,
                                        formConfigStr,
                                        sourceId
                                );
                                log.info("Saved form content: {} with sourceId: {} for function unit: {}", 
                                        formName, sourceId, importResult.getFunctionUnit().getId());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to save form content", e);
                        }
                    }
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> actions = (List<Map<String, Object>>) packageData.get("actions");
                // Delegate to a transactional bean: the delete-then-insert must run in an active
                // transaction (derived delete -> em.remove), which a plain controller method lacks.
                actionDefinitionImportWriter.replaceActions(
                        importResult.getFunctionUnit().getId(), actions);
                
                result.put("status", "SUCCESS");
                result.put("functionUnitId", importResult.getFunctionUnit().getId());
                result.put("name", importResult.getFunctionUnit().getName());
                result.put("version", importResult.getFunctionUnit().getVersion());
                result.put("versioned", importResult.isVersioned());
                result.put("message", i18nService.getMessage("admin.fu.import_success"));
                return ResponseEntity.ok(result);
            } else {
                result.put("status", "FAILED");
                result.put("message", importResult.getErrorMessage());
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Failed to import function unit", e);
            result.put("status", "FAILED");
            result.put("message", i18nService.getMessage("admin.fu.import_unexpected_error"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * Promote/import flow into an environment optionally wiring Flowable and auto-enable shortcuts.
     */
    @PostMapping("/{id}/deploy")
    @Operation(summary = "Deploy a function unit", description = "Deploy to a target environment and optionally push BPMN to Flowable")
    public ResponseEntity<Map<String, Object>> deployFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        
        log.info("Deploying function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);

            if (!functionUnit.isDeployable()) {
                result.put("status", "FAILED");
                if (functionUnit.getStatus() == FunctionUnitStatus.DRAFT) {
                    result.put("message", i18nService.getMessage("admin.fu.deploy_validation_required"));
                    result.put("errorCode", "VALIDATION_REQUIRED");
                } else {
                    result.put("message", i18nService.getMessage("admin.fu.deploy_status_invalid", functionUnit.getStatus()));
                    result.put("errorCode", "INVALID_STATUS");
                }
                return ResponseEntity.badRequest().body(result);
            }

            boolean autoEnable = booleanRequestSetting(request, "autoEnable", true);
            boolean deployToFlowable = booleanRequestSetting(request, "deployToFlowable", true);
            ProcessDeploymentComponent.ProcessDeploymentResult processResult = null;
            if (deployToFlowable) {
                processResult = processDeploymentComponent.deployFunctionUnitProcess(id);
                
                if (!processResult.isSuccess() && !processResult.isPartialSuccess()) {
                    if (processResult.isEngineUnavailable()) {
                        result.put("status", "FAILED");
                        result.put("message", processResult.getMessage());
                        result.put("errors", processResult.getErrors());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
                    }
                }
                
                result.put("processDeployment", Map.of(
                        "success", processResult.isSuccess(),
                        "partialSuccess", processResult.isPartialSuccess(),
                        "message", processResult.getMessage(),
                        "deployedProcesses", processResult.getDeployedProcesses(),
                        "errors", processResult.getErrors()
                ));
            }
            
            if (autoEnable) {
                functionUnit.markAsDeployed();
                functionUnitManager.saveFunctionUnit(functionUnit);
                
                List<String> disabledVersions = functionUnitManager.disableOtherVersions(
                        functionUnit.getCode(), 
                        functionUnit.getVersion(), 
                        userId);
                
                if (!disabledVersions.isEmpty()) {
                    result.put("disabledVersions", disabledVersions);
                    log.info("Automatically disabled {} previous versions: {}", 
                            disabledVersions.size(), disabledVersions);
                }
                
                try {
                    String envStr = (String) request.getOrDefault("environment", "DEVELOPMENT");
                    DeploymentEnvironment environment = DeploymentEnvironment.valueOf(envStr);

                    FunctionUnitDeployment deployment = deploymentManager.createDeployment(
                            id, environment, DeploymentStrategy.FULL, userId);
                    
                    if (!deploymentManager.requiresApproval(environment)) {
                        deployment = deploymentManager.executeDeployment(deployment.getId());
                        result.put("deploymentId", deployment.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to create deployment record for audit: {}", e.getMessage());
                }

                functionUnitManager.finalizeOneClickDeployEnable(id, userId);
                
                result.put("status", "SUCCESS");
                result.put("functionUnitId", id);
                result.put("message", i18nService.getMessage("admin.deploy.one_click_success"));
                
                log.info("One-click deploy completed for function unit: {}", id);
                return ResponseEntity.ok(result);
            }
            
            String envStr = (String) request.getOrDefault("environment", "PRODUCTION");
            DeploymentEnvironment environment = DeploymentEnvironment.valueOf(envStr);

            FunctionUnitDeployment deployment = deploymentManager.createDeployment(
                    id, environment, DeploymentStrategy.FULL, userId);

            if (!deploymentManager.requiresApproval(environment)) {
                deployment = deploymentManager.executeDeployment(deployment.getId());
            }
            
            result.put("status", "SUCCESS");
            result.put("deploymentId", deployment.getId());
            result.put("deploymentStatus", deployment.getStatus().name());
            result.put("message", i18nService.getMessage("admin.deploy.record_success"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to deploy function unit", e);
            result.put("status", "FAILED");
            result.put("message", i18nService.getMessage("admin.deploy.deploy_failed_generic"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /** Backing handler for persisted Flowable linkage metadata returned to clients. */
    @GetMapping("/{id}/process-deployment")
    @Operation(summary = "Get Flowable deployment summary", description = "Returns BPMN deployment linkage for the function unit")
    public ResponseEntity<Map<String, Object>> getProcessDeploymentInfo(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        
        log.info("Getting process deployment info for function unit: {}", id);
        
        try {
            Map<String, Object> info = processDeploymentComponent.getProcessDeploymentInfo(id);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Failed to get process deployment info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", i18nService.getMessage("admin.deploy.retrieve_process_info_failed")));
        }
    }
    
    /** BPMN-only deploy path when Flowable rollout is deferred from main bundle import. */
    @PostMapping("/{id}/deploy-process")
    @Operation(summary = "Deploy BPMN to Flowable only", description = "Pushes BPMN XML from stored content to Flowable")
    public ResponseEntity<Map<String, Object>> deployProcessToFlowable(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        
        log.info("Deploying process to Flowable for function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ProcessDeploymentComponent.ProcessDeploymentResult deployResult = 
                    processDeploymentComponent.deployFunctionUnitProcess(id);
            
            result.put("functionUnitId", id);
            result.put("success", deployResult.isSuccess());
            result.put("partialSuccess", deployResult.isPartialSuccess());
            result.put("message", deployResult.getMessage());
            result.put("deployedProcesses", deployResult.getDeployedProcesses());
            result.put("errors", deployResult.getErrors());
            
            if (deployResult.isSuccess() || deployResult.isPartialSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
            
        } catch (Exception e) {
            log.error("Failed to deploy process to Flowable", e);
            result.put("success", false);
            result.put("message", i18nService.getMessage("admin.deploy.deploy_process_generic_failed"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /** Removes Flowable deployment artefacts for archived versions. */
    @DeleteMapping("/{id}/undeploy-process")
    @Operation(summary = "Undeploy BPMN from Flowable", description = "Deletes process definitions optionally cascading instances")
    public ResponseEntity<Map<String, Object>> undeployProcess(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "Cascade running instances flag")
            @RequestParam(defaultValue = "false") boolean cascade) {
        
        log.info("Undeploying process for function unit: {}, cascade: {}", id, cascade);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = processDeploymentComponent.undeployFunctionUnitProcess(id, cascade);
            
            result.put("functionUnitId", id);
            result.put("success", success);
            result.put("message",
                    success
                            ? i18nService.getMessage("admin.deploy.process_undeploy_success")
                            : i18nService.getMessage("admin.deploy.process_undeploy_failed"));
            
            return success ? ResponseEntity.ok(result) : 
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            
        } catch (Exception e) {
            log.error("Failed to undeploy process", e);
            result.put("success", false);
            result.put("message", i18nService.getMessage("admin.deploy.undeploy_process_failed_generic"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * Read-only catalogue for portals listing deployable artefacts.
     */
    @GetMapping("/deployed")
    @Operation(summary = "List deployed function units",
            description = "Returns DEPLOYED+ENABLED artefacts consumable by the user portal launcher")
    public ResponseEntity<Map<String, Object>> getDeployedFunctionUnits() {
        log.info("Getting deployed and enabled function units for end users");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            var units = functionUnitManager.listDeployedAndEnabledFunctionUnits(
                    org.springframework.data.domain.Pageable.unpaged());
            
            result.put("content", units.map(FunctionUnitInfo::fromEntity).getContent());
            result.put("totalElements", units.getTotalElements());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get deployed function units", e);
            result.put("error", i18nService.getMessage("admin.deploy.retrieve_deployed_list_failed"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /** Idempotent publisher enablement flag for operator consoles. */
    @PostMapping("/{id}/enable")
    @Operation(summary = "Enable function unit toggle")
    public ResponseEntity<Map<String, Object>> enableFunctionUnit(
            @PathVariable String id) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Enabling function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);
            
            if (functionUnit.getStatus() == FunctionUnitStatus.VALIDATED) {
                functionUnit.markAsDeployed();
                functionUnitManager.saveFunctionUnit(functionUnit);
            }
            
            result.put("status", "SUCCESS");
            result.put("message", i18nService.getMessage("admin.deploy.fu_enable_success"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to enable function unit", e);
            result.put("status", "FAILED");
            result.put("message", i18nService.getMessage("admin.deploy.deploy_failed_generic"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /** Deprecates the consumer-facing launcher entry until re-enabled. */
    @PostMapping("/{id}/disable")
    @Operation(summary = "Disable feature toggle / deprecate view")
    public ResponseEntity<Map<String, Object>> disableFunctionUnit(@PathVariable String id) {
        log.info("Disabling function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);
            functionUnit.markAsDeprecated();
            
            result.put("status", "SUCCESS");
            result.put("message", i18nService.getMessage("admin.deploy.fu_disable_success"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to disable function unit", e);
            result.put("status", "FAILED");
            result.put("message", i18nService.getMessage("admin.deploy.deploy_failed_generic"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /** Walk workstation bundle layout and hydrate manifest/forms/actions payloads. */
    private Map<String, Object> parseZipFile(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        Map<String, byte[]> rawFiles = new HashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                rawFiles.put(entry.getName(), baos.toByteArray());
            }
        }
        
        if (rawFiles.containsKey("manifest.json")) {
            result.put("manifest", objectMapper.readValue(rawFiles.get("manifest.json"), Map.class));
        } else if (rawFiles.containsKey("metadata.json")) {
            result.put("metadata", objectMapper.readValue(rawFiles.get("metadata.json"), Map.class));
        }
        
        for (String fileName : rawFiles.keySet()) {
            if (fileName.endsWith(".bpmn")) {
                result.put("process", new String(rawFiles.get(fileName), StandardCharsets.UTF_8));
                break;
            }
        }
        
        List<Map<String, Object>> forms = new ArrayList<>();
        for (String fileName : rawFiles.keySet()) {
            if (fileName.startsWith("forms/") && fileName.endsWith(".json")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> formData = objectMapper.readValue(rawFiles.get(fileName), Map.class);
                    forms.add(formData);
                } catch (Exception e) {
                    log.warn("Failed to parse form file: {}", fileName, e);
                }
            }
        }
        if (!forms.isEmpty()) {
            result.put("forms", forms);
        }
        
        List<Map<String, Object>> actions = new ArrayList<>();
        if (rawFiles.containsKey("actions.json")) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> actionList = objectMapper.readValue(rawFiles.get("actions.json"), List.class);
                actions.addAll(actionList);
            } catch (Exception e) {
                log.warn("Failed to parse actions.json", e);
            }
        } else {
            for (String fileName : rawFiles.keySet()) {
                if (fileName.startsWith("actions/") && fileName.endsWith(".json")) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> actionData = objectMapper.readValue(rawFiles.get(fileName), Map.class);
                        actions.add(actionData);
                    } catch (Exception e) {
                        log.warn("Failed to parse action file: {}", fileName, e);
                    }
                }
            }
        }
        if (!actions.isEmpty()) {
            result.put("actions", actions);
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private String extractIconSvg(Map<String, Object> manifest) {
        if (manifest == null) return null;
        Map<String, Object> icon = (Map<String, Object>) manifest.get("icon");
        if (icon == null) return null;
        return (String) icon.get("svgContent");
    }
    
    /**
     * Hot-switches which semantic version stays enabled for launcher consumers (constraints enforced in manager).
     */
    @PostMapping("/{code}/activate/{version}")
    @Operation(summary = "Activate a catalogued version",
            description = "Target must remain DEPLOYED and the highest semantic version for the code.")
    public ResponseEntity<Map<String, Object>> activateVersion(
            @Parameter(description = "Business code") @PathVariable String code,
            @Parameter(description = "Semantic version") @PathVariable String version) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        
        log.info("Activating version {} for function unit code: {}", version, code);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FunctionUnit activated = functionUnitManager.activateVersion(code, version, userId);
            
            result.put("status", "SUCCESS");
            result.put("functionUnitId", activated.getId());
            result.put("code", activated.getCode());
            result.put("version", activated.getVersion());
            result.put("name", activated.getName());
            result.put("enabled", activated.getEnabled());
            result.put("message", i18nService.getMessage("admin.fu.activate_version_success"));
            
            return ResponseEntity.ok(result);
            
        } catch (com.admin.exception.FunctionUnitNotFoundException e) {
            log.error("Function unit version not found: {}:{}", code, version);
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            
        } catch (com.admin.exception.AdminBusinessException e) {
            log.error("Failed to activate version: {}", e.getMessage());
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            
        } catch (Exception e) {
            log.error("Failed to activate version", e);
            result.put("status", "FAILED");
            result.put("message", i18nService.getMessage("admin.fu.activate_version_failed"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /** Admin-only audit timeline for coexistence upgrades. */
    @GetMapping("/{code}/versions")
    @Operation(summary = "List version lineage", description = "Historical versions with deploy flags for auditing")
    public ResponseEntity<Map<String, Object>> getVersionHistory(
            @Parameter(description = "Business code") @PathVariable String code) {
        
        log.info("Getting version history for function unit code: {}", code);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<com.admin.dto.response.VersionHistoryEntry> history = 
                    functionUnitManager.getVersionHistoryWithStatus(code);
            
            result.put("code", code);
            result.put("versions", history);
            result.put("totalVersions", history.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get version history", e);
            result.put("error", i18nService.getMessage("admin.fu.version_history_load_failed"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /** Parses boolean-compatible JSON payloads where null means “fall back”. */
    private static boolean booleanRequestSetting(Map<String, Object> body, String key, boolean defaultIfAbsentOrNull) {
        if (!body.containsKey(key)) {
            return defaultIfAbsentOrNull;
        }
        Object v = body.get(key);
        if (v == null) {
            return defaultIfAbsentOrNull;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return defaultIfAbsentOrNull;
            }
            return Boolean.parseBoolean(t);
        }
        return defaultIfAbsentOrNull;
    }

    /** Trims inbound manifest primitives so semantic version predicates stay stable. */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
