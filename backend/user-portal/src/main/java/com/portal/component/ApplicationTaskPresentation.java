package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ApplicationTaskPresentation {
    private final TaskPresentationLabels labels;
    public ApplicationTaskPresentation(TaskPresentationLabels labels) { this.labels=labels; }
    public void annotate(Collection<ProcessInstanceInfo> instances) {
        var running=instances.stream().filter(info->"RUNNING".equals(info.getStatus())).toList();
        for (int start=0;start<running.size();start+=100) {
            var batch=running.subList(start,Math.min(start+100,running.size()));
            var displays=labels.read(batch.stream().map(ProcessInstanceInfo::getId).toList(),List.of());
            for (var info:batch) apply(info,displays.stream().filter(d->info.getId().equals(d.processId())).toList());
        }
    }
    static void apply(ProcessInstanceInfo info,List<TaskPresentationLabels.Display> tasks) {
        if (tasks.isEmpty()) return; // No active user task: retain the existing call/service-step presentation.
        var stages=new LinkedHashSet<String>(); tasks.forEach(task->stages.add(task.stage()));
        info.setCurrentStepName(String.join("; ",stages));
        var assignments=new LinkedHashSet<String>();
        for (var task:tasks) if (task.assignee()!=null && !task.assignee().isBlank())
            assignments.add(stages.size()>1 ? task.stage()+" → "+task.assignee() : task.assignee());
        info.setCurrentAssigneeDisplay(String.join("; ",assignments));
        info.setCurrentAssigneeUserIds(tasks.stream().flatMap(task->task.userIds().stream()).distinct().toList());
    }
}
