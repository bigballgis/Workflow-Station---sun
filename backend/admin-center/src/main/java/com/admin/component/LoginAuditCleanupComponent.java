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
 * Configured to 1-day retention with daily runs.
 * DELETE … WHERE created_at < cutoff is idempotent — safe to run concurrently across instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAuditCleanupComponent {

    private final LoginAuditQueryRepository loginAuditQueryRepository;

    private static final int RETENTION_DAYS = 1;

    /**
     * Delete audit records older than 1 day. Runs daily at 03:00.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldAuditRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        try {
            int deleted = loginAuditQueryRepository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Cleaned up {} login audit records older than {} day(s) (cutoff: {})",
                        deleted, RETENTION_DAYS, cutoff);
            }
        } catch (Exception e) {
            log.error("Failed to clean up old login audit records: {}", e.getMessage());
        }
    }
}
