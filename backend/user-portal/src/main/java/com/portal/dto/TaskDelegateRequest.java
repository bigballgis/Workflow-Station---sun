package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Portal single-task delegate body. Query-param USER form remains for compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDelegateRequest {

    /** USER (default) or BU_ROLE */
    private String delegatedTargetType;

    private String delegatedTo;

    private String delegatedBuCode;

    private String delegatedRoleCode;

    private String reason;
}
