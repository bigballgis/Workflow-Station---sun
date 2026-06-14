package com.developer.controller;

import com.platform.common.dto.ApiResponse;
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
