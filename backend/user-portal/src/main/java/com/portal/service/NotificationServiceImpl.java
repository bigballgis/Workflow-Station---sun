package com.portal.service;

import com.platform.messaging.event.NotificationEvent;
import com.portal.dto.NotificationDto;
import com.portal.dto.PageResponse;
import com.portal.entity.Notification;
import com.portal.entity.NotificationPreference;
import com.portal.enums.NotificationType;
import com.portal.exception.PortalException;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Optional;

/**
 * 站内通知服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    /**
     * WebSocket推送服务，延迟注入避免循环依赖。
     * 该服务将在 Task 7.3 中创建。
     */
    @Autowired(required = false)
    private WebSocketNotificationService webSocketNotificationService;

    @Override
    @Transactional
    public Notification createFromEvent(NotificationEvent event) {
        // 1. 检查用户通知偏好
        Optional<NotificationPreference> preferenceOpt = notificationPreferenceRepository
                .findByUserIdAndNotificationType(event.getTargetUserId(), event.getNotificationType());

        if (preferenceOpt.isPresent() && Boolean.FALSE.equals(preferenceOpt.get().getInAppEnabled())) {
            log.debug("用户 {} 已禁用 {} 类型的站内通知，跳过创建", event.getTargetUserId(), event.getNotificationType());
            return null;
        }

        // 2. 构建并保存通知实体
        Notification notification = Notification.builder()
                .userId(event.getTargetUserId())
                .type(NotificationType.valueOf(event.getNotificationType()))
                .title(event.getTitle())
                .content(event.getContent())
                .link(event.getLink())
                .isRead(false)
                .build();

        notification = notificationRepository.save(notification);
        log.info("创建站内通知: id={}, userId={}, type={}", notification.getId(), notification.getUserId(), notification.getType());

        // 3. 检查免打扰时段，决定是否WebSocket推送
        boolean inQuietHours = false;
        if (preferenceOpt.isPresent()) {
            NotificationPreference preference = preferenceOpt.get();
            inQuietHours = isInQuietHours(preference.getQuietStartTime(), preference.getQuietEndTime());
        }

        // 4. 非免打扰时段且WebSocket服务可用时推送
        if (webSocketNotificationService != null && !inQuietHours) {
            try {
                webSocketNotificationService.pushNotification(notification);
            } catch (Exception e) {
                log.warn("WebSocket推送通知失败: notificationId={}, error={}", notification.getId(), e.getMessage());
            }
        }

        return notification;
    }

    @Override
    public PageResponse<NotificationDto> getNotifications(String userId, int page, int size, String type, Boolean isRead) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Notification> notificationPage;

        if (type != null && isRead != null) {
            NotificationType notificationType = NotificationType.valueOf(type);
            notificationPage = notificationRepository.findByUserIdAndTypeAndIsReadOrderByCreatedAtDesc(userId, notificationType, isRead, pageRequest);
        } else if (type != null) {
            NotificationType notificationType = NotificationType.valueOf(type);
            notificationPage = notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, notificationType, pageRequest);
        } else if (isRead != null) {
            notificationPage = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageRequest);
        } else {
            notificationPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        }

        Page<NotificationDto> dtoPage = notificationPage.map(NotificationDto::fromEntity);
        return PageResponse.of(dtoPage);
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    @Transactional
    public void markAsRead(String userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new PortalException("404", "通知不存在"));

        if (!notification.getUserId().equals(userId)) {
            throw new PortalException("403", "无权操作此通知");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteNotification(String userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new PortalException("404", "通知不存在"));

        if (!notification.getUserId().equals(userId)) {
            throw new PortalException("403", "无权操作此通知");
        }

        notificationRepository.delete(notification);
    }

    /**
     * 判断当前时间是否在免打扰时段内。
     * 支持跨午夜的时段（如 22:00 到 06:00）。
     */
    private boolean isInQuietHours(LocalTime quietStartTime, LocalTime quietEndTime) {
        if (quietStartTime == null || quietEndTime == null) {
            return false;
        }

        LocalTime now = LocalTime.now();

        if (quietStartTime.isBefore(quietEndTime)) {
            // 不跨午夜：如 08:00 - 18:00
            return !now.isBefore(quietStartTime) && now.isBefore(quietEndTime);
        } else {
            // 跨午夜：如 22:00 - 06:00
            return !now.isBefore(quietStartTime) || now.isBefore(quietEndTime);
        }
    }
}
