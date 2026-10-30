package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAcceptedProcessState;
import com.platform.common.assignment.AssignmentFailure;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.Task;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pure projection over one batch of tasks and frozen call positions; no per-task HTTP or database query. */
final class AssignmentOutcomeWork {
    private final Map<String,AssignmentTaskStateRepository.RoundInstance> positions;
    private final List<Task> tasks;
    private final RepositoryService definitions;
    private final Map<String,BpmnModel> models=new HashMap<>();
    AssignmentOutcomeWork(Map<String,AssignmentTaskStateRepository.RoundInstance> positions,List<Task> tasks,RepositoryService definitions) {
        this.positions=positions; this.tasks=tasks; this.definitions=definitions;
    }
    List<AssignmentAcceptedProcessState.Task> project(AssignmentTaskStateRepository.RoundInstance owner) {
        var own=tasks.stream().filter(task->owner.instance().equals(task.getProcessInstanceId())).toList();
        var selected=own.isEmpty()?tasks.stream().filter(task->descends(task.getProcessInstanceId(),owner.instance())).toList():own;
        return selected.stream().map(task->project(task,owner)).toList();
    }
    private AssignmentAcceptedProcessState.Task project(Task task,AssignmentTaskStateRepository.RoundInstance owner) {
        if (task.getIdentityLinks()==null) throw unavailable();
        var users=task.getIdentityLinks().stream().filter(link->"candidate".equals(link.getType()) && link.getUserId()!=null)
                .map(org.flowable.identitylink.api.IdentityLinkInfo::getUserId).distinct().sorted().toList();
        return new AssignmentAcceptedProcessState.Task(task.getId(),task.getTaskDefinitionKey(),task.getName(),task.getAssignee(),users,
                task.getProcessInstanceId(),outer(task,owner));
    }
    private boolean descends(String instance,String ancestor) {
        var visited=new HashSet<String>();
        String cursor=instance;
        while (cursor!=null) {
            if (!visited.add(cursor)) throw unavailable();
            if (cursor.equals(ancestor)) return true;
            var position=positions.get(cursor);
            if (position==null) throw unavailable();
            cursor=position.parent();
        }
        return false;
    }
    private String outer(Task task,AssignmentTaskStateRepository.RoundInstance owner) {
        // Flowable caches deployed BPMN models; additionally load each frozen definition once for this capture.
        var model=models.computeIfAbsent(owner.definition(),definitions::getBpmnModel);
        if (model==null) throw unavailable();
        String node=task.getTaskDefinitionKey();
        if (!task.getProcessInstanceId().equals(owner.instance())) node=directCall(task.getProcessInstanceId(),owner.instance());
        FlowElement element=model.getFlowElement(node);
        if (element==null) throw unavailable();
        if (element instanceof org.flowable.bpmn.model.CallActivity call && call.getLoopCharacteristics()!=null) return name(call);
        var container=element.getParentContainer();
        while (container instanceof SubProcess parent) {
            if (parent.getLoopCharacteristics()!=null) return name(parent);
            container=parent.getParentContainer();
        }
        return null;
    }
    private String directCall(String instance,String owner) {
        var visited=new HashSet<String>();
        String cursor=instance;
        while (cursor!=null) {
            if (!visited.add(cursor)) throw unavailable();
            var position=positions.get(cursor);
            if (position==null) throw unavailable();
            if (owner.equals(position.parent())) {
                if (position.callActivity()==null) throw unavailable();
                return position.callActivity();
            }
            cursor=position.parent();
        }
        throw unavailable();
    }
    private String name(Activity activity) { return activity.getName()==null || activity.getName().isBlank()?activity.getId():activity.getName(); }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
