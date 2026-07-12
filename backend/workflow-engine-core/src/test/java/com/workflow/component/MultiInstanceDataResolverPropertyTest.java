package com.workflow.component;

import com.workflow.component.MultiInstanceDataResolver.OptimisticLockException;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import net.jqwik.api.*;
import org.flowable.engine.RuntimeService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiInstanceDataResolver 属性测试
 * 
 * 使用 jqwik 进行基于属性的测试，验证跨所有输入的通用属性
 * 
 * Feature: multi-instance-task-dispatch
 */
class MultiInstanceDataResolverPropertyTest {
    
    /**
     * Property 10: 子任务数据隔离
     *
     * For any 多实例子任务，加载行数据时仅能访问 ExtendedTaskInfo.extended_properties
     * 中 subTableRowId 对应的子表数据行，不能访问其他行。
     *
     * **Validates: Requirements 6.1, 6.2**
     */
    @Property(tries = 100)
    @Label("Property 10: 子任务数据隔离 - 每个子任务只能访问自己的数据行")
    void property10_dataIsolation(
        @ForAll("subTaskList") java.util.List<SubTaskData> subTasks
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);

        MultiInstanceDataResolver resolver = new MultiInstanceDataResolver();
        injectMocks(resolver, runtimeService, jdbcTemplate, extendedTaskInfoRepository);

        String subTableName = "fu_participants";

        for (SubTaskData subTask : subTasks) {
            Map<String, Object> subTableRow = new HashMap<>();
            subTableRow.put("id", subTask.rowId);
            subTableRow.put("name", "User-" + subTask.rowId);
            subTableRow.put("row_version", 1L);
            when(jdbcTemplate.queryForMap(anyString(), eq(subTask.rowId)))
                .thenReturn(subTableRow);
        }

