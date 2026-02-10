package com.portal.controller;

import com.portal.component.ProcessComponent;
import com.portal.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/data-api/fu-contents")
@RequiredArgsConstructor
@Tag(name = "功能单元内容API", description = "功能单元内容API端点，使用/data-api前缀以避免ResourceHttpRequestHandler拦截")
public class ApiDataController {

    private final ProcessComponent processComponent;

    @GetMapping("/{functionUnitId}")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitContents(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        log.info("ApiDataController: Getting function unit contents for: {}, contentType: {}", functionUnitId, contentType);
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }
}
