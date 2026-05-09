package com.developer.service.impl;

import com.developer.dto.LinkFormComponentResponse;
import com.developer.dto.LinkFormDataRequest;
import com.developer.dto.LinkFormDataResponse;
import com.developer.entity.FormDefinition;
import com.developer.entity.LinkFormComponent;
import com.developer.entity.LinkFormData;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.LinkFormComponentRepository;
import com.developer.repository.LinkFormDataRepository;
import com.developer.service.LinkFormComponentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkFormComponentServiceImpl implements LinkFormComponentService {
    
    private final LinkFormComponentRepository componentRepository;
    private final LinkFormDataRepository dataRepository;
    private final FormDefinitionRepository formRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    public List<LinkFormComponentResponse> getComponentsByFunctionUnit(Long functionUnitId) {
        List<LinkFormComponent> components = componentRepository.findByFunctionUnitIdOrderBySortOrderAsc(functionUnitId);
        return components.stream()
                .map(this::toResponseWithFormName)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public LinkFormDataResponse saveFormData(LinkFormDataRequest request) {
        String formDataJson = toJson(request.getFormData());
        
        Optional<LinkFormData> existing = dataRepository.findByComponentIdAndSubTableRowId(
                request.getComponentId(), request.getSubTableRowId());
        
        LinkFormData data;
        if (existing.isPresent()) {
            data = existing.get();
            data.setFormData(formDataJson);
        } else {
            data = LinkFormData.builder()
                    .componentId(request.getComponentId())
                    .subTableRowId(request.getSubTableRowId())
                    .formData(formDataJson)
                    .build();
        }
        
        data = dataRepository.save(data);
        log.info("Saved LinkFormData: id={}, componentId={}, subTableRowId={}", 
                data.getId(), data.getComponentId(), data.getSubTableRowId());
        return LinkFormDataResponse.fromEntity(data);
    }
    
    @Override
    public LinkFormDataResponse getFormData(Long componentId, Long subTableRowId) {
        return dataRepository.findByComponentIdAndSubTableRowId(componentId, subTableRowId)
                .map(LinkFormDataResponse::fromEntity)
                .orElse(null);
    }
    
    @Override
    public List<LinkFormDataResponse> getFormDataBySubTableRow(Long subTableRowId) {
        return dataRepository.findBySubTableRowId(subTableRowId).stream()
                .map(LinkFormDataResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    private LinkFormComponentResponse toResponseWithFormName(LinkFormComponent component) {
        String formName = formRepository.findById(component.getLinkedFormId())
                .map(FormDefinition::getFormName)
                .orElse(null);
        return LinkFormComponentResponse.fromEntity(component, formName);
    }
    
    private String toJson(Object data) {
        if (data == null) {
            return "{}";
        }
        if (data instanceof String) {
            return (String) data;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize form data", e);
            return "{}";
        }
    }
}
