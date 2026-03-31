package com.portal.controller;

import com.portal.dto.ApiResponse;
import com.portal.dto.NotificationDto;
import com.portal.security.CurrentUserId;
import com.portal.dto.PageResponse;
import com.portal.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知API
 */
@Tag(name = "站内通知", description = "站内通知管理")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "查询通知列表")
    @GetMapping
    public ApiResponse<PageResponse<NotificationDto>> getNotifications(
            @CurrentUserId String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead) {
        // Validate type parameter
        if (type != null) {
            try {
                com.portal.enums.NotificationType.valueOf(type);
            } catch (IllegalArgumentException e) {
                return ApiResponse.error("400", "无效的通知类型: " + type);
            }
        }
        PageResponse<NotificationDto> result = notificationService.getNotifications(userId, page, size, type, isRead);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取未读通知数量")
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(
            @CurrentUserId String userId) {
        long count = notificationService.getUnreadCount(userId);
        return ApiResponse.success(count);
    }

    @Operation(summary = "标记通知为已读")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @CurrentUserId String userId,
            @PathVariable Long id) {
        notificationService.markAsRead(userId, id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "全部标记为已读")
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @CurrentUserId String userId) {
        notificationService.markAllAsRead(userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @CurrentUserId String userId,
            @PathVariable Long id) {
        notificationService.deleteNotification(userId, id);
        return ApiResponse.success(null);
    }
}
