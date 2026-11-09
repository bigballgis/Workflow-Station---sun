package com.workflow.component;

import com.workflow.repository.FlowableRuntimeTaskQueries;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The engine's own moves of a task off its holder: the D-40 release of a holder who lost the BU + Role and the D-42
 * hand-over to a leaver's manager (platform access model §7.3). They are started by Admin Center pushes, read-path
 * repairs and two scheduled passes, which ShedLock does not keep apart and which run on any replica, so two of them can
 * act on the same task from the same earlier read. Each move therefore runs in one transaction that first locks the
 * task row and checks it still has the holder that read saw: one of them moves the task and reports it to the Portal,
 * the others find the holder changed and leave the task, and its change history, alone. A move that saves a task's
 * extended row it read earlier fails on the row's version when the row changed meanwhile; the whole move is then
 * undone and left to the next pass in the same way.
 */
@Slf4j
@Component
public class TaskHandover {

    private final FlowableRuntimeTaskQueries runtimeTaskQueries;
    private final TransactionTemplate transactionTemplate;

    public TaskHandover(FlowableRuntimeTaskQueries runtimeTaskQueries, PlatformTransactionManager transactionManager) {
        this.runtimeTaskQueries = runtimeTaskQueries;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs {@code move} in one transaction if the task still has {@code seenHolder} as assignee (null: still
     * unclaimed). Returns false, running nothing, when it does not: the task moved on or was completed meanwhile.
     * Also false, with everything the move did rolled back, when the move hits a version conflict on a row it read
     * before the lock.
     */
    public boolean ifStillHeldBy(String taskId, String seenHolder, Runnable move) {
        String holder = seenHolder == null || seenHolder.isBlank() ? null : seenHolder;
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                if (!runtimeTaskQueries.lockIfHeldBy(taskId, holder)) {
                    return false;
                }
                move.run();
                return true;
            }));
        } catch (OptimisticLockingFailureException e) {
            log.info("Task {} not moved, a row it read changed meanwhile: {}", taskId, e.getMessage());
            return false;
        }
    }
}
