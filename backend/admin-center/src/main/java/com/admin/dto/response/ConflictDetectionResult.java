package com.admin.dto.response;

import com.admin.entity.PermissionConflict;
import com.admin.enums.ConflictResolutionStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 权限冲突检测结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictDetectionResult {
    
    private boolean hasConflicts;
    private List<ConflictInfo> conflicts;
    private ConflictResolutionStrategy recommendedStrategy;
    private String message;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictInfo {
        private String conflictId;
        private String userId;
        private String permissionId;
        private String permissionName;
        private String conflictSource1;
        private String conflictSource2;
        private String conflictDescription;
        private ConflictResolutionStrategy resolutionStrategy;
        private String status;
        private Instant detectedAt;
        
        public static ConflictInfo fromEntity(PermissionConflict conflict) {
            return ConflictInfo.builder()
                    .conflictId(conflict.getId())
                    .userId(conflict.getUserId())
                    .permissionId(conflict.getPermission().getId())
                    .permissionName(conflict.getPermission().getName())
                    .conflictSource1(conflict.getConflictSource1())
                    .conflictSource2(conflict.getConflictSource2())
                    .conflictDescription(conflict.getConflictDescription())
                    .resolutionStrategy(conflict.getResolutionStrategy())
                    .status(conflict.getStatus())
                    .detectedAt(conflict.getDetectedAt())
                    .build();
        }
    }
    
    public static ConflictDetectionResult noConflicts() {
        return ConflictDetectionResult.builder()
                .hasConflicts(false)
                .message("未检测到权限冲突")
                .build();
    }
    
    public static ConflictDetectionResult withConflicts(List<ConflictInfo> conflicts, 
                                                       ConflictResolutionStrategy recommendedStrategy) {
        return ConflictDetectionResult.builder()
                .hasConflicts(true)
                .conflicts(conflicts)
                .recommendedStrategy(recommendedStrategy)
                .message("检测到 " + conflicts.size() + " 个权限冲突")
                .build();
    }
}