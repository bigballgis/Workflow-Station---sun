package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class CatalogViewIdentityTest {
    @Test void historicalDetailUsesStableKeyEvenAfterTheViewIsRenamed() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq(String.class),eq("old-release"),eq("MAIN_TABLE_VIEW")))
                .thenReturn(List.of("""
                        [{"viewKey":"stable-key","viewName":"Old Name","mainTableName":"cases","detailFormName":"Old Form"},
                         {"viewKey":"other-key","viewName":"Old Name","mainTableName":"other","detailFormName":"Other Form"}]
                        """));
        when(jdbc.queryForList(anyString(),eq(String.class),eq("old-release"),eq("FORM")))
                .thenReturn(List.of("{\"formId\":1,\"formName\":\"Old Form\",\"formType\":\"DETAIL\",\"tableBindings\":[],\"configJson\":{\"label\":\"Old Layout\"}}"));
        when(jdbc.queryForList(anyString(),eq(String.class),eq("old-release"),eq("DATA_TABLE"))).thenReturn(List.of());
        var reader=new CatalogSnapshotReader(jdbc,new ObjectMapper());
        assertThat(reader.detailFormForView("old-release","stable-key","cases","New Name"))
                .containsEntry("formName","Old Form");
        assertThat(reader.detailFormForView("old-release","new-unrelated-key","cases","Old Name")).isEmpty();
    }
}
