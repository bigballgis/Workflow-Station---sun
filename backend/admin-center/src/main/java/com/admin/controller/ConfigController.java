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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;
import com.admin.security.AdminTeamScope;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
@Tag(name = "System Config", description = "System config CRUD, rollback and sync APIs")
@AdminAccess(AdminAudience.SHARED_READ)
public class ConfigController {
    
    private final ConfigManagerComponent configManager;
    private final I18nService i18nService;
    private final AdminTeamScope teamScope;
    
    // ==================== Config CRUD ====================
    
    @PostMapping
    @Operation(summary = "Create config")
    public ResponseEntity<SystemConfig> createConfig(
            @Valid @RequestBody ConfigCreateRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configManager.createConfig(request, userId));
    }
    
    @GetMapping
    @Operation(summary = "Get all configs")
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        return ResponseEntity.ok(forCaller(configManager.getAllConfigs()));
    }

    @GetMapping("/{configKey}")
    @Operation(summary = "Get config")
    public ResponseEntity<SystemConfig> getConfig(@PathVariable String configKey) {
        return ResponseEntity.ok(forCaller(configManager.getConfig(configKey)));
    }

    @GetMapping("/{configKey}/value")
    @Operation(summary = "Get config value")
    public ResponseEntity<String> getConfigValue(@PathVariable String configKey) {
        if (hidesSecrets() && isEncrypted(configManager.getConfig(configKey))) {
            throw new AccessDeniedException(i18nService.getMessage("admin.config.secret_hidden"));
        }
        return ResponseEntity.ok(configManager.getConfigValue(configKey));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get configs by category")
    public ResponseEntity<List<SystemConfig>> getConfigsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(forCaller(configManager.getConfigsByCategory(category)));
    }

    @GetMapping("/environment/{environment}")
    @Operation(summary = "Get configs by environment")
    public ResponseEntity<List<SystemConfig>> getConfigsByEnvironment(@PathVariable String environment) {
        return ResponseEntity.ok(forCaller(configManager.getConfigsByEnvironment(environment)));
    }
    
    @PutMapping("/{configKey}")
    @Operation(summary = "Update config")
    public ResponseEntity<SystemConfig> updateConfig(
            @PathVariable String configKey,
            @Valid @RequestBody ConfigUpdateRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(configManager.updateConfig(configKey, request, userId));
    }
    
    @DeleteMapping("/{configKey}")
    @Operation(summary = "Delete config")
    public ResponseEntity<Void> deleteConfig(@PathVariable String configKey) {
        configManager.deleteConfig(configKey);
        return ResponseEntity.noContent().build();
    }
    
    // ==================== Version Management and Rollback ====================
    
    @GetMapping("/{configKey}/history")
    @Operation(summary = "Get config history")
    public ResponseEntity<Page<ConfigHistory>> getConfigHistory(
            @PathVariable String configKey, Pageable pageable) {
        Page<ConfigHistory> history = configManager.getConfigHistory(configKey, pageable);
        if (hidesSecrets() && isEncrypted(configManager.getConfig(configKey))) {
            history = history.map(change -> change.toBuilder().oldValue(null).newValue(null).build());
        }
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/{configKey}/rollback/{version}")
    @Operation(summary = "Rollback config")
    public ResponseEntity<SystemConfig> rollbackConfig(
            @PathVariable String configKey,
            @PathVariable Integer version) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(configManager.rollbackConfig(configKey, version, userId));
    }
    
    // ==================== Impact Assessment ====================

    // SYS_ADMIN only (no @AdminAccess): it returns the current value, and only a System Administrator saves configs.
    @PostMapping("/{configKey}/assess-impact")
    @Operation(summary = "Assess config change impact")
    public ResponseEntity<ImpactAssessment> assessConfigChange(
            @PathVariable String configKey,
            @RequestBody String newValue) {
        return ResponseEntity.ok(configManager.assessConfigChange(configKey, newValue));
    }
    
    // ==================== Multi-Environment Sync ====================
    
    @GetMapping("/compare/{sourceEnv}/{targetEnv}")
    @Operation(summary = "Compare environment config diffs")
    public ResponseEntity<ConfigDiffResult> compareEnvironments(
            @PathVariable String sourceEnv,
            @PathVariable String targetEnv) {
        ConfigDiffResult result = configManager.compareEnvironments(sourceEnv, targetEnv);
        if (!hidesSecrets()) {
            return ResponseEntity.ok(result);
        }
        Set<String> encryptedKeys = new HashSet<>();
        for (String environment : List.of(sourceEnv, targetEnv)) {
            configManager.getConfigsByEnvironment(environment).stream().filter(ConfigController::isEncrypted)
                    .forEach(config -> encryptedKeys.add(config.getConfigKey()));
        }
        List<ConfigDiffItem> differences = result.getDifferences().stream()
                .map(diff -> encryptedKeys.contains(diff.getConfigKey())
                        ? ConfigDiffItem.builder().configKey(diff.getConfigKey()).diffType(diff.getDiffType()).build()
                        : diff)
                .toList();
        return ResponseEntity.ok(ConfigDiffResult.builder().sourceEnvironment(result.getSourceEnvironment())
                .targetEnvironment(result.getTargetEnvironment()).differences(differences)
                .totalDifferences(result.getTotalDifferences()).build());
    }

    // ==================== Secrets (D-44) ====================

    /** Encrypted values are SYS_ADMIN's alone: Technical Leads and Auditors see the key, not the value. */
    private boolean hidesSecrets() {
        return !teamScope.caller().isSysAdmin();
    }

    private static boolean isEncrypted(SystemConfig config) {
        return Boolean.TRUE.equals(config.getEncrypted());
    }

    /** A copy without the value of an encrypted entry; the managed entity is never changed. */
    private SystemConfig forCaller(SystemConfig config) {
        return hidesSecrets() && isEncrypted(config)
                ? config.toBuilder().configValue(null).defaultValue(null).build()
                : config;
    }

    private List<SystemConfig> forCaller(List<SystemConfig> configs) {
        return configs.stream().map(this::forCaller).toList();
    }
    
    @PostMapping("/sync/{sourceEnv}/{targetEnv}")
    @Operation(summary = "Sync configs to target environment")
    public ResponseEntity<ConfigSyncResult> syncConfigs(
            @PathVariable String sourceEnv,
            @PathVariable String targetEnv,
            @RequestBody List<String> configKeys) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(configManager.syncConfigs(sourceEnv, targetEnv, configKeys, userId));
    }
}
