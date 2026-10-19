package com.workflow.recordnote;

import com.platform.common.dto.ApiResponse;
import com.platform.common.recordnote.RecordNoteRuntimeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Scoped service authentication; no uploader credential is retained by the async worker. */
@RestController
@RequestMapping("/api/v1/internal/record-note-targets")
@RequiredArgsConstructor
public class RecordNoteInternalController {
    private final RecordNoteTargetComponent component;
    @Value("${service.internal-token:}") private String serviceToken;

    @GetMapping
    public ApiResponse<RecordNoteRuntimeContext> resolve(
            @RequestHeader(value="X-Internal-Token",required=false) String token,
            @RequestParam String processInstanceId,@RequestParam List<String> nodes) {
        if (serviceToken.isBlank()) throw new com.platform.common.exception.BusinessException(com.platform.common.enums.ErrorCode.SERVICE_UNAVAILABLE);
        if (token==null || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) throw new com.platform.common.exception.BusinessException(com.platform.common.enums.ErrorCode.FORBIDDEN);
        return ApiResponse.success(component.resolve(processInstanceId,nodes));
    }
}
