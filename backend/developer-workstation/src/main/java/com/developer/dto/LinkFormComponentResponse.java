package com.developer.dto;

import com.developer.entity.LinkFormComponent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkFormComponentResponse {
    
    private Long id;
    private Long functionUnitId;
    private String componentName;
    private Long linkedFormId;
    private String linkedFormName;
    private String displayField;
    private String linkText;
    private String columnLabel;
    private Integer sortOrder;
    private String configJson;
    private Instant createdAt;
    private Instant updatedAt;
    
    public static LinkFormComponentResponse fromEntity(LinkFormComponent component) {
        return fromEntity(component, null);
    }
    
    public static LinkFormComponentResponse fromEntity(LinkFormComponent component, String linkedFormName) {
        return LinkFormComponentResponse.builder()
                .id(component.getId())
                .functionUnitId(component.getFunctionUnitId())
                .componentName(component.getComponentName())
                .linkedFormId(component.getLinkedFormId())
                .linkedFormName(linkedFormName)
                .displayField(component.getDisplayField())
                .linkText(component.getLinkText())
                .columnLabel(component.getColumnLabel())
                .sortOrder(component.getSortOrder())
                .configJson(component.getConfigJson())
                .createdAt(component.getCreatedAt())
                .updatedAt(component.getUpdatedAt())
                .build();
    }
}
