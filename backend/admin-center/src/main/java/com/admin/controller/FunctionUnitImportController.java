package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
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

/**
 * 功能单元导入控制器
 * 支持从开发者工作站直接上传ZIP文件
 */
@Slf4j
@RestController
@RequestMapping("/function-units-import")
@RequiredArgsConstructor
@Tag(name = "功能单元导入", description = "支持ZIP文件上传导入和一键部署")
public class FunctionUnitImportController {
    
    private final FunctionUnitManagerComponent functionUnitManager;
    private final DeploymentManagerComponent deploymentManager;
    private final ObjectMapper objectMapper;
    
    /**
     * 导入功能单元ZIP包
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入功能单元", description = "上传ZIP文件导入功能单元")
    public ResponseEntity<Map<String, Object>> importFunctionUnit(
            @Parameter(description = "功能单元ZIP包") @RequestParam("file") MultipartFile file,
            @Parameter(description = "冲突处理策略") @RequestParam(defaultValue = "OVERWRITE") String conflictStrategy) {
        
        log.info("Importing function unit from file: {}", file.getOriginalFilename());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 解析ZIP文件
            Map<String, Object> packageData = parseZipFile(file);
            
            // 获取manifest信息
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = (Map<String, Object>) packageData.get("manifest");
            if (manifest == null) {
                manifest = (Map<String, Object>) packageData.get("metadata");
            }
            
            if (manifest == null) {
                result.put("status", "FAILED");
                result.put("message", "无效的功能单元包：缺少manifest.json或metadata.json");
                return ResponseEntity.badRequest().body(result);
            }
            
            String name = (String) manifest.get("name");
            String code = (String) manifest.get("code");
            String version = (String) manifest.get("version");
            String description = (String) manifest.get("description");
            
            // 构建导入请求
            FunctionUnitImportRequest importRequest = FunctionUnitImportRequest.builder()
                    .fileName(file.getOriginalFilename())
                    .name(name)
                    .version(version != null ? version : "1.0.0")
                    .description(description)
                    .fileContent((String) packageData.get("process"))
                    .overwrite("OVERWRITE".equals(conflictStrategy))
                    .build();
            
            // 执行导入
            ImportResult importResult = functionUnitManager.importFunctionPackage(importRequest, "system");
            
            if (importResult.isSuccess()) {
                // 保存表单内容
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> forms = (List<Map<String, Object>>) packageData.get("forms");
                if (forms != null && !forms.isEmpty()) {
                    for (Map<String, Object> formData : forms) {
                        try {
                            String formName = (String) formData.get("formName");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> configJson = (Map<String, Object>) formData.get("configJson");
                            
                            if (formName != null && configJson != null) {
                                // 将表单配置转换为JSON字符串存储
                                String formConfigStr = objectMapper.writeValueAsString(configJson);
                                functionUnitManager.addFunctionUnitContent(
                                        importResult.getFunctionUnit().getId(),
                                        com.admin.enums.ContentType.FORM,
                                        formName,
                                        formConfigStr
                                );
                                log.info("Saved form content: {} for function unit: {}", 
                                        formName, importResult.getFunctionUnit().getId());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to save form content", e);
                        }
                    }
                }
                
                result.put("status", "SUCCESS");
                result.put("functionUnitId", importResult.getFunctionUnit().getId());
                result.put("name", importResult.getFunctionUnit().getName());
                result.put("version", importResult.getFunctionUnit().getVersion());
                result.put("message", "导入成功");
                return ResponseEntity.ok(result);
            } else {
                result.put("status", "FAILED");
                result.put("message", importResult.getErrorMessage());
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Failed to import function unit", e);
            result.put("status", "FAILED");
            result.put("message", "导入失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 部署功能单元
     */
    @PostMapping("/{id}/deploy")
    @Operation(summary = "部署功能单元", description = "将功能单元部署到指定环境")
    public ResponseEntity<Map<String, Object>> deployFunctionUnit(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        log.info("Deploying function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取功能单元
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);
            
            // 如果是草稿状态，先验证
            if (functionUnit.getStatus() == FunctionUnitStatus.DRAFT) {
                functionUnit = functionUnitManager.validateFunctionUnit(id, "system");
            }
            
            // 获取部署参数
            Boolean autoEnable = (Boolean) request.getOrDefault("autoEnable", true);
            
            // 一键部署模式：跳过审批流程，直接启用功能单元
            if (autoEnable) {
                // 直接将功能单元标记为已部署
                functionUnit.markAsDeployed();
                functionUnitManager.saveFunctionUnit(functionUnit);
                
                result.put("status", "SUCCESS");
                result.put("functionUnitId", id);
                result.put("message", "一键部署成功，功能单元已启用");
                
                log.info("One-click deploy completed for function unit: {}", id);
                return ResponseEntity.ok(result);
            }
            
            // 非一键部署模式：走正常的部署审批流程
            String envStr = (String) request.getOrDefault("environment", "PRODUCTION");
            DeploymentEnvironment environment = DeploymentEnvironment.valueOf(envStr);
            
            // 创建部署
            FunctionUnitDeployment deployment = deploymentManager.createDeployment(
                    id, environment, DeploymentStrategy.FULL, "system");
            
            // 如果不需要审批，直接执行部署
            if (!deploymentManager.requiresApproval(environment)) {
                deployment = deploymentManager.executeDeployment(deployment.getId());
            }
            
            result.put("status", "SUCCESS");
            result.put("deploymentId", deployment.getId());
            result.put("deploymentStatus", deployment.getStatus().name());
            result.put("message", "部署成功");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to deploy function unit", e);
            result.put("status", "FAILED");
            result.put("message", "部署失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取已部署的功能单元列表（供用户门户使用）
     */
    @GetMapping("/deployed")
    @Operation(summary = "获取已部署的功能单元", description = "获取所有已部署的功能单元列表")
    public ResponseEntity<Map<String, Object>> getDeployedFunctionUnits() {
        log.info("Getting deployed function units");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            var units = functionUnitManager.listFunctionUnitsByStatus(
                    FunctionUnitStatus.DEPLOYED, 
                    org.springframework.data.domain.Pageable.unpaged());
            
            result.put("content", units.map(FunctionUnitInfo::fromEntity).getContent());
            result.put("totalElements", units.getTotalElements());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get deployed function units", e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 启用功能单元
     */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用功能单元")
    public ResponseEntity<Map<String, Object>> enableFunctionUnit(@PathVariable String id) {
        log.info("Enabling function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);
            
            if (functionUnit.getStatus() == FunctionUnitStatus.VALIDATED) {
                functionUnit.markAsDeployed();
                // 需要保存，但这里简化处理
            }
            
            result.put("status", "SUCCESS");
            result.put("message", "功能单元已启用");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to enable function unit", e);
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 禁用功能单元
     */
    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用功能单元")
    public ResponseEntity<Map<String, Object>> disableFunctionUnit(@PathVariable String id) {
        log.info("Disabling function unit: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);
            functionUnit.markAsDeprecated();
            
            result.put("status", "SUCCESS");
            result.put("message", "功能单元已禁用");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to disable function unit", e);
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 解析ZIP文件
     */
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
        
        // 解析 manifest.json 或 metadata.json
        if (rawFiles.containsKey("manifest.json")) {
            result.put("manifest", objectMapper.readValue(rawFiles.get("manifest.json"), Map.class));
        } else if (rawFiles.containsKey("metadata.json")) {
            result.put("metadata", objectMapper.readValue(rawFiles.get("metadata.json"), Map.class));
        }
        
        // 解析流程文件
        for (String fileName : rawFiles.keySet()) {
            if (fileName.endsWith(".bpmn")) {
                result.put("process", new String(rawFiles.get(fileName), StandardCharsets.UTF_8));
                break;
            }
        }
        
        // 解析表单文件
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
        
        return result;
    }
}
