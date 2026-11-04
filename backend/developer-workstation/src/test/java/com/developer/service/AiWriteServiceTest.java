package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Icon;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.ActionType;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.enums.IconCategory;
import com.developer.enums.TableType;
import com.developer.exception.AiGenerationException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import com.developer.service.impl.AiEmailProposalWriter;
import com.developer.service.impl.AiWriteServiceImpl;
import com.developer.util.AiBpmnActionBindingWriter;
import com.developer.util.XmlEncodingUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiWriteService 单元测试
 * 写入流程示例：新建模式、修改模式、Icon 匹配/创建
 */
@ExtendWith(MockitoExtension.class)
class AiWriteServiceTest {

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private IconRepository iconRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AiWriteServiceImpl writeService;

    private FunctionUnit functionUnit;

    @BeforeEach
    void setUp() {
        functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("TestUnit")
                .code("fu-test")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();
    }

    @Test
    void applyGeneratedData_newMode_shouldWriteAllComponents() {
        // NEW mode: no existing data
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .name("Updated Name")
                .description("Updated Desc")
                .tableDefinitions(List.of(Map.of(
                        "tableName", "orders",
                        "tableType", "MAIN",
                        "tableDisplayName", "Order Table",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "BIGINT",
                                "isPrimaryKey", true,
                                "description", "Order identifier",
                                "sortOrder", 1
                        ))
                )))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // Verify save was called
        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());

        FunctionUnit saved = captor.getValue();
        assertEquals("Updated Name", saved.getName());
        assertEquals("Updated Desc", saved.getDisplayName());
        assertFalse(saved.getTableDefinitions().isEmpty(), "Table definitions should be written");
        assertEquals("orders", saved.getTableDefinitions().get(0).getTableName());
        assertEquals("Order identifier",
                saved.getTableDefinitions().get(0).getFieldDefinitions().get(0).getDisplayName());
        // Flush tables first, then forms/actions so generated IDs can be embedded in BPMN bindings.
        verify(entityManager, times(2)).flush();
    }

    @Test
    void applyGeneratedData_rejectsComputedFieldInsteadOfPersistingIt() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "orders",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "amount",
                                "dataType", "DECIMAL",
                                "isComputed", true,
                                "computedField", Map.of("source", "qty * price")
                        ))
                )))
                .build();

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> writeService.applyGeneratedData(1L, data, null));
        assertEquals("AI_COMPUTED_FIELD_UNSUPPORTED", ex.getErrorCode());
        assertTrue(functionUnit.getTableDefinitions().isEmpty(),
                "computed field must not be written before the rejection");
    }

    @Test
    void applyGeneratedData_modifyMode_shouldDeleteOldAndWriteNew() {
        // MODIFY mode: has existing data
        TableDefinition existingTable = TableDefinition.builder()
                .tableName("old_table")
                .tableType(TableType.MAIN)
                .build();
        functionUnit.getTableDefinitions().add(existingTable);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "new_table",
                        "tableType", "MAIN",
                        "tableDisplayName", "New Table",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "BIGINT",
                                "isPrimaryKey", true,
                                "sortOrder", 1
                        ))
                )))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // In MODIFY mode, entityManager.flush() should be called after clearing and after writeTableDefinitions
        verify(entityManager, atLeast(2)).flush();

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());

        FunctionUnit saved = captor.getValue();
        // Old data should be cleared and new data written
        assertEquals(1, saved.getTableDefinitions().size());
        assertEquals("new_table", saved.getTableDefinitions().get(0).getTableName());
    }

    @Test
    void handleIcon_existingIcon_shouldReuse() {
        Icon existingIcon = Icon.builder()
                .id(10L)
                .name("existing-icon")
                .category(IconCategory.GENERAL)
                .svgContent("<svg/>")
                .build();

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(iconRepository.findFirstByNameOrderByIdAsc("existing-icon")).thenReturn(Optional.of(existingIcon));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "existing-icon",
                        "category", "GENERAL",
                        "svgContent", "<svg/>"
                ))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // Should NOT create a new icon
        verify(iconRepository, never()).save(any(Icon.class));

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        assertEquals(existingIcon, captor.getValue().getIcon());
    }

    @Test
    void handleIcon_newIcon_shouldCreate() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(iconRepository.findFirstByNameOrderByIdAsc("brand-new-icon")).thenReturn(Optional.empty());

        Icon savedIcon = Icon.builder()
                .id(20L)
                .name("brand-new-icon")
                .category(IconCategory.APPROVAL)
                .svgContent("<svg><circle/></svg>")
                .description("A new icon")
                .build();
        when(iconRepository.save(any(Icon.class))).thenReturn(savedIcon);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "brand-new-icon",
                        "category", "APPROVAL",
                        "svgContent", "<svg><circle/></svg>",
                        "description", "A new icon"
                ))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        // Should create a new icon
        verify(iconRepository).save(any(Icon.class));

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        assertEquals(savedIcon, captor.getValue().getIcon());
    }

    /**
     * 多实例节点引用两个库生成 id：BPMN 里的 subTableId，和给 configJson.subForms 当键的子表绑定 id。
     * 生成阶段都拿不到，必须在 flush 之后回填——否则部署校验拦 MISSING_SUBTABLE_ID /
     * MISSING_MI_ASSIGNMENT_COMPONENT，契约不完整时更是一路绿灯但运行时没有指派入口。
     */
    @Test
    void applyGeneratedData_multiInstance_shouldBackfillSubTableIdAndAssignmentComponent() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        // 模拟数据库在 flush 时分配主键：表先拿到 id，表单绑定在写表单之后的那次 flush 拿到。
        AtomicLong sequence = new AtomicLong(100L);
        doAnswer(inv -> {
            functionUnit.getTableDefinitions().stream()
                    .filter(table -> table.getId() == null)
                    .forEach(table -> table.setId(sequence.incrementAndGet()));
            functionUnit.getFormDefinitions().stream()
                    .flatMap(form -> form.getTableBindings().stream())
                    .filter(binding -> binding.getId() == null)
                    .forEach(binding -> binding.setId(sequence.incrementAndGet()));
            return null;
        }).when(entityManager).flush();

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(
                        Map.of(
                                "tableName", "orders",
                                "tableType", "MAIN",
                                "fieldDefinitions", List.of(Map.of(
                                        "fieldName", "id", "dataType", "BIGINT",
                                        "isPrimaryKey", true, "sortOrder", 1))),
                        Map.of(
                                "tableName", "order_items",
                                "tableType", "SUB",
                                "fieldDefinitions", List.of(
                                        Map.of("fieldName", "id", "dataType", "BIGINT",
                                                "isPrimaryKey", true, "sortOrder", 1),
                                        Map.of("fieldName", "item_name", "dataType", "VARCHAR",
                                                "length", "50", "sortOrder", 2),
                                        Map.of("fieldName", "assignee_id", "dataType", "VARCHAR",
                                                "length", "50", "sortOrder", 3)))))
                .formDefinitions(List.of(Map.of(
                        "formName", "order_task_form",
                        "formType", "TASK",
                        "tableBindings", List.of(
                                Map.of("tableName", "orders", "bindingType", "PRIMARY",
                                        "bindingMode", "EDITABLE"),
                                Map.of("tableName", "order_items", "bindingType", "SUB",
                                        "bindingMode", "EDITABLE", "foreignKeyField", "order_id")))))
                .processDefinition(Map.of("bpmnXml", MULTI_INSTANCE_BPMN))
                .build();

        writeService.applyGeneratedData(1L, data, null);

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        FunctionUnit saved = captor.getValue();

        TableDefinition subTable = saved.getTableDefinitions().stream()
                .filter(table -> "order_items".equals(table.getTableName()))
                .findFirst().orElseThrow();
        assertTrue(saved.getProcessDefinition().getBpmnXml()
                        .contains("name=\"subTableId\" value=\"" + subTable.getId() + "\""),
                "BPMN must carry the persisted sub-table id");

        FormDefinition form = saved.getFormDefinitions().get(0);
        FormTableBinding subBinding = form.getTableBindings().stream()
                .filter(binding -> binding.getBindingType() == BindingType.SUB)
                .findFirst().orElseThrow();
        Object subForms = form.getConfigJson().get("subForms");
        assertInstanceOf(Map.class, subForms);
        Object entry = ((Map<?, ?>) subForms).get(String.valueOf(subBinding.getId()));
        assertInstanceOf(Map.class, entry, "sub-form must be keyed by the persisted binding id");
        assertTrue(containsMiAssignment(((Map<?, ?>) entry).get("rule")),
                "the sub-form must carry the miAssignment component the deploy guard demands");
    }

    @Test
    void applyGeneratedData_scopedApply_decodesBase64StoredBpmnBeforeMiBinding() {
        // 流程设计器按 XmlEncodingUtil 约定把 bpmn_xml Base64 落库；scope=TABLES 的 apply
        // 不重写 process，MI 回填必须先解码存量 BPMN 才能解析（dev FU "fghj" 的真实故障）。
        functionUnit.setProcessDefinition(ProcessDefinition.builder()
                .functionUnit(functionUnit)
                .bpmnXml(XmlEncodingUtil.encode(MULTI_INSTANCE_BPMN))
                .build());
        functionUnit.setTableRelations(new ArrayList<>());
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        AtomicLong sequence = new AtomicLong(100L);
        doAnswer(inv -> {
            functionUnit.getTableDefinitions().stream()
                    .filter(table -> table.getId() == null)
                    .forEach(table -> table.setId(sequence.incrementAndGet()));
            return null;
        }).when(entityManager).flush();

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(
                        Map.of(
                                "tableName", "orders",
                                "tableType", "MAIN",
                                "fieldDefinitions", List.of(Map.of(
                                        "fieldName", "id", "dataType", "BIGINT",
                                        "isPrimaryKey", true, "sortOrder", 1))),
                        Map.of(
                                "tableName", "order_items",
                                "tableType", "SUB",
                                "fieldDefinitions", List.of(Map.of(
                                        "fieldName", "id", "dataType", "BIGINT",
                                        "isPrimaryKey", true, "sortOrder", 1)))))
                .build();

        writeService.applyGeneratedData(1L, data, "TABLES");

        ArgumentCaptor<FunctionUnit> captor = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        FunctionUnit saved = captor.getValue();

        String bpmn = saved.getProcessDefinition().getBpmnXml();
        assertTrue(bpmn.trim().startsWith("<"), "stored BPMN must be decoded before parsing");
        TableDefinition subTable = saved.getTableDefinitions().stream()
                .filter(table -> "order_items".equals(table.getTableName()))
                .findFirst().orElseThrow();
        assertTrue(bpmn.contains("name=\"subTableId\" value=\"" + subTable.getId() + "\""),
                "MI binding must still run against the decoded stored BPMN");
    }

    @Test
    void applyGeneratedData_corruptStoredBpmn_failsWithExistingBpmnErrorCode() {
        // 存量 process definition 不是合法 XML（也不是 Base64）时，错误必须指向存量数据，
        // 而不是笼统的 AI_BPMN_MI_BINDING_FAILED 把排查方向引向 AI 回传 body。
        functionUnit.setProcessDefinition(ProcessDefinition.builder()
                .id(77L)
                .functionUnit(functionUnit)
                .bpmnXml("this is not xml, and not base64 either!!!")
                .build());
        functionUnit.setTableRelations(new ArrayList<>());
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        AtomicLong sequence = new AtomicLong(100L);
        doAnswer(inv -> {
            functionUnit.getTableDefinitions().stream()
                    .filter(table -> table.getId() == null)
                    .forEach(table -> table.setId(sequence.incrementAndGet()));
            return null;
        }).when(entityManager).flush();

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "orders",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "BIGINT",
                                "isPrimaryKey", true, "sortOrder", 1)))))
                .build();

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> writeService.applyGeneratedData(1L, data, "TABLES"));
        assertEquals("AI_EXISTING_BPMN_INVALID", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("process definition id 77"),
                "message must locate the stored process definition");
    }

    private static boolean containsMiAssignment(Object value) {
        if (value instanceof Map<?, ?> map) {
            return "miAssignment".equals(map.get("type"))
                    || map.values().stream().anyMatch(AiWriteServiceTest::containsMiAssignment);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsMiAssignment(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final String MULTI_INSTANCE_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:custom="http://custom.bpmn.io/schema"
                              xmlns:flowable="http://flowable.org/bpmn">
              <bpmn:process id="order_process" isExecutable="true">
                <bpmn:startEvent id="Start_1" />
                <bpmn:subProcess id="MultiInstance_SubTable_items">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>multiInstance_order_items_collection</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  <bpmn:userTask id="MI_UserTask_items" name="Item review">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableName" value="order_items" />
                        <custom:property name="assigneeMode" value="user" />
                        <custom:property name="assigneeField" value="assignee_id" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
                <bpmn:endEvent id="End_1" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="Start_1" targetRef="MultiInstance_SubTable_items" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="MultiInstance_SubTable_items" targetRef="End_1" />
              </bpmn:process>
            </bpmn:definitions>
            """;

    // ==================== Email slices: upsert scopes never clear, delegate to AiEmailProposalWriter ====================

    @Test
    void applyGeneratedData_emailScope_modifyMode_keepsExistingDesignAndDelegatesToEmailWriter() {
        FormDefinition existingForm = FormDefinition.builder().id(5L).formName("keep_me").build();
        functionUnit.getFormDefinitions().add(existingForm);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        AiEmailProposalWriter emailWriter = mock(AiEmailProposalWriter.class);
        ReflectionTestUtils.setField(writeService, "emailProposalWriter", emailWriter);

        AiGeneratedData data = AiGeneratedData.builder()
                .emailTemplates(List.of(Map.of("name", "Approved", "subject", "s", "bodyHtml", "<p/>")))
                .build();

        writeService.applyGeneratedData(1L, data, "EMAIL_TEMPLATES");

        // MODIFY 模式下 scoped 清理对 upsert scope 必须是 no-op：既有表单原样保留
        assertEquals(1, functionUnit.getFormDefinitions().size());
        assertSame(existingForm, functionUnit.getFormDefinitions().get(0));
        verify(emailWriter).write(functionUnit, data);
    }

    @Test
    void applyGeneratedData_emailSlicesWithoutWiredWriter_failsExplicitly() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));

        AiGeneratedData data = AiGeneratedData.builder()
                .emailConnections(List.of(Map.of("name", "notify@x.com")))
                .build();

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> writeService.applyGeneratedData(1L, data, "CONNECTIONS"));
        assertEquals("AI_WRITE_EMAIL_WRITER_UNAVAILABLE", ex.getErrorCode());
    }


    // ==================== scoped apply keeps BPMN bindings ====================

    private static final String STORED_TWO_TASKS = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:custom="http://custom.bpmn.io/schema">
              <bpmn:process id="Process_1">
                <bpmn:userTask id="Task_Submit" name="Submit">%s</bpmn:userTask>
                <bpmn:userTask id="Task_Review" name="Review">%s</bpmn:userTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

    private static String actionBinding(long id, String name) {
        return "<bpmn:extensionElements><custom:properties>"
                + "<custom:property name=\"actionIds\" value=\"[" + id + "]\"/>"
                + "<custom:property name=\"actionNames\" value=\"[&quot;" + name + "&quot;]\"/>"
                + "</custom:properties></bpmn:extensionElements>";
    }

    /** The designer stores BPMN Base64-encoded; the AI write must read and patch it in place. */
    private void givenStoredProcess(String bpmnXml) {
        functionUnit.setProcessDefinition(ProcessDefinition.builder().id(900L).functionUnit(functionUnit)
                .bpmnXml(XmlEncodingUtil.encode(bpmnXml)).build());
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Simulate the database assigning ids to new actions / forms at flush time. */
    private void assignIdsOnFlush(long firstId) {
        AtomicLong sequence = new AtomicLong(firstId);
        doAnswer(inv -> {
            functionUnit.getActionDefinitions().stream().filter(a -> a.getId() == null)
                    .forEach(a -> a.setId(sequence.incrementAndGet()));
            functionUnit.getFormDefinitions().stream().filter(f -> f.getId() == null)
                    .forEach(f -> f.setId(sequence.incrementAndGet()));
            return null;
        }).when(entityManager).flush();
    }

    private Map<String, List<String>> boundActionsByTask() {
        String xml = XmlEncodingUtil.smartDecode(functionUnit.getProcessDefinition().getBpmnXml());
        return AiBpmnActionBindingWriter.capture(xml, functionUnit.getActionDefinitions()).byTask();
    }

    @Test
    void actionsScope_writesStageIdsIntoTheStoredProcess() {
        functionUnit.getActionDefinitions().add(ActionDefinition.builder().id(5L).functionUnit(functionUnit)
                .actionName("old_action").actionType(ActionType.APPROVE).build());
        givenStoredProcess(STORED_TWO_TASKS.formatted("", actionBinding(5, "old_action")));
        assignIdsOnFlush(100);

        AiGeneratedData data = AiGeneratedData.builder().actionDefinitions(List.of(
                Map.of("actionName", "submit", "actionType", "PROCESS_SUBMIT", "stageIds", List.of("Task_Submit")),
                Map.of("actionName", "approve", "actionType", "APPROVE", "stageIds", List.of("Task_Review"))))
                .build();

        writeService.applyGeneratedData(1L, data, "ACTIONS");

        assertEquals(Map.of("Task_Submit", List.of("submit"), "Task_Review", List.of("approve")), boundActionsByTask());
        assertFalse(XmlEncodingUtil.smartDecode(functionUnit.getProcessDefinition().getBpmnXml()).contains("[5]"),
                "the rebuilt actions must not leave the deleted id behind");
    }

    @Test
    void processScope_keepsTheExistingActionBindingsOfSurvivingUserTasks() {
        functionUnit.getActionDefinitions().add(ActionDefinition.builder().id(7L).functionUnit(functionUnit)
                .actionName("submit").actionType(ActionType.PROCESS_SUBMIT).build());
        functionUnit.getActionDefinitions().add(ActionDefinition.builder().id(8L).functionUnit(functionUnit)
                .actionName("approve").actionType(ActionType.APPROVE).build());
        givenStoredProcess(STORED_TWO_TASKS.formatted(actionBinding(7, "submit"), actionBinding(8, "approve")));

        String fresh = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="Process_1">
                    <bpmn:startEvent id="Start"/>
                    <bpmn:userTask id="Task_Submit" name="Submit"/>
                    <bpmn:userTask id="Task_Review" name="Review again"/>
                    <bpmn:endEvent id="End"/>
                    <bpmn:sequenceFlow id="F1" sourceRef="Start" targetRef="Task_Submit"/>
                    <bpmn:sequenceFlow id="F2" sourceRef="Task_Submit" targetRef="Task_Review"/>
                    <bpmn:sequenceFlow id="F3" sourceRef="Task_Review" targetRef="End"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        AiGeneratedData data = AiGeneratedData.builder().processDefinition(Map.of("bpmnXml", fresh)).build();

        writeService.applyGeneratedData(1L, data, "PROCESS");

        assertEquals(Map.of("Task_Submit", List.of("submit"), "Task_Review", List.of("approve")), boundActionsByTask());
        assertTrue(functionUnit.getProcessDefinition().getBpmnXml().contains("Review again"));
    }

    @Test
    void formsScope_writesFormIdsIntoTheStoredProcessAndDropsDeadOnes() {
        FormDefinition old = FormDefinition.builder().id(30L).functionUnit(functionUnit).formName("old_form")
                .formType(FormType.PROCESS).build();
        functionUnit.getFormDefinitions().add(old);
        givenStoredProcess(STORED_TWO_TASKS.formatted(
                "<bpmn:extensionElements><custom:properties><custom:property name=\"formId\" value=\"30\"/>"
                        + "<custom:property name=\"formName\" value=\"old_form\"/></custom:properties></bpmn:extensionElements>",
                ""));
        assignIdsOnFlush(200);

        AiGeneratedData data = AiGeneratedData.builder().formDefinitions(List.of(Map.of(
                "formName", "review_form",
                "formType", "TASK",
                "configJson", new java.util.HashMap<>(),
                "stageBindings", List.of(Map.of("stageId", "Task_Review", "stageName", "Review", "readOnly", true)))))
                .build();

        writeService.applyGeneratedData(1L, data, "FORMS");

        String xml = XmlEncodingUtil.smartDecode(functionUnit.getProcessDefinition().getBpmnXml());
        Long reviewFormId = functionUnit.getFormDefinitions().get(0).getId();
        assertTrue(xml.contains("name=\"formId\" value=\"" + reviewFormId + "\""), xml);
        assertTrue(xml.contains("name=\"formReadOnly\" value=\"true\""), xml);
        assertFalse(xml.contains("old_form"), "the deleted form's binding must be dropped: " + xml);
    }

    @Test
    void unreadableStoredProcess_failsAScopedActionsApplyButNotAFullReplacement() {
        functionUnit.setProcessDefinition(ProcessDefinition.builder().id(900L).functionUnit(functionUnit)
                .bpmnXml("<bpmn:definitions").build());
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        AiGeneratedData data = AiGeneratedData.builder().actionDefinitions(List.of(
                Map.of("actionName", "submit", "actionType", "PROCESS_SUBMIT", "stageIds", List.of("Task_Submit"))))
                .build();

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> writeService.applyGeneratedData(1L, data, "ACTIONS"));
        assertEquals("AI_EXISTING_BPMN_INVALID", ex.getErrorCode());

        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        writeService.applyGeneratedData(1L, data, null);
        assertNull(functionUnit.getProcessDefinition(), "ALL clears the unreadable process instead of failing on it");
    }

    // ==================== scoped writes reference the tables the FU already has ====================

    private TableDefinition existingTable(long id, String name, TableType type) {
        TableDefinition table = TableDefinition.builder().id(id).functionUnit(functionUnit)
                .tableName(name).tableType(type).fieldDefinitions(new ArrayList<>()).build();
        functionUnit.getTableDefinitions().add(table);
        return table;
    }

    /** A FORMS proposal carries no tables: its bindings must resolve to the tables the FU already has. */
    @Test
    void formsScope_bindsFormsToTheExistingTables() {
        TableDefinition orders = existingTable(10L, "orders", TableType.MAIN);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder().formDefinitions(List.of(Map.of(
                "formName", "order_form", "formType", "TASK", "configJson", new java.util.HashMap<>(),
                "tableBindings", List.of(Map.of("tableName", "orders", "bindingType", "PRIMARY")))))
                .build();

        writeService.applyGeneratedData(1L, data, "FORMS");

        // ids, not assertSame: entity toString() recurses FunctionUnit <-> TableDefinition on a failure message
        FormDefinition form = functionUnit.getFormDefinitions().get(0);
        TableDefinition bound = form.getTableBindings().get(0).getTable();
        assertEquals(10L, bound == null ? null : bound.getId());
        assertEquals(10L, form.getBoundTable() == null ? null : form.getBoundTable().getId());
        assertTrue(functionUnit.getTableDefinitions().get(0) == orders, "a FORMS write must not touch the tables");
    }

    /** Same for TABLE_RELATIONS: before, every relation was skipped and the scope just deleted them all. */
    @Test
    void tableRelationsScope_linksTheExistingTables() {
        existingTable(10L, "orders", TableType.MAIN);
        existingTable(11L, "customers", TableType.MAIN);
        functionUnit.setTableRelations(new ArrayList<>());
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        AiGeneratedData data = AiGeneratedData.builder().tableRelations(List.of(Map.of(
                "sourceTableName", "orders", "sourceFieldName", "customer_id", "relationType", "MANY_TO_ONE",
                "targetTableName", "customers", "targetFieldName", "id"))).build();

        writeService.applyGeneratedData(1L, data, "TABLE_RELATIONS");

        assertEquals(1, functionUnit.getTableRelations().size());
        assertEquals(10L, functionUnit.getTableRelations().get(0).getSourceTableId());
        assertEquals(11L, functionUnit.getTableRelations().get(0).getTargetTableId());
    }
}
