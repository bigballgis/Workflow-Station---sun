package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.ImportResult;
import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.FunctionUnitTeamForbiddenException;
import com.admin.repository.ActionDefinitionRepository;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.service.FunctionUnitAccessService;
import com.admin.service.FunctionUnitAuditAccessService;
import com.admin.service.FunctionUnitTeamService;
import com.admin.service.FunctionUnitTeamService.ImportChannel;
import com.admin.service.FunctionUnitTeamService.PackageTeam;
import com.admin.util.ChecksumUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Where an imported unit lands, decided before anything is written (platform access model §8.1, D-23, D-34). */
class FunctionUnitImportTeamTest {

    private static final String FILE = "UEsDBBQAAAAIAA==";
    private static final PackageTeam TEAM_B = new PackageTeam("TEAM_B", "Team B");

    private final FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
    private final FunctionUnitPackageParser parser = mock(FunctionUnitPackageParser.class);
    private final RelationTableStructureImporter relationImporter = mock(RelationTableStructureImporter.class);
    private final FunctionUnitTeamService teamService = mock(FunctionUnitTeamService.class);
    private FunctionUnitImportComponent component;

    @BeforeEach
    void setUp() throws Exception {
        component = new FunctionUnitImportComponent(repository, mock(FunctionUnitDependencyRepository.class),
                mock(FunctionUnitContentRepository.class), mock(FunctionUnitAccessRepository.class),
                mock(FunctionUnitAccessService.class), mock(FunctionUnitAuditAccessService.class), parser,
                mock(ActionDefinitionRepository.class), mock(FunctionUnitVersionComponent.class), relationImporter,
                new ObjectMapper(), mock(I18nService.class), mock(EmailConnectionSyncComponent.class),
                mock(EmailMonitorSyncComponent.class), mock(ImportBpmnStructureValidator.class),
                mock(ImportViewAccessValidator.class), teamService);
        when(parser.parseBase64Zip(anyString())).thenReturn(parsed(TEAM_B));
        when(repository.findLatestByCode("fu-x")).thenReturn(Optional.empty());
        when(repository.findLatestByName(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(FunctionUnit.class))).thenAnswer(call -> call.getArgument(0));
        when(teamService.teamOf(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void aDeploymentLandsInThePackageTeamOnTheNewVersionRow() {
        when(teamService.resolveImportTeam(ImportChannel.DEPLOYMENT, TEAM_B, null, null, "u1")).thenReturn("TEAM_B");

        ImportResult result = component.importFunctionPackage(request(null), "u1", ImportChannel.DEPLOYMENT);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<FunctionUnit> saved = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getTeamCode()).isEqualTo("TEAM_B");
    }

    @Test
    void theDialogsChoiceAndTheUnitsCurrentTeamGoToTheDecision() {
        FunctionUnit existing = unit("1.0.0", "TEAM_A");
        when(repository.findLatestByCode("fu-x")).thenReturn(Optional.of(existing));
        when(repository.findByCodeAndVersion("fu-x", "1.0.1")).thenReturn(Optional.empty());
        when(teamService.teamOf("fu-x")).thenReturn(Optional.of("TEAM_A"));
        when(teamService.resolveImportTeam(ImportChannel.MANUAL, TEAM_B, "TEAM_A", "TEAM_A", "u1")).thenReturn("TEAM_A");

        component.importFunctionPackage(request("TEAM_A"), "u1", ImportChannel.MANUAL);

        verify(teamService).resolveImportTeam(ImportChannel.MANUAL, TEAM_B, "TEAM_A", "TEAM_A", "u1");
    }

    /** D-61: a stock unit without a team goes to the decision as a new one; the new row carries the chosen team. */
    @Test
    void aStockUnitWithoutATeamIsClaimedByTheTeamChosenInTheDialog() {
        FunctionUnit stock = unit("1.0.0", null);
        when(repository.findLatestByCode("fu-x")).thenReturn(Optional.of(stock));
        when(repository.findByCodeAndVersion("fu-x", "1.0.1")).thenReturn(Optional.empty());
        when(teamService.resolveImportTeam(ImportChannel.MANUAL, TEAM_B, "TEAM_A", null, "lead-a")).thenReturn("TEAM_A");

        ImportResult result = component.importFunctionPackage(request("TEAM_A"), "lead-a", ImportChannel.MANUAL);

        assertThat(result.isVersioned()).isTrue();
        ArgumentCaptor<FunctionUnit> saved = ArgumentCaptor.forClass(FunctionUnit.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(row -> {
            assertThat(row.getVersion()).isEqualTo("1.0.1");
            assertThat(row.getTeamCode()).isEqualTo("TEAM_A");
        });
    }

    @Test
    void aRefusedTeamWritesNothing() {
        when(teamService.resolveImportTeam(any(), any(), any(), any(), any()))
                .thenThrow(new FunctionUnitTeamForbiddenException("You cannot deploy function units of team Team B"));

        assertThatThrownBy(() -> component.importFunctionPackage(request(null), "u1", ImportChannel.DEPLOYMENT))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class);
        verify(repository, never()).save(any());
        verify(relationImporter, never()).importRelationTables(any(), any(), any());
    }

    @Test
    void reImportingTheSameVersionAfterAMoveRecordsTheNewTeamOnThatRow() {
        FunctionUnit published = unit("1.0.1", "TEAM_A");
        published.setChecksum(ChecksumUtils.sha256Hex(FILE));
        when(repository.findLatestByCode("fu-x")).thenReturn(Optional.of(published));
        when(repository.findByCodeAndVersion("fu-x", "1.0.1")).thenReturn(Optional.of(published));
        when(teamService.teamOf("fu-x")).thenReturn(Optional.of("TEAM_A"));
        when(teamService.resolveImportTeam(ImportChannel.DEPLOYMENT, TEAM_B, null, "TEAM_A", "u2")).thenReturn("TEAM_B");

        ImportResult result = component.importFunctionPackage(request(null), "u2", ImportChannel.DEPLOYMENT);

        assertThat(result.isVersioned()).isTrue();
        assertThat(published.getTeamCode()).isEqualTo("TEAM_B");
        assertThat(published.getImportedBy()).isEqualTo("u2");
        verify(repository).save(published);
    }

    /** v1.0.0 was A, v1.0.1 (B) was imported later: re-importing v1.0.0 into A makes that row the latest again. */
    @Test
    void reImportingAnOlderVersionIntoItsOwnTeamStillMovesTheUnit() {
        FunctionUnit older = unit("1.0.1", "TEAM_A");
        older.setChecksum(ChecksumUtils.sha256Hex(FILE));
        older.setImportedBy("u1");
        when(repository.findLatestByCode("fu-x")).thenReturn(Optional.of(unit("1.0.2", "TEAM_B")));
        when(repository.findByCodeAndVersion("fu-x", "1.0.1")).thenReturn(Optional.of(older));
        when(teamService.teamOf("fu-x")).thenReturn(Optional.of("TEAM_B"));
        when(teamService.resolveImportTeam(ImportChannel.MANUAL, TEAM_B, "TEAM_A", "TEAM_B", "u2")).thenReturn("TEAM_A");

        component.importFunctionPackage(request("TEAM_A"), "u2", ImportChannel.MANUAL);

        assertThat(older.getTeamCode()).isEqualTo("TEAM_A");
        assertThat(older.getImportedBy()).isEqualTo("u2");
        verify(repository).save(older);
    }

    @Test
    void reImportingTheSameVersionInTheSameTeamChangesNothing() {
        FunctionUnit published = unit("1.0.1", "TEAM_B");
        published.setChecksum(ChecksumUtils.sha256Hex(FILE));
        published.setImportedBy("u1");
        when(repository.findLatestByCode("fu-x")).thenReturn(Optional.of(published));
        when(repository.findByCodeAndVersion("fu-x", "1.0.1")).thenReturn(Optional.of(published));
        when(teamService.teamOf("fu-x")).thenReturn(Optional.of("TEAM_B"));
        when(teamService.resolveImportTeam(eq(ImportChannel.DEPLOYMENT), eq(TEAM_B), any(), eq("TEAM_B"), eq("u2")))
                .thenReturn("TEAM_B");

        component.importFunctionPackage(request(null), "u2", ImportChannel.DEPLOYMENT);

        assertThat(published.getImportedBy()).isEqualTo("u1");
        verify(repository, never()).save(any());
    }

    private static FunctionUnitImportRequest request(String teamCode) {
        return FunctionUnitImportRequest.builder().fileName("fu.zip").fileContent(FILE).teamCode(teamCode).build();
    }

    private static FunctionUnit unit(String version, String teamCode) {
        return FunctionUnit.builder().id("id-" + version).code("fu-x").name("FU X").version(version)
                .status(FunctionUnitStatus.DEPLOYED).teamCode(teamCode).build();
    }

    private static FunctionUnitPackageParser.ParsedImportPackage parsed(PackageTeam team) {
        return FunctionUnitPackageParser.ParsedImportPackage.builder()
                .packageContent(FunctionUnitManagerComponent.FunctionPackageContent.builder()
                        .code("fu-x").name("FU X").version("1.0.1")
                        .dependencies(new ArrayList<>()).contents(new ArrayList<>()).build())
                .forms(List.of()).actions(List.of()).relationTables(List.of()).connections(List.of())
                .emailMonitors(List.of()).team(team).build();
    }
}
