package com.workflow.component;

import com.workflow.dto.request.*;
import com.workflow.dto.response.DataTableOperationResult;
import com.workflow.dto.response.DataTableQueryResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.platform.common.i18n.I18nService;
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
 * Data table management component
 * 
 * Handles CRUD operations with PostgreSQL data tables
 * Supports dynamic SQL generation and execution
 * Provides data validation and type conversion
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataTableManagerComponent {

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;
    
    // Safe table name and field name pattern (prevent SQL injection)
    private static final String SAFE_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";
    
    // Allowed sort directions
    private static final Set<String> ALLOWED_ORDER_DIRECTIONS = Set.of("ASC", "DESC");
    
    // Allowed join types
    private static final Set<String> ALLOWED_JOIN_TYPES = Set.of("INNER", "LEFT", "RIGHT", "FULL");

    // DOS protection: collection size limits
    private static final int MAX_SELECT_FIELDS = 100;
    private static final int MAX_INSERT_COLUMNS = 200;
    private static final int MAX_UPDATE_COLUMNS = 200;
    private static final int MAX_WHERE_CONDITIONS = 50;
    private static final int MAX_JOIN_CONDITIONS = 10;

    /**
     * Query data table records
     * 
     * @param request the query request
     * @return query result
     */
    @Transactional(readOnly = true)
    public DataTableQueryResult queryTable(DataTableQueryRequest request) {
        log.info("Querying data table: tableName={}, conditions={}", request.getTableName(), request.getWhereConditions());
        
        try {
            // Validate request parameters
            validateQueryRequest(request);
            
            // Build query SQL
            SqlBuilder sqlBuilder = buildSelectSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("Execute query SQL: {}, params: {}", sql, Arrays.toString(params));
            
            // Execute query
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, params);
            
            // Query total count (if pagination needed)
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
            // Re-throw validation exception for caller to handle
            throw e;
        } catch (Exception e) {
            log.error("Query data table failed: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableQueryResult.builder()
                    .success(false)
                    .errorMessage(i18nService.getMessage("workflow.dt.query_failed", e.getMessage()))
                    .build();
        }
    }

    /**
     * Insert data table record
     * 
     * @param request the insert request
     * @return operation result
     */
    @Transactional
    public DataTableOperationResult insertRecord(DataTableInsertRequest request) {
        log.info("Inserting data table record: tableName={}, data={}", request.getTableName(), request.getData());
        
        try {
            // Validate request parameters
            validateInsertRequest(request);
            
            // Build insert SQL
            SqlBuilder sqlBuilder = buildInsertSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("Execute insert SQL: {}, params: {}", sql, Arrays.toString(params));
            
            int affectedRows;
            Map<String, Object> generatedKeys = new HashMap<>();
            
            if (request.isReturnGeneratedKeys()) {
                // Need to return generated keys
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
                // No generated keys needed
                affectedRows = jdbcTemplate.update(sql, params);
            }
            
            return DataTableOperationResult.builder()
                    .success(true)
                    .affectedRows(affectedRows)
                    .generatedKeys(generatedKeys)
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // Re-throw validation exception for caller to handle
            throw e;
        } catch (Exception e) {
            log.error("Insert data table record failed: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableOperationResult.builder()
                    .success(false)
                    .errorMessage(i18nService.getMessage("workflow.dt.insert_failed", e.getMessage()))
                    .build();
        }
    }

    /**
     * Update data table record
     * 
     * @param request the update request
     * @return operation result
     */
    @Transactional
    public DataTableOperationResult updateRecord(DataTableUpdateRequest request) {
        log.info("Updating data table record: tableName={}, updateData={}, conditions={}", 
                request.getTableName(), request.getUpdateData(), request.getWhereConditions());
        
        try {
            // Validate request parameters
            validateUpdateRequest(request);
            
            // Build update SQL
            SqlBuilder sqlBuilder = buildUpdateSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("Execute update SQL: {}, params: {}", sql, Arrays.toString(params));
            
            // Execute update
            int affectedRows = jdbcTemplate.update(sql, params);
            
            return DataTableOperationResult.builder()
                    .success(true)
                    .affectedRows(affectedRows)
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // Re-throw validation exception for caller to handle
            throw e;
        } catch (Exception e) {
            log.error("Update data table record failed: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableOperationResult.builder()
                    .success(false)
                    .errorMessage(i18nService.getMessage("workflow.dt.update_failed", e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete data table record
     * 
     * @param request the delete request
     * @return operation result
     */
    @Transactional
    public DataTableOperationResult deleteRecord(DataTableDeleteRequest request) {
        log.info("Deleting data table record: tableName={}, conditions={}", request.getTableName(), request.getWhereConditions());
        
        try {
            // Validate request parameters
            validateDeleteRequest(request);
            
            // Build delete SQL
            SqlBuilder sqlBuilder = buildDeleteSql(request);
            String sql = sqlBuilder.getSql();
            Object[] params = sqlBuilder.getParams().toArray();
            
            log.debug("Execute delete SQL: {}, params: {}", sql, Arrays.toString(params));
            
            // Execute delete
            int affectedRows = jdbcTemplate.update(sql, params);
            
            return DataTableOperationResult.builder()
                    .success(true)
                    .affectedRows(affectedRows)
                    .executedSql(sql)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            // Re-throw validation exception for caller to handle
            throw e;
        } catch (Exception e) {
            log.error("Delete data table record failed: tableName={}, error={}", request.getTableName(), e.getMessage(), e);
            return DataTableOperationResult.builder()
                    .success(false)
                    .errorMessage(i18nService.getMessage("workflow.dt.delete_failed", e.getMessage()))
                    .build();
        }
    }

    /**
     * Validate query request parameters
     */
    private void validateQueryRequest(DataTableQueryRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_empty"), request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_invalid", request.getTableName()), request.getTableName()));
        }
        
        // DOS protection: limit select field count
        if (request.getSelectFields() != null && request.getSelectFields().size() > MAX_SELECT_FIELDS) {
            errors.add(new WorkflowValidationException.ValidationError("selectFields",
                    i18nService.getMessage("workflow.dt.select_fields_exceeded", request.getSelectFields().size(), MAX_SELECT_FIELDS), request.getSelectFields().size()));
        }

        // Validate field names
        if (request.getSelectFields() != null) {
            for (String field : request.getSelectFields()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("selectFields", i18nService.getMessage("workflow.dt.field_name_invalid", field), field));
                }
            }
        }

        // DOS protection: limit WHERE condition count
        if (request.getWhereConditions() != null && request.getWhereConditions().size() > MAX_WHERE_CONDITIONS) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions",
                    i18nService.getMessage("workflow.dt.where_conditions_exceeded", request.getWhereConditions().size(), MAX_WHERE_CONDITIONS), request.getWhereConditions().size()));
        }

        // DOS protection: limit JOIN count
        if (request.getJoinConditions() != null && request.getJoinConditions().size() > MAX_JOIN_CONDITIONS) {
            errors.add(new WorkflowValidationException.ValidationError("joinConditions",
                    i18nService.getMessage("workflow.dt.join_conditions_exceeded", request.getJoinConditions().size(), MAX_JOIN_CONDITIONS), request.getJoinConditions().size()));
        }

        // Validate sort parameters
        if (StringUtils.hasText(request.getOrderBy()) && !isValidName(request.getOrderBy())) {
            errors.add(new WorkflowValidationException.ValidationError("orderBy", i18nService.getMessage("workflow.dt.order_by_invalid", request.getOrderBy()), request.getOrderBy()));
        }
        
        // Validate sort direction
        if (StringUtils.hasText(request.getOrderDirection()) && 
            !ALLOWED_ORDER_DIRECTIONS.contains(request.getOrderDirection().toUpperCase())) {
            errors.add(new WorkflowValidationException.ValidationError("orderDirection", i18nService.getMessage("workflow.dt.order_direction_invalid", request.getOrderDirection()), request.getOrderDirection()));
        }
        
        // Validate pagination parameters
        if (request.getOffset() != null && request.getOffset() < 0) {
            errors.add(new WorkflowValidationException.ValidationError("offset", i18nService.getMessage("workflow.dt.offset_negative"), request.getOffset()));
        }
        
        if (request.getLimit() != null && request.getLimit() <= 0) {
            errors.add(new WorkflowValidationException.ValidationError("limit", i18nService.getMessage("workflow.dt.limit_must_be_positive"), request.getLimit()));
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Validate insert request parameters
     */
    private void validateInsertRequest(DataTableInsertRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_empty"), request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_invalid", request.getTableName()), request.getTableName()));
        }
        
        if (request.getData() == null || request.getData().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("data", i18nService.getMessage("workflow.dt.insert_data_empty"), request.getData()));
        }
        
        // DOS protection: limit insert column count
        if (request.getData() != null && request.getData().size() > MAX_INSERT_COLUMNS) {
            errors.add(new WorkflowValidationException.ValidationError("data",
                    i18nService.getMessage("workflow.dt.insert_columns_exceeded", request.getData().size(), MAX_INSERT_COLUMNS), request.getData().size()));
        }

        // Validate field names
        if (request.getData() != null) {
            for (String field : request.getData().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("data", i18nService.getMessage("workflow.dt.field_name_invalid", field), field));
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Validate update request parameters
     */
    private void validateUpdateRequest(DataTableUpdateRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_empty"), request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_invalid", request.getTableName()), request.getTableName()));
        }
        
        if (request.getUpdateData() == null || request.getUpdateData().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("updateData", i18nService.getMessage("workflow.dt.update_data_empty"), request.getUpdateData()));
        }
        
        if (request.getWhereConditions() == null || request.getWhereConditions().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions", i18nService.getMessage("workflow.dt.update_conditions_empty"), request.getWhereConditions()));
        }
        
        // DOS protection: limit update column count
        if (request.getUpdateData() != null && request.getUpdateData().size() > MAX_UPDATE_COLUMNS) {
            errors.add(new WorkflowValidationException.ValidationError("updateData",
                    i18nService.getMessage("workflow.dt.set_clauses_exceeded", request.getUpdateData().size(), MAX_UPDATE_COLUMNS), request.getUpdateData().size()));
        }

        // DOS protection: limit WHERE condition count
        if (request.getWhereConditions() != null && request.getWhereConditions().size() > MAX_WHERE_CONDITIONS) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions",
                    i18nService.getMessage("workflow.dt.where_conditions_exceeded", request.getWhereConditions().size(), MAX_WHERE_CONDITIONS), request.getWhereConditions().size()));
        }

        // Validate field names
        if (request.getUpdateData() != null) {
            for (String field : request.getUpdateData().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("updateData", i18nService.getMessage("workflow.dt.field_name_invalid", field), field));
                }
            }
        }
        
        if (request.getWhereConditions() != null) {
            for (String field : request.getWhereConditions().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("whereConditions", i18nService.getMessage("workflow.dt.condition_field_invalid", field), field));
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Validate delete request parameters
     */
    private void validateDeleteRequest(DataTableDeleteRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_empty"), request.getTableName()));
        }
        
        if (StringUtils.hasText(request.getTableName()) && !isValidName(request.getTableName())) {
            errors.add(new WorkflowValidationException.ValidationError("tableName", i18nService.getMessage("workflow.dt.table_name_invalid", request.getTableName()), request.getTableName()));
        }
        
        if (request.getWhereConditions() == null || request.getWhereConditions().isEmpty()) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions", i18nService.getMessage("workflow.dt.delete_conditions_empty"), request.getWhereConditions()));
        }
        
        // DOS protection: limit WHERE condition count
        if (request.getWhereConditions() != null && request.getWhereConditions().size() > MAX_WHERE_CONDITIONS) {
            errors.add(new WorkflowValidationException.ValidationError("whereConditions",
                    i18nService.getMessage("workflow.dt.where_conditions_exceeded", request.getWhereConditions().size(), MAX_WHERE_CONDITIONS), request.getWhereConditions().size()));
        }

        // Validate field names
        if (request.getWhereConditions() != null) {
            for (String field : request.getWhereConditions().keySet()) {
                if (!isValidName(field)) {
                    errors.add(new WorkflowValidationException.ValidationError("whereConditions", i18nService.getMessage("workflow.dt.condition_field_invalid", field), field));
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Validate name safety (prevent SQL injection)
     */
    private boolean isValidName(String name) {
        return name != null && name.matches(SAFE_NAME_PATTERN);
    }

    /**
     * Build query SQL
     */
    private SqlBuilder buildSelectSql(DataTableQueryRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        // SELECT clause
        if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
            String fields = request.getSelectFields().stream()
                    .collect(Collectors.joining(", "));
            builder.append("SELECT ").append(fields);
        } else {
            builder.append("SELECT *");
        }
        
        // FROM clause
        builder.append(" FROM ").append(request.getTableName());
        
        // JOIN clause
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
        
        // WHERE clause
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
        
        // ORDER BY clause
        if (StringUtils.hasText(request.getOrderBy())) {
            builder.append(" ORDER BY ").append(request.getOrderBy());
            if (StringUtils.hasText(request.getOrderDirection())) {
                builder.append(" ").append(request.getOrderDirection().toUpperCase());
            }
        }
        
        // LIMIT and OFFSET clause
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
     * Build insert SQL
     */
    private SqlBuilder buildInsertSql(DataTableInsertRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        List<String> fields = new ArrayList<>(request.getData().keySet());
        String fieldList = String.join(", ", fields);
        String placeholders = fields.stream().map(f -> "?").collect(Collectors.joining(", "));
        
        builder.append("INSERT INTO ").append(request.getTableName())
               .append(" (").append(fieldList).append(")")
               .append(" VALUES (").append(placeholders).append(")");
        
        // Add parameters
        for (String field : fields) {
            builder.addParam(request.getData().get(field));
        }
        
        return builder;
    }

    /**
     * Build update SQL
     */
    private SqlBuilder buildUpdateSql(DataTableUpdateRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        builder.append("UPDATE ").append(request.getTableName()).append(" SET ");
        
        // SET clause
        boolean first = true;
        for (Map.Entry<String, Object> entry : request.getUpdateData().entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(" = ?");
            builder.addParam(entry.getValue());
            first = false;
        }
        
        // WHERE clause
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
     * Build delete SQL
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
     * Query total record count
     */
    private Long queryTotalCount(DataTableQueryRequest request) {
        SqlBuilder builder = new SqlBuilder();
        
        builder.append("SELECT COUNT(*) FROM ").append(request.getTableName());
        
        // JOIN clause
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
        
        // WHERE clause
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
     * SQL builder inner class
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