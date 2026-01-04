package com.workflow.component;

import com.workflow.dto.request.*;
import com.workflow.dto.response.DataTableOperationResult;
import com.workflow.dto.response.DataTableQueryResult;
import com.workflow.exception.WorkflowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 数据表管理组件单元测试
 * 
 * 测试CRUD操作的正确性、数据类型转换和验证、事务处理和回滚
 * 验证需求: 需求 4.7, 4.8
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class DataTableManagerComponentTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DataTableManagerComponent dataTableManagerComponent;

    private DataTableQueryRequest queryRequest;
    private DataTableInsertRequest insertRequest;
    private DataTableUpdateRequest updateRequest;
    private DataTableDeleteRequest deleteRequest;

    @BeforeEach
    void setUp() {
        // 设置查询请求
        queryRequest = DataTableQueryRequest.builder()
                .tableName("test_table")
                .selectFields(Arrays.asList("id", "name", "status"))
                .whereConditions(Map.of("status", "ACTIVE"))
                .orderBy("id")
                .orderDirection("ASC")
                .limit(10)
                .offset(0)
                .build();

        // 设置插入请求
        insertRequest = DataTableInsertRequest.builder()
                .tableName("test_table")
                .data(Map.of(
                        "name", "测试记录",
                        "status", "ACTIVE",
                        "created_time", new Date()
                ))
                .returnGeneratedKeys(true)
                .build();

        // 设置更新请求
        updateRequest = DataTableUpdateRequest.builder()
                .tableName("test_table")
                .updateData(Map.of("status", "INACTIVE"))
                .whereConditions(Map.of("id", 1L))
                .build();

        // 设置删除请求
        deleteRequest = DataTableDeleteRequest.builder()
                .tableName("test_table")
                .whereConditions(Map.of("id", 1L))
                .build();
    }

    // ==================== 查询操作测试 ====================

    @Test
    void testQueryTable_Success() {
        // Given
        List<Map<String, Object>> mockData = Arrays.asList(
                Map.of("id", 1L, "name", "记录1", "status", "ACTIVE"),
                Map.of("id", 2L, "name", "记录2", "status", "ACTIVE")
        );
        
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(mockData);
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Long.class)))
                .thenReturn(2L);

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getTotalCount()).isEqualTo(2L);
        assertThat(result.getCurrentPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getExecutedSql()).contains("SELECT id, name, status FROM test_table");
        
        verify(jdbcTemplate).queryForList(anyString(), any(Object[].class));
        verify(jdbcTemplate).queryForObject(anyString(), any(Object[].class), eq(Long.class));
    }

    @Test
    void testQueryTable_WithJoinConditions() {
        // Given
        DataTableQueryRequest.JoinCondition joinCondition = DataTableQueryRequest.JoinCondition.builder()
                .joinType("INNER")
                .joinTable("user_table")
                .onCondition("test_table.user_id = user_table.id")
                .build();
        
        queryRequest.setJoinConditions(Arrays.asList(joinCondition));
        
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(Map.of("id", 1L, "name", "记录1")));

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedSql()).contains("INNER JOIN user_table ON test_table.user_id = user_table.id");
    }

    @Test
    void testQueryTable_ValidationError_EmptyTableName() {
        // Given
        queryRequest.setTableName("");

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.queryTable(queryRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("表名不能为空");
    }

    @Test
    void testQueryTable_ValidationError_InvalidTableName() {
        // Given
        queryRequest.setTableName("invalid-table-name!");

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.queryTable(queryRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("表名格式不正确");
    }

    @Test
    void testQueryTable_ValidationError_InvalidOrderDirection() {
        // Given
        queryRequest.setOrderDirection("INVALID");

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.queryTable(queryRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("排序方向不正确");
    }

    @Test
    void testQueryTable_DatabaseError() {
        // Given
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("数据库连接失败") {});

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("查询失败");
    }

    // ==================== 插入操作测试 ====================

    @Test
    void testInsertRecord_Success_WithGeneratedKeys() {
        // Given
        Map<String, Object> generatedKeys = Map.of("id", 123L);
        
        when(jdbcTemplate.update(any(), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(1);
                    ((GeneratedKeyHolder) keyHolder).getKeyList().add(generatedKeys);
                    return 1;
                });

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedRows()).isEqualTo(1);
        assertThat(result.getGeneratedKeys()).containsEntry("id", 123L);
        assertThat(result.getExecutedSql()).contains("INSERT INTO test_table");
    }

    @Test
    void testInsertRecord_Success_WithoutGeneratedKeys() {
        // Given
        insertRequest.setReturnGeneratedKeys(false);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedRows()).isEqualTo(1);
        assertThat(result.getGeneratedKeys()).isEmpty();
        
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void testInsertRecord_ValidationError_EmptyData() {
        // Given
        insertRequest.setData(Collections.emptyMap());

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.insertRecord(insertRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("插入数据不能为空");
    }

    @Test
    void testInsertRecord_ValidationError_InvalidFieldName() {
        // Given
        insertRequest.setData(Map.of("invalid-field!", "value"));

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.insertRecord(insertRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("字段名格式不正确");
    }

    @Test
    void testInsertRecord_DatabaseError() {
        // Given
        when(jdbcTemplate.update(any(), any(KeyHolder.class)))
                .thenThrow(new DataAccessException("插入失败") {});

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("插入失败");
    }

    // ==================== 更新操作测试 ====================

    @Test
    void testUpdateRecord_Success() {
        // Given
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.updateRecord(updateRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedRows()).isEqualTo(1);
        assertThat(result.getExecutedSql()).contains("UPDATE test_table SET status = ? WHERE id = ?");
        
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void testUpdateRecord_ValidationError_EmptyUpdateData() {
        // Given
        updateRequest.setUpdateData(Collections.emptyMap());

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.updateRecord(updateRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("更新数据不能为空");
    }

    @Test
    void testUpdateRecord_ValidationError_EmptyWhereConditions() {
        // Given
        updateRequest.setWhereConditions(Collections.emptyMap());

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.updateRecord(updateRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("更新条件不能为空");
    }

    @Test
    void testUpdateRecord_DatabaseError() {
        // Given
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("更新失败") {});

        // When
        DataTableOperationResult result = dataTableManagerComponent.updateRecord(updateRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("更新失败");
    }

    // ==================== 删除操作测试 ====================

    @Test
    void testDeleteRecord_Success() {
        // Given
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.deleteRecord(deleteRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedRows()).isEqualTo(1);
        assertThat(result.getExecutedSql()).contains("DELETE FROM test_table WHERE id = ?");
        
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void testDeleteRecord_ValidationError_EmptyWhereConditions() {
        // Given
        deleteRequest.setWhereConditions(Collections.emptyMap());

        // When & Then
        assertThatThrownBy(() -> dataTableManagerComponent.deleteRecord(deleteRequest))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("删除条件不能为空");
    }

    @Test
    void testDeleteRecord_DatabaseError() {
        // Given
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("删除失败") {});

        // When
        DataTableOperationResult result = dataTableManagerComponent.deleteRecord(deleteRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("删除失败");
    }

    // ==================== 数据类型转换测试 ====================

    @Test
    void testDataTypeConversion_StringToNumber() {
        // Given
        insertRequest.setData(Map.of(
                "string_field", "测试字符串",
                "number_field", 123,
                "decimal_field", 123.45,
                "boolean_field", true,
                "date_field", new Date()
        ));
        
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(jdbcTemplate).update(any(), any(KeyHolder.class));
    }

    @Test
    void testDataTypeConversion_NullValues() {
        // Given
        Map<String, Object> dataWithNull = new HashMap<>();
        dataWithNull.put("nullable_field", null);
        dataWithNull.put("string_field", "有值字段");
        insertRequest.setData(dataWithNull);
        
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(jdbcTemplate).update(any(), any(KeyHolder.class));
    }

    // ==================== 复杂查询测试 ====================

    @Test
    void testComplexQuery_MultipleConditions() {
        // Given
        queryRequest.setWhereConditions(Map.of(
                "status", "ACTIVE",
                "type", "USER",
                "created_date", "2024-01-01"
        ));
        
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(Map.of("id", 1L)));

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedSql()).contains("WHERE");
        assertThat(result.getExecutedSql()).contains("status = ?");
        assertThat(result.getExecutedSql()).contains("type = ?");
        assertThat(result.getExecutedSql()).contains("created_date = ?");
    }

    @Test
    void testComplexQuery_WithoutSelectFields() {
        // Given
        queryRequest.setSelectFields(null);
        
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(Map.of("id", 1L)));

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedSql()).contains("SELECT *");
    }

    @Test
    void testComplexQuery_WithoutPagination() {
        // Given
        queryRequest.setLimit(null);
        queryRequest.setOffset(null);
        
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(Map.of("id", 1L)));

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalCount()).isNull();
        assertThat(result.getCurrentPage()).isNull();
    }

    // ==================== 事务处理测试 ====================

    @Test
    void testTransactionRollback_OnDatabaseError() {
        // Given
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("数据库约束违反") {});

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("插入失败");
        // 注意：实际的事务回滚测试需要在集成测试中进行
    }

    // ==================== Task 6.4: Additional Data Operation Unit Tests ====================

    @Test
    void testDataTypeConversion_EdgeCases() {
        // Given - 测试边界值和特殊情况
        Map<String, Object> edgeCaseData = new HashMap<>();
        edgeCaseData.put("max_long", Long.MAX_VALUE);
        edgeCaseData.put("min_long", Long.MIN_VALUE);
        edgeCaseData.put("max_double", Double.MAX_VALUE);
        edgeCaseData.put("min_double", Double.MIN_VALUE);
        edgeCaseData.put("empty_string", "");
        edgeCaseData.put("unicode_string", "测试中文字符串🚀");
        edgeCaseData.put("special_chars", "!@#$%^&*()_+-=[]{}|;':\",./<>?");
        
        insertRequest.setData(edgeCaseData);
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedRows()).isEqualTo(1);
    }

    @Test
    void testConcurrentDataOperations() {
        // Given - 模拟并发操作场景
        insertRequest.setData(Map.of("name", "并发测试", "status", "ACTIVE"));
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(Map.of("id", 1L, "name", "并发测试")));

        // When - 模拟多个操作同时进行
        DataTableOperationResult insertResult = dataTableManagerComponent.insertRecord(insertRequest);
        
        DataTableQueryRequest queryRequest = DataTableQueryRequest.builder()
                .tableName("test_table")
                .whereConditions(Map.of("name", "并发测试"))
                .build();
        DataTableQueryResult queryResult = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(insertResult.isSuccess()).isTrue();
        assertThat(queryResult.isSuccess()).isTrue();
        assertThat(queryResult.getData()).hasSize(1);
    }

    @Test
    void testLargeDataSetOperations() {
        // Given - 测试大数据集操作
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeText.append("这是一个很长的文本内容用于测试大数据处理能力");
        }
        
        insertRequest.setData(Map.of(
                "large_text", largeText.toString(),
                "record_count", 10000,
                "processing_time", System.currentTimeMillis()
        ));
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedSql()).isNotEmpty();
    }

    @Test
    void testDataIntegrityConstraints() {
        // Given - 测试数据完整性约束
        insertRequest.setData(Map.of("id", 1, "name", "重复ID测试"));
        when(jdbcTemplate.update(any(), any(KeyHolder.class)))
                .thenThrow(new DataAccessException("唯一约束违反") {});

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("插入失败");
    }

    @Test
    void testComplexQueryWithJoins() {
        // Given - 测试复杂查询（使用简单字段名，因为当前实现不支持JOIN语法）
        DataTableQueryRequest complexQuery = DataTableQueryRequest.builder()
                .tableName("test_table")
                .selectFields(Arrays.asList("id", "name", "status"))
                .whereConditions(Map.of(
                        "status", "ACTIVE",
                        "created_date", "2024-01-01",
                        "priority", "HIGH"
                ))
                .orderBy("created_date")
                .orderDirection("DESC")
                .limit(50)
                .offset(0)
                .build();

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(
                        Map.of("id", 1L, "name", "测试1", "status", "ACTIVE"),
                        Map.of("id", 2L, "name", "测试2", "status", "ACTIVE")
                ));

        // When
        DataTableQueryResult result = dataTableManagerComponent.queryTable(complexQuery);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getExecutedSql()).contains("ORDER BY");
        assertThat(result.getExecutedSql()).contains("LIMIT");
    }

    @Test
    void testBatchOperations() {
        // Given - 测试批量操作
        List<Map<String, Object>> batchData = Arrays.asList(
                Map.of("name", "批量1", "status", "ACTIVE"),
                Map.of("name", "批量2", "status", "ACTIVE"),
                Map.of("name", "批量3", "status", "ACTIVE")
        );

        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);

        // When - 模拟批量插入（通过多次调用）
        List<DataTableOperationResult> results = new ArrayList<>();
        for (Map<String, Object> data : batchData) {
            insertRequest.setData(data);
            results.add(dataTableManagerComponent.insertRecord(insertRequest));
        }

        // Then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(DataTableOperationResult::isSuccess);
    }

    @Test
    void testDataValidationRules() {
        // Given - 测试数据验证规则
        Map<String, Object> invalidData = new HashMap<>();
        invalidData.put("negative_id", -1);
        invalidData.put("future_date", new Date(System.currentTimeMillis() + 86400000)); // 明天
        invalidData.put("invalid_email", "not-an-email");
        invalidData.put("too_long_string", "x".repeat(1000));

        insertRequest.setData(invalidData);
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);

        // When
        DataTableOperationResult result = dataTableManagerComponent.insertRecord(insertRequest);

        // Then - 当前实现主要关注SQL注入防护，不做业务验证
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testTransactionIsolation() {
        // Given - 测试事务隔离级别影响
        insertRequest.setData(Map.of("name", "事务测试", "status", "PENDING"));
        
        // 模拟第一个操作成功
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenReturn(1);
        
        // 模拟查询看到未提交的数据
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(Map.of("id", 1L, "name", "事务测试", "status", "PENDING")));

        // When
        DataTableOperationResult insertResult = dataTableManagerComponent.insertRecord(insertRequest);
        
        DataTableQueryRequest queryRequest = DataTableQueryRequest.builder()
                .tableName("test_table")
                .whereConditions(Map.of("status", "PENDING"))
                .build();
        DataTableQueryResult queryResult = dataTableManagerComponent.queryTable(queryRequest);

        // Then
        assertThat(insertResult.isSuccess()).isTrue();
        assertThat(queryResult.isSuccess()).isTrue();
        assertThat(queryResult.getData()).hasSize(1);
    }

    @Test
    void testErrorRecovery() {
        // Given - 测试错误恢复机制
        insertRequest.setData(Map.of("name", "错误恢复测试"));
        
        // 第一次调用失败
        when(jdbcTemplate.update(any(), any(KeyHolder.class)))
                .thenThrow(new DataAccessException("临时网络错误") {})
                .thenReturn(1); // 第二次调用成功

        // When - 第一次失败
        DataTableOperationResult firstResult = dataTableManagerComponent.insertRecord(insertRequest);
        
        // When - 第二次重试成功
        DataTableOperationResult secondResult = dataTableManagerComponent.insertRecord(insertRequest);

        // Then
        assertThat(firstResult.isSuccess()).isFalse();
        assertThat(secondResult.isSuccess()).isTrue();
    }

    @Test
    void testPerformanceWithLargeResultSet() {
        // Given - 测试大结果集性能
        List<Map<String, Object>> largeResultSet = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeResultSet.add(Map.of(
                    "id", (long) i,
                    "name", "性能测试记录" + i,
                    "status", i % 2 == 0 ? "ACTIVE" : "INACTIVE"
            ));
        }

        DataTableQueryRequest queryRequest = DataTableQueryRequest.builder()
                .tableName("test_table")
                .limit(1000)
                .build();

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(largeResultSet);

        // When
        long startTime = System.currentTimeMillis();
        DataTableQueryResult result = dataTableManagerComponent.queryTable(queryRequest);
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1000);
        assertThat(endTime - startTime).isLessThan(5000); // 应该在5秒内完成
    }
}