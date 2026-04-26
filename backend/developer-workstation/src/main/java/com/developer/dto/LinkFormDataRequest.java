package com.developer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkFormDataRequest {
    
    @NotNull(message = "{validation.component_id_required}")
    private Long componentId;
    
    @NotNull(message = "{validation.sub_table_row_id_required}")
    private Long subTableRowId;
    
    private Object formData;
}
