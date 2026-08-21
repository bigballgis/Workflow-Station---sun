package com.developer.service.impl;

import com.developer.dto.MainTableViewDtos.CreateMainTableViewRequest;
import com.developer.dto.MainTableViewDtos.MainTableViewAccessRuleDTO;
import com.developer.dto.MainTableViewDtos.MainTableViewFieldDTO;
import com.developer.dto.MainTableViewDtos.UpdateMainTableViewRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.MainTableViewAccess;
import com.developer.entity.MainTableViewField;
import com.developer.enums.MainTableViewAccessTargetType;
import com.developer.entity.TableDefinition;
import com.developer.enums.MainTableViewStatus;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.MainTableViewAccessRepository;
import com.developer.repository.MainTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.MainTableViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MainTableViewServiceImplTest {

    @Mock
    private MainTableViewConfigRepository viewConfigRepository;
    @Mock
    private MainTableViewAccessRepository mainTableViewAccessRepository;
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private MainTableViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MainTableViewServiceImpl(
                viewConfigRepository, mainTableViewAccessRepository,
                functionUnitRepository, tableDefinitionRepository, jdbcTemplate);
    }

    @Test
    void seedDefaultViewIfAbsent_createsMainViewWithFields() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        FieldDefinition field = FieldDefinition.builder()
                .fieldName("title")
                .displayName("Title")
                .sortOrder(0)
                .build();
        TableDefinition main = TableDefinition.builder()
                .id(10L)
                .tableType(TableType.MAIN)
                .fieldDefinitions(new ArrayList<>(List.of(field)))
                .build();

        when(viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(10L)).thenReturn(false);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findById(10L)).thenReturn(Optional.of(main));
        when(viewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaultViewIfAbsent(1L, 10L);

        ArgumentCaptor<MainTableViewConfig> captor = ArgumentCaptor.forClass(MainTableViewConfig.class);
        verify(viewConfigRepository).save(captor.capture());
        MainTableViewConfig saved = captor.getValue();
        assertThat(saved.getViewName()).isEqualTo("Main view");
        assertThat(saved.getIsDefault()).isTrue();
        assertThat(saved.getViewFields()).anyMatch(f -> "title".equals(f.getFieldName()));
        // MAIN tables get appended workflow system fields.
        assertThat(saved.getViewFields()).anyMatch(MainTableViewField::getIsSystemField);
    }

    @Test
    void seedDefaultViewIfAbsent_subTableHasNoSystemFields() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        FieldDefinition field = FieldDefinition.builder()
                .fieldName("line_item")
                .displayName("Line Item")
                .sortOrder(0)
                .build();
        TableDefinition sub = TableDefinition.builder()
                .id(20L)
                .tableType(TableType.SUB)
                .fieldDefinitions(new ArrayList<>(List.of(field)))
                .build();

        when(viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(20L)).thenReturn(false);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(viewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaultViewIfAbsent(1L, 20L);

        ArgumentCaptor<MainTableViewConfig> captor = ArgumentCaptor.forClass(MainTableViewConfig.class);
        verify(viewConfigRepository).save(captor.capture());
        MainTableViewConfig saved = captor.getValue();
        assertThat(saved.getViewFields()).anyMatch(f -> "line_item".equals(f.getFieldName()));
        assertThat(saved.getViewFields()).noneMatch(MainTableViewField::getIsSystemField);
    }

    @Test
    void seedDefaultViewIfAbsent_skipsEmptySubTable() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        TableDefinition emptySub = TableDefinition.builder()
                .id(21L)
                .tableType(TableType.SUB)
                .fieldDefinitions(new ArrayList<>())
                .build();

        when(viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(21L)).thenReturn(false);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findById(21L)).thenReturn(Optional.of(emptySub));

        service.seedDefaultViewIfAbsent(1L, 21L);

        verify(viewConfigRepository, never()).save(any());
    }

    @Test
    void seedDefaultViewIfAbsent_skipsRelationTable() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        TableDefinition relation = TableDefinition.builder()
                .id(22L)
                .tableType(TableType.RELATION)
                .fieldDefinitions(new ArrayList<>())
                .build();

        when(viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(22L)).thenReturn(false);
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findById(22L)).thenReturn(Optional.of(relation));

        service.seedDefaultViewIfAbsent(1L, 22L);

        verify(viewConfigRepository, never()).save(any());
    }

    @Test
    void seedDefaultViewIfAbsent_skipsWhenDefaultExists() {
        when(viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(10L)).thenReturn(true);
        service.seedDefaultViewIfAbsent(1L, 10L);
        verify(viewConfigRepository, never()).save(any());
    }

    @Test
    void deleteView_allowsDefaultView() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        MainTableViewConfig config = MainTableViewConfig.builder()
                .id(5L)
                .functionUnit(fu)
                .isDefault(true)
                .build();
        when(viewConfigRepository.findByIdWithFields(5L)).thenReturn(Optional.of(config));

        service.deleteView(1L, 5L);

        verify(viewConfigRepository).delete(config);
    }

    /**
     * The portal serves PUBLISHED views only, so resetting the status here would pull a live view
     * out of the portal as a side effect of picking a detail form — which is exactly why this has
     * its own endpoint instead of reusing updateView.
     */
    @Test
    void updateViewDetailForm_setsTheFormWithoutTouchingStatus() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        MainTableViewConfig view = MainTableViewConfig.builder()
                .id(5L)
                .functionUnit(fu)
                .status(MainTableViewStatus.PUBLISHED)
                .viewFields(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByIdWithFields(5L)).thenReturn(Optional.of(view));
        when(viewConfigRepository.save(any(MainTableViewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateViewDetailForm(1L, 5L, 99L);

        assertThat(view.getDetailFormId()).isEqualTo(99L);
        assertThat(view.getStatus()).isEqualTo(MainTableViewStatus.PUBLISHED);
    }

    /** Null is the meaningful "rows are not clickable" state, so it must be writable. */
    @Test
    void updateViewDetailForm_clearsTheFormWhenGivenNull() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        MainTableViewConfig view = MainTableViewConfig.builder()
                .id(5L)
                .functionUnit(fu)
                .detailFormId(99L)
                .status(MainTableViewStatus.PUBLISHED)
                .viewFields(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByIdWithFields(5L)).thenReturn(Optional.of(view));
        when(viewConfigRepository.save(any(MainTableViewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateViewDetailForm(1L, 5L, null);

        assertThat(view.getDetailFormId()).isNull();
        assertThat(view.getStatus()).isEqualTo(MainTableViewStatus.PUBLISHED);
    }

    @Test
    void publishViewsForFunctionUnit_setsPublishedStatus() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        MainTableViewConfig view = MainTableViewConfig.builder()
                .id(1L)
                .functionUnit(fu)
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(view));
        when(viewConfigRepository.saveAll(any())).thenReturn(List.of(view));

        service.publishViewsForFunctionUnit(1L);

        assertThat(view.getStatus()).isEqualTo(MainTableViewStatus.PUBLISHED);
    }

    @Test
    void createView_failsWhenTableMissing() {
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(FunctionUnit.builder().id(1L).build()));
        when(tableDefinitionRepository.findByIdWithFields(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createView(1L, new CreateMainTableViewRequest("Custom", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createView_rejectsRelationTable() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        TableDefinition relation = TableDefinition.builder()
                .id(30L)
                .functionUnit(fu)
                .tableType(TableType.RELATION)
                .fieldDefinitions(new ArrayList<>())
                .build();
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findByIdWithFields(30L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> service.createView(1L, new CreateMainTableViewRequest("Custom", 30L)))
                .isInstanceOf(DeveloperBusinessException.class);
    }

    @Test
    void updateView_rejectsPartialAccessRulesBuOnly() {
        MainTableViewConfig config = viewConfigForAccessUpdate();
        when(viewConfigRepository.findByIdWithFields(10L)).thenReturn(Optional.of(config));

        UpdateMainTableViewRequest request = new UpdateMainTableViewRequest(
                null,
                null,
                null,
                List.of(MainTableViewAccessRuleDTO.builder()
                        .targetType("BUSINESS_UNIT")
                        .targetId("bu-e2e-finance")
                        .build()),
                null,
                null,
                null);

        assertThatThrownBy(() -> service.updateView(1L, 10L, request))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BIZ_VIEW_ACCESS_BU_ROLE_PAIR");
    }

    @Test
    void updateView_rejectsPartialAccessRulesRoleOnly() {
        MainTableViewConfig config = viewConfigForAccessUpdate();
        when(viewConfigRepository.findByIdWithFields(10L)).thenReturn(Optional.of(config));

        UpdateMainTableViewRequest request = new UpdateMainTableViewRequest(
                null,
                null,
                null,
                List.of(MainTableViewAccessRuleDTO.builder()
                        .targetType("ROLE")
                        .targetId("role-manager")
                        .build()),
                null,
                null,
                null);

        assertThatThrownBy(() -> service.updateView(1L, 10L, request))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BIZ_VIEW_ACCESS_BU_ROLE_PAIR");
    }

    @Test
    void updateView_allowsPairedAccessRules() {
        MainTableViewConfig config = viewConfigForAccessUpdate();
        when(viewConfigRepository.findByIdWithFields(10L)).thenReturn(Optional.of(config));
        when(tableDefinitionRepository.findByIdWithFields(20L)).thenReturn(Optional.of(
                TableDefinition.builder().id(20L).fieldDefinitions(new ArrayList<>()).build()));
        when(viewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.queryForList(anyString(), eq(10L))).thenReturn(List.of());

        UpdateMainTableViewRequest request = new UpdateMainTableViewRequest(
                null,
                null,
                null,
                List.of(
                        MainTableViewAccessRuleDTO.builder()
                                .targetType("BUSINESS_UNIT")
                                .targetId("bu-e2e-finance")
                                .build(),
                        MainTableViewAccessRuleDTO.builder()
                                .targetType("ROLE")
                                .targetId("role-manager")
                                .build()),
                null,
                null,
                null);

        service.updateView(1L, 10L, request);

        verify(mainTableViewAccessRepository).deleteByViewConfigId(10L);
        verify(mainTableViewAccessRepository).flush();
        assertThat(config.getAccessRules()).hasSize(2);
        assertThat(config.getAccessRules()).extracting(MainTableViewAccess::getTargetType)
                .containsExactlyInAnyOrder(
                        MainTableViewAccessTargetType.BUSINESS_UNIT,
                        MainTableViewAccessTargetType.ROLE);
    }

    @Test
    void updateView_canRenameViewWithExistingAccessRulesInDb() {
        MainTableViewConfig config = viewConfigForAccessUpdate();
        when(viewConfigRepository.findByIdWithFields(10L)).thenReturn(Optional.of(config));
        when(tableDefinitionRepository.findByIdWithFields(20L)).thenReturn(Optional.of(
                TableDefinition.builder().id(20L).fieldDefinitions(new ArrayList<>()).build()));
        when(viewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.queryForList(anyString(), eq(10L))).thenReturn(List.of());

        UpdateMainTableViewRequest request = new UpdateMainTableViewRequest(
                "HMDC Case23",
                null,
                null,
                List.of(
                        MainTableViewAccessRuleDTO.builder()
                                .targetType("BUSINESS_UNIT")
                                .targetId("bu-e2e-finance")
                                .build(),
                        MainTableViewAccessRuleDTO.builder()
                                .targetType("ROLE")
                                .targetId("role-manager")
                                .build()),
                null,
                null,
                null);

        service.updateView(1L, 10L, request);

        verify(mainTableViewAccessRepository).deleteByViewConfigId(10L);
        verify(mainTableViewAccessRepository).flush();
        assertThat(config.getViewName()).isEqualTo("HMDC Case23");
    }

    @Test
    void updateView_acceptsFkDisplayColumnWhenSourceIsForeignKey() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        FieldDefinition caseId = FieldDefinition.builder()
                .fieldName("case_id")
                .displayName("Case ID")
                .isForeignKey(true)
                .refTableId(10L)
                .refPrimaryKeyFields(List.of("case_number"))
                .sortOrder(0)
                .build();
        TableDefinition attachment = TableDefinition.builder()
                .id(20L)
                .functionUnit(fu)
                .tableType(TableType.SUB)
                .fieldDefinitions(new ArrayList<>(List.of(caseId)))
                .build();
        MainTableViewConfig config = MainTableViewConfig.builder()
                .id(10L)
                .functionUnit(fu)
                .mainTableId(20L)
                .viewName("Attachment")
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .accessRules(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByIdWithFields(10L)).thenReturn(Optional.of(config));
        when(tableDefinitionRepository.findByIdWithFields(20L)).thenReturn(Optional.of(attachment));
        when(viewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.queryForList(anyString(), eq(10L))).thenReturn(List.of());

        UpdateMainTableViewRequest request = new UpdateMainTableViewRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(MainTableViewFieldDTO.builder()
                        .fieldName("case_id@legal_hold")
                        .displayLabel("Legal Hold")
                        .columnType("fk_display")
                        .lookupSourceField("case_id")
                        .lookupDisplayField("legal_hold")
                        .visible(true)
                        .build()));

        service.updateView(1L, 10L, request);

        assertThat(config.getViewFields()).hasSize(1);
        MainTableViewField saved = config.getViewFields().get(0);
        assertThat(saved.getColumnType()).isEqualTo("fk_display");
        assertThat(saved.getFieldName()).isEqualTo("case_id@legal_hold");
        assertThat(saved.getLookupSourceField()).isEqualTo("case_id");
        assertThat(saved.getLookupDisplayField()).isEqualTo("legal_hold");
    }

    @Test
    void updateView_rejectsFkDisplayWhenSourceNotForeignKey() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        FieldDefinition fileName = FieldDefinition.builder()
                .fieldName("file_name")
                .displayName("File")
                .isForeignKey(false)
                .sortOrder(0)
                .build();
        TableDefinition attachment = TableDefinition.builder()
                .id(20L)
                .functionUnit(fu)
                .tableType(TableType.SUB)
                .fieldDefinitions(new ArrayList<>(List.of(fileName)))
                .build();
        MainTableViewConfig config = MainTableViewConfig.builder()
                .id(10L)
                .functionUnit(fu)
                .mainTableId(20L)
                .viewName("Attachment")
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .accessRules(new ArrayList<>())
                .build();
        when(viewConfigRepository.findByIdWithFields(10L)).thenReturn(Optional.of(config));
        when(tableDefinitionRepository.findByIdWithFields(20L)).thenReturn(Optional.of(attachment));

        UpdateMainTableViewRequest request = new UpdateMainTableViewRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(MainTableViewFieldDTO.builder()
                        .fieldName("file_name@x")
                        .columnType("fk_display")
                        .lookupSourceField("file_name")
                        .lookupDisplayField("x")
                        .visible(true)
                        .build()));

        assertThatThrownBy(() -> service.updateView(1L, 10L, request))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo("BIZ_VIEW_FK_DISPLAY_SOURCE");
    }

    private MainTableViewConfig viewConfigForAccessUpdate() {
        FunctionUnit fu = FunctionUnit.builder().id(1L).build();
        return MainTableViewConfig.builder()
                .id(10L)
                .functionUnit(fu)
                .mainTableId(20L)
                .viewName("Case")
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .accessRules(new ArrayList<>())
                .build();
    }
}
