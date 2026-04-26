package com.developer.dto;

import com.developer.entity.LinkFormData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkFormDataResponse {
    
    private Long id;
    private Long componentId;
    private Long subTableRowId;
    private Object formData;
    private Instant createdAt;
    private Instant updatedAt;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static LinkFormDataResponse fromEntity(LinkFormData data) {
        Object formDataObj = null;
        if (data.getFormData() != null) {
            try {
                formDataObj = objectMapper.readValue(data.getFormData(), Object.class);
            } catch (JsonProcessingException e) {
                formDataObj = data.getFormData();
            }
        }
        
        return LinkFormDataResponse.builder()
                .id(data.getId())
                .componentId(data.getComponentId())
                .subTableRowId(data.getSubTableRowId())
                .formData(formDataObj)
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .build();
    }
}
