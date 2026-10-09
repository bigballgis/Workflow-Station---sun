package com.developer.controller;

import com.developer.component.impl.VersionedProcessReader;
import com.developer.dto.VersionedProcess;
import com.developer.security.RequireDeveloperPermission;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the process diagram of a published version.
 *
 * <p>Used when following a version-pinned call: the caller runs that version, so that is
 * the one to show — not the callee's live draft.
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/versions/by-number/{versionNumber}/process")
@Tag(name = "Versioned Process", description = "Read-only process diagram of a published version")
public class VersionedProcessController extends BaseController {

    private final VersionedProcessReader versionedProcessReader;

    public VersionedProcessController(VersionedProcessReader versionedProcessReader) {
        this.versionedProcessReader = versionedProcessReader;
    }

    @GetMapping
    @Operation(summary = "Get the process diagram of a published version (read-only)")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<VersionedProcess>> get(
            @PathVariable Long functionUnitId,
            @PathVariable String versionNumber) {
        return handleRequest(() -> versionedProcessReader.read(functionUnitId, versionNumber));
    }
}
