package com.developer.service;

import com.developer.dto.LinkFormComponentResponse;
import com.developer.dto.LinkFormDataRequest;
import com.developer.dto.LinkFormDataResponse;

import java.util.List;

public interface LinkFormComponentService {
    
    List<LinkFormComponentResponse> getComponentsByFunctionUnit(Long functionUnitId);
    
    /** The request's component must belong to {@code functionUnitId} (404 otherwise). */
    LinkFormDataResponse saveFormData(Long functionUnitId, LinkFormDataRequest request);

    /** The component must belong to {@code functionUnitId} (404 otherwise). */
    LinkFormDataResponse getFormData(Long functionUnitId, Long componentId, Long subTableRowId);
    
    List<LinkFormDataResponse> getFormDataBySubTableRow(Long subTableRowId);
}
