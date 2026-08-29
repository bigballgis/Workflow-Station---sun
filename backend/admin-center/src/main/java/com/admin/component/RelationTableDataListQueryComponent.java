package com.admin.component;

import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.RelationTableDataListQueryRequest;

import com.admin.list.ListQuerySupport;
import com.admin.list.RelationTableColumnSpec;
import com.admin.service.RelationTableDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.dto.RelationTableDataRowDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListFilterSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Relation Table Data list: COUNT(*) and the page share {@code table_id}, the toolbar
 * keyword (trgm guard + per-field ILIKE), and column filters. No GROUP BY.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationTableDataListQueryComponent {

    static final String LIST_KEY = "admin-relation-table-data";

    private static final String DATA_ROWS_TABLE = "rt_table_data_rows";
    private static final String FROM = " FROM " + DATA_ROWS_TABLE + " WHERE table_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RelationTableDataService dataService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminListPage<RelationTableDataRowDTO> query(Long tableId,
                                                        RelationTableDataListQueryRequest request) {
        long started = System.nanoTime();
        List<RelationFieldDTO> fields = dataService.loadDeployedFieldsForQuery(tableId);
        List<ListColumnMeta> columns = RelationTableColumnSpec.columnsFor(fields);
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns) {
            byField.put(column.field(), column);
        }
        ListFilterSql filterSql = ListFilterSql.orderedById(byField, ListFilterSql.JSON_ROW);

        List<Object> params = new ArrayList<>();
        params.add(tableId);
        StringBuilder where = new StringBuilder(FROM);
        where.append(buildJsonSearchWhereClause(fields, request.search(), params));
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT row_id, data, status" + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<List<RelationTableDataRowDTO>> extractor = rs -> {
            List<RelationTableDataRowDTO> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> data = parseRowData(rs.getString("data"));
                data.put("status", rs.getString("status"));
                rows.add(RelationTableDataRowDTO.builder()
                        .rowId(rs.getString("row_id"))
                        .tableId(tableId)
                        .data(data)
                        .build());
            }
            return rows;
        };
        List<RelationTableDataRowDTO> rows = ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(columns, rows, request.page(), request.size(), total);
    }

    /**
     * Same trgm-guard + per-field ILIKE as {@code RelationTableDataServiceImpl#queryData}
     * so the toolbar keyword does not change meaning.
     */
    private String buildJsonSearchWhereClause(List<RelationFieldDTO> fields, String search,
                                              List<Object> params) {
        if (search == null || search.isBlank()) {
            return "";
        }
        List<String> searchableFields = fields.stream()
                .filter(this::isTextType)
                .map(RelationFieldDTO::getFieldName)
                .filter(this::isSafeFieldName)
                .collect(Collectors.toList());
        if (searchableFields.isEmpty()) {
            return "";
        }
        String searchPattern = "%" + ListFilterSql.escapeLike(search.trim()) + "%";
        String conditions = searchableFields.stream()
                .map(f -> "data->>'" + SqlIdentifiers.requireIdentifier(f) + "' ILIKE ? ESCAPE '\\'")
                .collect(Collectors.joining(" OR "));
        params.add(searchPattern);
        for (int i = 0; i < searchableFields.size(); i++) {
            params.add(searchPattern);
        }
        return " AND data::text ILIKE ? ESCAPE '\\' AND (" + conditions + ")";
    }

    private boolean isTextType(RelationFieldDTO field) {
        RelationDataType dataType = field.getDataType();
        return dataType == RelationDataType.VARCHAR
                || dataType == RelationDataType.TEXT
                || dataType == RelationDataType.LOOKUP;
    }

    private boolean isSafeFieldName(String fieldName) {
        return fieldName != null && fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    private Map<String, Object> parseRowData(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse relation-table row JSON", e);
        }
    }
}
