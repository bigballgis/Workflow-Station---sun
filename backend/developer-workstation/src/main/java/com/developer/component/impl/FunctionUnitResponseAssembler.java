package com.developer.component.impl;

import com.developer.dto.FunctionUnitResponse;
import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnitDevGroupAssignment;
import com.developer.entity.Icon;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 功能单元响应组装协作类。
 * 负责把 FunctionUnit 实体安全转换为 FunctionUnitResponse（含懒加载兜底与计数统计）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
class FunctionUnitResponseAssembler {

    private final FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;

    FunctionUnitResponse toResponse(FunctionUnit entity) {
        FunctionUnitResponse.IconInfo iconInfo = null;
        try {
            if (entity.getIcon() != null) {
                Icon icon = entity.getIcon();
                iconInfo = FunctionUnitResponse.IconInfo.builder()
                        .id(icon.getId())
                        .name(icon.getName())
                        .svgContent(icon.getSvgContent())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to load icon for function unit {}: {}", entity.getId(), e.getMessage());
        }

        // Safely get collection size to avoid LazyInitializationException
        int tableCount = 0;
        int formCount = 0;
        int actionCount = 0;
        boolean hasProcess = false;

        try {
            if (entity.getTableDefinitions() != null) {
                tableCount = entity.getTableDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load table definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }

        try {
            if (entity.getFormDefinitions() != null) {
                formCount = entity.getFormDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load form definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }

        try {
            if (entity.getActionDefinitions() != null) {
                actionCount = entity.getActionDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load action definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }

        int decisionCount = 0;
        try {
            if (entity.getDecisionDefinitions() != null) {
                decisionCount = entity.getDecisionDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load decision definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }

        try {
            hasProcess = entity.getProcessDefinition() != null;
        } catch (Exception e) {
            log.warn("Failed to load process definition for function unit {}: {}", entity.getId(), e.getMessage());
        }

        return FunctionUnitResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDisplayName())
                .iconId(entity.getIcon() != null ? entity.getIcon().getId() : null)
                .icon(iconInfo)
                .status(entity.getStatus())
                .currentVersion(entity.getCurrentVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .tableCount(tableCount)
                .formCount(formCount)
                .actionCount(actionCount)
                .decisionCount(decisionCount)
                .hasProcess(hasProcess)
                .assignedVirtualGroupIds(functionUnitDevGroupAssignmentRepository.findByFunctionUnitId(entity.getId())
                        .stream()
                        .map(FunctionUnitDevGroupAssignment::getVirtualGroupId)
                        .toList())
                .build();
    }
}
