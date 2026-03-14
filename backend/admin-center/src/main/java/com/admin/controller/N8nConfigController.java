package com.admin.controller;

import com.admin.dto.request.N8nConfigCreateRequest;
import com.admin.dto.request.N8nConfigUpdateRequest;
import com.admin.dto.response.N8nConnectionTestResult;
import com.admin.dto.response.N8nWorkflowDTO;
import com.admin.entity.N8nConfig;
import com.admin.service.N8nConfigService;
import com.admin.service.N8nWorkflowProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/n8n-config")
@RequiredArgsConstructor
@Tag(name = "N8N 连接配置", description = "N8N 自动化引擎连接配置管理接口")
public class N8nConfigController {

    private final N8nConfigService n8nConfigService;
    private final N8nWorkflowProxyService n8nWorkflowProxyService;

    @PostMapping
    @Operation(summary = "创建 N8N 连接配置")
    public ResponseEntity<N8nConfig> create(@Valid @RequestBody N8nConfigCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(n8nConfigService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 N8N 连接配置")
    public ResponseEntity<N8nConfig> update(
            @PathVariable String id,
            @Valid @RequestBody N8nConfigUpdateRequest request) {
        return ResponseEntity.ok(n8nConfigService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 N8N 连接配置")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        n8nConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "获取所有 N8N 连接配置列表")
    public ResponseEntity<List<N8nConfig>> list() {
        return ResponseEntity.ok(n8nConfigService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个 N8N 连接配置")
    public ResponseEntity<N8nConfig> getById(@PathVariable String id) {
        return ResponseEntity.ok(n8nConfigService.getById(id));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试 N8N 连接")
    public ResponseEntity<N8nConnectionTestResult> testConnection(@PathVariable String id) {
        return ResponseEntity.ok(n8nConfigService.testConnection(id));
    }

    @GetMapping("/{id}/internal")
    @Operation(summary = "内部 API：获取含解密 apiKey 的完整 N8N 连接配置")
    public ResponseEntity<N8nConfig> getByIdInternal(@PathVariable String id) {
        return ResponseEntity.ok(n8nConfigService.getByIdInternal(id));
    }

    @GetMapping("/{configId}/workflows")
    @Operation(summary = "获取 N8N 工作流列表（带缓存）")
    public ResponseEntity<List<N8nWorkflowDTO>> listWorkflows(@PathVariable String configId) {
        return ResponseEntity.ok(n8nWorkflowProxyService.listWorkflows(configId));
    }
}
