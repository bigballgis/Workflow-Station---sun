package com.admin.controller;

import com.admin.entity.MemberChangeLog;
import com.admin.enums.MemberChangeType;
import com.admin.repository.MemberChangeLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成员变更审计查询（供 user-portal 等内部调用）
 */
@RestController
@RequestMapping("/member-change-logs")
@RequiredArgsConstructor
@Tag(name = "成员变更记录", description = "成员加入/退出/移除审计查询")
public class MemberChangeLogController {

    private final MemberChangeLogRepository memberChangeLogRepository;

    @GetMapping
    @Operation(summary = "按用户查询成员变更记录", description = "支持按变更类型筛选，默认按 createdAt 倒序")
    public ResponseEntity<List<Map<String, Object>>> listByUser(
            @RequestParam String userId,
            @RequestParam(required = false) MemberChangeType changeType) {
        List<MemberChangeLog> logs = changeType != null
                ? memberChangeLogRepository.findByUserIdAndChangeTypeOrderByCreatedAtDesc(userId, changeType)
                : memberChangeLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> body = logs.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toMap(MemberChangeLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("changeType", log.getChangeType() != null ? log.getChangeType().name() : null);
        m.put("targetType", log.getTargetType() != null ? log.getTargetType().name() : null);
        m.put("targetId", log.getTargetId());
        m.put("userId", log.getUserId());
        m.put("roleIds", log.getRoleIds());
        m.put("operatorId", log.getOperatorId());
        m.put("reason", log.getReason());
        m.put("createdAt", log.getCreatedAt());
        return m;
    }
}
