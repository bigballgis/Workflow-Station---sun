package com.admin.controller;

import com.admin.component.ConfigManagerComponent;
import com.admin.component.ConfigManagerComponent.*;
import com.admin.entity.ConfigHistory;
import com.admin.entity.SystemConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
@Tag(name = "系统配置", description = "系统配置CRUD、回滚和同步接口")
public class ConfigController {
    
    private final ConfigManagerComponent configManager;
    
    // ==================== 配置 CRUD ====================
    
    @PostMapping
    @Operation(summary = "创建配置")
    public ResponseEntity<SystemConfig> createConfig(
            @Valid @RequestBody ConfigCreateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configManager.createConfig(request, userId));
    }
    
    @GetMapping
    @Operation(summary = "获取所有配置")
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        return ResponseEntity.ok(configManager.getAllConfigs());
    }
    
    @GetMapping("/{configKey}")
    @Operation(summary = "获取配置")
    public ResponseEntity<SystemConfig> getConfig(@PathVariable String configKey) {
        return ResponseEntity.ok(configManager.getConfig(configKey));
    }
    
    @GetMapping("/{configKey}/value")
    @Operation(summary = "获取配置值")
    public ResponseEntity<String> getConfigValue(@PathVariable String configKey) {
        return ResponseEntity.ok(configManager.getConfigValue(configKey));
    }
    
    @GetMapping("/category/{category}")
    @Operation(summary = "按类别获取配置")
    public ResponseEntity<List<SystemConfig>> getConfigsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(configManager.getConfigsByCategory(category));
    }
    
    @GetMapping("/environment/{environment}")
    @Operation(summary = "按环境获取配置")
    public ResponseEntity<List<SystemConfig>> getConfigsByEnvironment(@PathVariable String environment) {
        return ResponseEntity.ok(configManager.getConfigsByEnvironment(environment));
    }
    
    @PutMapping("/{configKey}")
    @Operation(summary = "更新配置")
    public ResponseEntity<SystemConfig> updateConfig(
            @PathVariable String configKey,
            @Valid @RequestBody ConfigUpdateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(configManager.updateConfig(configKey, request, userId));
    }
    
    @DeleteMapping("/{configKey}")
    @Operation(summary = "删除配置")
    public ResponseEntity<Void> deleteConfig(@PathVariable String configKey) {
        configManager.deleteConfig(configKey);
        return ResponseEntity.noContent().build();
    }
    
    // ==================== 版本管理和回滚 ====================
    
    @GetMapping("/{configKey}/history")
    @Operation(summary = "获取配置历史")
    public ResponseEntity<Page<ConfigHistory>> getConfigHistory(
            @PathVariable String configKey, Pageable pageable) {
        return ResponseEntity.ok(configManager.getConfigHistory(configKey, pageable));
    }
    
    @PostMapping("/{configKey}/rollback/{version}")
    @Operation(summary = "回滚配置")
    public ResponseEntity<SystemConfig> rollbackConfig(
            @PathVariable String configKey,
            @PathVariable Integer version,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(configManager.rollbackConfig(configKey, version, userId));
    }
    
    // ==================== 影响评估 ====================
    
    @PostMapping("/{configKey}/assess-impact")
    @Operation(summary = "评估配置变更影响")
    public ResponseEntity<ImpactAssessment> assessConfigChange(
            @PathVariable String configKey,
            @RequestBody String newValue) {
        return ResponseEntity.ok(configManager.assessConfigChange(configKey, newValue));
    }
    
    // ==================== 多环境同步 ====================
    
    @GetMapping("/compare/{sourceEnv}/{targetEnv}")
    @Operation(summary = "比较环境配置差异")
    public ResponseEntity<ConfigDiffResult> compareEnvironments(
            @PathVariable String sourceEnv,
            @PathVariable String targetEnv) {
        return ResponseEntity.ok(configManager.compareEnvironments(sourceEnv, targetEnv));
    }
    
    @PostMapping("/sync/{sourceEnv}/{targetEnv}")
    @Operation(summary = "同步配置到目标环境")
    public ResponseEntity<ConfigSyncResult> syncConfigs(
            @PathVariable String sourceEnv,
            @PathVariable String targetEnv,
            @RequestBody List<String> configKeys,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(configManager.syncConfigs(sourceEnv, targetEnv, configKeys, userId));
    }
}
