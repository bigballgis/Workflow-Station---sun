package com.developer.property;

import com.developer.component.FunctionUnitComponent;
import com.developer.component.impl.FunctionUnitComponentImpl;
import com.developer.dto.FunctionUnitRequest;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.ActionType;
import com.developer.enums.FormType;
import com.developer.enums.FunctionUnitStatus;
import com.developer.enums.TableType;
import com.developer.repository.*;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.developer.service.UserDisplayNameService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 功能单元属性测试
 * Property 1-3: 名称唯一性、发布状态一致性、克隆完整性
 */
public class FunctionUnitPropertyTest {
    
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    private FunctionUnitComponent createComponent(FunctionUnitRepository repository) {
        ProcessDefinitionRepository processRepo = mock(ProcessDefinitionRepository.class);
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        ActionDefinitionRepository actionRepo = mock(ActionDefinitionRepository.class);
        VersionRepository versionRepo = mock(VersionRepository.class);
        IconRepository iconRepo = mock(IconRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserDisplayNameService userDisplayNameService = mock(UserDisplayNameService.class);
        FunctionUnitWorkspaceAccessService workspaceAccessService = mock(FunctionUnitWorkspaceAccessService.class);
        FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository = mock(FunctionUnitDevGroupAssignmentRepository.class);
        when(devGroupAssignmentRepository.findByFunctionUnitId(anyLong())).thenReturn(java.util.Collections.emptyList());
        return new FunctionUnitComponentImpl(
                repository, processRepo, tableRepo, formRepo, actionRepo,
                mock(com.developer.repository.DecisionDefinitionRepository.class),
                mock(com.developer.repository.FormTableBindingRepository.class),
                mock(com.developer.repository.FormStageBindingRepository.class),
                mock(com.developer.repository.TableRelationRepository.class),
                mock(com.developer.repository.SubTableViewConfigRepository.class),
                versionRepo, iconRepo, objectMapper, userDisplayNameService,
                workspaceAccessService, devGroupAssignmentRepository, mock(com.developer.component.VersionComponent.class), mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class), mock(com.developer.service.MainTableViewService.class), mock(com.developer.repository.ForeignKeyRepository.class), mock(com.developer.component.TableDesignComponent.class));
    }
    
    /**
     * Property 1: 功能单元名称唯一性
     * 对于任意有效名称，创建后再次创建相同名称应抛出异常
     */
    @Property(tries = 20)
    void nameUniquenessProperty(@ForAll("validNames") String name) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        FunctionUnitComponent component = createComponent(repository);
        
