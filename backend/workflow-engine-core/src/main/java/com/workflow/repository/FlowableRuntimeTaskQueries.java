package com.workflow.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read-only lookups on Flowable's runtime tables that its query API cannot express (claim-pool resync and the tasks
 * of people who left, platform access model WP8b), and the row lock the engine's own hand-overs take first.
 */
@Repository
@RequiredArgsConstructor
public class FlowableRuntimeTaskQueries {

    private static final int BATCH = 500;
    private static final String DISTINCT_NODES_SQL =
            "SELECT DISTINCT proc_def_id_, task_def_key_ FROM act_ru_task "
                    + "WHERE proc_def_id_ IS NOT NULL AND task_def_key_ IS NOT NULL";
    private static final String UNCLAIMED_CANDIDATE_TASKS_SQL =
            "SELECT DISTINCT t.id_ FROM act_ru_task t JOIN act_ru_identitylink l ON l.task_id_ = t.id_ "
                    + "WHERE t.assignee_ IS NULL AND l.type_ = 'candidate' AND l.user_id_ IN (%s)";
    private static final String OPEN_TASK_PEOPLE_SQL =
            "SELECT assignee_ FROM act_ru_task WHERE assignee_ IS NOT NULL "
                    + "UNION SELECT l.user_id_ FROM act_ru_identitylink l JOIN act_ru_task t ON t.id_ = l.task_id_ "
                    + "WHERE t.assignee_ IS NULL AND l.type_ = 'candidate' AND l.user_id_ IS NOT NULL";
    private static final String LOCK_HELD_BY_SQL =
            "SELECT id_ FROM act_ru_task WHERE id_ = ? AND assignee_ = ? FOR UPDATE";
    private static final String LOCK_UNCLAIMED_SQL =
            "SELECT id_ FROM act_ru_task WHERE id_ = ? AND assignee_ IS NULL FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;

    /** A user task definition that has at least one open task. */
    public record TaskNode(String processDefinitionId, String taskDefinitionKey) {
    }

    /** Every (process definition, user task) pair with open tasks; the pool kind is a BPMN property per pair. */
    public List<TaskNode> distinctOpenTaskNodes() {
        return jdbcTemplate.query(DISTINCT_NODES_SQL,
                (rs, i) -> new TaskNode(rs.getString(1), rs.getString(2)));
    }

    /** Holders of open tasks and candidate users of unclaimed ones: whose leaving can move a task (D-42). */
    public List<String> openTaskPeople() {
        return jdbcTemplate.queryForList(OPEN_TASK_PEOPLE_SQL, String.class);
    }

    /** Ids of unclaimed tasks where any of {@code userIds} is a candidate (Flowable has no "candidate in" criterion). */
    public List<String> unclaimedTaskIdsWithCandidateIn(Collection<String> userIds) {
        List<String> ids = userIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        List<String> taskIds = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<String> slice = ids.subList(from, Math.min(from + BATCH, ids.size()));
            String placeholders = String.join(",", slice.stream().map(id -> "?").toList());
            taskIds.addAll(jdbcTemplate.queryForList(String.format(UNCLAIMED_CANDIDATE_TASKS_SQL, placeholders),
                    String.class, slice.toArray()));
        }
        return taskIds.stream().distinct().toList();
    }

    /**
     * Locks the open task's row for the current transaction if it still has {@code holder} as assignee (null: still
     * unclaimed), and says whether it does. A transaction that waited for another one's lock reads the row again, so
     * of two that saw the same holder only the first finds it.
     */
    public boolean lockIfHeldBy(String taskId, String holder) {
        String sql = holder == null ? LOCK_UNCLAIMED_SQL : LOCK_HELD_BY_SQL;
        Object[] args = holder == null ? new Object[]{taskId} : new Object[]{taskId, holder};
        return !jdbcTemplate.queryForList(sql, String.class, args).isEmpty();
    }
}
