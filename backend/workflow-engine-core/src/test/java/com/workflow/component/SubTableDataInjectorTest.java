package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.platform.common.i18n.I18nService;
import com.platform.common.i18n.impl.I18nServiceImpl;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubTableDataInjector 单元测试
 * 
 * 测试场景：
 * 1. 测试 3 行子表数据正确注入为集合变量
 * 2. 测试子表数据为空时抛出 WorkflowValidationException
 * 3. 测试 assigneeField 为空时抛出 WorkflowValidationException 并包含行号信息
 * 4. 测试子表不存在时抛出 WorkflowBusinessException
 * 
 * 需求: 3.4, 3.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubTableDataInjector 单元测试")
class SubTableDataInjectorTest {
    
    @Mock
    private RuntimeService runtimeService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;

    /** Real i18n service (English) backed by the production message bundle. */
    @Spy
    private I18nService i18nService = buildRealI18nService();
    
    @InjectMocks
    private SubTableDataInjector injector;

    private static I18nService buildRealI18nService() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        return new I18nServiceImpl(messageSource);
    }
    
    private static final String PROCESS_INSTANCE_ID = "proc-001";
    private static final String SUB_TABLE_NAME = "fu_participants";
    private static final String FOREIGN_KEY_FIELD = "main_record_id";
    private static final Long MAIN_RECORD_ID = 1001L;
    private static final String ASSIGNEE_FIELD = "assignee_id";
    private static final String COLLECTION_VARIABLE_NAME = "multiInstance_fu_participants_collection";
    
    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        lenient().when(jdbcTemplate.query(
                contains("constraint_type"),
                any(RowMapper.class),
                eq(SUB_TABLE_NAME)))
                .thenReturn(List.of("id"));
    }
    
    @Test
    @DisplayName("正常场景 - 3 行子表数据正确注入为集合变量")
    void testInjectSubTableData_Success_ThreeRows() {
        // Given: 准备 3 行子表数据
        List<Map<String, Object>> subTableRows = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 101L);
        row1.put(ASSIGNEE_FIELD, "user-001");
        row1.put("row_version", 1L);
        subTableRows.add(row1);
        
        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 102L);
        row2.put(ASSIGNEE_FIELD, "user-002");
        row2.put("row_version", 1L);
        subTableRows.add(row2);
        
        Map<String, Object> row3 = new HashMap<>();
        row3.put("id", 103L);
        row3.put(ASSIGNEE_FIELD, "user-003");
        row3.put("row_version", 2L);
        subTableRows.add(row3);
        
        // 模拟数据库查询返回 3 行数据
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenReturn(subTableRows);
        
        // When: 执行注入
        injector.injectSubTableData(
            PROCESS_INSTANCE_ID,
            SUB_TABLE_NAME,
            FOREIGN_KEY_FIELD,
            MAIN_RECORD_ID,
            ASSIGNEE_FIELD,
            COLLECTION_VARIABLE_NAME
        );
        
        // Then: 验证集合变量被正确注入
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(runtimeService).setVariable(
            eq(PROCESS_INSTANCE_ID),
            eq(COLLECTION_VARIABLE_NAME),
            captor.capture()
        );
        
        List<Map<String, Object>> collectionVariable = captor.getValue();
        assertNotNull(collectionVariable);
        assertEquals(3, collectionVariable.size());
        
        // 验证第 1 个元素
        Map<String, Object> element1 = collectionVariable.get(0);
        assertEquals(101L, element1.get("rowId"));
        assertEquals("user-001", element1.get("assigneeId"));
        assertEquals(1L, element1.get("rowVersion"));
        
        // 验证第 2 个元素
        Map<String, Object> element2 = collectionVariable.get(1);
        assertEquals(102L, element2.get("rowId"));
        assertEquals("user-002", element2.get("assigneeId"));
        assertEquals(1L, element2.get("rowVersion"));
        
        // 验证第 3 个元素
        Map<String, Object> element3 = collectionVariable.get(2);
        assertEquals(103L, element3.get("rowId"));
        assertEquals("user-003", element3.get("assigneeId"));
        assertEquals(2L, element3.get("rowVersion"));
        
        // 验证数据库查询被调用
        verify(jdbcTemplate).queryForList(
            contains(SUB_TABLE_NAME),
            eq(MAIN_RECORD_ID)
        );
    }
    
    @Test
    @DisplayName("边界条件 - 子表数据为空时抛出 WorkflowValidationException")
    void testInjectSubTableData_EmptyData_ThrowsException() {
        // Given: 模拟数据库查询返回空列表
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenReturn(new ArrayList<>());
        
        // When & Then: 执行注入应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> injector.injectSubTableData(
                PROCESS_INSTANCE_ID,
                SUB_TABLE_NAME,
                FOREIGN_KEY_FIELD,
                MAIN_RECORD_ID,
                ASSIGNEE_FIELD,
                COLLECTION_VARIABLE_NAME
            )
        );
        
        // 验证异常信息
        assertTrue(exception.getMessage().contains("Multi-instance data source is empty"));
        assertTrue(exception.getMessage().contains("at least one sub-table data row is required"));
        
        // 验证 runtimeService.setVariable 没有被调用
        verify(runtimeService, never()).setVariable(anyString(), anyString(), any());
    }
    
    @Test
    @DisplayName("边界条件 - assigneeField 为空时抛出 WorkflowValidationException 并包含行号信息")
    void testInjectSubTableData_EmptyAssigneeField_ThrowsExceptionWithRowNumbers() {
        // Given: 准备 5 行数据，其中第 2 行和第 4 行的 assigneeField 为空
        List<Map<String, Object>> subTableRows = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 101L);
        row1.put(ASSIGNEE_FIELD, "user-001");
        row1.put("row_version", 1L);
        subTableRows.add(row1);
        
        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 102L);
        row2.put(ASSIGNEE_FIELD, null); // 第 2 行为空
        row2.put("row_version", 1L);
        subTableRows.add(row2);
        
        Map<String, Object> row3 = new HashMap<>();
        row3.put("id", 103L);
        row3.put(ASSIGNEE_FIELD, "user-003");
        row3.put("row_version", 1L);
        subTableRows.add(row3);
        
        Map<String, Object> row4 = new HashMap<>();
        row4.put("id", 104L);
        row4.put(ASSIGNEE_FIELD, ""); // 第 4 行为空字符串
        row4.put("row_version", 1L);
        subTableRows.add(row4);
        
        Map<String, Object> row5 = new HashMap<>();
        row5.put("id", 105L);
        row5.put(ASSIGNEE_FIELD, "user-005");
        row5.put("row_version", 1L);
        subTableRows.add(row5);
        
        // 模拟数据库查询返回数据
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenReturn(subTableRows);
        
        // When & Then: 执行注入应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> injector.injectSubTableData(
                PROCESS_INSTANCE_ID,
                SUB_TABLE_NAME,
                FOREIGN_KEY_FIELD,
                MAIN_RECORD_ID,
                ASSIGNEE_FIELD,
                COLLECTION_VARIABLE_NAME
            )
        );
        
        // 验证异常信息包含行号
        String message = exception.getMessage();
        assertTrue(message.contains("Row(s) 2, 4 missing assignee"));
        assertTrue(message.contains(ASSIGNEE_FIELD));
        
        // 验证 runtimeService.setVariable 没有被调用
        verify(runtimeService, never()).setVariable(anyString(), anyString(), any());
    }
    
    @Test
    @DisplayName("边界条件 - 单行 assigneeField 为空时抛出异常")
    void testInjectSubTableData_SingleEmptyAssigneeField_ThrowsException() {
        // Given: 准备 1 行数据，assigneeField 为空
        List<Map<String, Object>> subTableRows = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 101L);
        row1.put(ASSIGNEE_FIELD, null);
        row1.put("row_version", 1L);
        subTableRows.add(row1);
        
        // 模拟数据库查询返回数据
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenReturn(subTableRows);
        
        // When & Then: 执行注入应该抛出异常
        WorkflowValidationException exception = assertThrows(
            WorkflowValidationException.class,
            () -> injector.injectSubTableData(
                PROCESS_INSTANCE_ID,
                SUB_TABLE_NAME,
                FOREIGN_KEY_FIELD,
                MAIN_RECORD_ID,
                ASSIGNEE_FIELD,
                COLLECTION_VARIABLE_NAME
            )
        );
        
        // 验证异常信息包含行号
        assertTrue(exception.getMessage().contains("Row(s) 1 missing assignee"));
    }
    
    @Test
    @DisplayName("边界条件 - 子表不存在时抛出 WorkflowBusinessException")
    void testInjectSubTableData_TableNotExists_ThrowsException() {
        // Given: 模拟数据库查询抛出表不存在异常
        DataAccessException dbException = mock(DataAccessException.class);
        when(dbException.getMessage()).thenReturn("relation \"fu_participants\" does not exist");
        
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenThrow(dbException);
        
        // When & Then: 执行注入应该抛出异常
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> injector.injectSubTableData(
                PROCESS_INSTANCE_ID,
                SUB_TABLE_NAME,
                FOREIGN_KEY_FIELD,
                MAIN_RECORD_ID,
                ASSIGNEE_FIELD,
                COLLECTION_VARIABLE_NAME
            )
        );
        
        // 验证异常信息
        assertEquals("SUBTABLE_NOT_FOUND", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Associated sub-table"));
        assertTrue(exception.getMessage().contains(SUB_TABLE_NAME));
        assertTrue(exception.getMessage().contains("does not exist"));
        
        // 验证 runtimeService.setVariable 没有被调用
        verify(runtimeService, never()).setVariable(anyString(), anyString(), any());
    }
    
    @Test
    @DisplayName("边界条件 - 数据库查询失败时抛出 WorkflowBusinessException")
    void testInjectSubTableData_QueryFailed_ThrowsException() {
        // Given: 模拟数据库查询抛出其他异常
        DataAccessException dbException = mock(DataAccessException.class);
        when(dbException.getMessage()).thenReturn("Connection timeout");
        
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenThrow(dbException);
        
        // When & Then: 执行注入应该抛出异常
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> injector.injectSubTableData(
                PROCESS_INSTANCE_ID,
                SUB_TABLE_NAME,
                FOREIGN_KEY_FIELD,
                MAIN_RECORD_ID,
                ASSIGNEE_FIELD,
                COLLECTION_VARIABLE_NAME
            )
        );
        
        // 验证异常信息
        assertEquals("SUBTABLE_QUERY_FAILED", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Error querying sub-table data"));
        
        // 验证 runtimeService.setVariable 没有被调用
        verify(runtimeService, never()).setVariable(anyString(), anyString(), any());
    }
    
    @Test
    @DisplayName("正常场景 - 使用默认集合变量名")
    void testInjectSubTableData_DefaultCollectionVariableName() {
        // Given: 准备 1 行子表数据
        List<Map<String, Object>> subTableRows = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 101L);
        row1.put(ASSIGNEE_FIELD, "user-001");
        row1.put("row_version", 1L);
        subTableRows.add(row1);
        
        // 模拟数据库查询返回数据
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenReturn(subTableRows);
        
        // When: 执行注入（不指定 collectionVariableName）
        injector.injectSubTableData(
            PROCESS_INSTANCE_ID,
            SUB_TABLE_NAME,
            FOREIGN_KEY_FIELD,
            MAIN_RECORD_ID,
            ASSIGNEE_FIELD,
            null // 使用默认变量名
        );
        
        // Then: 验证使用默认变量名
        String expectedVariableName = "multiInstance_" + SUB_TABLE_NAME + "_collection";
        verify(runtimeService).setVariable(
            eq(PROCESS_INSTANCE_ID),
            eq(expectedVariableName),
            any(List.class)
        );
    }
    
    @Test
    @DisplayName("边界条件 - row_version 为 null 时使用默认值 1")
    void testInjectSubTableData_NullRowVersion_UsesDefaultValue() {
        // Given: 准备 1 行数据，row_version 为 null
        List<Map<String, Object>> subTableRows = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 101L);
        row1.put(ASSIGNEE_FIELD, "user-001");
        row1.put("row_version", null); // row_version 为 null
        subTableRows.add(row1);
        
        // 模拟数据库查询返回数据
        when(jdbcTemplate.queryForList(anyString(), eq(MAIN_RECORD_ID)))
            .thenReturn(subTableRows);
        
        // When: 执行注入
        injector.injectSubTableData(
            PROCESS_INSTANCE_ID,
            SUB_TABLE_NAME,
            FOREIGN_KEY_FIELD,
            MAIN_RECORD_ID,
            ASSIGNEE_FIELD,
            COLLECTION_VARIABLE_NAME
        );
        
        // Then: 验证 row_version 使用默认值 1
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(runtimeService).setVariable(
            eq(PROCESS_INSTANCE_ID),
            eq(COLLECTION_VARIABLE_NAME),
            captor.capture()
        );
        
        List<Map<String, Object>> collectionVariable = captor.getValue();
        assertEquals(1, collectionVariable.size());
        assertEquals(1L, collectionVariable.get(0).get("rowVersion"));
    }
}
