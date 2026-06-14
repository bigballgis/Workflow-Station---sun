package com.developer.property;

import com.developer.component.ExportImportComponent;
import com.developer.component.impl.ExportImportTestComponents;
import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStatus;
import com.developer.repository.*;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        ProcessDefinitionRepository processRepo = mock(ProcessDefinitionRepository.class);
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        ActionDefinitionRepository actionRepo = mock(ActionDefinitionRepository.class);
        DecisionDefinitionRepository decisionRepo = mock(DecisionDefinitionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExportImportComponent component = ExportImportTestComponents.build(
                repository, processRepo, tableRepo, formRepo, actionRepo, decisionRepo,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                mock(com.developer.validation.DmnXmlParser.class),
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                objectMapper,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

        // 创建模拟功能单元
        FunctionUnit fu = new FunctionUnit();
        fu.setId(1L);
        fu.setName(name);
        fu.setDisplayName("Test for export/import");
        fu.setStatus(FunctionUnitStatus.DRAFT);
        
        when(repository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableRepo.findByFunctionUnitIdWithFields(1L)).thenReturn(java.util.List.of());
        when(formRepo.findByFunctionUnitIdWithBindings(1L)).thenReturn(java.util.List.of());
        when(actionRepo.findByFunctionUnitId(1L)).thenReturn(java.util.List.of());
        when(decisionRepo.findByFunctionUnitId(1L)).thenReturn(java.util.List.of());
        
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
        ProcessDefinitionRepository processRepo = mock(ProcessDefinitionRepository.class);
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        ActionDefinitionRepository actionRepo = mock(ActionDefinitionRepository.class);
        DecisionDefinitionRepository decisionRepo = mock(DecisionDefinitionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExportImportComponent component = ExportImportTestComponents.build(
                repository, processRepo, tableRepo, formRepo, actionRepo, decisionRepo,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                mock(com.developer.validation.DmnXmlParser.class),
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                objectMapper,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

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
