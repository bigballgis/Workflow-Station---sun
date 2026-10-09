package com.developer.component.impl;

import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.repository.*;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.service.MainTableViewService;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionSnapshotPortabilityTest {
    @Mock DecisionDefinitionRepository decisions;
    @InjectMocks FunctionUnitImportWriter writer;

    @Test void exporterKeepsDraftMetadataAndLegacyXmlList() {
        FunctionUnitRepository units = mock(FunctionUnitRepository.class);
        when(units.findById(42L)).thenReturn(Optional.of(FunctionUnit.builder().id(42L).build()));
        when(decisions.findByFunctionUnitId(42L)).thenReturn(List.of(DecisionDefinition.builder()
                .id(7L).decisionKey("draft").decisionName("Draft name").description("Draft description")
                .hitPolicy("UNIQUE").build()));
        FunctionUnitExporter exporter = ExportImportTestComponents.exporter(units,
                mock(TableDefinitionRepository.class), mock(FormDefinitionRepository.class),
                mock(ActionDefinitionRepository.class), decisions, mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class), mock(FunctionUnitWorkspaceAccessService.class), new ObjectMapper());
        Map<String, Object> snapshot = exporter.buildVersionSnapshotPayload(42L);
        assertEquals(List.of(), snapshot.get("decisions"));
        List<?> records = (List<?>) snapshot.get("decisionDefinitions");
        Map<?, ?> record = (Map<?, ?>) records.get(0);
        assertEquals("draft", record.get("decisionKey"));
        assertEquals("UNIQUE", record.get("hitPolicy"));
        assertTrue(record.containsKey("dmnXml"));
        assertNull(record.get("dmnXml"));
    }

    @Test void restorePrefersCompleteRecordsAndPreservesNullXmlAndMetadata() {
        FunctionUnit fu = FunctionUnit.builder().id(42L).code("qa").build();
        AtomicLong ids = new AtomicLong(100);
        when(decisions.save(any())).thenAnswer(inv -> {
            DecisionDefinition saved = inv.getArgument(0); saved.setId(ids.incrementAndGet()); return saved;
        });
        FunctionUnitSnapshotRestorer restorer = new FunctionUnitSnapshotRestorer(writer,
                mock(EmailMonitorRulePortability.class), mock(FormDefinitionRepository.class),
                mock(ProcessDefinitionRepository.class), mock(ProcessBpmnStaleIdFixer.class),
                mock(RelationTableStructurePortability.class), mock(MainTableViewPortability.class),
                mock(MainTableViewService.class), mock(EntityManager.class),
                mock(DeveloperWorkstationSequenceSynchronizer.class), mock(FormTableBindingRestorer.class),
                new FunctionUnitBasicPortability(mock(IconRepository.class)));
        Map<String, Object> record = new HashMap<>();
        record.put("decisionId", 7L); record.put("decisionKey", "draft"); record.put("decisionName", "Draft name");
        record.put("description", "Draft description"); record.put("hitPolicy", "UNIQUE"); record.put("dmnXml", null);
        restorer.restore(fu, Map.of("snapshotSchemaVersion", 2, "tables", List.of(), "forms", List.of(),
                "actions", List.of(), "decisions", List.of("ignored legacy XML"), "decisionDefinitions", List.of(record)));
        var captor = org.mockito.ArgumentCaptor.forClass(DecisionDefinition.class);
        verify(decisions).save(captor.capture());
        DecisionDefinition restored = captor.getValue();
        assertEquals("draft", restored.getDecisionKey()); assertEquals("Draft name", restored.getDecisionName());
        assertEquals("Draft description", restored.getDescription()); assertEquals("UNIQUE", restored.getHitPolicy());
        assertNull(restored.getDmnXml()); assertSame(fu, restored.getFunctionUnit());
        assertNotEquals(7L, restored.getId());
    }

    @Test void malformedRecordIsRejectedInsteadOfSilentlyDropped() {
        assertThrows(DeveloperBusinessException.class,
                () -> writer.importDecisionRecord(FunctionUnit.builder().id(42L).build(), Map.of("decisionName", "Invalid")));
        verifyNoInteractions(decisions);
    }

    @Test void restoredNumericBindingsUseNewDecisionIdsButStableKeysStayUnchanged() {
        String xml = "<definitions><serviceTask id='a' decisionId='7' decisionTableReferenceKey='approval'/></definitions>";
        String restored = FunctionUnitSnapshotRestorer.rewriteDecisionReferences(xml, Map.of(7L, 101L));
        assertTrue(restored.contains("decisionId=\"101\""));
        assertTrue(restored.contains("decisionTableReferenceKey=\"approval\""));
        assertEquals(xml, FunctionUnitSnapshotRestorer.rewriteDecisionReferences(xml, Map.of()));
    }

    @Test void referenceRewriteDoesNotModifyScriptsCommentsOrOversizedUnmappedIds() {
        String xml = "<definitions xmlns:c='urn:custom'><serviceTask id='a' c:decisionId='7'/>"
                + "<serviceTask id='b' decisionId='9999999999999999999999'/>"
                + "<scriptTask><script><![CDATA[decisionId='7']]></script></scriptTask>"
                + "<!-- decisionId='7' --></definitions>";
        String restored = FunctionUnitSnapshotRestorer.rewriteDecisionReferences(xml, Map.of(7L, 101L));
        assertTrue(restored.contains("c:decisionId=\"101\""));
        assertTrue(restored.contains("decisionId='7'"));
        assertTrue(restored.contains("9999999999999999999999"));
    }

    @Test void referenceRewriteRejectsDoctype() {
        assertThrows(DeveloperBusinessException.class, () -> FunctionUnitSnapshotRestorer.rewriteDecisionReferences(
                "<!DOCTYPE definitions [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><definitions>&x;</definitions>", Map.of(7L, 101L)));
    }
}
