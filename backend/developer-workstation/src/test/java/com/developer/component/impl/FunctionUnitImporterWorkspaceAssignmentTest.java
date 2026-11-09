package com.developer.component.impl;

import com.developer.client.AdminCenterAutomationFlowClient;
import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnitDevGroupAssignment;
import com.developer.exception.DeveloperBusinessException;
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
import com.developer.security.WorkspaceAccessAction;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A function unit created by import lands in exactly one workspace (platform access model §6.3): the team of this
 * environment with the package's team code; an old package without a team needs the workspace the user chose. A new
 * version of an existing unit keeps its workspace and needs the unit's Technical Lead.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FunctionUnitImporter — workspace assignment")
class FunctionUnitImporterWorkspaceAssignmentTest {

    private static final String MANIFEST =
            "{\"name\":\"ImportedFU\",\"code\":\"imported-fu\",\"version\":\"1.0.0\"}";
    private static final String MANIFEST_WITH_TEAM = "{\"name\":\"ImportedFU\",\"code\":\"imported-fu\","
            + "\"version\":\"1.0.0\",\"team\":{\"code\":\"PAYMENT_OPS\",\"name\":\"Payment Ops\"}}";

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
    @Mock private DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;

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
                sequenceSynchronizer,
                automationFlowClient);

        when(functionUnitRepository.existsByCode(any())).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private static MockMultipartFile manifestOnlyPackage() throws Exception {
        return packageWith(MANIFEST);
    }

    private static MockMultipartFile packageWith(String manifest) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new MockMultipartFile("file", "fu.zip", "application/zip", baos.toByteArray());
    }

    @Test
    @DisplayName("package with a team → the team with that code in this environment")
    void newUnitLandsInThePackagesTeam() throws Exception {
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(workspaceAccessService.resolvePackageWorkspace("PAYMENT_OPS", "Payment Ops")).thenReturn("vg-payment-ops");

        impl.importFunctionUnit(packageWith(MANIFEST_WITH_TEAM), null, null);

        ArgumentCaptor<FunctionUnitDevGroupAssignment> saved =
                ArgumentCaptor.forClass(FunctionUnitDevGroupAssignment.class);
        verify(devGroupAssignmentRepository).save(saved.capture());
        assertEquals(100L, saved.getValue().getFunctionUnitId());
        assertEquals("vg-payment-ops", saved.getValue().getVirtualGroupId());
    }

    @Test
    @DisplayName("team missing in this environment → refused before any unit is written")
    void missingTeamAbortsBeforeAnyWrite() throws Exception {
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(workspaceAccessService.resolvePackageWorkspace("PAYMENT_OPS", "Payment Ops")).thenThrow(
                new DeveloperBusinessException("IMPORT_TEAM_NOT_FOUND", "create team Payment Ops (PAYMENT_OPS) first"));

        DeveloperBusinessException error = assertThrows(DeveloperBusinessException.class,
                () -> impl.importFunctionUnit(packageWith(MANIFEST_WITH_TEAM), null, null));

        assertEquals("IMPORT_TEAM_NOT_FOUND", error.getErrorCode());
        verify(functionUnitRepository, never()).save(any());
        verify(devGroupAssignmentRepository, never()).save(any());
        verify(sequenceSynchronizer, never()).synchronizeAll();
    }

    @Test
    @DisplayName("old package without a team → needs the chosen workspace")
    void oldPackageNeedsAChosenWorkspace() throws Exception {
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());

        DeveloperBusinessException error = assertThrows(DeveloperBusinessException.class,
                () -> impl.importFunctionUnit(manifestOnlyPackage(), null, null));
        assertEquals("IMPORT_WORKSPACE_REQUIRED", error.getErrorCode());
        verify(functionUnitRepository, never()).save(any());

        when(workspaceAccessService.resolveTargetWorkspace(List.of("vg-team-a"))).thenReturn("vg-team-a");
        impl.importFunctionUnit(manifestOnlyPackage(), null, "vg-team-a");

        ArgumentCaptor<FunctionUnitDevGroupAssignment> saved =
                ArgumentCaptor.forClass(FunctionUnitDevGroupAssignment.class);
        verify(devGroupAssignmentRepository).save(saved.capture());
        assertEquals("vg-team-a", saved.getValue().getVirtualGroupId());
    }

    @Test
    @DisplayName("a chosen workspace other than the package's team → refused")
    void choiceContradictingThePackageIsRefused() throws Exception {
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(workspaceAccessService.resolvePackageWorkspace("PAYMENT_OPS", "Payment Ops")).thenReturn("vg-payment-ops");

        DeveloperBusinessException error = assertThrows(DeveloperBusinessException.class,
                () -> impl.importFunctionUnit(packageWith(MANIFEST_WITH_TEAM), null, "vg-team-a"));

        assertEquals("IMPORT_WORKSPACE_CONFLICT", error.getErrorCode());
        verify(functionUnitRepository, never()).save(any());
    }

    @Test
    @DisplayName("new version of an existing unit → keeps its workspace, needs its Technical Lead")
    void newVersionKeepsExistingAssignments() throws Exception {
        FunctionUnit existing = FunctionUnit.builder().name("ImportedFU").code("imported-fu").build();
        existing.setId(100L);
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.of(existing));

        impl.importFunctionUnit(packageWith(MANIFEST_WITH_TEAM), null, null);

        verify(workspaceAccessService, never()).resolvePackageWorkspace(any(), any());
        verify(workspaceAccessService, never()).resolveTargetWorkspace(any());
        verify(devGroupAssignmentRepository, never()).save(any());
        verify(workspaceAccessService).assertCanAccess(100L, WorkspaceAccessAction.IMPORT_VERSION);
    }

    @Test
    void forbiddenExistingUnitAbortsBeforeAnyWrite() throws Exception {
        FunctionUnit existing = FunctionUnit.builder().name("ImportedFU").code("imported-fu").build();
        existing.setId(100L);
        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.of(existing));
        doThrow(new FunctionUnitWorkspaceAccessDeniedException("Developers cannot import a new version"))
                .when(workspaceAccessService).assertCanAccess(100L, WorkspaceAccessAction.IMPORT_VERSION);

        assertThrows(FunctionUnitWorkspaceAccessDeniedException.class,
                () -> impl.importFunctionUnit(manifestOnlyPackage(), null, null));
        verify(functionUnitRepository, never()).save(any());
        verify(processDefinitionRepository, never()).save(any());
        verify(devGroupAssignmentRepository, never()).save(any());
        verify(sequenceSynchronizer, never()).synchronizeAll();
    }
}
