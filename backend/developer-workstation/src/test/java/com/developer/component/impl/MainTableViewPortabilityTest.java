package com.developer.component.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.MainTableViewField;
import com.developer.enums.MainTableViewStatus;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.MainTableViewConfigRepository;
import com.developer.util.MainTableViewAccessRulesValidator;
import com.developer.util.MainTableViewNameValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MainTableViewPortabilityTest {

    @Mock
    private MainTableViewConfigRepository mainTableViewConfigRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MainTableViewPortability portability;

    @BeforeEach
    void setUp() {
        portability = new MainTableViewPortability(mainTableViewConfigRepository, jdbcTemplate);
    }

    @Test
    void export_loadsAccessRulesViaJdbcNotLazyCollection() {
        MainTableViewConfig view = MainTableViewConfig.builder()
                .id(10L)
                .mainTableId(20L)
                .viewName("HMDC Case")
                .isDefault(false)
                .status(MainTableViewStatus.PUBLISHED)
                .restrictToInvolvedUsers(false)
                .viewFields(List.of(MainTableViewField.builder()
                        .fieldName("id")
                        .displayLabel("ID")
                        .sortOrder(0)
                        .visible(true)
                        .isSystemField(false)
                        .selectDisplay("label")
                        .build()))
                .build();

        when(mainTableViewConfigRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(view));
        when(jdbcTemplate.queryForList(anyString(), eq(10L))).thenReturn(List.of(
                Map.of("target_type", "BUSINESS_UNIT", "target_id", "bu-e2e-finance"),
                Map.of("target_type", "ROLE", "target_id", "role-manager")));
        when(jdbcTemplate.queryForObject(eq("SELECT code FROM sys_business_units WHERE id = ?"),
                eq(String.class), eq("bu-e2e-finance"))).thenReturn("FINANCE");
        when(jdbcTemplate.queryForObject(eq("SELECT code FROM sys_roles WHERE id = ?"),
                eq(String.class), eq("role-manager"))).thenReturn("MANAGER");

        List<Map<String, Object>> exported = portability.export(1L, Map.of(20L, "HMDC_Case"));

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0)).doesNotContainKey("viewId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) exported.get(0).get("accessRules");
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0)).containsEntry("targetType", "BUSINESS_UNIT");
        assertThat(rules.get(0)).containsEntry("targetId", "bu-e2e-finance");
        assertThat(rules.get(0)).containsEntry("targetCode", "FINANCE");
        assertThat(exported.get(0)).containsEntry("restrictToInvolvedUsers", false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) exported.get(0).get("fields");
        assertThat(fields.get(0)).containsEntry("selectDisplay", "label");
    }

    @Test
    void snapshotCapturesIdentityButZipExportDoesNot() {
        MainTableViewConfig view = MainTableViewConfig.builder().id(10L).mainTableId(20L)
                .viewName("Saved View").status(MainTableViewStatus.PUBLISHED).viewFields(List.of()).build();
        when(mainTableViewConfigRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(view));
        Map<String, Object> snapshot = portability.snapshot(1L, Map.of(20L, "main"), Map.of()).get(0);
        Map<String, Object> exported = portability.export(1L, Map.of(20L, "main"), Map.of()).get(0);
        assertThat(snapshot).containsEntry("viewId", 10L);
        assertThat(exported).doesNotContainKey("viewId");
        snapshot.remove("viewId");
        assertThat(snapshot).isEqualTo(exported);
    }

    /**
     * The target environment remaps a rule only by its BU/Role code; a rule whose id has no code here
     * would arrive pointing at nothing, so the export stops and names the rule. A same-database
     * snapshot (rollback backup) keeps the id and must not be blocked.
     */
    @Test
    void export_rejectsAnAccessRuleWhoseIdHasNoCodeHereButSnapshotKeepsIt() {
        MainTableViewConfig view = MainTableViewConfig.builder().id(10L).mainTableId(20L)
                .viewName("HMDC Case").status(MainTableViewStatus.PUBLISHED).viewFields(List.of()).build();
        when(mainTableViewConfigRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(view));
        when(jdbcTemplate.queryForList(anyString(), eq(10L))).thenReturn(List.of(
                Map.of("target_type", "BUSINESS_UNIT", "target_id", "bu-deleted"),
                Map.of("target_type", "ROLE", "target_id", "role-manager")));
        when(jdbcTemplate.queryForObject(eq("SELECT code FROM sys_business_units WHERE id = ?"),
                eq(String.class), eq("bu-deleted"))).thenThrow(new EmptyResultDataAccessException(1));
        when(jdbcTemplate.queryForObject(eq("SELECT code FROM sys_roles WHERE id = ?"),
                eq(String.class), eq("role-manager"))).thenReturn("MANAGER");

        assertThatThrownBy(() -> portability.export(1L, Map.of(20L, "HMDC_Case")))
                .isInstanceOf(DeveloperBusinessException.class)
                .hasMessageContaining("View 'HMDC Case'")
                .hasMessageContaining("BUSINESS_UNIT id=bu-deleted")
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewAccessRulesValidator.EXPORT_UNRESOLVED_CODE);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) portability
                .snapshot(1L, Map.of(20L, "HMDC_Case"), Map.of()).get(0).get("accessRules");
        assertThat(rules.get(0)).containsEntry("targetId", "bu-deleted").doesNotContainKey("targetCode");
    }

    /** Portal and BI find a View by its name, so two Views of one unit may not share it in a package. */
    @Test
    void export_rejectsDuplicateViewNamesButSnapshotDoesNot() {
        MainTableViewConfig a = MainTableViewConfig.builder().id(10L).mainTableId(20L)
                .viewName("Cases").status(MainTableViewStatus.PUBLISHED).viewFields(List.of()).build();
        MainTableViewConfig b = MainTableViewConfig.builder().id(11L).mainTableId(21L)
                .viewName(" CASES").status(MainTableViewStatus.PUBLISHED).viewFields(List.of()).build();
        when(mainTableViewConfigRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> portability.export(1L, Map.of(20L, "case", 21L, "line")))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewNameValidator.DUPLICATE_CODE);
        assertThat(portability.snapshot(1L, Map.of(20L, "case", 21L, "line"), Map.of())).hasSize(2);
    }

    /** Import and version rollback both replay through importAll; nothing is deleted before the check. */
    @Test
    void import_rejectsDuplicateViewNamesBeforeReplacingAnything() {
        FunctionUnit fu = FunctionUnit.builder().id(99L).build();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("mainTableName", "HMDC_Case");
        first.put("viewName", "Cases");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("mainTableName", "HMDC_Line");
        second.put("viewName", "cases");

        assertThatThrownBy(() -> portability.importAll(
                List.of(first, second), fu, Map.of("HMDC_Case", 20L, "HMDC_Line", 21L)))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewNameValidator.DUPLICATE_CODE);
        verify(mainTableViewConfigRepository, never()).deleteByFunctionUnitId(any());
        verify(mainTableViewConfigRepository, never()).save(any());
    }

    @Test
    void import_readsSelectDisplayAndDefaultsOlderPackagesToValue() {
        FunctionUnit fu = FunctionUnit.builder().id(99L).build();
        Map<String, Object> viewPayload = new LinkedHashMap<>();
        viewPayload.put("mainTableName", "HMDC_Case");
        viewPayload.put("viewName", "HMDC Case");
        viewPayload.put("viewId", 12345L); // Compare metadata must never be reused as the restored entity ID.
        viewPayload.put("isDefault", false);
        viewPayload.put("status", "DRAFT");
        viewPayload.put("restrictToInvolvedUsers", false);
        viewPayload.put("accessRules", List.of());
        Map<String, Object> labelField = new LinkedHashMap<>();
        labelField.put("fieldName", "merchant_credit");
        labelField.put("selectDisplay", "label");
        Map<String, Object> legacyField = new LinkedHashMap<>();
        legacyField.put("fieldName", "channel");
        viewPayload.put("fields", List.of(labelField, legacyField));
        when(mainTableViewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        portability.importAll(List.of(viewPayload), fu, Map.of("HMDC_Case", 20L));

        ArgumentCaptor<MainTableViewConfig> captor = ArgumentCaptor.forClass(MainTableViewConfig.class);
        verify(mainTableViewConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getViewFields()).extracting(MainTableViewField::getSelectDisplay)
                .containsExactly("label", "value");
    }

    @Test
    void import_rejectsUnresolvedAccessCode() {
        FunctionUnit fu = FunctionUnit.builder().id(99L).build();
        Map<String, Object> viewPayload = new LinkedHashMap<>();
        viewPayload.put("mainTableName", "HMDC_Case");
        viewPayload.put("viewName", "HMDC Case");
        viewPayload.put("isDefault", false);
        viewPayload.put("status", "DRAFT");
        viewPayload.put("restrictToInvolvedUsers", true);
        viewPayload.put("fields", List.of());
        viewPayload.put("accessRules", List.of(
                Map.of("targetType", "BUSINESS_UNIT", "targetCode", "UNKNOWN_BU"),
                Map.of("targetType", "ROLE", "targetCode", "MANAGER")));

        when(jdbcTemplate.queryForObject(
                eq("SELECT id FROM sys_business_units WHERE code = ?"), eq(String.class), eq("UNKNOWN_BU")))
                .thenReturn(null);

        assertThatThrownBy(() -> portability.importAll(
                List.of(viewPayload), fu, Map.of("HMDC_Case", 20L)))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewAccessRulesValidator.IMPORT_UNRESOLVED_CODE);
    }

    /**
     * Packages exported before MAIN views lost their detail form still name one. Dropping it keeps
     * the invariant the service enforces; doing so with a warning rather than an exception keeps a
     * legacy package — and the version rollback that replays it — importable.
     */
    @Test
    void import_dropsDetailFormOnAMainTableViewWithoutFailing() {
        FunctionUnit fu = FunctionUnit.builder().id(99L).build();
        Map<String, Object> viewPayload = new LinkedHashMap<>();
        viewPayload.put("mainTableName", "HMDC_Case");
        viewPayload.put("viewName", "HMDC Case");
        viewPayload.put("isDefault", true);
        viewPayload.put("status", "PUBLISHED");
        viewPayload.put("fields", List.of());
        viewPayload.put("detailFormName", "Case Detail");

        when(jdbcTemplate.queryForList(
                anyString(), eq(String.class), eq(20L))).thenReturn(List.of("MAIN"));

        portability.importAll(List.of(viewPayload), fu, Map.of("HMDC_Case", 20L),
                Map.of("Case Detail", 700L));

        ArgumentCaptor<MainTableViewConfig> captor = ArgumentCaptor.forClass(MainTableViewConfig.class);
        verify(mainTableViewConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getDetailFormId()).isNull();
    }

    /** SUB views are where a detail form belongs, so import must still resolve it by name. */
    @Test
    void import_keepsDetailFormOnASubTableView() {
        FunctionUnit fu = FunctionUnit.builder().id(99L).build();
        Map<String, Object> viewPayload = new LinkedHashMap<>();
        viewPayload.put("mainTableName", "HMDC_Stage");
        viewPayload.put("viewName", "Stages");
        viewPayload.put("isDefault", true);
        viewPayload.put("status", "PUBLISHED");
        viewPayload.put("fields", List.of());
        viewPayload.put("detailFormName", "Stage Detail");

        when(jdbcTemplate.queryForList(
                anyString(), eq(String.class), eq(30L))).thenReturn(List.of("SUB"));

        portability.importAll(List.of(viewPayload), fu, Map.of("HMDC_Stage", 30L),
                Map.of("Stage Detail", 800L));

        ArgumentCaptor<MainTableViewConfig> captor = ArgumentCaptor.forClass(MainTableViewConfig.class);
        verify(mainTableViewConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getDetailFormId()).isEqualTo(800L);
    }

    @Test
    void import_rejectsPartialAccessAfterResolve() {
        FunctionUnit fu = FunctionUnit.builder().id(99L).build();
        Map<String, Object> viewPayload = new LinkedHashMap<>();
        viewPayload.put("mainTableName", "HMDC_Case");
        viewPayload.put("viewName", "HMDC Case");
        viewPayload.put("isDefault", false);
        viewPayload.put("status", "DRAFT");
        viewPayload.put("fields", List.of());
        viewPayload.put("accessRules", List.of(
                Map.of("targetType", "BUSINESS_UNIT", "targetId", "bu-e2e-finance")));

        assertThatThrownBy(() -> portability.importAll(
                List.of(viewPayload), fu, Map.of("HMDC_Case", 20L)))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewAccessRulesValidator.PAIR_ERROR_CODE);
    }
}
