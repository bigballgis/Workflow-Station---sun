package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.entity.ActionDefinition;
import com.developer.repository.ActionDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 动作查询控制器
 * 提供跨功能单元的动作查询功能
 */
@RestController
@RequestMapping("/actions")
@RequiredArgsConstructor
@Tag(name = "动作查询", description = "动作查询相关操作")
public class ActionQueryController {
    
    private final ActionDefinitionRepository actionDefinitionRepository;
    
    @GetMapping("/batch")
    @Operation(summary = "批量获取动作定义", description = "根据动作ID列表批量获取动作定义信息")
    public ResponseEntity<ApiResponse<List<ActionDefinition>>> batchGet(
            @RequestParam String ids) {
        
        // 解析ID列表: "12,16,17,18" -> [12L, 16L, 17L, 18L]
        List<Long> actionIds = Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());
        
        // 批量查询
        List<ActionDefinition> actions = actionDefinitionRepository.findAllById(actionIds);
        
        return ResponseEntity.ok(ApiResponse.success(actions));
    }
    
    @GetMapping("/{actionId}")
    @Operation(summary = "获取单个动作定义")
    public ResponseEntity<ApiResponse<ActionDefinition>> getById(@PathVariable Long actionId) {
        ActionDefinition action = actionDefinitionRepository.findById(actionId)
            .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(action));
    }
}
