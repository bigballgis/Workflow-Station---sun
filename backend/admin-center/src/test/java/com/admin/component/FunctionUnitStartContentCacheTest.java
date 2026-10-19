package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.repository.FunctionUnitContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionUnitStartContentCacheTest {
    @Test void freshStartReadsNeverReuseOrPoisonTheLegacyCache() throws Exception {
        var repository = mock(FunctionUnitContentRepository.class);
        var lookup = mock(FunctionUnitLookup.class);
        var jdbc = mock(JdbcTemplate.class);
        var component = new FunctionUnitContentComponent(repository, jdbc, lookup,
                mock(FormTableBindingLoader.class), new ObjectMapper());
        var original = FunctionUnit.builder().id("synthetic").name("Original").code("synthetic").version("1").build();
        var changed = FunctionUnit.builder().id("synthetic").name("Changed").code("synthetic").version("1").build();
        when(lookup.getById("synthetic")).thenReturn(original, changed);
        when(repository.findByFunctionUnitId("synthetic")).thenReturn(List.of());
        var cached = component.assembleFunctionUnitContent("synthetic");
        assertThat(cached.getName()).isEqualTo("Original");
        var authoritative = component.assembleFunctionUnitContent("synthetic", true);
        assertThat(authoritative.getName()).isEqualTo("Changed");
        assertThat(authoritative.getStartContentAuthoritative()).isTrue();
        assertThat(new ObjectMapper().writeValueAsString(cached)).doesNotContain("startContentAuthoritative");
        assertThat(component.assembleFunctionUnitContent("synthetic")).isSameAs(cached);
        verify(repository, times(2)).findByFunctionUnitId("synthetic");
        verify(lookup, times(2)).getById("synthetic");
    }
}