        // 第一次检查名称不存在
        when(repository.existsByName(name)).thenReturn(false);
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            fu.setId(idGenerator.getAndIncrement());
            return fu;
        });
        
        FunctionUnitRequest request = new FunctionUnitRequest();
        request.setName(name);
        request.setDescription("Test description");
        
        FunctionUnit created = component.create(request);
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo(name);
        
        // 第二次检查名称已存在
        when(repository.existsByName(name)).thenReturn(true);
        
        assertThatThrownBy(() -> component.create(request))
                .isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Property 2: 功能单元发布状态一致性
     * 新创建的功能单元状态应为DRAFT
     */
    @Property(tries = 20)
    void initialStatusProperty(@ForAll("validNames") String name) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        FunctionUnitComponent component = createComponent(repository);
        
        when(repository.existsByName(name)).thenReturn(false);
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            fu.setId(idGenerator.getAndIncrement());
            return fu;
        });
        
        FunctionUnitRequest request = new FunctionUnitRequest();
        request.setName(name);
        
        FunctionUnit created = component.create(request);
        
        assertThat(created.getStatus()).isEqualTo(FunctionUnitStatus.DRAFT);
    }

    /**
     * Property 3: 功能单元克隆完整性
     * 克隆后的功能单元应具有新名称但保留原有描述
     */
    @Property(tries = 20)
    void cloneIntegrityProperty(
            @ForAll("validNames") String originalName,
            @ForAll("validNames") String cloneName) {
        
        Assume.that(!originalName.equals(cloneName));
        
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        FunctionUnitComponent component = createComponent(repository);
        
        // 创建原始功能单元
        FunctionUnit original = new FunctionUnit();
        original.setId(1L);
        original.setName(originalName);
        original.setDisplayName("Original description");
        original.setStatus(FunctionUnitStatus.PUBLISHED);
        
        when(repository.findById(1L)).thenReturn(Optional.of(original));
        when(repository.existsByName(cloneName)).thenReturn(false);
        
        // 使用不同的 ID 生成器确保克隆的 ID 不同
        final long[] nextId = {2L};
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            if (fu.getId() == null) {
                fu.setId(nextId[0]++);
            }
            return fu;
        });
        
        FunctionUnit cloned = component.clone(1L, cloneName);
        
        assertThat(cloned.getName()).isEqualTo(cloneName);
        assertThat(cloned.getDisplayName()).isEqualTo(original.getDisplayName());
        assertThat(cloned.getStatus()).isEqualTo(FunctionUnitStatus.DRAFT);
        assertThat(cloned.getId()).isNotEqualTo(original.getId());
    }

    /**
     * Property 4: 克隆带流程定义的功能单元时，ProcessDefinition.functionUnitVersionId 必须非空
     * 防止回归 dw_process_definitions.function_unit_version_id NOT NULL 约束违反
     */
    @Property(tries = 10)
    void cloneProcessDefinitionVersionIdProperty(
            @ForAll("validNames") String originalName,
            @ForAll("validNames") String cloneName) {

        Assume.that(!originalName.equals(cloneName));

        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        ProcessDefinitionRepository processRepo = mock(ProcessDefinitionRepository.class);
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        ActionDefinitionRepository actionRepo = mock(ActionDefinitionRepository.class);
        VersionRepository versionRepo = mock(VersionRepository.class);
        IconRepository iconRepo = mock(IconRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserDisplayNameService userDisplayNameService = mock(UserDisplayNameService.class);
        FunctionUnitWorkspaceAccessService workspaceAccessService = mock(FunctionUnitWorkspaceAccessService.class);
        FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository = mock(FunctionUnitDevGroupAssignmentRepository.class);
        when(devGroupAssignmentRepository.findByFunctionUnitId(anyLong())).thenReturn(java.util.Collections.emptyList());
        com.developer.repository.TableRelationRepository relationRepo =
                mock(com.developer.repository.TableRelationRepository.class);

        FunctionUnitComponent component = new FunctionUnitComponentImpl(
                repository, processRepo, tableRepo, formRepo, actionRepo,
                mock(com.developer.repository.DecisionDefinitionRepository.class),
                mock(com.developer.repository.FormTableBindingRepository.class),
                mock(com.developer.repository.FormStageBindingRepository.class),
                relationRepo,
                mock(com.developer.repository.SubTableViewConfigRepository.class),
                versionRepo, iconRepo, objectMapper, userDisplayNameService,
                workspaceAccessService, devGroupAssignmentRepository,
                mock(com.developer.component.VersionComponent.class), mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class), mock(com.developer.service.MainTableViewService.class), mock(com.developer.repository.ForeignKeyRepository.class), mock(com.developer.component.TableDesignComponent.class));

        // 原始功能单元 + 流程定义
        FunctionUnit original = new FunctionUnit();
        original.setId(1L);
        original.setName(originalName);
        original.setDisplayName("Original description");
        original.setStatus(FunctionUnitStatus.PUBLISHED);
        ProcessDefinition originalProcess = ProcessDefinition.builder()
                .functionUnit(original)
                .functionUnitVersionId(1L)
                .bpmnXml("<bpmn:definitions/>")
                .build();
        original.setProcessDefinition(originalProcess);

        when(repository.findById(1L)).thenReturn(Optional.of(original));
        when(repository.existsByName(cloneName)).thenReturn(false);
        when(tableRepo.findByFunctionUnitIdWithFields(1L)).thenReturn(java.util.Collections.emptyList());
        when(formRepo.findByFunctionUnitIdWithBindings(1L)).thenReturn(java.util.Collections.emptyList());
        when(relationRepo.findByFunctionUnitId(1L)).thenReturn(java.util.Collections.emptyList());

        final long[] nextId = {2L};
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            if (fu.getId() == null) {
                fu.setId(nextId[0]++);
            }
            return fu;
        });

        FunctionUnit cloned = component.clone(1L, cloneName);

        ArgumentCaptor<ProcessDefinition> captor = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(processRepo).save(captor.capture());
        ProcessDefinition saved = captor.getValue();

        assertThat(saved.getFunctionUnitVersionId())
                .as("functionUnitVersionId 不能为空，否则违反数据库 NOT NULL 约束")
                .isNotNull();
        assertThat(saved.getFunctionUnitVersionId()).isEqualTo(cloned.getId());
        assertThat(saved.getFunctionUnit()).isEqualTo(cloned);
        assertThat(saved.getBpmnXml()).isEqualTo("<bpmn:definitions/>");
    }

    /**
     * Property 5: 克隆时 BPMN 中的 subTableId / formId / actionIds 必须重写为新功能单元的 ID
     * 防止回归 deploy 阶段 SUBTABLE_WRONG_FUNCTION_UNIT / FORM_WRONG_FUNCTION_UNIT 校验失败
     */
    @Property(tries = 5)
    void cloneBpmnIdRewritingProperty(@ForAll("validNames") String cloneName) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        ProcessDefinitionRepository processRepo = mock(ProcessDefinitionRepository.class);
        TableDefinitionRepository tableRepo = mock(TableDefinitionRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        ActionDefinitionRepository actionRepo = mock(ActionDefinitionRepository.class);
        VersionRepository versionRepo = mock(VersionRepository.class);
        IconRepository iconRepo = mock(IconRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserDisplayNameService userDisplayNameService = mock(UserDisplayNameService.class);
        FunctionUnitWorkspaceAccessService workspaceAccessService = mock(FunctionUnitWorkspaceAccessService.class);
        FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository = mock(FunctionUnitDevGroupAssignmentRepository.class);
        when(devGroupAssignmentRepository.findByFunctionUnitId(anyLong())).thenReturn(java.util.Collections.emptyList());
        com.developer.repository.FormTableBindingRepository bindingRepo =
                mock(com.developer.repository.FormTableBindingRepository.class);
        com.developer.repository.FormStageBindingRepository stageRepo =
                mock(com.developer.repository.FormStageBindingRepository.class);
        com.developer.repository.TableRelationRepository relationRepo =
                mock(com.developer.repository.TableRelationRepository.class);

        // Clone renames cloned tables to a free name; the cloner asks this component whether a candidate
        // is available, so it must report true (an unstubbed mock returns false → "name exhausted").
        com.developer.component.TableDesignComponent tableDesignComponent =
                mock(com.developer.component.TableDesignComponent.class);
        org.mockito.Mockito.when(tableDesignComponent.isTableNameAvailable(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(true);

        FunctionUnitComponent component = new FunctionUnitComponentImpl(
                repository, processRepo, tableRepo, formRepo, actionRepo,
                mock(com.developer.repository.DecisionDefinitionRepository.class),
                bindingRepo,
                stageRepo,
                relationRepo,
                mock(com.developer.repository.SubTableViewConfigRepository.class),
                versionRepo, iconRepo, objectMapper, userDisplayNameService,
                workspaceAccessService, devGroupAssignmentRepository,
                mock(com.developer.component.VersionComponent.class), mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class), mock(com.developer.service.MainTableViewService.class), mock(com.developer.repository.ForeignKeyRepository.class), tableDesignComponent);

        // 源功能单元：含 1 sub 表（id=13）、1 表单（id=11）、1 动作（id=12），
        // BPMN 中 subTableId=13 / formId=11 / actionIds=[12]
        FunctionUnit original = new FunctionUnit();
        original.setId(1L);
        original.setName("Original");
        original.setStatus(FunctionUnitStatus.PUBLISHED);
        original.setTableDefinitions(new ArrayList<>());
        original.setFormDefinitions(new ArrayList<>());
        original.setActionDefinitions(new ArrayList<>());
        original.setDecisionDefinitions(new ArrayList<>());

        TableDefinition sourceSubTable = TableDefinition.builder()
                .functionUnit(original)
                .tableName("participants")
                .tableType(TableType.SUB)
                .build();
        sourceSubTable.setId(13L);
        sourceSubTable.setFieldDefinitions(new ArrayList<>());
        original.getTableDefinitions().add(sourceSubTable);

        FormDefinition sourceForm = FormDefinition.builder()
                .functionUnit(original)
                .formName("MainForm")
                .formType(FormType.PROCESS)
                .configJson(new HashMap<>())
                .build();
        sourceForm.setId(11L);
        sourceForm.setTableBindings(new ArrayList<>());
        original.getFormDefinitions().add(sourceForm);

        ActionDefinition sourceAction = ActionDefinition.builder()
                .functionUnit(original)
                .actionName("Submit")
                .actionType(ActionType.PROCESS_SUBMIT)
                .configJson(new HashMap<>())
                .build();
        sourceAction.setId(12L);
        original.getActionDefinitions().add(sourceAction);

        String sourceBpmn = """
                <?xml version="1.0"?>
                <bpmn:definitions>
                  <bpmn:userTask id="MI_UserTask_13">
                    <bpmn:extensionElements>
                      <custom:property name="subTableId" value="13" />
                      <custom:property name="formId" value="11" />
                      <custom:property name="actionIds" value="[12]" />
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:definitions>
                """;
        ProcessDefinition originalProcess = ProcessDefinition.builder()
                .functionUnit(original)
                .functionUnitVersionId(1L)
                .bpmnXml(sourceBpmn)
                .build();
        original.setProcessDefinition(originalProcess);

        when(repository.findById(1L)).thenReturn(Optional.of(original));
        when(repository.existsByName(cloneName)).thenReturn(false);
        when(tableRepo.findByFunctionUnitIdWithFields(1L)).thenReturn(original.getTableDefinitions());
        when(formRepo.findByFunctionUnitIdWithBindings(1L)).thenReturn(original.getFormDefinitions());
        when(relationRepo.findByFunctionUnitId(1L)).thenReturn(new ArrayList<>());
        when(bindingRepo.findByFormIdWithTable(11L)).thenReturn(new ArrayList<>());
        when(stageRepo.findByFormId(11L)).thenReturn(new ArrayList<>());

        final long[] nextFuId = {2L};
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            if (fu.getId() == null) {
                fu.setId(nextFuId[0]++);
            }
            return fu;
        });

        final long[] nextTableId = {213L};
        when(tableRepo.save(any(TableDefinition.class))).thenAnswer(invocation -> {
            TableDefinition td = invocation.getArgument(0);
            if (td.getId() == null) {
                td.setId(nextTableId[0]++);
            }
            return td;
        });

        final long[] nextFormId = {211L};
        when(formRepo.save(any(FormDefinition.class))).thenAnswer(invocation -> {
            FormDefinition fd = invocation.getArgument(0);
            if (fd.getId() == null) {
                fd.setId(nextFormId[0]++);
            }
            return fd;
        });

        final long[] nextActionId = {212L};
        when(actionRepo.save(any(ActionDefinition.class))).thenAnswer(invocation -> {
            ActionDefinition ad = invocation.getArgument(0);
            if (ad.getId() == null) {
                ad.setId(nextActionId[0]++);
            }
            return ad;
        });

        component.clone(1L, cloneName);

        ArgumentCaptor<ProcessDefinition> captor = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(processRepo).save(captor.capture());
        String savedBpmn = captor.getValue().getBpmnXml();

        assertThat(savedBpmn)
                .as("克隆后 BPMN 必须使用新功能单元的 subTableId 而非源 ID")
                .doesNotContain("name=\"subTableId\" value=\"13\"")
                .contains("name=\"subTableId\" value=\"213\"");
        assertThat(savedBpmn)
                .as("克隆后 BPMN 必须使用新功能单元的 formId 而非源 ID")
                .doesNotContain("name=\"formId\" value=\"11\"")
                .contains("name=\"formId\" value=\"211\"");
        assertThat(savedBpmn)
                .as("克隆后 BPMN 必须使用新功能单元的 actionIds 而非源 ID")
                .doesNotContain("name=\"actionIds\" value=\"[12]\"")
                .contains("name=\"actionIds\" value=\"[212]\"");
        assertThat(savedBpmn)
                .as("BPMN 节点 id 属性不应被改写（id 中含的 13 仅是命名巧合）")
                .contains("id=\"MI_UserTask_13\"");
    }

    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(50)
                .map(s -> "FU_" + s);
    }
}

