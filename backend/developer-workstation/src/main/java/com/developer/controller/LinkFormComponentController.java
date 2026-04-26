package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.dto.LinkFormComponentRequest;
import com.developer.dto.LinkFormComponentResponse;
import com.developer.dto.LinkFormDataRequest;
import com.developer.dto.LinkFormDataResponse;
import com.developer.service.LinkFormComponentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/function-units/{functionUnitId}/link-form-components")
@RequiredArgsConstructor
public class LinkFormComponentController {
    
    private final LinkFormComponentService service;
    
    @GetMapping
    public ApiResponse<List<LinkFormComponentResponse>> getComponents(
            @PathVariable Long functionUnitId) {
        return ApiResponse.success(service.getComponentsByFunctionUnit(functionUnitId));
    }
    
    @GetMapping("/{id}")
    public ApiResponse<LinkFormComponentResponse> getComponent(@PathVariable Long id) {
        return ApiResponse.success(service.getComponentById(id));
    }
    
    @PostMapping
    public ApiResponse<LinkFormComponentResponse> createComponent(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody LinkFormComponentRequest request) {
        return ApiResponse.success(service.createComponent(functionUnitId, request));
    }
    
    @PutMapping("/{id}")
    public ApiResponse<LinkFormComponentResponse> updateComponent(
            @PathVariable Long id,
            @Valid @RequestBody LinkFormComponentRequest request) {
        return ApiResponse.success(service.updateComponent(id, request));
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComponent(@PathVariable Long id) {
        service.deleteComponent(id);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/data")
    public ApiResponse<LinkFormDataResponse> saveFormData(
            @Valid @RequestBody LinkFormDataRequest request) {
        return ApiResponse.success(service.saveFormData(request));
    }
    
    @GetMapping("/data")
    public ApiResponse<LinkFormDataResponse> getFormData(
            @RequestParam Long componentId,
            @RequestParam Long subTableRowId) {
        return ApiResponse.success(service.getFormData(componentId, subTableRowId));
    }
}
