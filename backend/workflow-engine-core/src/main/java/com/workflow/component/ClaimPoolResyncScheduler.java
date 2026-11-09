package com.workflow.component;

import com.workflow.exception.AdminCenterUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The backstop for claim pools (D-37): Admin Center pushes a resync after each UBR change but does not retry when the
 * engine is unreachable, and UBR rows can also change outside Admin Center (cascading deletes, scripts). This pass
 * recomputes every open BU + Role pool task from the current UBR; one replica runs it at a time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimPoolResyncScheduler {

    private final ClaimPoolMembershipSync claimPoolMembershipSync;

    @Scheduled(initialDelayString = "${workflow.claim-pool.full-resync-initial-delay-ms:120000}",
            fixedDelayString = "${workflow.claim-pool.full-resync-delay-ms:600000}")
    @SchedulerLock(name = "ClaimPool_fullResync", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void resyncAll() {
        try {
            claimPoolMembershipSync.resyncAll();
        } catch (AdminCenterUnavailableException e) {
            // Retried by the next run; the pools stay as they were, nothing is half-applied past the failure.
            log.error("Claim-pool full resync stopped, Admin Center unavailable: {}", e.getMessage());
        }
    }
}
