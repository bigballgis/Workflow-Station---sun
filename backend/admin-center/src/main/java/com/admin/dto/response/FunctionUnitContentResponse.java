package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 功能单元完整内容响应 DTO
 * 用于替代 getFunctionUnitContent 端点中的 Map&lt;String, Object&gt; 返回值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitContentResponse {
    private String id;
    private String name;
    private String code;
    private String version;
    private String description;
    private String status;
    private List<FormContentDTO> forms;
    private List<ProcessContentDTO> processes;
    private List<DataTableContentDTO> dataTables;
}
