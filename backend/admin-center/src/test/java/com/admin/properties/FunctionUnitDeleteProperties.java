package com.admin.properties;

import com.admin.component.FunctionUnitManagerComponent;
import com.admin.dto.response.DeletePreviewResponse;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.entity.FunctionUnitDependency;
import com.admin.enums.ContentType;
import com.admin.enums.DependencyType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 功能单元删除属性测试
 * 
 * Property 1: 删除预览数量正确性
 * Property 4: 级联删除完整性
 * 
 * Feature: function-unit-management
 * Validates: Requirements 1.4, 1.9
 */
class FunctionUnitDeleteProperties {

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

    // ==================== Property 1: 删除预览数量正确性 ====================
    // *对于任意* 功能单元，删除预览返回的关联数据数量应与数据库中实际存在的关联记录数量一致
    // Validates: Requirements 1.4
    
    @Property(tries = 100)
    void deletePreviewShouldReturnCorrectCounts(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll @IntRange(min = 0, max = 10) int formCount,
            @ForAll @IntRange(min = 0, max = 10) int processCount,
            @ForAll @IntRange(min = 0, max = 10) int dataTableCount,
            @ForAll @IntRange(min = 0, max = 5) int dependencyCount) {
        
        // Given: 功能单元和关联数据
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", "test-unit");
        List<FunctionUnitContent> contents = createContents(functionUnitId, formCount, processCount, dataTableCount);
        List<FunctionUnitDependency> dependencies = createDependencies(functionUnitId, dependencyCount);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(contentRepository.findByFunctionUnitId(functionUnitId)).thenReturn(contents);
        when(dependencyRepository.findByFunctionUnitId(functionUnitId)).thenReturn(dependencies);
        
        // When: 获取删除预览
        DeletePreviewResponse preview = component.getDeletePreview(functionUnitId);
        
        // Then: 预览数量应与实际数量一致
        assertThat(preview.getFormCount()).isEqualTo(formCount);
        assertThat(preview.getProcessCount()).isEqualTo(processCount);
        assertThat(preview.getDataTableCount()).isEqualTo(dataTableCount);
        assertThat(preview.getDependencyCount()).isEqualTo(dependencyCount);
        assertThat(preview.getFunctionUnitId()).isEqualTo(functionUnitId);
        assertThat(preview.getFunctionUnitName()).isEqualTo("Test Unit");
    }
    
    @Property(tries = 100)
    void deletePreviewTotalCountShouldBeSum(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll @IntRange(min = 0, max = 5) int formCount,
            @ForAll @IntRange(min = 0, max = 5) int processCount,
            @ForAll @IntRange(min = 0, max = 5) int dataTableCount,
            @ForAll @IntRange(min = 0, max = 5) int dependencyCount) {
        
        // Given: 功能单元和关联数据
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", "test-unit");
        List<FunctionUnitContent> contents = createContents(functionUnitId, formCount, processCount, dataTableCount);
        List<FunctionUnitDependency> dependencies = createDependencies(functionUnitId, dependencyCount);
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        when(contentRepository.findByFunctionUnitId(functionUnitId)).thenReturn(contents);
        when(dependencyRepository.findByFunctionUnitId(functionUnitId)).thenReturn(dependencies);
        
        // When: 获取删除预览
        DeletePreviewResponse preview = component.getDeletePreview(functionUnitId);
        
        // Then: 总数应为各类数量之和
        int expectedTotal = formCount + processCount + dataTableCount + dependencyCount;
        assertThat(preview.getTotalRelatedCount()).isEqualTo(expectedTotal);
    }

    // ==================== Property 4: 级联删除完整性 ====================
    // *对于任意* 功能单元，删除操作完成后，该功能单元及其所有关联内容都应从数据库中移除
    // Validates: Requirements 1.9
    
    @Property(tries = 100)
    void cascadeDeleteShouldRemoveAllRelatedData(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll @IntRange(min = 1, max = 5) int contentCount,
            @ForAll @IntRange(min = 0, max = 3) int dependencyCount) {
        
        // Given: 功能单元和关联数据
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", "test-unit");
        
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        
        // When: 执行级联删除
        component.deleteFunctionUnitCascade(functionUnitId);
        
        // Then: 应该删除所有关联数据
        verify(contentRepository).deleteByFunctionUnitId(functionUnitId);
        verify(dependencyRepository).deleteByFunctionUnitId(functionUnitId);
        verify(functionUnitRepository).delete(unit);
    }
    
    @Property(tries = 100)
    void cascadeDeleteShouldDeleteInCorrectOrder(
            @ForAll("functionUnitIds") String functionUnitId) {
        
        // Given: 功能单元
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit", "test-unit");
        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(unit));
        
        // When: 执行级联删除
        component.deleteFunctionUnitCascade(functionUnitId);
        
        // Then: 应该先删除关联数据，再删除功能单元
        var inOrder = inOrder(contentRepository, dependencyRepository, functionUnitRepository);
        inOrder.verify(contentRepository).deleteByFunctionUnitId(functionUnitId);
        inOrder.verify(dependencyRepository).deleteByFunctionUnitId(functionUnitId);
        inOrder.verify(functionUnitRepository).delete(unit);
    }

    // ==================== 辅助方法 ====================
    
    private FunctionUnit createFunctionUnit(String id, String name, String code) {
        return FunctionUnit.builder()
                .id(id)
                .name(name)
                .code(code)
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .enabled(true)
                .deployments(new HashSet<>())
                .build();
    }
    
    private List<FunctionUnitContent> createContents(String functionUnitId, int formCount, int processCount, int dataTableCount) {
        List<FunctionUnitContent> contents = new ArrayList<>();
        
        for (int i = 0; i < formCount; i++) {
            contents.add(FunctionUnitContent.builder()
                    .id(UUID.randomUUID().toString())
                    .contentType(ContentType.FORM)
                    .contentName("form-" + i)
                    .build());
        }
        
        for (int i = 0; i < processCount; i++) {
            contents.add(FunctionUnitContent.builder()
                    .id(UUID.randomUUID().toString())
                    .contentType(ContentType.PROCESS)
                    .contentName("process-" + i)
                    .build());
        }
        
        for (int i = 0; i < dataTableCount; i++) {
            contents.add(FunctionUnitContent.builder()
                    .id(UUID.randomUUID().toString())
                    .contentType(ContentType.DATA_TABLE)
                    .contentName("table-" + i)
                    .build());
        }
        
        return contents;
    }
    
    private List<FunctionUnitDependency> createDependencies(String functionUnitId, int count) {
        List<FunctionUnitDependency> dependencies = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            dependencies.add(FunctionUnitDependency.builder()
                    .id(UUID.randomUUID().toString())
                    .dependencyCode("dep-" + i)
                    .dependencyVersion("1.0.0")
                    .dependencyType(DependencyType.REQUIRED)
                    .build());
        }
        
        return dependencies;
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
}
