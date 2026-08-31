package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.List;

/**
 * Fixed column declaration for Tasks to Claim. Same shape as {@link TodoTaskColumnSpec} minus
 * {@code assignmentType} (every row here is the same BU Role pool) plus {@code claimedByName},
 * which tells the rest of the role who is currently holding a request.
 */
public final class ToClaimTaskColumnSpec {

    private ToClaimTaskColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("requestId", "task.requestId", Kind.TEXT),
                ListColumnMeta.of("taskName", "task.taskName", Kind.TEXT),
                ListColumnMeta.of("currentStepName", "task.currentStep", Kind.TEXT),
                ListColumnMeta.of("processDefinitionName", "task.processName", Kind.TEXT),
                ListColumnMeta.of("initiatorName", "task.initiator", Kind.TEXT),
                ListColumnMeta.of("assigneeName", "task.claimedBy", Kind.TEXT),
                ListColumnMeta.withOptions("priority", "task.priority", Kind.ENUM, priorityOptions()),
                ListColumnMeta.of("createTime", "task.createTime", Kind.DATETIME),
                ListColumnMeta.of("dueDate", "task.dueDate", Kind.DATETIME)
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
