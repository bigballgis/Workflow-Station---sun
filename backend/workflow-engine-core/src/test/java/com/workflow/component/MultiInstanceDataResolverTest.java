package com.workflow.component;

import com.workflow.component.MultiInstanceDataResolver.OptimisticLockException;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.platform.common.i18n.I18nService;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultiInstanceDataResolver 单元测试
 * 
 * 测试范围：
 * 1. 加载子任务表单数据（主表单数据 + 子表数据行）
 * 2. 数据隔离验证
 * 3. 乐观锁回写机制
 * 4. 错误场景处理（数据行删除、版本冲突）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiInstanceDataResolver 单元测试")
class MultiInstanceDataResolverTest {
    
    @Mock
    private RuntimeService runtimeService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Mock
    private BpmnActionParser bpmnActionParser;

    @Mock
    private I18nService i18nService;
    
    @InjectMocks
    private MultiInstanceDataResolver resolver;

    @BeforeEach
    void stubInformationSchemaColumnLookup() {
        lenient().when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                any(),
                any()))
            .thenReturn(0);
        // Production resolves physical PK columns via information_schema (with a static cache);
        // clear the cache and answer the catalog query with the single-column PK "id".
        PostgresPhysicalTablePrimaryKeys.clearCache();
        lenient().when(jdbcTemplate.query(
                contains("PRIMARY KEY"),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(SUB_TABLE_NAME)))
            .thenReturn(List.of("id"));
    }

    /**
     * Row-key params originate from extended_properties JSON where Jackson yields Integer for
     * small ids; match by numeric value rather than boxed type (Long vs Integer).
     */
    private static Object rowIdArg(long rowId) {
        return ArgumentMatchers.<Object>argThat(v -> v instanceof Number n && n.longValue() == rowId);
    }
    
    private static final String TASK_ID = "task-001";
    private static final String PROCESS_INSTANCE_ID = "proc-001";
    private static final Long SUB_TABLE_ROW_ID = 101L;
    private static final String SUB_TABLE_NAME = "fu_participants";
    
    @Nested
    @DisplayName("loadMainFormData 测试")
    class LoadMainFormDataTests {
        
        @Test
        @DisplayName("正确过滤系统变量和集合变量")
        void shouldFilterSystemAndCollectionVariables() {
            // Given: 准备流程变量
            Map<String, Object> processVariables = new HashMap<>();
            processVariables.put("businessField1", "value1");
            processVariables.put("businessField2", "value2");
            processVariables.put("multiInstance_participants_collection", new Object());
            processVariables.put("currentItem", new Object());
            processVariables.put("nrOfInstances", 5);
            processVariables.put("nrOfActiveInstances", 3);
            processVariables.put("nrOfCompletedInstances", 2);
            processVariables.put("loopCounter", 1);
            processVariables.put("_internalVar", "internal");
            
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(processVariables);
            
            // When: 加载主表单数据
            Map<String, Object> result = resolver.loadMainFormData(PROCESS_INSTANCE_ID);
            
            // Then: 只包含业务字段
            assertThat(result).hasSize(2);
            assertThat(result).containsEntry("businessField1", "value1");
            assertThat(result).containsEntry("businessField2", "value2");
            assertThat(result).doesNotContainKeys(
                "multiInstance_participants_collection",
                "currentItem",
                "nrOfInstances",
                "nrOfActiveInstances",
                "nrOfCompletedInstances",
                "loopCounter",
                "_internalVar"
            );
        }
    }
    
    @Nested
    @DisplayName("loadSubTableRow 测试")
    class LoadSubTableRowTests {
        
        @Test
        @DisplayName("正常加载子表数据行")
        void shouldLoadSubTableRowSuccessfully() {
            // Given: 准备子表数据行
            Map<String, Object> subTableRow = new HashMap<>();
            subTableRow.put("id", SUB_TABLE_ROW_ID);
            subTableRow.put("name", "张三");
            subTableRow.put("row_version", 1L);
            
            when(jdbcTemplate.queryForMap(anyString(), eq(SUB_TABLE_ROW_ID)))
                .thenReturn(subTableRow);
            
            // When: 加载子表数据行
            Map<String, Object> result = resolver.loadSubTableRow(SUB_TABLE_NAME, SUB_TABLE_ROW_ID);
            
            // Then: 验证返回数据
            assertThat(result).hasSize(3);
            assertThat(result).containsEntry("id", SUB_TABLE_ROW_ID);
            assertThat(result).containsEntry("name", "张三");
            assertThat(result).containsEntry("row_version", 1L);
        }
        
        @Test
        @DisplayName("数据行不存在时抛出异常")
        void shouldThrowExceptionWhenRowNotFound() {
            // Given: 数据行不存在
            when(jdbcTemplate.queryForMap(anyString(), eq(SUB_TABLE_ROW_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));
            
            // When & Then: 抛出异常
            assertThatThrownBy(() -> resolver.loadSubTableRow(SUB_TABLE_NAME, SUB_TABLE_ROW_ID))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessage("The associated data row no longer exists");
        }
    }
    
    @Nested
    @DisplayName("writeBackSubTableRow 测试")
    class WriteBackSubTableRowTests {
        
        @Test
        @DisplayName("正常回写数据并递增 row_version")
        void shouldWriteBackDataSuccessfully() {
            // Given: 准备 ExtendedTaskInfo
            ExtendedTaskInfo extInfo = createExtendedTaskInfo();
            when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(extInfo));
            
            // 当前 row_version 为 1
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(SUB_TABLE_ROW_ID)))
                .thenReturn(1L);
            
            // UPDATE 成功
            when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1);
            
            // 准备表单数据
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "张三");
            formData.put("phone", "138xxxx1234");
            formData.put("willAttend", true);
            
            // When: 回写数据
            assertThatCode(() -> resolver.writeBackSubTableRow(TASK_ID, formData, 1L))
                .doesNotThrowAnyException();
            
            // Then: 验证 UPDATE 被调用
            verify(jdbcTemplate).update(
                contains("UPDATE " + SUB_TABLE_NAME),
                any(Object[].class)
            );
        }
        
        @Test
        @DisplayName("row_version 不一致时抛出 OptimisticLockException")
        void shouldThrowOptimisticLockExceptionWhenVersionMismatch() {
            // Given: 准备 ExtendedTaskInfo
            ExtendedTaskInfo extInfo = createExtendedTaskInfo();
            when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(extInfo));
            
            // 当前 row_version 为 2（与期望的 1 不一致）
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(SUB_TABLE_ROW_ID)))
                .thenReturn(2L);
            
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "张三");
            
            // When & Then: 抛出乐观锁异常
            assertThatThrownBy(() -> resolver.writeBackSubTableRow(TASK_ID, formData, 1L))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessage("Data has been modified, please refresh and try again");
        }
        
        @Test
        @DisplayName("数据行被删除时抛出 WorkflowValidationException")
        void shouldThrowValidationExceptionWhenRowDeleted() {
            // Given: 准备 ExtendedTaskInfo
            ExtendedTaskInfo extInfo = createExtendedTaskInfo();
            when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(extInfo));
            
            // 数据行不存在
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(SUB_TABLE_ROW_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));
            
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "张三");
            
            // When & Then: 抛出验证异常
            assertThatThrownBy(() -> resolver.writeBackSubTableRow(TASK_ID, formData, 1L))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessage("The associated data row no longer exists");
        }
        
        @Test
        @DisplayName("UPDATE 影响行数为 0 时区分删除和版本冲突")
        void shouldDistinguishBetweenDeletionAndVersionConflict() {
            // Given: 准备 ExtendedTaskInfo
            ExtendedTaskInfo extInfo = createExtendedTaskInfo();
            when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(extInfo));
            
            // 第一次查询：row_version 为 1（匹配）
            // 第二次查询（UPDATE 后）：row_version 为 2（说明被其他事务修改）
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), rowIdArg(SUB_TABLE_ROW_ID)))
                .thenReturn(1L)  // 第一次查询
                .thenReturn(2L); // 第二次查询（UPDATE 失败后）
            
            // UPDATE 影响行数为 0
            when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(0);
            
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "张三");
            
            // When & Then: 抛出乐观锁异常（而不是数据行删除异常）
            assertThatThrownBy(() -> resolver.writeBackSubTableRow(TASK_ID, formData, 1L))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessage("Data has been modified, please refresh and try again");
        }
    }
    
    @Nested
    @DisplayName("isSystemVariable 测试")
    class IsSystemVariableTests {
        
        @Test
        @DisplayName("正确识别系统变量")
        void shouldIdentifySystemVariables() {
            assertThat(resolver.isSystemVariable("nrOfInstances")).isTrue();
            assertThat(resolver.isSystemVariable("nrOfActiveInstances")).isTrue();
            assertThat(resolver.isSystemVariable("nrOfCompletedInstances")).isTrue();
            assertThat(resolver.isSystemVariable("loopCounter")).isTrue();
            assertThat(resolver.isSystemVariable("_internalVar")).isTrue();
        }
        
        @Test
        @DisplayName("正确识别非系统变量")
        void shouldIdentifyNonSystemVariables() {
            assertThat(resolver.isSystemVariable("businessField")).isFalse();
            assertThat(resolver.isSystemVariable("meetingTitle")).isFalse();
            assertThat(resolver.isSystemVariable("multiInstance_participants_collection")).isFalse();
            assertThat(resolver.isSystemVariable("currentItem")).isFalse();
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private ExtendedTaskInfo createExtendedTaskInfo() {
        String extendedProperties = String.format(
            "{\"multiInstance\":true,\"subTableRowId\":%d,\"subTableName\":\"%s\",\"subTableRowVersion\":1}",
            SUB_TABLE_ROW_ID, SUB_TABLE_NAME
        );
        
        return ExtendedTaskInfo.builder()
            .taskId(TASK_ID)
            .processInstanceId(PROCESS_INSTANCE_ID)
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
