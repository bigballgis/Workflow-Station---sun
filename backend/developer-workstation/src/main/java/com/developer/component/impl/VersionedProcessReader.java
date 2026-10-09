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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Reads the process diagram of one published version of a Function Unit.
 *
 * <p>Published versions are stored as JSON snapshots in {@code dw_versions}; the diagram
 * sits under {@code process} (older snapshots used {@code processXml}). Nothing is
 * modified here — the snapshot is read and handed back for display only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionedProcessReader {

    private final FunctionUnitRepository functionUnitRepository;
    private final VersionRepository versionRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FunctionUnitWorkspaceAccessService workspaceAccessService;
    private final ObjectMapper objectMapper;

    /**
     * The diagram of {@code versionNumber} of the given unit.
     *
     * <p>The unit's <em>current</em> version may have no snapshot row yet (snapshots are
     * written on the next publish), but it is still something a call can be pinned to;
     * for that one case the current design is served instead.
     *
     * @throws ResourceNotFoundException when the unit or that version does not exist
     */
    @Transactional(readOnly = true)
    public VersionedProcess read(Long functionUnitId, String versionNumber) {
        workspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);

        FunctionUnit unit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        Optional<Version> snapshot =
                versionRepository.findByFunctionUnitIdAndVersionNumber(functionUnitId, versionNumber);

        if (snapshot.isPresent()) {
            return VersionedProcess.builder()
                    .functionUnitId(unit.getId())
                    .functionUnitName(unit.getName())
                    .functionUnitCode(unit.getCode())
                    .versionNumber(versionNumber)
                    .currentVersion(unit.getCurrentVersion())
                    .bpmnXml(processOf(snapshot.get()))
                    .publishedAt(snapshot.get().getPublishedAt())
                    .build();
        }

        if (versionNumber.equals(unit.getCurrentVersion())) {
            String xml = processDefinitionRepository.findByFunctionUnitId(functionUnitId)
                    .map(ProcessDefinition::getBpmnXml)
                    .map(XmlEncodingUtil::smartDecode)
                    .orElse(null);
            return VersionedProcess.builder()
                    .functionUnitId(unit.getId())
                    .functionUnitName(unit.getName())
                    .functionUnitCode(unit.getCode())
                    .versionNumber(versionNumber)
                    .currentVersion(unit.getCurrentVersion())
                    .bpmnXml(xml)
                    .build();
        }

        throw new ResourceNotFoundException("Version " + versionNumber + " of FunctionUnit", functionUnitId);
    }

    @SuppressWarnings("unchecked")
    private String processOf(Version version) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(version.getSnapshotData(), Map.class);
            Object xml = snapshot.get("process");
            if (xml == null) {
                xml = snapshot.get("processXml");
            }
            return xml instanceof String s ? XmlEncodingUtil.smartDecode(s) : null;
        } catch (Exception e) {
            log.warn("Could not read process from snapshot of version {}: {}",
                    version.getVersionNumber(), e.getMessage());
            return null;
        }
    }
}
