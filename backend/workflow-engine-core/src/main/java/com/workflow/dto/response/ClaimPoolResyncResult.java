package com.workflow.dto.response;

/**
 * Outcome of a claim-pool resync.
 *
 * @param tasksExamined   open pool tasks looked at
 * @param tasksUpdated    tasks whose candidates changed
 * @param holdersReleased holders who no longer hold the BU + Role, whose task went back to the pool (D-40)
 * @param tasksSkipped    tasks left as they are: the BU does not admit the role (or the pool names an unknown code)
 * @param tasksFailed     tasks a Flowable error stopped (e.g. completed meanwhile); the next pass retries them
 */
public record ClaimPoolResyncResult(int tasksExamined, int tasksUpdated, int holdersReleased,
                                    int tasksSkipped, int tasksFailed) {
}
