package com.developer.component.impl;

import com.developer.dto.VersionedProcess;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.Version;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.VersionRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.util.XmlEncodingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading the diagram of one published version for read-only display — the thing a
 * pinned call actually runs, as opposed to the callee's live draft.
 */
@ExtendWith(MockitoExtension.class)
class VersionedProcessReaderTest {

    private static final long FU_ID = 7L;

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private VersionRepository versionRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private FunctionUnitWorkspaceAccessService workspaceAccessService;

    private VersionedProcessReader reader;

    @BeforeEach
    void setUp() {
        reader = new VersionedProcessReader(functionUnitRepository, versionRepository,
                processDefinitionRepository, workspaceAccessService, new ObjectMapper());

        FunctionUnit unit = new FunctionUnit();
        unit.setId(FU_ID);
        unit.setName("Vendor Check");
        unit.setCode("fu-vendor");
        unit.setCurrentVersion("2.0.0");
        lenient().when(functionUnitRepository.findById(FU_ID)).thenReturn(Optional.of(unit));
    }

    private static Version snapshot(String versionNumber, String json) {
        Version v = new Version();
        v.setVersionNumber(versionNumber);
        v.setSnapshotData(json.getBytes(StandardCharsets.UTF_8));
        v.setPublishedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return v;
    }

    @Test
    void returnsTheDiagramOfAPublishedVersion() {
        when(versionRepository.findByFunctionUnitIdAndVersionNumber(FU_ID, "1.2.0"))
                .thenReturn(Optional.of(snapshot("1.2.0",
                        "{\"process\":\"<bpmn:definitions>v120</bpmn:definitions>\"}")));

        VersionedProcess out = reader.read(FU_ID, "1.2.0");

        assertThat(out.getVersionNumber()).isEqualTo("1.2.0");
        assertThat(out.getCurrentVersion()).isEqualTo("2.0.0");
        assertThat(out.getBpmnXml()).contains("v120");
        assertThat(out.getPublishedAt()).isNotNull();
    }

    /** Snapshots written before the current format kept the diagram under another key. */
    @Test
    void readsTheLegacySnapshotKey() {
        when(versionRepository.findByFunctionUnitIdAndVersionNumber(FU_ID, "1.0.0"))
                .thenReturn(Optional.of(snapshot("1.0.0",
                        "{\"processXml\":\"<bpmn:definitions>legacy</bpmn:definitions>\"}")));

        assertThat(reader.read(FU_ID, "1.0.0").getBpmnXml()).contains("legacy");
    }

    /**
     * The current version may have no snapshot row yet, but a call can still be pinned to
     * it; the current design is served for that one case.
     */
    @Test
    void servesTheCurrentDesignForTheCurrentVersionWithoutASnapshot() {
        when(versionRepository.findByFunctionUnitIdAndVersionNumber(FU_ID, "2.0.0"))
                .thenReturn(Optional.empty());
        ProcessDefinition pd = new ProcessDefinition();
        pd.setBpmnXml(XmlEncodingUtil.encode("<bpmn:definitions>current</bpmn:definitions>"));
        when(processDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(Optional.of(pd));

        VersionedProcess out = reader.read(FU_ID, "2.0.0");

        assertThat(out.getBpmnXml()).contains("current");
        assertThat(out.getPublishedAt()).isNull();
    }

    /** A version that never existed is refused — never silently replaced by the draft. */
    @Test
    void refusesAVersionThatDoesNotExist() {
        when(versionRepository.findByFunctionUnitIdAndVersionNumber(FU_ID, "9.9.9"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.read(FU_ID, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checksWorkspaceAccessBeforeReading() {
        when(versionRepository.findByFunctionUnitIdAndVersionNumber(FU_ID, "1.2.0"))
                .thenReturn(Optional.of(snapshot("1.2.0", "{\"process\":\"<x/>\"}")));

        reader.read(FU_ID, "1.2.0");

        verify(workspaceAccessService).assertCanAccess(FU_ID, WorkspaceAccessAction.VIEW);
    }
}
