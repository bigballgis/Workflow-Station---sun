package com.developer.dto;

import com.developer.enums.DataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Request DTO for a table field definition. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinitionRequest {

    /**
     * Sent by the frontend only when updating an existing field; used to match rename / display-name changes.
     * {@link com.developer.component.impl.TableDesignComponentImpl#update} uses delete-and-reinsert, so the
     * persisted id becomes invalid after save; this id is used only inside the transaction to sync the form designer
     * (rule.field / rule.title / fieldPermissions keys).
     */
    private Long id;

    @NotBlank(message = "{validation.field_name_required}")
    @Size(max = 100, message = "{validation.field_name_max_length}")
    private String fieldName;
    
    @NotNull(message = "{validation.data_type_required}")
    private DataType dataType;
    
    private Integer length;
    private Integer precision;
    private Integer scale;
    
    @Builder.Default
    private Boolean nullable = true;
    
    private String defaultValue;
    
    @Builder.Default
    private Boolean isPrimaryKey = false;
    
    @Builder.Default
    private Boolean isUnique = false;
    
    private String displayName;
    private Integer sortOrder;

    private Boolean isForeignKey;
    private Long refTableId;
    private List<String> refPrimaryKeyFields;
    private Map<String, Object> pkGeneration;
    /** readonly | hidden */
    private String fkDisplayMode;
    /** oneToOne | oneToMany | manyToMany */
    private String relationCardinality;
}
