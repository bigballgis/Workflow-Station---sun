package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FkFillSourceDTO {
    /** Live JSON may carry {@code dw_field_definitions.id} until the name is resolved. */
    private Long fieldId;
    private String fieldName;
    private String kind;
    private Long ancestorBindingId;
    private String ancestorTableName;
    private String ancestorFilterFkFieldName;
}
