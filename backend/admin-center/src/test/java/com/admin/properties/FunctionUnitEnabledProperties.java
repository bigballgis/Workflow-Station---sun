package com.admin.properties;

import com.admin.component.FunctionUnitManagerComponent;
import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 功能单元启用状态属性测试
 * 
 * Property 5: 启用状态切换正确性
 * *对于任意* 功能单元，切换启用状态后，数据库中的 enabled 字段应立即反映新状态
 * 
 * Feature: function-unit-management
 * Validates: Requirements 2.3, 2.6, 2.7
 */
class FunctionUnitEnabledProperties {

    private FunctionUnitRepository functionUnitRepository;
    private FunctionUnitDependencyRepository dependencyRepository;
    private FunctionUnitContentRepository contentRepository;
    private FunctionUnitAccessRepository accessRepository;
    private FunctionUnitManagerComponent component;

    @BeforeTry
    void setUp() {
        functionUnitRepository = Mockito.mock(FunctionUnitRepository.class);
        dependencyRepository = Mockito.mock(FunctionUnitDependencyRepository.class);
        contentRepository = Mockito.mock(FunctionUnitContentRepository.class);
        accessRepository = Mockito.mock(FunctionUnitAccessRepository.class);
        component = new FunctionUnitManagerComponent(
                functionUnitRepository, dependencyRepository, contentRepository, accessRepository);
    }

    // ==================== Property 5: 启用状态切换正确性 ====================
    // *对于任意* 功能单元，切换启用状态后，数据库中的 enabled 字段应立即反映新状态
    // Validates: Requirements 2.3, 2.6, 2.7
    
    @Property(tries = 100)
    void setEnabledShouldUpdateState(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll boolean initialEnabled,
            @ForAll boolean newEnabled) {
        
        // Given: 功能单元初始状态
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", initialEnabled);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            return saved;
        });
        
        // When: 设置新的启用状态
        FunctionUnit result = component.setEnabled(functionUnitId, newEnabled);
        
        // Then: 返回的功能单元应该反映新状态
        assertThat(result.getEnabled()).isEqualTo(newEnabled);
    }
    
    @Property(tries = 100)
    void setEnabledShouldPersistChange(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll boolean newEnabled) {
        
        // Given: 功能单元
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", !newEnabled);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            return saved;
        });
        
        // When: 设置启用状态
        component.setEnabled(functionUnitId, newEnabled);
        
        // Then: 应该调用 save 方法持久化
        verify(functionUnitRepository).save(argThat(u -> u.getEnabled() == newEnabled));
    }
    
    @Property(tries = 100)
    void enabledToggleShouldBeIdempotent(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll boolean enabled) {
        
        // Given: 功能单元已经是目标状态
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", enabled);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            return saved;
        });
        
        // When: 设置相同的状态
        FunctionUnit result = component.setEnabled(functionUnitId, enabled);
        
        // Then: 状态应该保持不变
        assertThat(result.getEnabled()).isEqualTo(enabled);
    }
    
    @Property(tries = 100)
    void enabledRoundTripShouldRestoreState(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll boolean initialEnabled) {
        
        // Given: 功能单元初始状态
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", initialEnabled);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            // 模拟数据库保存后返回
            unit.setEnabled(saved.getEnabled());
            return saved;
        });
        
        // When: 切换状态两次（禁用再启用，或启用再禁用）
        component.setEnabled(functionUnitId, !initialEnabled);
        FunctionUnit result = component.setEnabled(functionUnitId, initialEnabled);
        
        // Then: 应该恢复到初始状态
        assertThat(result.getEnabled()).isEqualTo(initialEnabled);
    }
    
    @Property(tries = 100)
    void setEnabledShouldNotAffectOtherFields(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll("functionUnitNames") String name,
            @ForAll("functionUnitCodes") String code,
            @ForAll boolean newEnabled) {
        
        // Given: 功能单元
        FunctionUnit unit = createFunctionUnit(functionUnitId, name, !newEnabled);
        unit.setCode(code);
        unit.setVersion("1.0.0");
        unit.setStatus(FunctionUnitStatus.DEPLOYED);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            return saved;
        });
        
        // When: 设置启用状态
        FunctionUnit result = component.setEnabled(functionUnitId, newEnabled);
        
        // Then: 其他字段应该保持不变
        assertThat(result.getId()).isEqualTo(functionUnitId);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getCode()).isEqualTo(code);
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getStatus()).isEqualTo(FunctionUnitStatus.DEPLOYED);
    }

    // ==================== 辅助方法 ====================
    
    private FunctionUnit createFunctionUnit(String id, String name, boolean enabled) {
        return FunctionUnit.builder()
                .id(id)
                .name(name)
                .code("test-code")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .enabled(enabled)
                .deployments(new HashSet<>())
                .build();
    }

    // ==================== 数据提供者 ====================
    
    @Provide
    Arbitrary<String> functionUnitIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(8)
                .ofMaxLength(16)
                .map(s -> "fu-" + s);
    }
    
    @Provide
    Arbitrary<String> functionUnitNames() {
        return Arbitraries.of(
                "审批流程",
                "请假申请",
                "报销流程",
                "采购申请",
                "Test Function Unit"
        );
    }
    
    @Provide
    Arbitrary<String> functionUnitCodes() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "code-" + s);
    }
}
