package com.admin.controller;

import com.admin.entity.ActionDefinition;
import com.admin.repository.ActionDefinitionRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action Definition Controller
 * Provides API to query action definitions
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/actions")
@RequiredArgsConstructor
public class ActionDefinitionController {
    
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final I18nService i18nService;
    
    /**
     * Batch query actions by IDs
     * GET /api/v1/admin/actions/batch?ids=id1,id2,id3
     */
    @GetMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchQuery(@RequestParam String ids) {
        try {
            String[] idArray = ids.split(",");
            List<String> actionIds = new java.util.ArrayList<>();
            for (String id : idArray) {
                actionIds.add(id.trim());
            }
            
            List<ActionDefinition> actions = actionDefinitionRepository.findAllById(actionIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", "SUCCESS");
            response.put("data", actions);
            response.put("message", i18nService.getMessage("admin.query_success"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to batch query actions", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", "ERROR");
            error.put("message", i18nService.getMessage("admin.query_failed", e.getMessage()));
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Query actions by function unit ID
     * GET /api/v1/admin/actions/function-unit/{functionUnitId}
     */
    @GetMapping("/function-unit/{functionUnitId}")
    public ResponseEntity<Map<String, Object>> queryByFunctionUnit(@PathVariable String functionUnitId) {
        try {
            List<ActionDefinition> actions = actionDefinitionRepository.findByFunctionUnitId(functionUnitId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", "SUCCESS");
            response.put("data", actions);
            response.put("message", i18nService.getMessage("admin.query_success"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to query actions by function unit", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", "ERROR");
            error.put("message", i18nService.getMessage("admin.query_failed", e.getMessage()));
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
