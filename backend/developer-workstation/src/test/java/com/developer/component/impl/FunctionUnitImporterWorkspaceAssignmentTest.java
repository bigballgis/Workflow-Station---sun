package com.developer.component.impl;

import com.developer.client.AdminCenterAutomationFlowClient;
import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnitDevGroupAssignment;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A function unit created by import must land in a workspace, resolved exactly as on create;
 * otherwise it has no dev-group assignment and no workspace lists it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FunctionUnitImporter — workspace assignment")
class FunctionUnitImporterWorkspaceAssignmentTest {

    private static final String MANIFEST =
            "{\"name\":\"ImportedFU\",\"code\":\"imported-fu\",\"version\":\"1.0.0\"}";

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private DecisionDefinitionRepository decisionDefinitionRepository;
    @Mock private DmnXmlParser dmnXmlParser;
    @Mock private AdminCenterAutomationFlowClient automationFlowClient;
    @Mock private FunctionUnitWorkspaceAccessService workspaceAccessService;
    @Mock private FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository;

    private ExportImportComponentImpl impl;

    @BeforeEach
    void setUp() {
        impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                dmnXmlParser,
                workspaceAccessService,
                devGroupAssignmentRepository,
                mock(jakarta.persistence.EntityManager.class),
                new ObjectMapper(),
                mock(DeveloperWorkstationSequenceSynchronizer.class),
                automationFlowClient);

        when(functionUnitRepository.existsByCode(any())).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private static MockMultipartFile manifestOnlyPackage() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(MANIFEST.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new MockMultipartFile("file", "fu.zip", "application/zip", baos.toByteArray());
    }

    @Test
    @DisplayName("new unit → assigned to the workspace resolved as on create")
    void newUnitIsAssignedToResolvedWorkspace() throws Exception {
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(workspaceAccessService.resolveCreationTeamGroupIds(null)).thenReturn(List.of("vg-dev-public"));

        impl.importFunctionUnit(manifestOnlyPackage(), null);

        ArgumentCaptor<FunctionUnitDevGroupAssignment> saved =
                ArgumentCaptor.forClass(FunctionUnitDevGroupAssignment.class);
        verify(devGroupAssignmentRepository).save(saved.capture());
        assertEquals(100L, saved.getValue().getFunctionUnitId());
        assertEquals("vg-dev-public", saved.getValue().getVirtualGroupId());
    }

    @Test
    @DisplayName("no workspace can be resolved → import fails before any unit is written")
    void unresolvableWorkspaceAbortsBeforeAnyWrite() throws Exception {
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(workspaceAccessService.resolveCreationTeamGroupIds(null)).thenThrow(
                new FunctionUnitWorkspaceAccessDeniedException("Please select a team for this function unit"));

        assertThrows(FunctionUnitWorkspaceAccessDeniedException.class,
                () -> impl.importFunctionUnit(manifestOnlyPackage(), null));

        verify(functionUnitRepository, never()).save(any());
        verify(devGroupAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("new version of an existing unit → keeps its assignments, resolves none")
    void newVersionKeepsExistingAssignments() throws Exception {
        FunctionUnit existing = FunctionUnit.builder().name("ImportedFU").code("imported-fu").build();
        existing.setId(100L);
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.of(existing));

        impl.importFunctionUnit(manifestOnlyPackage(), null);

        verify(workspaceAccessService, never()).resolveCreationTeamGroupIds(any());
        verify(devGroupAssignmentRepository, never()).save(any());
    }
}
