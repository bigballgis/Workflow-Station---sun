package com.admin.service;

import com.admin.client.EngineInternalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Tells the engine that a user left (account set INACTIVE or deleted; platform access model §7.3, D-42), once that is
 * committed: the engine hands the user's tasks to their Entity or Function Manager and reads the account state itself,
 * which an uncommitted change would not show.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartedUserTasksService {

    private final EngineInternalClient engineInternalClient;

    /** Called after the user is set INACTIVE or deleted; the engine is told after commit and not at all on rollback. */
    public void userDeparted(String userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            push(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                push(userId);
            }
        });
    }

    private void push(String userId) {
        try {
            engineInternalClient.reassignTasksOfDepartedUsers(List.of(userId));
            log.info("Engine told that user {} left; their tasks go to their manager", userId);
        } catch (RuntimeException e) {
            // FALLBACK(external): the status change is committed and stays the authority; failing the request now
            // would report a saved deactivation / deletion as failed. The backstop is the engine's
            // DepartedHolderReassignScheduler (D-42): every 10 minutes it hands the open tasks of every user who left
            // to their manager, so a missed call only delays the hand-over.
            log.error("Engine not told that user {} left after the committed change: {}", userId, e.getMessage(), e);
        }
    }
}
