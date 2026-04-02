package com.workflow.component;

import net.jqwik.api.*;
import org.flowable.engine.RuntimeService;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubTableDataInjector 属性测试
 * 
 * Property 5: 子表数据注入正确性
 * 
 * For any 包含 N 条数据行的子表（N > 0，且所有行的 assigneeField 非空），
 * 注入到流程实例的集合变量应包含恰好 N 个元素，每个元素包含正确的 rowId、assigneeId 和 rowVersion，
 * 且变量名符合 `multiInstance_{subTableName}_collection` 格式。
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3**
 */
public class SubTableDataInjectorPropertyTest {
    
    /**
     * Property 5: 子表数据注入正确性
     * 
     * 随机生成 N 条子表数据行（N>0，assigneeField 非空），
     * 验证集合变量包含恰好 N 个元素且结构正确
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 5: 子表数据注入正确性")
    void collectionVariableContainsExactlyNElementsWithCorrectStructure(
            @ForAll("subTableDataScenarios") SubTableDataScenario scenario) {
        
        // Given: 创建 mock 对象
        RuntimeService runtimeService = mock(RuntimeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SubTableDataInjector injector = new SubTableDataInjector();
        
        // 使用反射注入依赖（因为是 @Autowired）
        try {
            java.lang.reflect.Field runtimeServiceField = 
                SubTableDataInjector.class.getDeclaredField("runtimeService");
            runtimeServiceField.setAccessible(true);
            runtimeServiceField.set(injector, runtimeService);
            
            java.lang.reflect.Field jdbcTemplateField = 
                SubTableDataInjector.class.getDeclaredField("jdbcTemplate");
            jdbcTemplateField.setAccessible(true);
            jdbcTemplateField.set(injector, jdbcTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject dependencies", e);
        }
        
        // 模拟数据库查询返回子表数据
        when(jdbcTemplate.queryForList(anyString(), eq(scenario.mainRecordId)))
            .thenReturn(scenario.subTableRows);
        
        // When: 执行注入
        injector.injectSubTableData(
            scenario.processInstanceId,
            scenario.subTableName,
            scenario.foreignKeyField,
            scenario.mainRecordId,
            scenario.assigneeField,
            scenario.collectionVariableName
        );
        
        // Then: 验证集合变量被正确注入
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(runtimeService).setVariable(
            eq(scenario.processInstanceId),
            eq(scenario.expectedVariableName),
            captor.capture()
        );
        
        List<Map<String, Object>> collectionVariable = captor.getValue();
        
        // 验证 1: 集合变量包含恰好 N 个元素
        assertThat(collectionVariable).hasSize(scenario.expectedRowCount);
        
        // 验证 2: 每个元素包含正确的 rowId、assigneeId 和 rowVersion
        for (int i = 0; i < scenario.expectedRowCount; i++) {
            Map<String, Object> element = collectionVariable.get(i);
            Map<String, Object> originalRow = scenario.subTableRows.get(i);
            
            // 验证元素包含必需的键
            assertThat(element).containsKeys("rowId", "assigneeId", "rowVersion");
            
            // 验证 rowId 正确
            Long expectedRowId = ((Number) originalRow.get("id")).longValue();
            assertThat(element.get("rowId")).isEqualTo(expectedRowId);
            
            // 验证 assigneeId 正确
            String expectedAssigneeId = originalRow.get(scenario.assigneeField).toString();
            assertThat(element.get("assigneeId")).isEqualTo(expectedAssigneeId);
            
            // 验证 rowVersion 正确（如果原始数据为 null，应该使用默认值 1）
            Object originalRowVersion = originalRow.get("row_version");
            Long expectedRowVersion = originalRowVersion != null ? 
                ((Number) originalRowVersion).longValue() : 1L;
            assertThat(element.get("rowVersion")).isEqualTo(expectedRowVersion);
        }
        
        // 验证 3: 变量名符合格式
        if (scenario.collectionVariableName == null) {
            // 如果没有指定变量名，应该使用默认格式
            String expectedDefaultName = "multiInstance_" + scenario.subTableName + "_collection";
            assertThat(scenario.expectedVariableName).isEqualTo(expectedDefaultName);
        } else {
            // 如果指定了变量名，应该使用指定的名称
            assertThat(scenario.expectedVariableName).isEqualTo(scenario.collectionVariableName);
        }
    }
    
    /**
     * 子表数据场景
     */
    private static class SubTableDataScenario {
        final String processInstanceId;
        final String subTableName;
        final String foreignKeyField;
        final Long mainRecordId;
        final String assigneeField;
        final String collectionVariableName;
        final List<Map<String, Object>> subTableRows;
        final int expectedRowCount;
        final String expectedVariableName;
        
