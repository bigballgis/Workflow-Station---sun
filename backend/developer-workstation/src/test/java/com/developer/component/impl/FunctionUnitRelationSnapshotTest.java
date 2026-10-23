package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.relationtable.SystemUserRelationTable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionUnitRelationSnapshotTest {
    @ParameterizedTest @ValueSource(longs = {-1_000_000_001L, 73L})
    void snapshotAndZipRetainRelatedTableIdentity(long relationId) throws Exception {
        var units = mock(FunctionUnitRepository.class);
        var forms = mock(FormDefinitionRepository.class);
        var unit = FunctionUnit.builder().id(91L).code("relation-proof").name("Relation proof")
                .currentVersion("1.0.0").build();
        when(units.findById(91L)).thenReturn(Optional.of(unit));
        var binding = FormTableBinding.builder().id(81L).relationTableId(relationId)
                .bindingType(BindingType.RELATED).bindingMode(BindingMode.READONLY).build();
        var form = FormDefinition.builder().id(17L).formName("Request").formType(FormType.PROCESS)
                .tableBindings(List.of(binding)).configJson(Map.of()).build();
        when(forms.findByFunctionUnitIdWithBindings(91L)).thenReturn(List.of(form));
        var mapper = new ObjectMapper();
        var exporter = ExportImportTestComponents.exporter(units, mock(TableDefinitionRepository.class), forms,
                mock(ActionDefinitionRepository.class), mock(DecisionDefinitionRepository.class),
                mock(FormStageBindingRepository.class), mock(TableRelationRepository.class),
                mock(FunctionUnitWorkspaceAccessService.class), mapper);
        var structures = mock(RelationTableStructurePortability.class);
        String name = relationId == SystemUserRelationTable.ID ? SystemUserRelationTable.NAME : "reviewers";
        when(structures.exportByIds(List.of(relationId))).thenReturn(List.of(Map.of(
                "relationTableId", relationId, "tableName", name, "fields", List.of())));
        ReflectionTestUtils.setField(exporter, "relationTablePortability", structures);
        var payload = exporter.buildVersionSnapshotPayload(91L);
        var exportedForm = (Map<?, ?>) ((List<?>) payload.get("forms")).get(0);
        assertBinding(exportedForm, name, relationId);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(exporter.exportFunctionUnit(91L)))) {
            boolean found = false;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("forms/form_0.json")) {
                    assertBinding(mapper.readValue(zip.readAllBytes(), Map.class), name, relationId);
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    private void assertBinding(Map<?, ?> form, String name, long id) {
        var binding = (Map<?, ?>) ((List<?>) form.get("tableBindings")).get(0);
        assertEquals(name, binding.get("tableName"));
        assertEquals(id, ((Number) binding.get("relationTableId")).longValue());
    }
}
