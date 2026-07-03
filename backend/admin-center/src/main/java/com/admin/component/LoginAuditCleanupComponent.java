package com.admin.component;

import com.admin.repository.LoginAuditQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled cleanup of login audit records.
 * Configured to 1-hour retention with hourly runs for local testing.
 * DELETE … WHERE created_at < cutoff is idempotent — safe to run concurrently across instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAuditCleanupComponent {

    private final LoginAuditQueryRepository loginAuditQueryRepository;

    private static final int RETENTION_HOURS = 1;

    /**
     * Delete audit records older than 1 hour. Runs every hour.
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanupOldAuditRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
        try {
            int deleted = loginAuditQueryRepository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Cleaned up {} login audit records older than {} hour(s) (cutoff: {})",
                        deleted, RETENTION_HOURS, cutoff);
            }
        } catch (Exception e) {
            log.error("Failed to clean up old login audit records: {}", e.getMessage());
        }
    }
}
