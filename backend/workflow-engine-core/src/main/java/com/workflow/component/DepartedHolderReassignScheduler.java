package com.workflow.component;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The backstop for tasks of people who left (D-42): Admin Center tells the engine when it sets a user INACTIVE or
 * deletes them but does not retry when the engine is unreachable, and a task can also reach a departed user later (a
 * suspended task resumed, the last colleague in a pool losing Portal access). This pass looks at every open task's
 * holder and candidates; one replica runs it at a time.
 */
@Component
@RequiredArgsConstructor
public class DepartedHolderReassignScheduler {

    private final DepartedHolderReassignment departedHolderReassignment;

    @Scheduled(initialDelayString = "${workflow.departed-holders.full-pass-initial-delay-ms:150000}",
            fixedDelayString = "${workflow.departed-holders.full-pass-delay-ms:600000}")
    @SchedulerLock(name = "DepartedHolders_fullPass", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void reassignAll() {
        departedHolderReassignment.reassignAll();
    }
}
