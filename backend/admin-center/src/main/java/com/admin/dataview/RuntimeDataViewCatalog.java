package com.admin.dataview;

import com.platform.common.dataview.RuntimeDataView;
import com.platform.common.functionunit.CatalogSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class RuntimeDataViewCatalog {
    private final JdbcTemplate jdbc;
    private final CatalogSnapshotReader snapshots;
    private final RuntimeDataViewAccess access;

    private record Visible(List<Map<String,Object>> rows, boolean administrator) { }

    @Transactional(readOnly = true)
    public boolean isAdministrator(String userId) {
        return access.principal(userId).administrator();
    }

    private Visible visible(String userId) {
        var principal=access.principal(userId);
        var rows=jdbc.queryForList("SELECT * FROM ac_current_data_views ORDER BY function_unit_name,table_name,is_default DESC NULLS LAST,view_name,view_id");
        List<Map<String,Object>> allowed=new ArrayList<>();
        for(var row:rows) {
            var definition=snapshots.parse(String.valueOf(row.get("definition")));
            if(access.allows(principal,definition)) allowed.add(row);
        }
        return new Visible(allowed,principal.administrator());
    }

    @Transactional(readOnly = true)
    public List<RuntimeDataView> list(String userId, boolean includeDefinitions) {
        var visible=visible(userId);
        return visible.rows().stream().map(row -> materialize(row,includeDefinitions,List.of(),visible.administrator())).toList();
    }

    @Transactional(readOnly = true)
    public RuntimeDataView require(String userId, Long viewId) {
        var visible=visible(userId);
        var matches=visible.rows().stream().filter(row -> number(row,"view_id").equals(viewId)).toList();
        if(matches.size()!=1) throw new AccessDeniedException("Data view is not accessible");
        var references=visible.rows().stream().map(row -> new RuntimeDataView.Reference(number(row,"view_id"),
                text(row,"function_unit_code"),text(row,"table_name"),Boolean.TRUE.equals(row.get("is_default")))).toList();
        return materialize(matches.get(0),true,references,visible.administrator());
    }

    private RuntimeDataView materialize(Map<String, Object> row, boolean full,
                                       List<RuntimeDataView.Reference> refs, boolean admin) {
        String catalog = text(row,"catalog_id");
        return new RuntimeDataView(number(row,"view_id"), number(row,"function_unit_id"), number(row,"table_id"),
                text(row,"view_key"), catalog, text(row,"function_unit_code"), text(row,"function_unit_name"),
                text(row,"version"), text(row,"icon_svg"), text(row,"table_name"),
                row.get("table_display_name") == null ? text(row,"table_name") : text(row,"table_display_name"),
                text(row,"table_type"), snapshots.parse(String.valueOf(row.get("definition"))),
                full ? snapshots.contents(catalog,"DATA_TABLE") : List.of(),
                full ? snapshots.contents(catalog,"FORM") : List.of(), refs, admin);
    }
    private static String text(Map<String,Object> row, String key) { return row.get(key) == null ? null : row.get(key).toString(); }
    private static Long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
