package com.portal.recordnote;

import com.platform.common.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/record-notes/notifications")
@RequiredArgsConstructor
public class RecordNoteNavigationController {
    private final RecordNoteNavigationComponent component;
    @GetMapping("/{id}/target")
    public ApiResponse<com.portal.dto.RecordNoteDestination> destination(
            @CurrentUserId String user,@PathVariable String id) {
        return ApiResponse.success(component.destination(user,id));
    }
}
