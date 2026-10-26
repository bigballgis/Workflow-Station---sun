package com.admin.bi.component;

import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiDataViewAssignmentListQueryRequest;
import com.admin.list.BiDataViewAssignmentColumnSpec;
import com.admin.list.ListQuerySupport;
import com.platform.common.list.ListFilterSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BiDataViewAssignmentListQueryComponent {
    private static final String LIST_KEY = "admin-bi-data-view-assignments";
    private final JdbcTemplate jdbcTemplate;
    private final BiDataViewMetadataRepository metadata;

    @Transactional(readOnly = true)
    public AdminListPage<DataViewAssignmentResponse> query(BiDataViewAssignmentListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = BiDataViewAssignmentColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 FROM bi_data_view_assignment a
                 JOIN bi_dashboard_registry d ON d.id = a.dashboard_id
                 JOIN dw_function_units fu ON fu.id = a.function_unit_id
                 LEFT JOIN dw_table_definitions t ON t.id = a.table_id AND t.function_unit_id = a.function_unit_id
                 LEFT JOIN dw_main_table_view_configs v ON v.id = a.view_id
                   AND v.function_unit_id = a.function_unit_id AND v.main_table_id = a.table_id
                 WHERE 1=1
                """);
        if (request.dashboardTitle() != null && !request.dashboardTitle().isBlank()) {
            where.append(" AND d.dashboard_title").append(ListFilterSql.ILIKE);
            params.add("%" + ListFilterSql.escapeLike(request.dashboardTitle().trim()) + "%");
        }
        if (request.functionUnitId() != null) {
            where.append(" AND a.function_unit_id = ?");
            params.add(request.functionUnitId());
        }
        where.append(filterSql.whereClause(request.filters(), params));
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        long total = ListQuerySupport.requireCount(ListQuerySupport.query(jdbcTemplate,
                "SELECT COUNT(*)" + where, params, rs -> rs.next() ? rs.getLong(1) : 0L), LIST_KEY);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        List<String> ids = ListQuerySupport.query(jdbcTemplate,
                "SELECT a.id" + where + orderBy + " LIMIT ? OFFSET ?", pageParams, rs -> {
                    List<String> values = new ArrayList<>();
                    while (rs.next()) values.add(rs.getString("id"));
                    return values;
                });
        Map<String, DataViewAssignmentResponse> byId = metadata.responses(ids).stream()
                .collect(Collectors.toMap(DataViewAssignmentResponse::getId, Function.identity()));
        List<DataViewAssignmentResponse> rows = ids.stream().map(id -> {
            DataViewAssignmentResponse row = byId.get(id);
            if (row == null) throw new IllegalStateException("Data View Assignment page referenced missing row " + id);
            return row;
        }).toList();
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(BiDataViewAssignmentColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }
}
