package com.developer.dto;

import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图标DTO - 用于前端展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IconDTO {
    
    private Long id;
    private String name;
    private IconCategory category;
    private String svgContent;
    private Integer fileSize;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
    
    /**
     * 从实体转换为DTO
     */
    public static IconDTO fromEntity(Icon icon) {
        if (icon == null) {
            return null;
        }
        
        return IconDTO.builder()
                .id(icon.getId())
                .name(icon.getName())
                .category(icon.getCategory())
                .svgContent(icon.getSvgContent())
                .fileSize(icon.getFileSize())
                .description(icon.getDescription())
                .createdBy(icon.getCreatedBy())
                .createdAt(icon.getCreatedAt())
                .build();
    }
}
