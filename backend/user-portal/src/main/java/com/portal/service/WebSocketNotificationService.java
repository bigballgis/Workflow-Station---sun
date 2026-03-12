package com.portal.service;

import com.portal.entity.Notification;

/**
 * WebSocket通知推送服务接口。
 * 实现将在 WebSocket 任务中创建。
 */
public interface WebSocketNotificationService {

    /**
     * 向目标用户推送通知
     */
    void pushNotification(Notification notification);
}