        for (SubTaskData subTask : subTasks) {
            Map<String, Object> row = resolver.loadSubTableRow(subTableName, subTask.rowId);

            assertThat(row.get("id"))
                .as("Task %s should only access row %d", subTask.taskId, subTask.rowId)
                .isEqualTo(subTask.rowId);
            assertThat(row.get("name")).isEqualTo("User-" + subTask.rowId);

            verify(jdbcTemplate, atLeastOnce()).queryForMap(
                contains(subTableName),
                eq(subTask.rowId)
            );
        }
    }

    /**
     * Property 11: 子表数据回写往返一致性
     * 
     * For any 子任务表单数据提交，回写到子表后再次加载该行数据，应与提交的表单数据一致
     * （排除 row_version 字段）。
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Label("Property 11: 子表数据回写往返一致性")
    void property11_writeBackRoundTripConsistency(
        @ForAll("taskIds") String taskId,
        @ForAll("rowIds") long subTableRowId,
        @ForAll("tableNames") String subTableName,
        @ForAll("fieldValues") String fieldValue1,
        @ForAll("fieldValues") String fieldValue2
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        MultiInstanceDataResolver resolver = new MultiInstanceDataResolver();
        injectMocks(resolver, runtimeService, jdbcTemplate, extendedTaskInfoRepository);
        
        // Given: 准备 ExtendedTaskInfo
        ExtendedTaskInfo extInfo = createExtendedTaskInfo(taskId, subTableRowId, subTableName);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId))
            .thenReturn(Optional.of(extInfo));
        
        // 当前 row_version 为 1
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(subTableRowId)))
            .thenReturn(1L);
        
        // UPDATE 成功
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
            .thenReturn(1);
        
        // 准备表单数据
        Map<String, Object> formData = new HashMap<>();
        formData.put("field1", fieldValue1);
        formData.put("field2", fieldValue2);
        
        // When: 回写数据
        resolver.writeBackSubTableRow(taskId, formData, 1L);
        
        // 模拟再次加载数据（包含回写的数据 + 递增的 row_version）
        Map<String, Object> reloadedData = new HashMap<>();
        reloadedData.put("id", subTableRowId);
        reloadedData.put("field1", fieldValue1);
        reloadedData.put("field2", fieldValue2);
        reloadedData.put("row_version", 2L); // row_version 递增
        
        when(jdbcTemplate.queryForMap(anyString(), eq(subTableRowId)))
            .thenReturn(reloadedData);
        
        Map<String, Object> result = resolver.loadSubTableRow(subTableName, subTableRowId);
        
        // Then: 验证数据一致性（排除 row_version）
        assertThat(result.get("field1")).isEqualTo(fieldValue1);
        assertThat(result.get("field2")).isEqualTo(fieldValue2);
        assertThat(result.get("row_version")).isEqualTo(2L); // row_version 已递增
    }
    
    /**
     * Property 12: 乐观锁正确性
     * 
     * For any 子表数据行，提交时如果提供的 row_version 与数据库中当前值不一致则更新被拒绝；
     * 如果一致则更新成功且 row_version 递增 1。
     * 
     * **Validates: Requirements 6.5, 6.6**
     */
    @Property(tries = 100)
    @Label("Property 12: 乐观锁正确性")
    void property12_optimisticLockCorrectness(
        @ForAll("taskIds") String taskId,
        @ForAll("rowIds") long subTableRowId,
        @ForAll("tableNames") String subTableName,
        @ForAll("versions") long expectedVersion,
        @ForAll("versions") long currentVersion
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        MultiInstanceDataResolver resolver = new MultiInstanceDataResolver();
        injectMocks(resolver, runtimeService, jdbcTemplate, extendedTaskInfoRepository);
        
        // Given: 准备 ExtendedTaskInfo
        ExtendedTaskInfo extInfo = createExtendedTaskInfo(taskId, subTableRowId, subTableName);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId))
            .thenReturn(Optional.of(extInfo));
        
        // 数据库中的当前 row_version
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(subTableRowId)))
            .thenReturn(currentVersion);
        
        Map<String, Object> formData = new HashMap<>();
        formData.put("field1", "value1");
        
        if (expectedVersion == currentVersion) {
            // Case 1: row_version 一致，更新应该成功
            when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1);
            
            // When & Then: 更新成功
            assertThatCode(() -> resolver.writeBackSubTableRow(taskId, formData, expectedVersion))
                .doesNotThrowAnyException();
            
            // 验证 UPDATE 被调用，且包含乐观锁条件
            verify(jdbcTemplate).update(
                argThat((String sql) -> sql.contains("WHERE ") && sql.contains(" AND row_version = ?")),
                any(Object[].class)
            );
        } else {
            // Case 2: row_version 不一致，更新应该被拒绝
            // When & Then: 抛出乐观锁异常
            assertThatThrownBy(() -> resolver.writeBackSubTableRow(taskId, formData, expectedVersion))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessage("Data has been modified, please refresh and try again");
            
            // 验证 UPDATE 没有被调用
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        }
    }
    
    /**
     * Property: 系统变量过滤正确性
     * 
     * For any 流程变量集合，loadMainFormData 应该过滤掉所有系统变量和集合变量，
     * 只返回业务字段。
     */
    @Property(tries = 100)
    @Label("系统变量过滤正确性")
    void shouldFilterSystemVariablesCorrectly(
        @ForAll("processInstanceIds") String processInstanceId,
        @ForAll("fieldNames") String businessField1,
        @ForAll("fieldNames") String businessField2,
        @ForAll("instanceCounts") int nrOfInstances
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        MultiInstanceDataResolver resolver = new MultiInstanceDataResolver();
        injectMocks(resolver, runtimeService, jdbcTemplate, extendedTaskInfoRepository);
        
        // Given: 准备流程变量（包含业务字段和系统变量）
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put(businessField1, "value1");
        processVariables.put(businessField2, "value2");
        processVariables.put("multiInstance_collection", new Object());
        processVariables.put("currentItem", new Object());
        processVariables.put("nrOfInstances", nrOfInstances);
        processVariables.put("nrOfActiveInstances", nrOfInstances / 2);
        processVariables.put("nrOfCompletedInstances", nrOfInstances / 2);
        processVariables.put("loopCounter", 1);
        processVariables.put("_internalVar", "internal");
        
        when(runtimeService.getVariables(processInstanceId)).thenReturn(processVariables);
        
        // When: 加载主表单数据
        Map<String, Object> result = resolver.loadMainFormData(processInstanceId);
        
        // Then: 只包含业务字段
        assertThat(result).containsOnlyKeys(businessField1, businessField2);
        assertThat(result).doesNotContainKeys(
            "multiInstance_collection",
            "currentItem",
            "nrOfInstances",
            "nrOfActiveInstances",
            "nrOfCompletedInstances",
            "loopCounter",
            "_internalVar"
        );
    }
    
    /**
     * Property: 数据行删除检测正确性
     * 
     * For any 子表数据行，如果数据行被删除，loadSubTableRow 和 writeBackSubTableRow 
     * 都应该抛出 WorkflowValidationException。
     */
    @Property(tries = 100)
    @Label("数据行删除检测正确性")
    void shouldDetectRowDeletionCorrectly(
        @ForAll("taskIds") String taskId,
        @ForAll("rowIds") long subTableRowId,
        @ForAll("tableNames") String subTableName
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        MultiInstanceDataResolver resolver = new MultiInstanceDataResolver();
        injectMocks(resolver, runtimeService, jdbcTemplate, extendedTaskInfoRepository);
        
        // Given: 数据行不存在
        when(jdbcTemplate.queryForMap(anyString(), eq(subTableRowId)))
            .thenThrow(new EmptyResultDataAccessException(1));
        
        // When & Then: loadSubTableRow 抛出异常
        assertThatThrownBy(() -> resolver.loadSubTableRow(subTableName, subTableRowId))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessage("The associated data row no longer exists");
        
        // Given: 准备 ExtendedTaskInfo（用于 writeBackSubTableRow）
        ExtendedTaskInfo extInfo = createExtendedTaskInfo(taskId, subTableRowId, subTableName);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId))
            .thenReturn(Optional.of(extInfo));
        
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(subTableRowId)))
            .thenThrow(new EmptyResultDataAccessException(1));
        
        Map<String, Object> formData = new HashMap<>();
        formData.put("field1", "value1");
        
        // When & Then: writeBackSubTableRow 抛出异常
        assertThatThrownBy(() -> resolver.writeBackSubTableRow(taskId, formData, 1L))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessage("The associated data row no longer exists");
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 子任务数据结构
     */
    private static class SubTaskData {
        final String taskId;
        final long rowId;
        
        SubTaskData(String taskId, long rowId) {
            this.taskId = taskId;
            this.rowId = rowId;
        }
    }
    
    @Provide
    Arbitrary<java.util.List<SubTaskData>> subTaskList() {
        // 生成 2-5 个子任务，每个关联不同的数据行
        return Arbitraries.integers().between(2, 5).flatMap(count -> {
            java.util.List<Arbitrary<SubTaskData>> subTaskArbitraries = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                Arbitrary<SubTaskData> subTaskArbitrary = Combinators.combine(
                    Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .numeric()
                        .ofMinLength(5)
                        .ofMaxLength(15),
                    Arbitraries.longs().between(1L, 1000L)
                ).as((taskIdBase, rowIdBase) -> new SubTaskData(
                    "task-" + index + "-" + taskIdBase,
                    rowIdBase + index * 1000 // 确保每个子任务的 rowId 不同
                ));
                subTaskArbitraries.add(subTaskArbitrary);
            }
            return Combinators.combine(subTaskArbitraries).as(list -> list);
        });
    }
    
    @Provide
    Arbitrary<Long> rowIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }
    
    @Provide
    Arbitrary<String> tableNames() {
        return Arbitraries.of(
            "fu_participants", "fu_approvers", "fu_reviewers", 
            "fu_items", "fu_details", "fu_attachments"
        );
    }
    
    @Provide
    Arbitrary<String> taskIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "task-" + s);
    }
    
    @Provide
    Arbitrary<String> fieldValues() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<Long> versions() {
        return Arbitraries.longs().between(1L, 100L);
    }
    
    @Provide
    Arbitrary<String> processInstanceIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "proc-" + s);
    }
    
    @Provide
    Arbitrary<String> fieldNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(5)
            .ofMaxLength(20);
    }
    
    @Provide
    Arbitrary<Integer> instanceCounts() {
        return Arbitraries.integers().between(1, 100);
    }
    
    private void injectMocks(MultiInstanceDataResolver resolver,
                            RuntimeService runtimeService,
                            JdbcTemplate jdbcTemplate,
                            ExtendedTaskInfoRepository extendedTaskInfoRepository) {
        try {
            java.lang.reflect.Field runtimeServiceField = 
                MultiInstanceDataResolver.class.getDeclaredField("runtimeService");
            runtimeServiceField.setAccessible(true);
            runtimeServiceField.set(resolver, runtimeService);
            
            java.lang.reflect.Field jdbcTemplateField = 
                MultiInstanceDataResolver.class.getDeclaredField("jdbcTemplate");
            jdbcTemplateField.setAccessible(true);
            jdbcTemplateField.set(resolver, jdbcTemplate);
            
            java.lang.reflect.Field extendedTaskInfoRepositoryField = 
                MultiInstanceDataResolver.class.getDeclaredField("extendedTaskInfoRepository");
            extendedTaskInfoRepositoryField.setAccessible(true);
            extendedTaskInfoRepositoryField.set(resolver, extendedTaskInfoRepository);

            lenient().when(jdbcTemplate.query(
                    contains("constraint_type"),
                    any(RowMapper.class),
                    any()))
                    .thenReturn(java.util.List.of("id"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }
    

    /**
     * Row-key params originate from extended_properties JSON where Jackson yields Integer for
     * small ids; match by numeric value rather than boxed type (Long vs Integer).
     */
    private static Object rowIdArg(long subTableRowId) {
        return ArgumentMatchers.<Object>argThat(v -> v instanceof Number n && n.longValue() == subTableRowId);
    }

    private ExtendedTaskInfo createExtendedTaskInfo(String taskId, long subTableRowId, String subTableName) {
        String extendedProperties = String.format(
            "{\"multiInstance\":true,\"subTableRowId\":%d,\"subTableName\":\"%s\",\"subTableRowVersion\":1}",
            subTableRowId, subTableName
        );
        
        return ExtendedTaskInfo.builder()
            .taskId(taskId)
            .processInstanceId("proc-001")
            .processDefinitionId("proc-def-001")
            .taskDefinitionKey("userTask1")
            .taskName("填写参会信息")
            .assignmentType(com.workflow.enums.AssignmentType.USER)
            .assignmentTarget("user-001")
            .status("ASSIGNED")
            .extendedProperties(extendedProperties)
            .build();
    }
}
