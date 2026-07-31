package com.admin.component;

import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionUnitContentComponentFormTypeTest {

    @Mock
    private FunctionUnitContentRepository contentRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private FunctionUnitLookup functionUnitLookup;
    @Mock
    private FormTableBindingLoader bindingLoader;

    private FunctionUnitContentComponent component;

    @BeforeEach
    void setUp() {
        component = new FunctionUnitContentComponent(
                contentRepository, jdbcTemplate, functionUnitLookup, bindingLoader);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assembleMapsFormTypeFromDwFormDefinitions() throws Exception {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-1")
                .name("Mask FU")
                .code("mask-fu")
                .version("1.0.0")
                .description("d")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();
        FunctionUnitContent formContent = FunctionUnitContent.builder()
                .id("content-1")
                .functionUnit(unit)
                .contentType(ContentType.FORM)
                .contentName("process-form")
                .contentData("{\"stale\":true}")
                .sourceId("42")
                .build();

        when(functionUnitLookup.getById("fu-1")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-1")).thenReturn(List.of(formContent));
        doNothing().when(bindingLoader).attachTableBindings(anyList());

        when(jdbcTemplate.query(contains("form_type"), any(ResultSetExtractor.class), anyLong()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getString("config_json")).thenReturn("{\"rule\":[]}");
                    when(rs.getString("form_type")).thenReturn("PROCESS");
                    return extractor.extractData(rs);
                });

        FunctionUnitContentResponse response = component.assembleFunctionUnitContent("fu-1");

        assertThat(response.getForms()).hasSize(1);
        FormContentDTO form = response.getForms().get(0);
        assertThat(form.getFormType()).isEqualTo("PROCESS");
        assertThat(form.getData()).isEqualTo("{\"rule\":[]}");
    }

    @Test
    void assembleFallsBackWhenSourceIdMissing() {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-2")
                .name("Mask FU")
                .code("mask-fu-2")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();
        FunctionUnitContent formContent = FunctionUnitContent.builder()
                .id("content-2")
                .functionUnit(unit)
                .contentType(ContentType.FORM)
                .contentName("orphan-form")
                .contentData("{\"fallback\":true}")
                .sourceId(null)
                .build();

        when(functionUnitLookup.getById("fu-2")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-2")).thenReturn(List.of(formContent));
        doNothing().when(bindingLoader).attachTableBindings(anyList());

        FunctionUnitContentResponse response = component.assembleFunctionUnitContent("fu-2");

        FormContentDTO form = response.getForms().get(0);
        assertThat(form.getFormType()).isNull();
        assertThat(form.getData()).isEqualTo("{\"fallback\":true}");
    }
}
