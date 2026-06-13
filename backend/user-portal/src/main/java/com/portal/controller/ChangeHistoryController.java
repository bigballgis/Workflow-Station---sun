package com.portal.controller;

import com.portal.component.ChangeHistoryComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.ChangeHistoryRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Change History REST API 控制器
 * 提供流程变更历史查询
 */
@Slf4j
@RestController
@RequestMapping("/processes")
@RequiredArgsConstructor
@Tag(name = "Change History", description = "Process change history retrieval")
public class ChangeHistoryController {

    private final ChangeHistoryComponent changeHistoryComponent;

    @GetMapping("/{processInstanceId}/change-history")
    @Operation(summary = "获取流程变更历史")
    public ApiResponse<List<ChangeHistoryRecord>> getChangeHistory(
            @PathVariable String processInstanceId) {
        log.debug("GET /processes/{}/change-history", processInstanceId);
        List<ChangeHistoryRecord> history = changeHistoryComponent.getChangeHistory(processInstanceId);
        return ApiResponse.success(history);
    }
}
