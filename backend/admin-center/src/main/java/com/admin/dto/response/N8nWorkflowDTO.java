package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * N8N 工作流列表项 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nWorkflowDTO {

    private String id;

    private String name;

    private Boolean active;

    private List<String> tags;

    private String createdAt;
}
