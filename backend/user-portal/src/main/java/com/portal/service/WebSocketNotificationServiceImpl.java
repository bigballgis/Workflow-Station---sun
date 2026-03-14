package com.portal.service;

import com.portal.dto.NotificationDto;
import com.portal.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationServiceImpl implements WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void pushNotification(Notification notification) {
        NotificationDto dto = NotificationDto.fromEntity(notification);
        String destination = "/queue/notifications";

        try {
            messagingTemplate.convertAndSendToUser(
                    notification.getUserId(),
                    destination,
                    dto
            );
            log.debug("WebSocket推送通知成功: userId={}, notificationId={}",
                    notification.getUserId(), notification.getId());
        } catch (Exception e) {
            log.warn("WebSocket推送通知失败: userId={}, notificationId={}, error={}",
                    notification.getUserId(), notification.getId(), e.getMessage());
        }
    }
}
