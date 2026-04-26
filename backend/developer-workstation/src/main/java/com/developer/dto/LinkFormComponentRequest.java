package com.developer.dto;

import com.developer.entity.LinkFormComponent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkFormComponentRequest {
    
    @NotBlank(message = "{validation.component_name_required}")
    private String componentName;
    
    @NotNull(message = "{validation.linked_form_required}")
    private Long linkedFormId;
    
    private String displayField;
    
    private String linkText;
    
    private String columnLabel;
    
    private Integer sortOrder;
    
    private String configJson;
    
    public static LinkFormComponent toEntity(LinkFormComponentRequest request, Long functionUnitId) {
        return LinkFormComponent.builder()
                .functionUnitId(functionUnitId)
                .componentName(request.getComponentName())
                .linkedFormId(request.getLinkedFormId())
                .displayField(request.getDisplayField())
                .linkText(request.getLinkText() != null ? request.getLinkText() : "详情")
                .columnLabel(request.getColumnLabel())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .configJson(request.getConfigJson())
                .build();
    }
}
