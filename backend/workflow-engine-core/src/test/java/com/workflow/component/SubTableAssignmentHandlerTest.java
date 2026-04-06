package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubTableAssignmentHandler 单元测试
 * 
 * 测试场景：
 * 1. 正常分配场景
 * 2. 任务不存在时抛出异常
 * 3. rowId 不属于当前任务时抛出异常
 * 4. 用户不存在时抛出异常
 * 5. 用户已禁用时抛出异常
 * 6. 子表行不存在时抛出异常
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubTableAssignmentHandler 单元测试")
class SubTableAssignmentHandlerTest {
    
    @Mock
    private TaskService taskService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    @Mock
    private AdminCenterClient adminCenterClient;
    
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BpmnActionParser bpmnActionParser;
    
    @Mock
    private TaskQuery taskQuery;
    
    @Mock
    private Task task;
    
    @InjectMocks
    private SubTableAssignmentHandler handler;
    
    private static final String TASK_ID = "task-001";
    private static final Long ROW_ID = 101L;
    private static final String ASSIGNEE_ID = "user-001";
    private static final String ASSIGNEE_NAME = "张三";
    private static final String SUB_TABLE_NAME = "fu_participants";
    private static final String ASSIGNEE_FIELD = "assignee_id";
    private static final String FOREIGN_KEY = "main_record_id";
    private static final Long MAIN_RECORD_ID = 1001L;
    
    @BeforeEach
    void setUp() {
        // 设置 TaskService mock
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
    }
    
