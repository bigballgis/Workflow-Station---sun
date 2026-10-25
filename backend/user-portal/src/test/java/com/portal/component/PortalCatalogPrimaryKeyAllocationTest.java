package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.portal.dto.AllocatePrimaryKeyRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortalCatalogPrimaryKeyAllocationTest {
    @ParameterizedTest @ValueSource(strings = {"null", "{}", "{\"strategy\":\"manual\"}", "{\"strategy\":\"prefixedSequence\",\"prefix\":\"CASE-\"}"})
    void frozenAllocationPreservesLegacyDefaultsAndExplicitStrategies(String json) {
        var jdbc = mock(JdbcTemplate.class);
        var allocator = mock(PrimaryKeyAllocationService.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("catalog"), eq("DATA_TABLE")))
                .thenReturn(List.of("{\"tableId\":42,\"fields\":[{\"fieldName\":\"case_key\",\"isPrimaryKey\":true,\"pkGenerationJson\":" + json + "}]}"));
        when(allocator.allocate(eq(42L), eq("case_key"), any(), eq(1), eq(""), eq("dw_pk_sequences")))
                .thenReturn(List.of("allocated"));
        var component = new PortalPrimaryKeyAllocationComponent(jdbc, allocator, new ObjectMapper(),
                mock(FunctionUnitAccessComponent.class));
        var request = new AllocatePrimaryKeyRequest();
        request.setTableId(42L);
        request.setFieldName("case_key");
        assertEquals(List.of("allocated"), component.allocateCatalog("catalog", request).getValues());
        var config = ArgumentCaptor.forClass(PkGenerationConfig.class);
        verify(allocator).allocate(eq(42L), eq("case_key"), config.capture(), eq(1), eq(""), eq("dw_pk_sequences"));
        String expected = json.equals("null") || json.equals("{}") ? "uuid"
                : json.contains("prefixedSequence") ? "prefixedSequence" : "manual";
        assertEquals(expected, config.getValue().getStrategy());
        if (expected.equals("prefixedSequence")) assertEquals("CASE-", config.getValue().getPrefix());
        verify(jdbc).queryForList(anyString(), eq(String.class), eq("catalog"), eq("DATA_TABLE"));
        verifyNoMoreInteractions(jdbc);
    }
}
