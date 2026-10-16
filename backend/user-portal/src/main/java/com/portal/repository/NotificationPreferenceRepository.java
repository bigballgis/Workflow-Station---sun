package com.portal.repository;

import com.portal.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 通知偏好Repository
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(String userId);

    Optional<NotificationPreference> findByUserIdAndNotificationType(String userId, String notificationType);

    void deleteByUserId(String userId);

    /** Initialise once, preserving an existing category preference and prior Process opt-outs. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO up_notification_preference
              (user_id,notification_type,email_enabled,browser_enabled,in_app_enabled,quiet_start_time,quiet_end_time,created_at,updated_at)
            SELECT :userId,'ATTACHMENT_UPDATE',COALESCE(p.email_enabled,false),
                   COALESCE(p.browser_enabled,true),COALESCE(p.in_app_enabled,true),
                   p.quiet_start_time,p.quiet_end_time,now(),now()
            FROM (SELECT 1) seed LEFT JOIN up_notification_preference p
              ON p.user_id=:userId AND p.notification_type='PROCESS'
            ON CONFLICT (user_id,notification_type) DO NOTHING
            """, nativeQuery = true)
    void ensureAttachmentUpdatePreference(@Param("userId") String userId);
    java.util.List<NotificationPreference> findByUserIdInAndNotificationTypeIn(
            java.util.Collection<String> userIds, java.util.Collection<String> notificationTypes);

}
