package com.developer.component.impl;

import com.developer.entity.EmailMonitorRule;
import com.developer.entity.FunctionUnit;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.EmailMonitorRuleRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.LinkFormComponentRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rollback snapshot parity: export-format detection and email monitor binding remap on restore.
 */
@ExtendWith(MockitoExtension.class)
class VersionRollbackParityTest {

    @Mock
    private EmailMonitorRuleRepository emailMonitorRuleRepository;

    private FunctionUnitImportWriter importWriter;

    @BeforeEach
    void setUp() {
        importWriter = new FunctionUnitImportWriter(
                mock(TableDefinitionRepository.class),
                mock(FormDefinitionRepository.class),
                mock(ActionDefinitionRepository.class),
                mock(DecisionDefinitionRepository.class),
                mock(com.developer.repository.EmailConnectionRepository.class),
                emailMonitorRuleRepository,
                mock(com.developer.repository.EmailTemplateRepository.class),
                mock(FormTableBindingRepository.class),
                mock(LinkFormComponentRepository.class),
                mock(TableRelationRepository.class),
                mock(SubTableViewConfigRepository.class),
                mock(DmnXmlParser.class),
                new ObjectMapper());
    }

    @Test
    void isExportFormatSnapshot_detectsSchemaV2() {
        Map<String, Object> snapshot = Map.of(
                "snapshotSchemaVersion", FunctionUnitExporter.VERSION_SNAPSHOT_SCHEMA,
                "tables", List.of());
        assertThat(FunctionUnitSnapshotRestorer.isExportFormatSnapshot(snapshot)).isTrue();
    }

    @Test
    void isExportFormatSnapshot_legacyUsesTableDefinitions() {
        Map<String, Object> snapshot = Map.of("tableDefinitions", List.of());
        assertThat(FunctionUnitSnapshotRestorer.isExportFormatSnapshot(snapshot)).isFalse();
    }

    @Test
    void importEmailMonitorRule_remapsFormAndBindingIds() {
        FunctionUnit functionUnit = FunctionUnit.builder().id(48L).code("fu_demo").build();
        Map<Long, Long> formIdMapping = Map.of(10L, 110L);
        Map<Long, Long> bindingIdMapping = Map.of(20L, 220L);

        Map<String, Object> ruleData = new HashMap<>();
        ruleData.put("ruleUid", "rule-uid-1");
        ruleData.put("name", "Inbound MCY");
        ruleData.put("connectionUid", "conn-uid-1");
        ruleData.put("targetFormId", 10);
        ruleData.put("targetBindingId", "20");

        when(emailMonitorRuleRepository.save(any(EmailMonitorRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        importWriter.importEmailMonitorRule(functionUnit, ruleData, formIdMapping, bindingIdMapping);

        ArgumentCaptor<EmailMonitorRule> captor = ArgumentCaptor.forClass(EmailMonitorRule.class);
        verify(emailMonitorRuleRepository).save(captor.capture());
        EmailMonitorRule saved = captor.getValue();
        assertThat(saved.getTargetFormId()).isEqualTo(110L);
        assertThat(saved.getTargetBindingId()).isEqualTo("220");
        assertThat(saved.getProcessDefinitionKey()).isEqualTo("fu_demo");
        assertThat(saved.getFunctionUnit()).isSameAs(functionUnit);
    }
}
