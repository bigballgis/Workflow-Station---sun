package com.developer.controller;

import com.developer.component.impl.FunctionUnitCallRelationComponent;
import com.developer.dto.FunctionUnitCallRelations;
import com.developer.security.RequireDeveloperPermission;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which Function Units a given unit calls, and which ones call it.
 *
 * <p>Derived on request from the design-time BPMN rather than stored, in the same
 * spirit as table relations: the diagrams already carry the answer.
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/call-relations")
@Slf4j
@Tag(name = "Function Unit Call Relations",
     description = "Cross-Function-Unit call relations derived from BPMN")
public class FunctionUnitCallRelationController extends BaseController {

    private final FunctionUnitCallRelationComponent callRelationComponent;

    public FunctionUnitCallRelationController(FunctionUnitCallRelationComponent callRelationComponent) {
        this.callRelationComponent = callRelationComponent;
    }

    @GetMapping
    @Operation(summary = "Get the call relations of a function unit (both directions)")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<FunctionUnitCallRelations>> get(
            @PathVariable Long functionUnitId) {
        return handleRequest(() -> callRelationComponent.resolve(functionUnitId));
    }

}
