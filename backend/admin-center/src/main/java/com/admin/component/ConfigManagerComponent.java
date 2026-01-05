package com.admin.component;

import com.admin.entity.ConfigHistory;
import com.admin.entity.SystemConfig;
import com.admin.repository.ConfigHistoryRepository;
import com.admin.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统配置管理组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigManagerComponent {
    
    private final SystemConfigRepository configRepository;
    private final ConfigHistoryRepository historyRepository;
    
    // ==================== 配置 CRUD ====================
    
    @Transactional
    public SystemConfig createConfig(ConfigCreateRequest request, String userId) {
        if (configRepository.existsByConfigKey(request.getConfigKey())) {
            throw new RuntimeException("Config key already exists: " + request.getConfigKey());
        }
        
        SystemConfig config = SystemConfig.builder()
                .id(UUID.randomUUID().toString())
                .category(request.getCategory())
                .configKey(request.getConfigKey())
                .configName(request.getConfigName())
                .configValue(request.getConfigValue())
                .defaultValue(request.getDefaultValue())
                .valueType(request.getValueType())
                .description(request.getDescription())
                .encrypted(request.getEncrypted() != null ? request.getEncrypted() : false)
                .editable(request.getEditable() != null ? request.getEditable() : true)
                .version(1)
                .environment(request.getEnvironment())
                .updatedBy(userId)
                .build();
        
        return configRepository.save(config);
    }
    
    @Transactional
    public SystemConfig updateConfig(String configKey, ConfigUpdateRequest request, String userId) {
        SystemConfig config = configRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new RuntimeException("Config not found: " + configKey));
        
        if (!config.getEditable()) {
            throw new RuntimeException("Config is not editable: " + configKey);
        }
        
        // 记录变更历史
        ConfigHistory history = ConfigHistory.builder()
                .id(UUID.randomUUID().toString())
                .configId(config.getId())
                .configKey(config.getConfigKey())
                .oldValue(config.getConfigValue())
                .newValue(request.getConfigValue())
                .oldVersion(config.getVersion())
                .newVersion(config.getVersion() + 1)
                .changeReason(request.getChangeReason())
                .changedBy(userId)
                .build();
        historyRepository.save(history);
        
        // 更新配置
        config.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        config.setVersion(config.getVersion() + 1);
        config.setUpdatedBy(userId);
        
        return configRepository.save(config);
    }
    
    public SystemConfig getConfig(String configKey) {
        return configRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new RuntimeException("Config not found: " + configKey));
    }
    
    public String getConfigValue(String configKey) {
        return configRepository.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }
    
    public String getConfigValue(String configKey, String defaultValue) {
        return configRepository.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }
    
    public List<SystemConfig> getConfigsByCategory(String category) {
        return configRepository.findByCategory(category);
    }
    
    public List<SystemConfig> getConfigsByEnvironment(String environment) {
        return configRepository.findByEnvironment(environment);
    }
    
    public List<SystemConfig> getAllConfigs() {
        return configRepository.findAll();
    }
    
    @Transactional
    public void deleteConfig(String configKey) {
        SystemConfig config = configRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new RuntimeException("Config not found: " + configKey));
        configRepository.delete(config);
    }
    
    // ==================== 版本管理和回滚 ====================
    
    public Page<ConfigHistory> getConfigHistory(String configKey, Pageable pageable) {
        SystemConfig config = getConfig(configKey);
        return historyRepository.findByConfigIdOrderByChangedAtDesc(config.getId(), pageable);
    }
    
    @Transactional
    public SystemConfig rollbackConfig(String configKey, Integer targetVersion, String userId) {
        SystemConfig config = getConfig(configKey);
        
        // 查找目标版本的历史记录
        ConfigHistory targetHistory = historyRepository.findByConfigIdAndNewVersion(config.getId(), targetVersion)
                .orElseThrow(() -> new RuntimeException("Version not found: " + targetVersion));
        
        // 记录回滚操作
        ConfigHistory rollbackHistory = ConfigHistory.builder()
                .id(UUID.randomUUID().toString())
                .configId(config.getId())
                .configKey(config.getConfigKey())
                .oldValue(config.getConfigValue())
                .newValue(targetHistory.getOldValue())  // 回滚到目标版本之前的值
                .oldVersion(config.getVersion())
                .newVersion(config.getVersion() + 1)
                .changeReason("Rollback to version " + targetVersion)
                .changedBy(userId)
                .build();
        historyRepository.save(rollbackHistory);
        
        // 执行回滚
        config.setConfigValue(targetHistory.getOldValue());
        config.setVersion(config.getVersion() + 1);
        config.setUpdatedBy(userId);
        
        return configRepository.save(config);
    }
    
    // ==================== 影响范围评估 ====================
    
    public ImpactAssessment assessConfigChange(String configKey, String newValue) {
        SystemConfig config = getConfig(configKey);
        
        List<String> affectedComponents = new ArrayList<>();
        String riskLevel = "LOW";
        List<String> recommendations = new ArrayList<>();
        
        // 根据配置类别评估影响
        switch (config.getCategory()) {
            case "SYSTEM":
                affectedComponents.add("系统核心服务");
                riskLevel = "HIGH";
                recommendations.add("建议在低峰期进行变更");
                recommendations.add("建议先在测试环境验证");
                break;
            case "PERFORMANCE":
                affectedComponents.add("性能相关服务");
                riskLevel = "MEDIUM";
                recommendations.add("建议监控变更后的性能指标");
                break;
            case "BUSINESS":
                affectedComponents.add("业务流程");
                riskLevel = "MEDIUM";
                recommendations.add("建议通知相关业务人员");
                break;
        }
        
        // 检查是否是敏感配置
        if (config.getEncrypted() != null && config.getEncrypted()) {
            riskLevel = "HIGH";
            recommendations.add("此配置包含敏感信息，请谨慎操作");
        }
        
        return ImpactAssessment.builder()
                .configKey(configKey)
                .currentValue(config.getConfigValue())
                .newValue(newValue)
                .affectedComponents(affectedComponents)
                .riskLevel(riskLevel)
                .recommendations(recommendations)
                .build();
    }
    
    // ==================== 多环境配置同步 ====================
    
    public ConfigDiffResult compareEnvironments(String sourceEnv, String targetEnv) {
        List<SystemConfig> sourceConfigs = configRepository.findByEnvironment(sourceEnv);
        List<SystemConfig> targetConfigs = configRepository.findByEnvironment(targetEnv);
        
        Map<String, SystemConfig> sourceMap = sourceConfigs.stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, c -> c));
        Map<String, SystemConfig> targetMap = targetConfigs.stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, c -> c));
        
        List<ConfigDiffItem> diffs = new ArrayList<>();
        
        // 检查源环境中的配置
        for (SystemConfig source : sourceConfigs) {
            SystemConfig target = targetMap.get(source.getConfigKey());
            if (target == null) {
                diffs.add(ConfigDiffItem.builder()
                        .configKey(source.getConfigKey())
                        .diffType("MISSING_IN_TARGET")
                        .sourceValue(source.getConfigValue())
                        .targetValue(null)
                        .build());
            } else if (!Objects.equals(source.getConfigValue(), target.getConfigValue())) {
                diffs.add(ConfigDiffItem.builder()
                        .configKey(source.getConfigKey())
                        .diffType("VALUE_DIFFERENT")
                        .sourceValue(source.getConfigValue())
                        .targetValue(target.getConfigValue())
                        .build());
            }
        }
        
        // 检查目标环境中独有的配置
        for (SystemConfig target : targetConfigs) {
            if (!sourceMap.containsKey(target.getConfigKey())) {
                diffs.add(ConfigDiffItem.builder()
                        .configKey(target.getConfigKey())
                        .diffType("MISSING_IN_SOURCE")
                        .sourceValue(null)
                        .targetValue(target.getConfigValue())
                        .build());
            }
        }
        
        return ConfigDiffResult.builder()
                .sourceEnvironment(sourceEnv)
                .targetEnvironment(targetEnv)
                .differences(diffs)
                .totalDifferences(diffs.size())
                .build();
    }
    
    @Transactional
    public ConfigSyncResult syncConfigs(String sourceEnv, String targetEnv, List<String> configKeys, String userId) {
        List<String> synced = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        
        for (String configKey : configKeys) {
            try {
                SystemConfig source = configRepository.findByConfigKey(configKey)
                        .filter(c -> sourceEnv.equals(c.getEnvironment()))
                        .orElseThrow(() -> new RuntimeException("Source config not found"));
                
                Optional<SystemConfig> targetOpt = configRepository.findByConfigKey(configKey + "_" + targetEnv);
                
                if (targetOpt.isPresent()) {
                    // 更新目标配置
                    SystemConfig target = targetOpt.get();
                    target.setConfigValue(source.getConfigValue());
                    target.setVersion(target.getVersion() + 1);
                    target.setUpdatedBy(userId);
                    configRepository.save(target);
                } else {
                    // 创建目标配置
                    SystemConfig newConfig = SystemConfig.builder()
                            .id(UUID.randomUUID().toString())
                            .category(source.getCategory())
                            .configKey(configKey + "_" + targetEnv)
                            .configName(source.getConfigName())
                            .configValue(source.getConfigValue())
                            .defaultValue(source.getDefaultValue())
                            .valueType(source.getValueType())
                            .description(source.getDescription())
                            .encrypted(source.getEncrypted())
                            .editable(source.getEditable())
                            .version(1)
                            .environment(targetEnv)
                            .updatedBy(userId)
                            .build();
                    configRepository.save(newConfig);
                }
                synced.add(configKey);
            } catch (Exception e) {
                log.error("Failed to sync config: {}", configKey, e);
                failed.add(configKey);
            }
        }
        
        return ConfigSyncResult.builder()
                .sourceEnvironment(sourceEnv)
                .targetEnvironment(targetEnv)
                .syncedConfigs(synced)
                .failedConfigs(failed)
                .build();
    }
    
    // ==================== 内部类 ====================
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfigCreateRequest {
        private String category;
        private String configKey;
        private String configName;
        private String configValue;
        private String defaultValue;
        private String valueType;
        private String description;
        private Boolean encrypted;
        private Boolean editable;
        private String environment;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfigUpdateRequest {
        private String configValue;
        private String description;
        private String changeReason;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ImpactAssessment {
        private String configKey;
        private String currentValue;
        private String newValue;
        private List<String> affectedComponents;
        private String riskLevel;
        private List<String> recommendations;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfigDiffResult {
        private String sourceEnvironment;
        private String targetEnvironment;
        private List<ConfigDiffItem> differences;
        private int totalDifferences;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfigDiffItem {
        private String configKey;
        private String diffType;
        private String sourceValue;
        private String targetValue;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfigSyncResult {
        private String sourceEnvironment;
        private String targetEnvironment;
        private List<String> syncedConfigs;
        private List<String> failedConfigs;
    }
}
