package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The process diagram of one published version of a Function Unit, for read-only viewing.
 *
 * <p>Exists so a pinned call can be inspected as the version it actually runs. Opening the
 * unit's live draft instead would show a process the call does not invoke.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionedProcess {

    private Long functionUnitId;
    private String functionUnitName;
    private String functionUnitCode;

    /** The version shown, e.g. {@code 1.2.0}. */
    private String versionNumber;

    /** The unit's current version, so the viewer can say how far behind this one is. */
    private String currentVersion;

    /** Plain BPMN XML of that version. */
    private String bpmnXml;

    /** When that version was published; null when served from the current design. */
    private Instant publishedAt;
}
