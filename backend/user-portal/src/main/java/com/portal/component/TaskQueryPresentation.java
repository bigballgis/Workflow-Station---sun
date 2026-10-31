package com.portal.component;

import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.util.TaskQueryColumnFilters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

/** Fill presentation before filtering; never changes the cached identity or permission fields. */
@Component
@RequiredArgsConstructor
public class TaskQueryPresentation {
    private final TaskPresentationLabels labels;

    public static boolean neededBeforeFiltering(TaskQueryRequest request) {
        if (request.getKeyword()!=null && !request.getKeyword().isBlank()) return true;
        if (List.of("assigneeName","currentStepName","currentNode").contains(request.getSortBy()==null ? "" : request.getSortBy().trim())) return true;
        return TaskQueryColumnFilters.normalize(request.getFilters()).stream()
                .anyMatch(filter->"assigneeName".equals(filter.field()) || "currentStepName".equals(filter.field()));
    }

    public void annotate(List<TaskInfo> tasks) {
        // An empty label is authoritative. Already enriched Mine rows need no second union lookup.
        var missing=tasks.stream().filter(task->task.getAssigneeDisplay()==null).toList();
        for (int start=0;start<missing.size();start+=1000) {
            labels.annotate(missing.subList(start,Math.min(start+1000,missing.size())));
        }
    }
}
