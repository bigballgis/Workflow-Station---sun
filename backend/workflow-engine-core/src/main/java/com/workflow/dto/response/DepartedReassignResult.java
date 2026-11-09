package com.workflow.dto.response;

/**
 * Outcome of handing the tasks of people who left to their managers (D-42).
 *
 * @param tasksExamined   open tasks held by, or offered to, people who left
 * @param tasksReassigned tasks now held by a manager
 * @param tasksStranded   tasks left where they are because no manager is active with Portal access (logged as errors)
 * @param tasksFailed     tasks an error stopped (pool unreadable, task changed meanwhile); the next pass retries them
 */
public record DepartedReassignResult(int tasksExamined, int tasksReassigned, int tasksStranded, int tasksFailed) {
}
