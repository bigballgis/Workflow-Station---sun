package com.admin.properties;

import com.admin.component.ConfigManagerComponent;
import com.admin.entity.ConfigHistory;
import com.admin.entity.SystemConfig;
import com.admin.repository.ConfigHistoryRepository;
import com.admin.repository.SystemConfigRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 配置回滚正确性属性测试
 * 属性 17: 配置回滚正确性
 * 验证需求: 需求 12.5
 */
class ConfigRollbackProperties {
    
    private ConfigManagerComponent component;
    private SystemConfigRepository configRepository;
    private ConfigHistoryRepository historyRepository;
    
    @BeforeTry
    void setUp() {
        configRepository = Mockito.mock(SystemConfigRepository.class);
        historyRepository = Mockito.mock(ConfigHistoryRepository.class);
        component = new ConfigManagerComponent(configRepository, historyRepository);
    }
    
    @Provide
    Arbitrary<String> configValues() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100);
    }
    
    @Provide
    Arbitrary<Integer> versions() {
        return Arbitraries.integers().between(1, 100);
    }
    
    /**
     * 属性 17.1: 回滚后配置值应恢复到目标版本之前的值
     */
    @Property(tries = 20)
    void rollbackRestoresOldValue(
            @ForAll("configValues") String currentValue,
            @ForAll("configValues") String oldValue,
            @ForAll("versions") Integer currentVersion,
            @ForAll("versions") Integer targetVersion) {
        Assume.that(targetVersion < currentVersion);
        Assume.that(!currentValue.equals(oldValue));
        
        String configKey = "test.config";
        SystemConfig config = SystemConfig.builder()
                .id("config-1")
                .configKey(configKey)
                .configValue(currentValue)
                .version(currentVersion)
                .editable(true)
                .build();
        
        ConfigHistory history = ConfigHistory.builder()
                .id("history-1")
                .configId("config-1")
                .configKey(configKey)
                .oldValue(oldValue)
                .newValue(currentValue)
                .oldVersion(targetVersion - 1)
                .newVersion(targetVersion)
                .build();
        
        when(configRepository.findByConfigKey(configKey)).thenReturn(Optional.of(config));
        when(historyRepository.findByConfigIdAndNewVersion("config-1", targetVersion))
                .thenReturn(Optional.of(history));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ConfigHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        
        SystemConfig result = component.rollbackConfig(configKey, targetVersion, "user-1");
        
        assertThat(result.getConfigValue()).isEqualTo(oldValue);
    }
    
    /**
     * 属性 17.2: 回滚后版本号应递增
     */
    @Property(tries = 20)
    void rollbackIncrementsVersion(
            @ForAll("versions") Integer currentVersion,
            @ForAll("versions") Integer targetVersion) {
        Assume.that(targetVersion < currentVersion);
        
        String configKey = "test.config";
        SystemConfig config = SystemConfig.builder()
                .id("config-1")
                .configKey(configKey)
                .configValue("current")
                .version(currentVersion)
                .editable(true)
                .build();
        
        ConfigHistory history = ConfigHistory.builder()
                .id("history-1")
                .configId("config-1")
                .configKey(configKey)
                .oldValue("old")
                .newValue("current")
                .oldVersion(targetVersion - 1)
                .newVersion(targetVersion)
                .build();
        
        when(configRepository.findByConfigKey(configKey)).thenReturn(Optional.of(config));
        when(historyRepository.findByConfigIdAndNewVersion("config-1", targetVersion))
                .thenReturn(Optional.of(history));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ConfigHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        
        SystemConfig result = component.rollbackConfig(configKey, targetVersion, "user-1");
        
        assertThat(result.getVersion()).isEqualTo(currentVersion + 1);
    }
    
    /**
     * 属性 17.3: 回滚操作应记录历史
     */
    @Property(tries = 20)
    void rollbackRecordsHistory(
            @ForAll("configValues") String currentValue,
            @ForAll("configValues") String oldValue,
            @ForAll("versions") Integer targetVersion) {
        Assume.that(!currentValue.equals(oldValue));
        
        String configKey = "test.config";
        SystemConfig config = SystemConfig.builder()
                .id("config-1")
                .configKey(configKey)
                .configValue(currentValue)
                .version(10)
                .editable(true)
                .build();
        
        ConfigHistory history = ConfigHistory.builder()
                .id("history-1")
                .configId("config-1")
                .configKey(configKey)
                .oldValue(oldValue)
                .newValue(currentValue)
                .oldVersion(targetVersion - 1)
                .newVersion(targetVersion)
                .build();
        
        when(configRepository.findByConfigKey(configKey)).thenReturn(Optional.of(config));
        when(historyRepository.findByConfigIdAndNewVersion("config-1", targetVersion))
                .thenReturn(Optional.of(history));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ConfigHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        
        component.rollbackConfig(configKey, targetVersion, "user-1");
        
        ArgumentCaptor<ConfigHistory> captor = ArgumentCaptor.forClass(ConfigHistory.class);
        verify(historyRepository).save(captor.capture());
        
        ConfigHistory savedHistory = captor.getValue();
        assertThat(savedHistory.getOldValue()).isEqualTo(currentValue);
        assertThat(savedHistory.getNewValue()).isEqualTo(oldValue);
        assertThat(savedHistory.getChangeReason()).contains("Rollback");
    }
    
    /**
     * 属性 17.4: 更新配置应递增版本号
     */
    @Property(tries = 20)
    void updateIncrementsVersion(
            @ForAll("configValues") String newValue,
            @ForAll("versions") Integer currentVersion) {
        String configKey = "test.config";
        SystemConfig config = SystemConfig.builder()
                .id("config-1")
                .configKey(configKey)
                .configValue("old")
                .version(currentVersion)
                .editable(true)
                .build();
        
        when(configRepository.findByConfigKey(configKey)).thenReturn(Optional.of(config));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ConfigHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        
        ConfigManagerComponent.ConfigUpdateRequest request = ConfigManagerComponent.ConfigUpdateRequest.builder()
                .configValue(newValue)
                .changeReason("Test update")
                .build();
        
        SystemConfig result = component.updateConfig(configKey, request, "user-1");
        
        assertThat(result.getVersion()).isEqualTo(currentVersion + 1);
    }
    
    /**
     * 属性 17.5: 更新配置应记录变更历史
     */
    @Property(tries = 20)
    void updateRecordsHistory(
            @ForAll("configValues") String oldValue,
            @ForAll("configValues") String newValue) {
        Assume.that(!oldValue.equals(newValue));
        
        String configKey = "test.config";
        SystemConfig config = SystemConfig.builder()
                .id("config-1")
                .configKey(configKey)
                .configValue(oldValue)
                .version(1)
                .editable(true)
                .build();
        
        when(configRepository.findByConfigKey(configKey)).thenReturn(Optional.of(config));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ConfigHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        
        ConfigManagerComponent.ConfigUpdateRequest request = ConfigManagerComponent.ConfigUpdateRequest.builder()
                .configValue(newValue)
                .changeReason("Test update")
                .build();
        
        component.updateConfig(configKey, request, "user-1");
        
        ArgumentCaptor<ConfigHistory> captor = ArgumentCaptor.forClass(ConfigHistory.class);
        verify(historyRepository).save(captor.capture());
        
        ConfigHistory savedHistory = captor.getValue();
        assertThat(savedHistory.getOldValue()).isEqualTo(oldValue);
        assertThat(savedHistory.getNewValue()).isEqualTo(newValue);
    }
}
