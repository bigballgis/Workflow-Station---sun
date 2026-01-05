package com.portal.controller;

import com.portal.component.UserPreferenceComponent;
import com.portal.dto.ApiResponse;
import com.portal.entity.DashboardLayout;
import com.portal.entity.NotificationPreference;
import com.portal.entity.UserPreference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            @RequestHeader("X-User-Id") String userId) {
        UserPreference preference = userPreferenceComponent.getUserPreference(userId);
        return ApiResponse.success(preference);
    }

    @Operation(summary = "更新用户偏好设置")
    @PutMapping
    public ApiResponse<UserPreference> updateUserPreference(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody UserPreference preference) {
        UserPreference updated = userPreferenceComponent.updateUserPreference(userId, preference);
        return ApiResponse.success("偏好设置更新成功", updated);
    }

    @Operation(summary = "获取工作台布局")
    @GetMapping("/dashboard-layout")
    public ApiResponse<List<DashboardLayout>> getDashboardLayout(
            @RequestHeader("X-User-Id") String userId) {
        List<DashboardLayout> layouts = userPreferenceComponent.getDashboardLayout(userId);
        return ApiResponse.success(layouts);
    }

    @Operation(summary = "保存工作台布局")
    @PutMapping("/dashboard-layout")
    public ApiResponse<List<DashboardLayout>> saveDashboardLayout(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody List<DashboardLayout> layouts) {
        List<DashboardLayout> saved = userPreferenceComponent.saveDashboardLayout(userId, layouts);
        return ApiResponse.success("工作台布局保存成功", saved);
    }

    @Operation(summary = "获取通知偏好")
    @GetMapping("/notifications")
    public ApiResponse<List<NotificationPreference>> getNotificationPreferences(
            @RequestHeader("X-User-Id") String userId) {
        List<NotificationPreference> preferences = userPreferenceComponent.getNotificationPreferences(userId);
        return ApiResponse.success(preferences);
    }

    @Operation(summary = "更新通知偏好")
    @PutMapping("/notifications")
    public ApiResponse<NotificationPreference> updateNotificationPreference(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody NotificationPreference preference) {
        NotificationPreference updated = userPreferenceComponent.updateNotificationPreference(userId, preference);
        return ApiResponse.success("通知偏好更新成功", updated);
    }
}
