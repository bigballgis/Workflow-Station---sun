package com.portal.service;

import com.platform.messaging.event.NotificationEvent;
import com.portal.dto.NotificationDto;
import com.portal.dto.PageResponse;
import com.portal.entity.Notification;

/**
 * 站内通知服务接口
 */
public interface NotificationService {

    /**
     * 从事件创建通知
     */
    Notification createFromEvent(NotificationEvent event);

    /**
     * 分页查询通知列表
     */
    PageResponse<NotificationDto> getNotifications(String userId, int page, int size, String type, Boolean isRead);

    /**
     * 获取未读通知数量
     */
    long getUnreadCount(String userId);

    /**
     * 标记通知为已读
     */
    void markAsRead(String userId, Long notificationId);

    /**
     * 标记所有通知为已读
     */
    void markAllAsRead(String userId);

    /**
     * 删除通知
     */
    void deleteNotification(String userId, Long notificationId);
}
