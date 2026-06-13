package com.portal.controller;

import com.portal.component.UserPreferenceComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import com.portal.entity.DashboardLayout;
import com.portal.entity.NotificationPreference;
import com.portal.entity.UserPreference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户偏好设置API
 */
@Tag(name = "用户偏好", description = "用户偏好设置和工作台布局管理")
@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final UserPreferenceComponent userPreferenceComponent;

    @Operation(summary = "获取用户偏好设置")
    @GetMapping
    public ApiResponse<UserPreference> getUserPreference(
            @CurrentUserId String userId) {
        UserPreference preference = userPreferenceComponent.getUserPreference(userId);
        return ApiResponse.success(preference);
    }

    @Operation(summary = "更新用户偏好设置")
    @PutMapping
    public ApiResponse<UserPreference> updateUserPreference(
            @CurrentUserId String userId,
            @RequestBody @Valid UserPreference preference) {
        UserPreference updated = userPreferenceComponent.updateUserPreference(userId, preference);
        return ApiResponse.success(updated);
    }

    @Operation(summary = "获取工作台布局")
    @GetMapping("/dashboard-layout")
    public ApiResponse<List<DashboardLayout>> getDashboardLayout(
            @CurrentUserId String userId) {
        List<DashboardLayout> layouts = userPreferenceComponent.getDashboardLayout(userId);
        return ApiResponse.success(layouts);
    }

    @Operation(summary = "保存工作台布局")
    @PutMapping("/dashboard-layout")
    public ApiResponse<List<DashboardLayout>> saveDashboardLayout(
            @CurrentUserId String userId,
            @RequestBody List<DashboardLayout> layouts) {
        List<DashboardLayout> saved = userPreferenceComponent.saveDashboardLayout(userId, layouts);
        return ApiResponse.success(saved);
    }

    @Operation(summary = "获取通知偏好")
    @GetMapping("/notifications")
    public ApiResponse<List<NotificationPreference>> getNotificationPreferences(
            @CurrentUserId String userId) {
        List<NotificationPreference> preferences = userPreferenceComponent.getNotificationPreferences(userId);
        return ApiResponse.success(preferences);
    }

    @Operation(summary = "更新通知偏好")
    @PutMapping("/notifications")
    public ApiResponse<NotificationPreference> updateNotificationPreference(
            @CurrentUserId String userId,
            @RequestBody @Valid NotificationPreference preference) {
        NotificationPreference updated = userPreferenceComponent.updateNotificationPreference(userId, preference);
        return ApiResponse.success(updated);
    }
}
