package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinitionInfo {
    private String id;
    private String key;
    private String name;
    private String description;
    private String category;
    private Integer version;
    private String icon;
    private Boolean isFavorite;
}
