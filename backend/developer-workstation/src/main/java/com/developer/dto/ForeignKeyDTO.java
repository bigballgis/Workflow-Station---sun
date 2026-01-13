package com.developer.dto;

import com.developer.entity.ForeignKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外键关系DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyDTO {
    
    private Long id;
    private Long sourceTableId;
    private String sourceTableName;
    private Long sourceFieldId;
    private String sourceFieldName;
    private Long targetTableId;
    private String targetTableName;
    private Long targetFieldId;
    private String targetFieldName;
    private String onDelete;
    private String onUpdate;
    
    /**
     * 从实体转换为DTO
     */
    public static ForeignKeyDTO fromEntity(ForeignKey fk) {
        return ForeignKeyDTO.builder()
                .id(fk.getId())
                .sourceTableId(fk.getTableDefinition().getId())
                .sourceTableName(fk.getTableDefinition().getTableName())
                .sourceFieldId(fk.getFieldDefinition().getId())
                .sourceFieldName(fk.getFieldDefinition().getFieldName())
                .targetTableId(fk.getRefTableDefinition().getId())
                .targetTableName(fk.getRefTableDefinition().getTableName())
                .targetFieldId(fk.getRefFieldDefinition().getId())
                .targetFieldName(fk.getRefFieldDefinition().getFieldName())
                .onDelete(fk.getOnDelete())
                .onUpdate(fk.getOnUpdate())
                .build();
    }
}
