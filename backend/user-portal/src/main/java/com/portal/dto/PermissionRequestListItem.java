package com.portal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 「我的申请」列表专用 DTO：由 JDBC 按列读取，避免 JPA 实体在脏数据下加载/序列化失败。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionRequestListItem {

    private Long id;
    private String applicantId;
    private String applicantUsername;
    private String submittedByUserId;
    private String submittedByUsername;
    private String requestType;
    private String targetId;
    private String targetName;
    private List<String> roleNames;
    private String reason;
    private String status;
    private String approverId;
    private String approverComment;
    @JsonProperty("approvedAt")
    private String approvedAt;
    private String createdAt;
    private String updatedAt;
}
