package com.admin.dataview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeDataViewIndexTest {
    @Test void sameKeySurvivesRenameAndNewReleaseWhileNewKeyCreatesAnotherIdentity() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ObjectMapper mapper=new ObjectMapper();
        var index=new RuntimeDataViewIndex(jdbc,mapper,new RuntimeExternalTableSnapshots(jdbc,mapper));
        when(jdbc.queryForList(startsWith("SELECT code"),eq(String.class),anyString())).thenReturn(List.of("FU"));
        when(jdbc.queryForList(contains("'DATA_TABLE'"),eq(String.class),anyString())).thenReturn(List.of(
                "{\"tableName\":\"cases\",\"tableType\":\"MAIN\",\"fields\":[]}"));
        String key=UUID.randomUUID().toString(),newKey=UUID.randomUUID().toString();
        when(jdbc.queryForList(contains("'MAIN_TABLE_VIEW'"),eq(String.class),eq("v1"))).thenReturn(List.of(payload(key,"Old")));
        when(jdbc.queryForList(contains("'MAIN_TABLE_VIEW'"),eq(String.class),eq("v2"))).thenReturn(List.of(payload(key,"Renamed")));
        when(jdbc.queryForList(contains("'MAIN_TABLE_VIEW'"),eq(String.class),eq("v3"))).thenReturn(List.of(payload(newKey,"Old")));
        when(jdbc.queryForObject(startsWith("INSERT INTO ac_data_view_function_units"),eq(Long.class),eq("FU"))).thenReturn(10L);
        when(jdbc.queryForObject(startsWith("INSERT INTO ac_data_view_tables"),eq(Long.class),eq(10L),eq("cases"))).thenReturn(20L);
        Map<String,Long> identities=new LinkedHashMap<>();
        when(jdbc.queryForObject(startsWith("INSERT INTO ac_data_view_identities"),eq(Long.class),eq(10L),anyString()))
                .thenAnswer(call -> identities.computeIfAbsent(call.getArgument(3),ignored -> (long)identities.size()+1));
        index.index("v1");index.index("v2");index.index("v3");
        assertThat(identities).hasSize(2);
        verify(jdbc).update(startsWith("INSERT INTO ac_data_view_releases"),eq(1L),eq("v1"),eq(20L),eq("cases"),isNull(),eq("MAIN"),eq("Old"),anyString());
        verify(jdbc).update(startsWith("INSERT INTO ac_data_view_releases"),eq(1L),eq("v2"),eq(20L),eq("cases"),isNull(),eq("MAIN"),eq("Renamed"),anyString());
        verify(jdbc).update(startsWith("INSERT INTO ac_data_view_releases"),eq(2L),eq("v3"),eq(20L),eq("cases"),isNull(),eq("MAIN"),eq("Old"),anyString());
        assertThat(mockingDetails(jdbc).getInvocations()).allSatisfy(call -> {
            if(call.getArguments().length>0 && call.getArgument(0) instanceof String sql) assertThat(sql).doesNotContain("dw_");
        });
    }
    private String payload(String key,String name) {
        return "[{\"viewKey\":\""+key+"\",\"viewName\":\""+name+"\",\"mainTableName\":\"cases\"}]";
    }
}
