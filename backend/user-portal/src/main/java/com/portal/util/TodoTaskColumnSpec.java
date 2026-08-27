package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.List;

/**
 * Fixed column declaration for To Do. Kind drives shared-header operators; filtering/sorting
 * that cannot be pushed into Flowable runs as an exact portal fullScan (see {@link EngineTaskPushdown}).
 *
 * <p>{@code requestId} is computed (enriched before filter/sort). It is ordinary TEXT
 * ({@link ListColumnMeta#of}) so the header can search and A→Z sort. {@code priority} is stored as Flowable's
 * numeric string ({@code "50"}); ENUM options map to numeric bands in
 * {@link TaskQueryColumnFilters} so chrome labels match the cell renderer.
 */
public final class TodoTaskColumnSpec {

    private TodoTaskColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("requestId", "task.requestId", Kind.TEXT),
                ListColumnMeta.of("taskName", "task.taskName", Kind.TEXT),
                ListColumnMeta.of("processDefinitionName", "task.processName", Kind.TEXT),
                ListColumnMeta.withOptions("assignmentType", "task.assignmentType", Kind.ENUM, assignmentOptions()),
                ListColumnMeta.of("initiatorName", "task.initiator", Kind.TEXT),
                ListColumnMeta.withOptions("priority", "task.priority", Kind.ENUM, priorityOptions()),
                ListColumnMeta.of("createTime", "task.createTime", Kind.DATETIME),
                ListColumnMeta.of("dueDate", "task.dueDate", Kind.DATETIME)
        );
    }

    private static List<ListColumnMeta.Option> assignmentOptions() {
        return List.of(
                new ListColumnMeta.Option("USER", "task.user"),
                new ListColumnMeta.Option("CANDIDATE_USERS", "task.candidateUsers"),
                new ListColumnMeta.Option("VIRTUAL_GROUP", "task.virtualGroup"),
                new ListColumnMeta.Option("DEPT_ROLE", "task.deptRole")
        );
    }

    private static List<ListColumnMeta.Option> priorityOptions() {
        return List.of(
                new ListColumnMeta.Option("URGENT", "task.urgent"),
                new ListColumnMeta.Option("HIGH", "task.high"),
                new ListColumnMeta.Option("NORMAL", "task.normal"),
                new ListColumnMeta.Option("LOW", "task.low")
        );
    }
}
