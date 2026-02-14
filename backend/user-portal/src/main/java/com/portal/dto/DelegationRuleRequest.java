package com.portal.dto;

import com.portal.enums.DelegationType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 委托规则请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegationRuleRequest {

    /** 被委托人ID */
    @NotBlank(message = "{validation.delegate_id_required}")
    private String delegateId;

    /** 委托类型 */
    @NotNull(message = "{validation.delegation_type_required}")
    private DelegationType delegationType;

    /** 流程类型筛选 */
    private List<String> processTypes;

    /** 优先级筛选 */
    private List<String> priorityFilter;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间 */
    private LocalDateTime endTime;

    /** 委托原因 */
    private String reason;
}