    @Test
    @DisplayName("正常分配场景 - 应该成功分配处理人")
    void testAssign_Success() throws Exception {
        // Given: 准备测试数据
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(TASK_ID);
        
        // 模拟 ExtendedTaskInfo 包含子表配置
        ExtendedTaskInfo extInfo = new ExtendedTaskInfo();
        String extPropsJson = String.format(
            "{\"subTableName\":\"%s\",\"assigneeField\":\"%s\",\"foreignKey\":\"%s\",\"mainRecordId\":%d}",
            SUB_TABLE_NAME, ASSIGNEE_FIELD, FOREIGN_KEY, MAIN_RECORD_ID
        );
        extInfo.setExtendedProperties(extPropsJson);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extInfo));
        
        // 模拟 ObjectMapper 解析
        Map<String, Object> extProps = new HashMap<>();
        extProps.put("subTableName", SUB_TABLE_NAME);
        extProps.put("assigneeField", ASSIGNEE_FIELD);
        extProps.put("foreignKey", FOREIGN_KEY);
        extProps.put("mainRecordId", MAIN_RECORD_ID);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(extProps);
        
        // 模拟验证子表行归属
        when(jdbcTemplate.queryForObject(
            contains("SELECT COUNT(*)"),
            eq(Integer.class),
            eq(ROW_ID),
            eq(MAIN_RECORD_ID)
        )).thenReturn(1);
        
        // 模拟用户验证
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", ASSIGNEE_ID);
        userInfo.put("name", ASSIGNEE_NAME);
        userInfo.put("enabled", true);
        when(adminCenterClient.getUserInfo(ASSIGNEE_ID)).thenReturn(userInfo);
        
        // 模拟更新子表
        when(jdbcTemplate.update(
            contains("UPDATE"),
            eq(ASSIGNEE_ID),
            eq(ROW_ID)
        )).thenReturn(1);
        
        // When: 执行分配
        SubTableAssignmentHandler.AssignmentResponse response = 
            handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID);
        
        // Then: 验证结果
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(ROW_ID, response.getRowId());
        assertEquals(ASSIGNEE_ID, response.getAssigneeId());
        assertEquals(ASSIGNEE_NAME, response.getAssigneeName());
        
        // 验证方法调用（getUserInfo 会被调用两次：一次验证用户，一次获取用户名）
        verify(taskService).createTaskQuery();
        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), eq(ROW_ID), eq(MAIN_RECORD_ID));
        verify(adminCenterClient, times(2)).getUserInfo(ASSIGNEE_ID);
        verify(jdbcTemplate).update(anyString(), eq(ASSIGNEE_ID), eq(ROW_ID));
    }
    
    @Test
    @DisplayName("任务不存在 - 应该抛出 WorkflowValidationException")
    void testAssign_TaskNotFound() {
        // Given: 任务不存在
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        
        // When & Then: 执行分配应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID)
        );
        
        assertTrue(exception.getMessage().contains("任务不存在"));
        verify(taskService).createTaskQuery();
    }
    
    @Test
    @DisplayName("子表配置不存在 - 应该抛出 WorkflowBusinessException")
    void testAssign_SubTableConfigNotFound() {
        // Given: 任务存在但没有子表配置
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(TASK_ID);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.empty());
        when(taskService.getVariables(TASK_ID)).thenReturn(new HashMap<>());
        
        // When & Then: 执行分配应该抛出异常
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID)
        );
        
        assertEquals("SUBTABLE_CONFIG_NOT_FOUND", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("任务未配置子表信息"));
    }
    
    @Test
    @DisplayName("rowId 不属于当前任务 - 应该抛出 WorkflowValidationException")
    void testAssign_RowNotBelongsToTask() throws Exception {
        // Given: 准备测试数据
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(TASK_ID);
        
        // 模拟 ExtendedTaskInfo 包含子表配置
        ExtendedTaskInfo extInfo = new ExtendedTaskInfo();
        String extPropsJson = String.format(
            "{\"subTableName\":\"%s\",\"assigneeField\":\"%s\",\"foreignKey\":\"%s\",\"mainRecordId\":%d}",
            SUB_TABLE_NAME, ASSIGNEE_FIELD, FOREIGN_KEY, MAIN_RECORD_ID
        );
        extInfo.setExtendedProperties(extPropsJson);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extInfo));
        
        // 模拟 ObjectMapper 解析
        Map<String, Object> extProps = new HashMap<>();
        extProps.put("subTableName", SUB_TABLE_NAME);
        extProps.put("assigneeField", ASSIGNEE_FIELD);
        extProps.put("foreignKey", FOREIGN_KEY);
        extProps.put("mainRecordId", MAIN_RECORD_ID);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(extProps);
        
        // 模拟验证子表行归属失败（COUNT 返回 0）
        when(jdbcTemplate.queryForObject(
            contains("SELECT COUNT(*)"),
            eq(Integer.class),
            eq(ROW_ID),
            eq(MAIN_RECORD_ID)
        )).thenReturn(0);
        
        // When & Then: 执行分配应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID)
        );
        
        assertTrue(exception.getMessage().contains("子表行"));
        assertTrue(exception.getMessage().contains("不属于当前任务"));
    }
    
    @Test
    @DisplayName("用户不存在 - 应该抛出 WorkflowValidationException")
    void testAssign_UserNotFound() throws Exception {
        // Given: 准备测试数据
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(TASK_ID);
        
        // 模拟 ExtendedTaskInfo 包含子表配置
        ExtendedTaskInfo extInfo = new ExtendedTaskInfo();
        String extPropsJson = String.format(
            "{\"subTableName\":\"%s\",\"assigneeField\":\"%s\",\"foreignKey\":\"%s\",\"mainRecordId\":%d}",
            SUB_TABLE_NAME, ASSIGNEE_FIELD, FOREIGN_KEY, MAIN_RECORD_ID
        );
        extInfo.setExtendedProperties(extPropsJson);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extInfo));
        
        // 模拟 ObjectMapper 解析
        Map<String, Object> extProps = new HashMap<>();
        extProps.put("subTableName", SUB_TABLE_NAME);
        extProps.put("assigneeField", ASSIGNEE_FIELD);
        extProps.put("foreignKey", FOREIGN_KEY);
        extProps.put("mainRecordId", MAIN_RECORD_ID);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(extProps);
        
        // 模拟验证子表行归属成功
        when(jdbcTemplate.queryForObject(
            contains("SELECT COUNT(*)"),
            eq(Integer.class),
            eq(ROW_ID),
            eq(MAIN_RECORD_ID)
        )).thenReturn(1);
        
        // 模拟用户不存在
        when(adminCenterClient.getUserInfo(ASSIGNEE_ID)).thenReturn(null);
        
        // When & Then: 执行分配应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID)
        );
        
        assertTrue(exception.getMessage().contains("用户不存在"));
    }
    
    @Test
    @DisplayName("用户已禁用 - 应该抛出 WorkflowValidationException")
    void testAssign_UserDisabled() throws Exception {
        // Given: 准备测试数据
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(TASK_ID);
        
        // 模拟 ExtendedTaskInfo 包含子表配置
        ExtendedTaskInfo extInfo = new ExtendedTaskInfo();
        String extPropsJson = String.format(
            "{\"subTableName\":\"%s\",\"assigneeField\":\"%s\",\"foreignKey\":\"%s\",\"mainRecordId\":%d}",
            SUB_TABLE_NAME, ASSIGNEE_FIELD, FOREIGN_KEY, MAIN_RECORD_ID
        );
        extInfo.setExtendedProperties(extPropsJson);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extInfo));
        
        // 模拟 ObjectMapper 解析
        Map<String, Object> extProps = new HashMap<>();
        extProps.put("subTableName", SUB_TABLE_NAME);
        extProps.put("assigneeField", ASSIGNEE_FIELD);
        extProps.put("foreignKey", FOREIGN_KEY);
        extProps.put("mainRecordId", MAIN_RECORD_ID);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(extProps);
        
        // 模拟验证子表行归属成功
        when(jdbcTemplate.queryForObject(
            contains("SELECT COUNT(*)"),
            eq(Integer.class),
            eq(ROW_ID),
            eq(MAIN_RECORD_ID)
        )).thenReturn(1);
        
        // 模拟用户已禁用
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", ASSIGNEE_ID);
        userInfo.put("name", ASSIGNEE_NAME);
        userInfo.put("enabled", false);
        when(adminCenterClient.getUserInfo(ASSIGNEE_ID)).thenReturn(userInfo);
        
        // When & Then: 执行分配应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID)
        );
        
        assertTrue(exception.getMessage().contains("用户已被禁用"));
    }
    
    @Test
    @DisplayName("子表行不存在 - 应该抛出 WorkflowValidationException")
    void testAssign_SubTableRowNotFound() throws Exception {
        // Given: 准备测试数据
        when(taskQuery.taskId(TASK_ID)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(TASK_ID);
        
        // 模拟 ExtendedTaskInfo 包含子表配置
        ExtendedTaskInfo extInfo = new ExtendedTaskInfo();
        String extPropsJson = String.format(
            "{\"subTableName\":\"%s\",\"assigneeField\":\"%s\",\"foreignKey\":\"%s\",\"mainRecordId\":%d}",
            SUB_TABLE_NAME, ASSIGNEE_FIELD, FOREIGN_KEY, MAIN_RECORD_ID
        );
        extInfo.setExtendedProperties(extPropsJson);
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extInfo));
        
        // 模拟 ObjectMapper 解析
        Map<String, Object> extProps = new HashMap<>();
        extProps.put("subTableName", SUB_TABLE_NAME);
        extProps.put("assigneeField", ASSIGNEE_FIELD);
        extProps.put("foreignKey", FOREIGN_KEY);
        extProps.put("mainRecordId", MAIN_RECORD_ID);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(extProps);
        
        // 模拟验证子表行归属成功
        when(jdbcTemplate.queryForObject(
            contains("SELECT COUNT(*)"),
            eq(Integer.class),
            eq(ROW_ID),
            eq(MAIN_RECORD_ID)
        )).thenReturn(1);
        
        // 模拟用户验证成功
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", ASSIGNEE_ID);
        userInfo.put("name", ASSIGNEE_NAME);
        userInfo.put("enabled", true);
        when(adminCenterClient.getUserInfo(ASSIGNEE_ID)).thenReturn(userInfo);
        
        // 模拟更新子表失败（影响行数为 0）
        when(jdbcTemplate.update(
            contains("UPDATE"),
            eq(ASSIGNEE_ID),
            eq(ROW_ID)
        )).thenReturn(0);
        
        // When & Then: 执行分配应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> handler.assign(TASK_ID, ROW_ID, ASSIGNEE_ID)
        );
        
        assertTrue(exception.getMessage().contains("子表行不存在或已被删除"));
    }
}
