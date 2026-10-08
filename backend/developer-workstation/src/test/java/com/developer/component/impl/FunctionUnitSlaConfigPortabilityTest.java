package com.developer.component.impl;

import com.developer.dto.SlaConfig;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The SLA due date mapping must survive export → import (and therefore version rollback, which
 * reuses {@link FunctionUnitImportWriter}).
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitSlaConfigPortabilityTest {

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private DecisionDefinitionRepository decisionDefinitionRepository;
    @Mock private FormStageBindingRepository formStageBindingRepository;
    @Mock private TableRelationRepository tableRelationRepository;
    @Mock private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;

    private FunctionUnitExporter exporter;
    private FunctionUnitImportWriter importWriter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        exporter = ExportImportTestComponents.exporter(
                functionUnitRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formStageBindingRepository,
                tableRelationRepository,
                functionUnitWorkspaceAccessService,
                objectMapper);
        importWriter = new FunctionUnitImportWriter(
                tableDefinitionRepository,
                mock(com.developer.repository.FieldDefinitionRepository.class),
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                mock(com.developer.repository.EmailConnectionRepository.class),
                mock(com.developer.repository.EmailTemplateRepository.class),
                mock(com.developer.repository.FormTableBindingRepository.class),
                mock(com.developer.repository.LinkFormComponentRepository.class),
                mock(com.developer.repository.TableRelationRepository.class),
                mock(com.developer.repository.SubTableViewConfigRepository.class),
                mock(DmnXmlParser.class),
                objectMapper);
        lenient().when(tableDefinitionRepository.save(any(TableDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void exportImport_roundTripsSlaConfigThroughJson() throws Exception {
        FunctionUnit fu = FunctionUnit.builder().id(1L).name("Cases").code("cases").build();
        TableDefinition table = TableDefinition.builder()
                .id(10L).functionUnit(fu).tableName("case_main").tableType(TableType.MAIN)
                .slaConfig(SlaConfig.builder()
                        .startDateSource(SlaConfig.StartDateSource.FIELD)
                        .startDateField("received_date")
                        .dueDateField("due_date")
                        .build())
                .build();
        table.setFieldDefinitions(new ArrayList<>(List.of(
                FieldDefinition.builder().fieldName("received_date").dataType(DataType.DATE).sortOrder(0).build(),
                FieldDefinition.builder().fieldName("due_date").dataType(DataType.DATE).sortOrder(1).build())));

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(fu));
        when(tableDefinitionRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(table));
        when(formDefinitionRepository.findByFunctionUnitIdWithBindings(1L)).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
        when(decisionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
        when(tableRelationRepository.findByFunctionUnitId(1L)).thenReturn(List.of());

        Map<String, Object> payload = exporter.buildVersionSnapshotPayload(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) payload.get("tables");
        // Snapshots and ZIP entries are stored as JSON: import must read the parsed map, not the Java object.
        ObjectMapper objectMapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(
                objectMapper.writeValueAsString(tables.get(0)), Map.class);

        TableDefinition imported = importWriter.importTable(fu, parsed);

        assertEquals(SlaConfig.StartDateSource.FIELD, imported.getSlaConfig().getStartDateSource());
        assertEquals("received_date", imported.getSlaConfig().getStartDateField());
        assertEquals("due_date", imported.getSlaConfig().getDueDateField());
    }

    @Test
    void import_withoutSlaConfigLeavesItNull() {
        Map<String, Object> tableData = new LinkedHashMap<>();
        tableData.put("tableName", "legacy");
        tableData.put("tableType", "MAIN");
        tableData.put("fields", List.of());

        TableDefinition imported = importWriter.importTable(FunctionUnit.builder().id(1L).build(), tableData);

        assertNull(imported.getSlaConfig());
    }
}
