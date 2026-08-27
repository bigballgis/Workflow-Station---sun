package com.developer.component.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.MainTableViewField;
import com.developer.enums.MainTableViewStatus;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.MainTableViewConfigRepository;
import com.developer.util.MainTableViewAccessRulesValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) exported.get(0).get("accessRules");
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0)).containsEntry("targetType", "BUSINESS_UNIT");
        assertThat(rules.get(0)).containsEntry("targetId", "bu-e2e-finance");
        assertThat(rules.get(0)).containsEntry("targetCode", "FINANCE");
        assertThat(exported.get(0)).containsEntry("restrictToInvolvedUsers", false);
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
