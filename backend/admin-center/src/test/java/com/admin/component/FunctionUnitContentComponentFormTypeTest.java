package com.admin.component;

import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.dto.response.TableBindingDTO;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.doAnswer;
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
                contentRepository, jdbcTemplate, functionUnitLookup, bindingLoader, new ObjectMapper());
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
                    when(rs.getString("scene")).thenReturn("TASK");
                    return extractor.extractData(rs);
                });

        FunctionUnitContentResponse response = component.assembleFunctionUnitContent("fu-1");

        assertThat(response.getForms()).hasSize(1);
        FormContentDTO form = response.getForms().get(0);
        assertThat(form.getFormType()).isEqualTo("PROCESS");
        assertThat(form.getData())
                .as("PROCESS/TASK form JSON stays the catalog snapshot, not live DW config_json")
                .isEqualTo("{\"stale\":true}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assembleIgnoresLiveDwConfigForNonDetailForms() throws Exception {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-1")
                .name("Mask FU")
                .code("mask-fu")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();
        FunctionUnitContent formContent = FunctionUnitContent.builder()
                .id("content-1")
                .functionUnit(unit)
                .contentType(ContentType.FORM)
                .contentName("task-form")
                .contentData("{\"snapshot\":true}")
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
                    when(rs.getString("config_json")).thenReturn("{\"live\":true}");
                    when(rs.getString("form_type")).thenReturn("TASK");
                    when(rs.getString("scene")).thenReturn("TASK");
                    return extractor.extractData(rs);
                });

        FormContentDTO form = component.assembleFunctionUnitContent("fu-1").getForms().get(0);
        assertThat(form.getFormType()).isEqualTo("TASK");
        assertThat(form.getScene()).isEqualTo("TASK");
        assertThat(form.getData()).isEqualTo("{\"snapshot\":true}");
    }

    /**
     * A DETAIL form created after the last deploy has no {@code sys_function_unit_contents} row.
     * The portal resolves a Main Table View's live {@code detail_form_id} against this list, so
     * leaving it out makes the record page report "This record could not be loaded".
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailFormsAbsentFromTheSnapshotAreStillReturned() {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-detail")
                .name("ATM")
                .code("atm-20260623-gaevus")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();

        when(functionUnitLookup.getById("fu-detail")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-detail")).thenReturn(List.of());
        doNothing().when(bindingLoader).attachTableBindings(anyList());
        when(jdbcTemplate.queryForList(contains("form_type = 'DETAIL'"), any(Object[].class)))
                .thenReturn(List.of(java.util.Map.of(
                        "id", 50618L,
                        "form_name", "test2",
                        "config_json", "{\"rule\":[{\"field\":\"a\"}]}",
                        "form_type", "DETAIL",
                        "scene", "TASK")));

        FunctionUnitContentResponse response = component.assembleFunctionUnitContent("fu-detail");

        assertThat(response.getForms()).hasSize(1);
        FormContentDTO form = response.getForms().get(0);
        assertThat(form.getSourceId()).isEqualTo("50618");
        assertThat(form.getFormType()).isEqualTo("DETAIL");
        assertThat(form.getData()).isEqualTo("{\"rule\":[{\"field\":\"a\"}]}");
    }

    /** A DETAIL form the snapshot already carries must not be added a second time. */
    @Test
    @SuppressWarnings("unchecked")
    void aDetailFormAlreadyInTheSnapshotIsNotDuplicated() throws Exception {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-dup")
                .name("MI demo")
                .code("fu-20260422-23tfag")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();
        FunctionUnitContent snapshotDetail = FunctionUnitContent.builder()
                .id("content-d")
                .functionUnit(unit)
                .contentType(ContentType.FORM)
                .contentName("Test_meeting")
                .contentData("{\"stale\":true}")
                .sourceId("50613")
                .build();

        when(functionUnitLookup.getById("fu-dup")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-dup")).thenReturn(List.of(snapshotDetail));
        doNothing().when(bindingLoader).attachTableBindings(anyList());
        when(jdbcTemplate.query(contains("form_type"), any(ResultSetExtractor.class), anyLong()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getString("config_json")).thenReturn("{\"liveDetail\":true}");
                    when(rs.getString("form_type")).thenReturn("DETAIL");
                    when(rs.getString("scene")).thenReturn("TASK");
                    return extractor.extractData(rs);
                });
        when(jdbcTemplate.queryForList(contains("form_type = 'DETAIL'"), any(Object[].class)))
                .thenReturn(List.of(java.util.Map.of(
                        "id", 50613L,
                        "form_name", "Test_meeting",
                        "config_json", "{\"liveDetail\":true}",
                        "form_type", "DETAIL",
                        "scene", "TASK")));

        FunctionUnitContentResponse response = component.assembleFunctionUnitContent("fu-dup");

        assertThat(response.getForms())
                .as("the snapshot row and the live row are the same form")
                .hasSize(1);
        assertThat(response.getForms().get(0).getSourceId()).isEqualTo("50613");
        assertThat(response.getForms().get(0).getData())
                .as("DETAIL in the snapshot still uses live DW config (Main Table View)")
                .isEqualTo("{\"liveDetail\":true}");
    }

    /** A failed live lookup must not break the whole content payload. */
    @Test
    void aFailedDetailLookupLeavesTheSnapshotFormsIntact() {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-err")
                .name("ATM")
                .code("atm-err")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();

        when(functionUnitLookup.getById("fu-err")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-err")).thenReturn(List.of());
        doNothing().when(bindingLoader).attachTableBindings(anyList());
        when(jdbcTemplate.queryForList(contains("form_type = 'DETAIL'"), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        FunctionUnitContentResponse response = component.assembleFunctionUnitContent("fu-err");

        assertThat(response.getForms()).isEmpty();
        assertThat(response.getCode()).isEqualTo("atm-err");
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

    @Test
    @SuppressWarnings("unchecked")
    void assembleFreezesProcessSnapshotBindingsBeforeLiveAttach() throws Exception {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-freeze")
                .name("P3 MI")
                .code("p3-mi-dual-binding-test")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();
        String snapshot = """
                {"formName":"task-form","formType":"TASK","configJson":{"snapshot":true},
                 "tableBindings":[
                   {"bindingId":50729,"bindingType":"SUB","tableName":"p3_mi_file",
                    "filterFkFieldName":"case_id","filterFkRefTableName":"p3_mi_case"},
                   {"bindingId":50730,"bindingType":"SUB","tableName":"p3_mi_file",
                    "filterFkFieldName":"party_id","filterFkRefTableName":"p3_mi_party"}
                 ]}
                """;
        FunctionUnitContent formContent = FunctionUnitContent.builder()
                .id("content-freeze")
                .functionUnit(unit)
                .contentType(ContentType.FORM)
                .contentName("task-form")
                .contentData(snapshot)
                .sourceId("42")
                .build();

        when(functionUnitLookup.getById("fu-freeze")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-freeze")).thenReturn(List.of(formContent));
        doAnswer(invocation -> {
            java.util.List<FormContentDTO> forms = invocation.getArgument(0);
            assertThat(forms.get(0).getTableBindings()).hasSize(2);
            assertThat(forms.get(0).getTableBindings())
                    .extracting(TableBindingDTO::getBindingId)
                    .containsExactly(50729L, 50730L);
            return null;
        }).when(bindingLoader).attachTableBindings(anyList());
        when(jdbcTemplate.query(contains("form_type"), any(ResultSetExtractor.class), anyLong()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getString("config_json")).thenReturn("{\"live\":true}");
                    when(rs.getString("form_type")).thenReturn("TASK");
                    when(rs.getString("scene")).thenReturn("TASK");
                    return extractor.extractData(rs);
                });

        FormContentDTO form = component.assembleFunctionUnitContent("fu-freeze").getForms().get(0);
        assertThat(form.getData()).isEqualTo("{\"snapshot\":true}");
        assertThat(form.getTableBindings()).hasSize(2);
        assertThat(form.getTableBindings().get(0).getFilterFkFieldName()).isEqualTo("case_id");
        assertThat(form.getTableBindings().get(1).getFilterFkFieldName()).isEqualTo("party_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assembleDoesNotFreezeDetailSnapshotBindings() throws Exception {
        FunctionUnit unit = FunctionUnit.builder()
                .id("fu-detail-freeze")
                .name("ATM")
                .code("atm-detail")
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .build();
        String snapshot = """
                {"formName":"detail","formType":"DETAIL","configJson":{"stale":true},
                 "tableBindings":[{"bindingId":1,"bindingType":"PRIMARY","tableName":"old_table"}]}
                """;
        FunctionUnitContent formContent = FunctionUnitContent.builder()
                .id("content-d")
                .functionUnit(unit)
                .contentType(ContentType.FORM)
                .contentName("detail")
                .contentData(snapshot)
                .sourceId("50613")
                .build();

        when(functionUnitLookup.getById("fu-detail-freeze")).thenReturn(unit);
        when(contentRepository.findByFunctionUnitId("fu-detail-freeze")).thenReturn(List.of(formContent));
        doNothing().when(bindingLoader).attachTableBindings(anyList());
        when(jdbcTemplate.query(contains("form_type"), any(ResultSetExtractor.class), anyLong()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getString("config_json")).thenReturn("{\"liveDetail\":true}");
                    when(rs.getString("form_type")).thenReturn("DETAIL");
                    when(rs.getString("scene")).thenReturn("TASK");
                    return extractor.extractData(rs);
                });
        when(jdbcTemplate.queryForList(contains("form_type = 'DETAIL'"), any(Object[].class)))
                .thenReturn(List.of());

        FormContentDTO form = component.assembleFunctionUnitContent("fu-detail-freeze").getForms().get(0);
        assertThat(form.getData()).isEqualTo("{\"liveDetail\":true}");
        assertThat(form.getTableBindings())
                .as("DETAIL leaves tableBindings null so live attach can overlay")
                .isNull();
    }
}
