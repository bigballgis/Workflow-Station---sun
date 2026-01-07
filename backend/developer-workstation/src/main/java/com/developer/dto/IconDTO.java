package com.developer.dto;

import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
    private Integer width;
    private Integer height;
    private String tags;
    private String createdBy;
    private Instant createdAt;
    
    /**
     * 从实体转换为DTO
     */
    public static IconDTO fromEntity(Icon icon) {
        if (icon == null) {
            return null;
        }
        
        String svgContent = null;
        if (icon.getFileData() != null) {
            svgContent = new String(icon.getFileData(), StandardCharsets.UTF_8);
        }
        
        return IconDTO.builder()
                .id(icon.getId())
                .name(icon.getName())
                .category(icon.getCategory())
                .svgContent(svgContent)
                .fileSize(icon.getFileSize())
                .width(icon.getWidth())
                .height(icon.getHeight())
                .tags(icon.getTags())
                .createdBy(icon.getCreatedBy())
                .createdAt(icon.getCreatedAt())
                .build();
    }
}
