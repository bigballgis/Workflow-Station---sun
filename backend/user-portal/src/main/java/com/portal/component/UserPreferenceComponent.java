package com.portal.component;

import com.portal.entity.DashboardLayout;
import com.portal.entity.NotificationPreference;
import com.portal.entity.UserPreference;
import com.portal.repository.DashboardLayoutRepository;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 用户偏好设置组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPreferenceComponent {

    private final UserPreferenceRepository userPreferenceRepository;
    private final DashboardLayoutRepository dashboardLayoutRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    /**
     * 获取用户偏好设置
     */
    public UserPreference getUserPreference(String userId) {
        return userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreference(userId));
    }

    /**
     * 更新用户偏好设置
     */
    @Transactional
    public UserPreference updateUserPreference(String userId, UserPreference preference) {
        UserPreference existing = userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserPreference.builder().userId(userId).build());

        if (preference.getTheme() != null) {
            existing.setTheme(preference.getTheme());
        }
        if (preference.getThemeColor() != null) {
            existing.setThemeColor(preference.getThemeColor());
        }
        if (preference.getFontSize() != null) {
            existing.setFontSize(preference.getFontSize());
        }
        if (preference.getLayoutDensity() != null) {
            existing.setLayoutDensity(preference.getLayoutDensity());
        }
        if (preference.getLanguage() != null) {
            existing.setLanguage(preference.getLanguage());
        }
        if (preference.getTimezone() != null) {
            existing.setTimezone(preference.getTimezone());
        }
        if (preference.getDateFormat() != null) {
            existing.setDateFormat(preference.getDateFormat());
        }
        if (preference.getPageSize() != null) {
            existing.setPageSize(preference.getPageSize());
        }

        return userPreferenceRepository.save(existing);
    }

    /**
     * 获取工作台布局
     */
    public List<DashboardLayout> getDashboardLayout(String userId) {
        List<DashboardLayout> layouts = dashboardLayoutRepository.findByUserIdOrderByGridYAscGridXAsc(userId);
        if (layouts.isEmpty()) {
            layouts = createDefaultDashboardLayout(userId);
        }
        return layouts;
    }

    /**
     * 保存工作台布局
     */
    @Transactional
    public List<DashboardLayout> saveDashboardLayout(String userId, List<DashboardLayout> layouts) {
        // 删除旧布局
        dashboardLayoutRepository.deleteByUserId(userId);

        // 保存新布局
        for (DashboardLayout layout : layouts) {
            layout.setUserId(userId);
            layout.setId(null); // 确保是新建
        }
        return dashboardLayoutRepository.saveAll(layouts);
    }

    /**
     * 获取通知偏好
     */
    public List<NotificationPreference> getNotificationPreferences(String userId) {
        List<NotificationPreference> preferences = notificationPreferenceRepository.findByUserId(userId);
        if (preferences.isEmpty()) {
            preferences = createDefaultNotificationPreferences(userId);
        }
        return preferences;
    }

    /**
     * 更新通知偏好
     */
    @Transactional
    public NotificationPreference updateNotificationPreference(String userId, NotificationPreference preference) {
        NotificationPreference existing = notificationPreferenceRepository
                .findByUserIdAndNotificationType(userId, preference.getNotificationType())
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId)
                        .notificationType(preference.getNotificationType())
                        .build());

        existing.setEmailEnabled(preference.getEmailEnabled());
        existing.setBrowserEnabled(preference.getBrowserEnabled());
        existing.setInAppEnabled(preference.getInAppEnabled());
        existing.setQuietStartTime(preference.getQuietStartTime());
        existing.setQuietEndTime(preference.getQuietEndTime());

        return notificationPreferenceRepository.save(existing);
    }

    /**
     * 创建默认用户偏好
     */
    private UserPreference createDefaultPreference(String userId) {
        UserPreference preference = UserPreference.builder()
                .userId(userId)
                .theme("light")
                .themeColor("#DB0011")
                .fontSize("medium")
                .layoutDensity("normal")
                .language("zh-CN")
                .timezone("Asia/Shanghai")
                .dateFormat("YYYY-MM-DD")
                .pageSize(20)
                .build();
        return userPreferenceRepository.save(preference);
    }

    /**
     * 创建默认工作台布局
     */
    private List<DashboardLayout> createDefaultDashboardLayout(String userId) {
        List<DashboardLayout> layouts = List.of(
                DashboardLayout.builder()
                        .userId(userId)
                        .componentId("task-overview")
                        .componentType("TaskOverviewWidget")
                        .gridX(0)
                        .gridY(0)
                        .gridW(6)
                        .gridH(8)
                        .isVisible(true)
                        .build(),
                DashboardLayout.builder()
                        .userId(userId)
                        .componentId("process-statistics")
                        .componentType("ProcessStatisticsWidget")
                        .gridX(6)
                        .gridY(0)
                        .gridW(6)
                        .gridH(8)
                        .isVisible(true)
                        .build(),
                DashboardLayout.builder()
                        .userId(userId)
                        .componentId("quick-actions")
                        .componentType("QuickActionsWidget")
                        .gridX(0)
                        .gridY(8)
                        .gridW(4)
                        .gridH(6)
                        .isVisible(true)
                        .build(),
                DashboardLayout.builder()
                        .userId(userId)
                        .componentId("notification-center")
                        .componentType("NotificationWidget")
                        .gridX(4)
                        .gridY(8)
                        .gridW(8)
                        .gridH(6)
                        .isVisible(true)
                        .build()
        );
        return dashboardLayoutRepository.saveAll(layouts);
    }

    /**
     * 创建默认通知偏好
     */
    private List<NotificationPreference> createDefaultNotificationPreferences(String userId) {
        List<NotificationPreference> preferences = List.of(
                NotificationPreference.builder()
                        .userId(userId)
                        .notificationType("TASK_ASSIGNED")
                        .emailEnabled(true)
                        .browserEnabled(true)
                        .inAppEnabled(true)
                        .build(),
                NotificationPreference.builder()
                        .userId(userId)
                        .notificationType("TASK_OVERDUE")
                        .emailEnabled(true)
                        .browserEnabled(true)
                        .inAppEnabled(true)
                        .build(),
                NotificationPreference.builder()
                        .userId(userId)
                        .notificationType("PROCESS_COMPLETED")
                        .emailEnabled(true)
                        .browserEnabled(false)
                        .inAppEnabled(true)
                        .build(),
                NotificationPreference.builder()
                        .userId(userId)
                        .notificationType("SYSTEM_NOTICE")
                        .emailEnabled(false)
                        .browserEnabled(false)
                        .inAppEnabled(true)
                        .build()
        );
        return notificationPreferenceRepository.saveAll(preferences);
    }
}
