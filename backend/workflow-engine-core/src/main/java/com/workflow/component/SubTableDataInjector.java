package com.workflow.component;

import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子表数据注入器
 *
 * 集合变量元素含 {@code rowKey}（完整主键，支持联合主键）；单列数值主键时额外保留 {@code rowId} 兼容旧逻辑。
 */
@Slf4j
@Component
public class SubTableDataInjector {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void injectSubTableData(
            String processInstanceId,
            String subTableName,
            String foreignKeyField,
            Long mainRecordId,
            String assigneeField,
            String collectionVariableName) {

        log.info("开始注入子表数据: processInstanceId={}, subTableName={}, mainRecordId={}",
                processInstanceId, subTableName, mainRecordId);

        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, subTableName);

        List<Map<String, Object>> subTableRows = querySubTableData(
                subTableName, foreignKeyField, mainRecordId, assigneeField, pkCols);

        if (subTableRows.isEmpty()) {
            throw new WorkflowValidationException(
                    "多实例数据源为空，至少需要一条子表数据"
            );
        }

        log.debug("查询到 {} 条子表数据行", subTableRows.size());

        List<Map<String, Object>> collectionVariable = buildCollectionVariable(
                subTableRows, assigneeField, pkCols);

        String variableName = collectionVariableName != null
                ? collectionVariableName
                : String.format("multiInstance_%s_collection", subTableName);

        runtimeService.setVariable(processInstanceId, variableName, collectionVariable);

        log.info("子表数据注入成功: processInstanceId={}, variableName={}, rowCount={}",
                processInstanceId, variableName, collectionVariable.size());
    }

    private List<Map<String, Object>> querySubTableData(
            String subTableName,
            String foreignKeyField,
            Long mainRecordId,
            String assigneeField,
            List<String> pkCols) {

        try {
            String pkSelect = PostgresPhysicalTablePrimaryKeys.commaJoinedPkSelect(pkCols);
            String sql = String.format(
                    "SELECT %s, %s, row_version FROM %s WHERE %s = ?",
                    pkSelect,
                    assigneeField,
                    subTableName,
                    foreignKeyField
            );

            log.debug("执行子表查询: sql={}, mainRecordId={}", sql, mainRecordId);

            return jdbcTemplate.queryForList(sql, mainRecordId);
        } catch (Exception e) {
            log.error("查询子表数据失败: subTableName={}, mainRecordId={}",
                    subTableName, mainRecordId, e);

            if (e.getMessage() != null
                    && (e.getMessage().contains("does not exist")
                    || e.getMessage().contains("doesn't exist")
                    || e.getMessage().contains("relation") && e.getMessage().contains("does not exist"))) {
                throw new WorkflowBusinessException(
                        "SUBTABLE_NOT_FOUND",
                        String.format("关联的子表 %s 不存在", subTableName),
                        e
                );
            }

            throw new WorkflowBusinessException(
                    "SUBTABLE_QUERY_FAILED",
                    String.format("查询子表数据时发生错误: %s", e.getMessage()),
                    e
            );
        }
    }

    private List<Map<String, Object>> buildCollectionVariable(
            List<Map<String, Object>> subTableRows,
            String assigneeField,
            List<String> pkCols) {

        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        List<Integer> emptyAssigneeRows = new ArrayList<>();

        for (int i = 0; i < subTableRows.size(); i++) {
            Map<String, Object> row = subTableRows.get(i);

            Map<String, Object> rowKey = new LinkedHashMap<>();
            for (String col : pkCols) {
                rowKey.put(col, row.get(col));
            }
            if (!SubTableRowKeySupport.isComplete(pkCols, rowKey)) {
                throw new WorkflowValidationException("子表行主键列存在空值，无法构建多实例集合");
            }

            Object assigneeObj = row.get(assigneeField);
            Object rowVersionObj = row.get("row_version");

            if (assigneeObj == null || assigneeObj.toString().trim().isEmpty()) {
                emptyAssigneeRows.add(i + 1);
                continue;
            }

            Map<String, Object> element = new HashMap<>();
            element.put("rowKey", rowKey);
            if (pkCols.size() == 1) {
                Object only = rowKey.get(pkCols.get(0));
                if (only instanceof Number) {
                    element.put("rowId", ((Number) only).longValue());
                }
            }
            element.put("assigneeId", assigneeObj.toString());
            element.put("rowVersion", rowVersionObj != null ? ((Number) rowVersionObj).longValue() : 1L);

            collectionVariable.add(element);

            log.debug("构建集合变量元素: rowKey={}, assigneeId={}, rowVersion={}",
                    rowKey, element.get("assigneeId"), element.get("rowVersion"));
        }

        if (!emptyAssigneeRows.isEmpty()) {
            String rowNumbers = String.join(", ",
                    emptyAssigneeRows.stream().map(String::valueOf).toArray(String[]::new));
            throw new WorkflowValidationException(
                    String.format("第 %s 行缺少处理人（%s 字段为空）", rowNumbers, assigneeField)
            );
        }

        return collectionVariable;
    }
}