        SubTableDataScenario(
                String processInstanceId,
                String subTableName,
                String foreignKeyField,
                Long mainRecordId,
                String assigneeField,
                String collectionVariableName,
                int rowCount,
                List<String> assigneeIds,
                List<Long> rowVersions) {
            
            this.processInstanceId = processInstanceId;
            this.subTableName = subTableName;
            this.foreignKeyField = foreignKeyField;
            this.mainRecordId = mainRecordId;
            this.assigneeField = assigneeField;
            this.collectionVariableName = collectionVariableName;
            this.expectedRowCount = rowCount;
            
            // 构建子表数据行
            this.subTableRows = new ArrayList<>();
            for (int i = 0; i < rowCount; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", 100L + i);
                row.put(assigneeField, assigneeIds.get(i));
                row.put("row_version", rowVersions.get(i));
                subTableRows.add(row);
            }
            
            // 确定期望的变量名
            this.expectedVariableName = collectionVariableName != null ? 
                collectionVariableName : 
                "multiInstance_" + subTableName + "_collection";
        }
    }
    
    /**
     * 生成子表数据场景
     */
    @Provide
    Arbitrary<SubTableDataScenario> subTableDataScenarios() {
        // 流程实例 ID
        Arbitrary<String> processInstanceIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "proc-" + s);
        
        // 子表名称
        Arbitrary<String> subTableNames = Arbitraries.of(
            "fu_participants", "fu_approvers", "fu_reviewers", 
            "fu_items", "fu_details", "fu_attachments"
        );
        
        // 外键字段名
        Arbitrary<String> foreignKeyFields = Arbitraries.of(
            "main_record_id", "parent_id", "master_id"
        );
        
        // 主表记录 ID
        Arbitrary<Long> mainRecordIds = Arbitraries.longs().between(1L, 10000L);
        
        // 处理人字段名
        Arbitrary<String> assigneeFields = Arbitraries.of(
            "assignee_id", "approver_id", "reviewer_id", "handler_id", "owner_id"
        );
        
        // 集合变量名（50% 概率为 null，使用默认名称）
        Arbitrary<String> collectionVariableNames = Arbitraries.frequencyOf(
            Tuple.of(1, Arbitraries.just(null)),
            Tuple.of(1, Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(10)
                .ofMaxLength(30)
                .map(s -> "collection_" + s))
        );
        
        // 数据行数（1 到 20 行）
        Arbitrary<Integer> rowCounts = Arbitraries.integers().between(1, 20);
        
        return Combinators.combine(
            processInstanceIds,
            subTableNames,
            foreignKeyFields,
            mainRecordIds,
            assigneeFields,
            collectionVariableNames,
            rowCounts
        ).flatAs((procId, tableName, fkField, mainId, assigneeField, collVarName, rowCount) -> {
            // 为每行生成 assigneeId（非空）
            Arbitrary<List<String>> assigneeIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(10)
                .map(s -> "user-" + s)
                .list()
                .ofSize(rowCount);
            
            // 为每行生成 rowVersion（可能为 null，测试默认值）
            Arbitrary<List<Long>> rowVersions = Arbitraries.frequencyOf(
                Tuple.of(8, Arbitraries.longs().between(1L, 100L)), // 80% 概率有值
                Tuple.of(2, Arbitraries.just(null))                  // 20% 概率为 null
            ).list().ofSize(rowCount);
            
            return Combinators.combine(assigneeIds, rowVersions)
                .as((ids, versions) -> new SubTableDataScenario(
                    procId, tableName, fkField, mainId, assigneeField, 
                    collVarName, rowCount, ids, versions
                ));
        });
    }
}
