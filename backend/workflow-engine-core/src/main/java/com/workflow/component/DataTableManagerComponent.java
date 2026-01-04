package com.workflow.component;

import com.workflow.dto.request.*;
import com.workflow.dto.response.DataTableOperationResult;
import com.workflow.dto.response.DataTableQueryResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据表管理组件
 * 
 * 负责与PostgreSQL数据表的CRUD操作
 * 支持动态SQL生成和执行
 * 提供数据验证和类型转换功能
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataTableManagerComponent {

    private final JdbcTemplate jdbcTemplate;
    
    // 安全的表名和字段名模式（防止SQL注入）
    private static final String SAFE_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";
    
    // 允许的排序方向
    private static final Set<String> ALLOWED_ORDER_DIRECTIONS = Set.of("ASC", "DESC");
    
    // 允许的连接类型
    private static final Set<String> ALLOWED_JOIN_TYPES = Set.of("INNER", "LEFT", "RIGHT", "FULL");

    /**
     * 查询数据表记录
     * 
     * @param request 查询请求
     * @return 查询结果
     */
    @Transactional(readOnly = true)
    public DataTableQueryResult queryTable(DataTableQueryRequest request) {
        log.info("查询数据表: tableName={}, conditions={}", request.getTableName(), request.getWhereConditions());
        
        try {
            // 验证请求参数
            validateQueryRequest(request);
            
            // 构建查询SQL
            SqlBuilder sqlBuilder = buildSelectSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("执行查询SQL: {}, 参数: {}", sql, Arrays.toString(params));
            
            // 执行查询
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, params);
            
            // 查询总数（如果需要分页）
            Long totalCount = null;
            if (request.getLimit() != null) {
                totalCount = queryTotalCount(request);
            }
            
            return DataTableQueryResult.builder()
                    .success(true)
                    .data(data)
                    .totalCount(totalCount)
                    .currentPage(request.getOffset() != null && request.getLimit() != null ? 
                               (request.getOffset() / request.getLimit()) + 1 : null)
                    .pageSize(request.getLimit())
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // 重新抛出验证异常，让调用者处理
            throw e;
        } catch (Exception e) {
            log.error("查询数据表失败: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableQueryResult.builder()
                    .success(false)
                    .errorMessage("查询失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 插入数据表记录
     * 
     * @param request 插入请求
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult insertRecord(DataTableInsertRequest request) {
        log.info("插入数据表记录: tableName={}, data={}", request.getTableName(), request.getData());
        
        try {
            // 验证请求参数
            validateInsertRequest(request);
            
            // 构建插入SQL
            SqlBuilder sqlBuilder = buildInsertSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("执行插入SQL: {}, 参数: {}", sql, Arrays.toString(params));
            
            int affectedRows;
            Map<String, Object> generatedKeys = new HashMap<>();
            
            if (request.isReturnGeneratedKeys()) {
                // 需要返回生成的主键
                KeyHolder keyHolder = new GeneratedKeyHolder();
                affectedRows = jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < params.length; i++) {
                        ps.setObject(i + 1, params[i]);
                    }
                    return ps;
                }, keyHolder);
                
                if (keyHolder.getKeys() != null) {
                    generatedKeys.putAll(keyHolder.getKeys());
                }
            } else {
                // 不需要返回生成的主键
                affectedRows = jdbcTemplate.update(sql, params);
            }
            
            return DataTableOperationResult.builder()
                    .success(true)
                    .affectedRows(affectedRows)
                    .generatedKeys(generatedKeys)
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // 重新抛出验证异常，让调用者处理
            throw e;
        } catch (Exception e) {
            log.error("插入数据表记录失败: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableOperationResult.builder()
                    .success(false)
                    .errorMessage("插入失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 更新数据表记录
     * 
     * @param request 更新请求
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult updateRecord(DataTableUpdateRequest request) {
        log.info("更新数据表记录: tableName={}, updateData={}, conditions={}", 
                request.getTableName(), request.getUpdateData(), request.getWhereConditions());
        
        try {
            // 验证请求参数
            validateUpdateRequest(request);
            
            // 构建更新SQL
            SqlBuilder sqlBuilder = buildUpdateSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("执行更新SQL: {}, 参数: {}", sql, Arrays.toString(params));
            
            // 执行更新
            int affectedRows = jdbcTemplate.update(sql, params);
            
            return DataTableOperationResult.builder()
                    .success(true)
                    .affectedRows(affectedRows)
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // 重新抛出验证异常，让调用者处理
            throw e;
        } catch (Exception e) {
            log.error("更新数据表记录失败: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableOperationResult.builder()
                    .success(false)
                    .errorMessage("更新失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 删除数据表记录
     * 
     * @param request 删除请求
     * @return 操作结果
     */
    @Transactional
    public DataTableOperationResult deleteRecord(DataTableDeleteRequest request) {
        log.info("删除数据表记录: tableName={}, conditions={}", request.getTableName(), request.getWhereConditions());
        
        try {
            // 验证请求参数
            validateDeleteRequest(request);
            
            // 构建删除SQL
            SqlBuilder sqlBuilder = buildDeleteSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("执行删除SQL: {}, 参数: {}", sql, Arrays.toString(params));
            
            // 执行删除
            int affectedRows = jdbcTemplate.update(sql, params);
            
            return DataTableOperationResult.builder()
                    .success(true)
                    .affectedRows(affectedRows)
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // 重新抛出验证异常，让调用者处理
            throw e;
        } catch (Exception e) {
            log.error("删除数据表记录失败: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableOperationResult.builder()
                    .success(false)
                    .errorMessage("删除失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 验证查询请求参数
     */
    private void validateQueryRequest(DataTableQueryRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名不能为空", request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名格式不正确: " + request.getTableName(), request.getTableName()));
        }
        
        // 验证字段名
        if (request.getSelectFields() != null) {
            for (String field : request.getSelectFields()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("selectFields", "字段名格式不正确: " + field, field));
                }
            }
        }
        
        // 验证排序参数
        if (StringUtils.hasText(request.getOrderBy()) && !isValidName(request.getOrderBy())) {
            errors.add(new WorkflowValidationException.ValidationError("orderBy", "排序字段名格式不正确: " + request.getOrderBy(), request.getOrderBy()));
        }
        
        // 验证排序方向
        if (StringUtils.hasText(request.getOrderDirection()) && 
            !ALLOWED_ORDER_DIRECTIONS.contains(request.getOrderDirection().toUpperCase())) {
            errors.add(new WorkflowValidationException.ValidationError("orderDirection", "排序方向不正确: " + request.getOrderDirection(), request.getOrderDirection()));
        }
        
        // 验证分页参数
        if (request.getOffset() != null && request.getOffset() < 0) {
            errors.add(new WorkflowValidationException.ValidationError("offset", "偏移量不能为负数", request.getOffset()));
        }
        
        if (request.getLimit() != null && request.getLimit() <= 0) {
            errors.add(new WorkflowValidationException.ValidationError("limit", "分页大小必须大于0", request.getLimit()));
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * 验证插入请求参数
     */
    private void validateInsertRequest(DataTableInsertRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名不能为空", request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名格式不正确: " + request.getTableName(), request.getTableName()));
        }
        
        if (request.getData() == null || request.getData().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("data", "插入数据不能为空", request.getData()));
        }
        
        // 验证字段名
        if (request.getData() != null) {
            for (String field : request.getData().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("data", "字段名格式不正确: " + field, field));
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * 验证更新请求参数
     */
    private void validateUpdateRequest(DataTableUpdateRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名不能为空", request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名格式不正确: " + request.getTableName(), request.getTableName()));
        }
        
        if (request.getUpdateData() == null || request.getUpdateData().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("updateData", "更新数据不能为空", request.getUpdateData()));
        }
        
        if (request.getWhereConditions() == null || request.getWhereConditions().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions", "更新条件不能为空", request.getWhereConditions()));
        }
        
        // 验证字段名
        if (request.getUpdateData() != null) {
            for (String field : request.getUpdateData().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("updateData", "字段名格式不正确: " + field, field));
                }
            }
        }
        
        if (request.getWhereConditions() != null) {
            for (String field : request.getWhereConditions().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("whereConditions", "条件字段名格式不正确: " + field, field));
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * 验证删除请求参数
     */
    private void validateDeleteRequest(DataTableDeleteRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名不能为空", request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", "表名格式不正确: " + request.getTableName(), request.getTableName()));
        }
        
        if (request.getWhereConditions() == null || request.getWhereConditions().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions", "删除条件不能为空", request.getWhereConditions()));
        }
        
        // 验证字段名
        if (request.getWhereConditions() != null) {
            for (String field : request.getWhereConditions().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("whereConditions", "条件字段名格式不正确: " + field, field));
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * 验证名称是否安全（防止SQL注入）
     */
    private boolean isValidName(String name) {
        return name != null && name.matches(SAFE_NAME_PATTERN);
    }

    /**
     * 构建查询SQL
     */
    private SqlBuilder buildSelectSql(DataTableQueryRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        // SELECT 子句
        if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
            String fields = request.getSelectFields().stream()
                    .collect(Collectors.joining(", "));
            builder.append("SELECT ").append(fields);
        } else {
            builder.append("SELECT *");
        }
        
        // FROM 子句
        builder.append(" FROM ").append(request.getTableName());
        
        // JOIN 子句
        if (request.getJoinConditions() != null && !request.getJoinConditions().isEmpty()) {
            for (DataTableQueryRequest.JoinCondition join : request.getJoinConditions()) {
                if (ALLOWED_JOIN_TYPES.contains(join.getJoinType().toUpperCase()) &&
                    isValidName(join.getJoinTable()) &&
                    StringUtils.hasText(join.getOnCondition())) {
                    
                    builder.append(" ").append(join.getJoinType().toUpperCase())
                           .append(" JOIN ").append(join.getJoinTable())
                           .append(" ON ").append(join.getOnCondition());
                }
            }
        }
        
        // WHERE 子句
        if (request.getWhereConditions() != null && !request.getWhereConditions().isEmpty()) {
            builder.append(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : request.getWhereConditions().entrySet()) {
                if (!first) {
                    builder.append(" AND ");
                }
                builder.append(entry.getKey()).append(" = ?");
                builder.addParam(entry.getValue());
                first = false;
            }
        }
        
        // ORDER BY 子句
        if (StringUtils.hasText(request.getOrderBy())) {
            builder.append(" ORDER BY ").append(request.getOrderBy());
            if (StringUtils.hasText(request.getOrderDirection())) {
                builder.append(" ").append(request.getOrderDirection().toUpperCase());
            }
        }
        
        // LIMIT 和 OFFSET 子句
        if (request.getLimit() != null) {
            builder.append(" LIMIT ?");
            builder.addParam(request.getLimit());
            
            if (request.getOffset() != null) {
                builder.append(" OFFSET ?");
                builder.addParam(request.getOffset());
            }
        }
        
        return builder;
    }

    /**
     * 构建插入SQL
     */
    private SqlBuilder buildInsertSql(DataTableInsertRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        List<String> fields = new ArrayList<>(request.getData().keySet());
        String fieldList = String.join(", ", fields);
        String placeholders = fields.stream().map(f -> "?").collect(Collectors.joining(", "));
        
        builder.append("INSERT INTO ").append(request.getTableName())
               .append(" (").append(fieldList).append(")")
               .append(" VALUES (").append(placeholders).append(")");
        
        // 添加参数
        for (String field : fields) {
            builder.addParam(request.getData().get(field));
        }
        
        return builder;
    }

    /**
     * 构建更新SQL
     */
    private SqlBuilder buildUpdateSql(DataTableUpdateRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        builder.append("UPDATE ").append(request.getTableName()).append(" SET ");
        
        // SET 子句
        boolean first = true;
        for (Map.Entry<String, Object> entry : request.getUpdateData().entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(" = ?");
            builder.addParam(entry.getValue());
            first = false;
        }
        
        // WHERE 子句
        builder.append(" WHERE ");
        first = true;
        for (Map.Entry<String, Object> entry : request.getWhereConditions().entrySet()) {
            if (!first) {
                builder.append(" AND ");
            }
            builder.append(entry.getKey()).append(" = ?");
            builder.addParam(entry.getValue());
            first = false;
        }
        
        return builder;
    }

    /**
     * 构建删除SQL
     */
    private SqlBuilder buildDeleteSql(DataTableDeleteRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        builder.append("DELETE FROM ").append(request.getTableName()).append(" WHERE ");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : request.getWhereConditions().entrySet()) {
            if (!first) {
                builder.append(" AND ");
            }
            builder.append(entry.getKey()).append(" = ?");
            builder.addParam(entry.getValue());
            first = false;
        }
        
        return builder;
    }

    /**
     * 查询总记录数
     */
    private Long queryTotalCount(DataTableQueryRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        builder.append("SELECT COUNT(*) FROM ").append(request.getTableName());
        
        // JOIN 子句
        if (request.getJoinConditions() != null && !request.getJoinConditions().isEmpty()) {
            for (DataTableQueryRequest.JoinCondition join : request.getJoinConditions()) {
                if (ALLOWED_JOIN_TYPES.contains(join.getJoinType().toUpperCase()) &&
                    isValidName(join.getJoinTable()) &&
                    StringUtils.hasText(join.getOnCondition())) {
                    
                    builder.append(" ").append(join.getJoinType().toUpperCase())
                           .append(" JOIN ").append(join.getJoinTable())
                           .append(" ON ").append(join.getOnCondition());
                }
            }
        }
        
        // WHERE 子句
        if (request.getWhereConditions() != null && !request.getWhereConditions().isEmpty()) {
            builder.append(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : request.getWhereConditions().entrySet()) {
                if (!first) {
                    builder.append(" AND ");
                }
                builder.append(entry.getKey()).append(" = ?");
                builder.addParam(entry.getValue());
                first = false;
            }
        }
        
        Object[] params = builder.getParams().toArray();
        return jdbcTemplate.queryForObject(builder.getSql(), params, Long.class);
    }

    /**
     * SQL构建器内部类
     */
    private static class SqlBuilder {
        private final StringBuilder sql = new StringBuilder();
        private final List<Object> params = new ArrayList<>();
        
        public SqlBuilder append(String text) {
            sql.append(text);
            return this;
        }
        
        public SqlBuilder addParam(Object param) {
            params.add(param);
            return this;
        }
        
        public String getSql() {
            return sql.toString();
        }
        
        public List<Object> getParams() {
            return params;
        }
    }
}