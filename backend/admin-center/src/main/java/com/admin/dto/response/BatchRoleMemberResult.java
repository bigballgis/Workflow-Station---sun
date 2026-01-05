package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量角色成员操作结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRoleMemberResult {
    
    /**
     * 总数
     */
    private int total;
    
    /**
     * 成功数
     */
    private int successCount;
    
    /**
     * 失败数
     */
    private int failureCount;
    
    /**
     * 成功的用户ID列表
     */
    @Builder.Default
    private List<String> successUserIds = new ArrayList<>();
    
    /**
     * 失败详情
     */
    @Builder.Default
    private List<FailureDetail> failures = new ArrayList<>();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureDetail {
        private String userId;
        private String errorCode;
        private String errorMessage;
    }
    
    public static BatchRoleMemberResult empty() {
        return BatchRoleMemberResult.builder()
                .total(0)
                .successCount(0)
                .failureCount(0)
                .build();
    }
    
    public void addSuccess(String userId) {
        successUserIds.add(userId);
        successCount++;
    }
    
    public void addFailure(String userId, String errorCode, String errorMessage) {
        failures.add(FailureDetail.builder()
                .userId(userId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build());
        failureCount++;
    }
}
