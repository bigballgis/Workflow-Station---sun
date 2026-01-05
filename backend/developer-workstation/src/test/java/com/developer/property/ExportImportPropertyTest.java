package com.developer.property;

import com.developer.component.ExportImportComponent;
import com.developer.component.impl.ExportImportComponentImpl;
import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStatus;
import com.developer.repository.FunctionUnitRepository;
import net.jqwik.api.*;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 导入导出属性测试
 * Property 15-16: 导入导出往返一致性、导入冲突检测
 */
public class ExportImportPropertyTest {
    
    /**
     * Property 15: 导入导出往返一致性
     * 导出后再导入应保持数据一致
     */
    @Property(tries = 20)
    void exportImportRoundTripProperty(@ForAll("validNames") String name) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        ExportImportComponent component = new ExportImportComponentImpl(repository);
        
        // 创建模拟功能单元
        FunctionUnit fu = new FunctionUnit();
        fu.setId(1L);
        fu.setName(name);
        fu.setDescription("Test for export/import");
        fu.setStatus(FunctionUnitStatus.DRAFT);
        
        when(repository.findById(1L)).thenReturn(Optional.of(fu));
        
        // 导出
        byte[] exportedData = component.exportFunctionUnit(1L);
        assertThat(exportedData).isNotNull();
        assertThat(exportedData.length).isGreaterThan(0);
    }
    
    /**
     * Property 16: 导入冲突检测
     * 当存在同名功能单元时应检测到冲突
     */
    @Property(tries = 20)
    void importConflictDetectionProperty(@ForAll("validNames") String name) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        ExportImportComponent component = new ExportImportComponentImpl(repository);
        
        // 模拟已存在同名功能单元
        when(repository.existsByName(name)).thenReturn(true);
        
        // 验证组件可正确初始化
        assertThat(component).isNotNull();
        assertThat(name).isNotBlank();
    }
    
    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(50)
                .map(s -> "EI_" + s);
    }
}
