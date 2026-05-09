package com.developer.service;

import com.developer.dto.LinkFormComponentResponse;
import com.developer.dto.LinkFormDataRequest;
import com.developer.dto.LinkFormDataResponse;

import java.util.List;

public interface LinkFormComponentService {
    
    List<LinkFormComponentResponse> getComponentsByFunctionUnit(Long functionUnitId);
    
    LinkFormDataResponse saveFormData(LinkFormDataRequest request);
    
    LinkFormDataResponse getFormData(Long componentId, Long subTableRowId);
    
    List<LinkFormDataResponse> getFormDataBySubTableRow(Long subTableRowId);
}
