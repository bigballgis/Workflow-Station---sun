package com.workflow.component;

import com.workflow.assignment.AssignmentDefinitionPolicy;
import com.workflow.assignment.AssignmentTaskStateRepository;
import com.workflow.dto.response.TaskListResult;
import com.workflow.enums.AssigneeType;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import jakarta.validation.constraints.*;
import java.util.*;

/** Read-only presentation facts; never a qualification or write decision. */
@Component
public class TaskPresentationReader {
    private final TaskService tasks;
    private final HistoryService history;
    private final TaskInfoAssembler assembler;
    private final AssignmentTaskStateRepository states;
    private final AssignmentDefinitionPolicy definitions;
    @org.springframework.beans.factory.annotation.Autowired private com.workflow.repository.ExtendedTaskInfoRepository extended;
    @org.springframework.beans.factory.annotation.Autowired private com.workflow.assignment.AssignmentSourceValues sourceValues;
    public TaskPresentationReader(TaskService tasks,HistoryService history,TaskInfoAssembler assembler,
            AssignmentTaskStateRepository states,AssignmentDefinitionPolicy definitions) {
        this.tasks=tasks; this.history=history; this.assembler=assembler; this.states=states; this.definitions=definitions;
    }
    public record Request(@NotNull @Size(max=100) List<@NotBlank @Size(max=255) String> processIds,
                          @NotNull @Size(max=1000) List<@NotBlank @Size(max=255) String> taskIds) { }
    public record Scope(String businessUnit,List<String> roles) { }
    public record Entry(TaskListResult.TaskInfo task,Scope scope) { }

    public List<Entry> read(Request request) {
        if (request.processIds().isEmpty() && request.taskIds().isEmpty()) throw new IllegalArgumentException("Task presentation scope is required");
        var active=new LinkedHashMap<String,Task>();
        if (!request.processIds().isEmpty()) tasks.createTaskQuery().processInstanceIdIn(request.processIds()).active()
                .orderByTaskCreateTime().asc().list().forEach(task->active.put(task.getId(),task));
        if (!request.taskIds().isEmpty()) tasks.createTaskQuery().taskIds(request.taskIds()).active()
                .orderByTaskCreateTime().asc().list().forEach(task->active.put(task.getId(),task));
        var result=new ArrayList<Entry>();
        var projections=new LinkedHashMap<String,AssignmentTaskStateRepository.State>();
        var dynamic=new LinkedHashMap<Task,AssignmentTaskStateRepository.State>();
        for (var task:active.values()) if ((task.getAssignee()==null || task.getAssignee().isBlank()) && definitions.tracked(task.getProcessDefinitionId())) {
            var state=states.find(task.getId()).orElseThrow(()->new IllegalStateException("Tracked task presentation is unavailable"));
            projections.put(task.getId(),state);
            var source=state.sourceSpec();
            if (source!=null && (source.kind()==com.workflow.assignment.AssignmentSourceSpec.Kind.ROW
                    || source.kind()==com.workflow.assignment.AssignmentSourceSpec.Kind.MANUAL)) dynamic.put(task,state);
        }
        var facts=dynamic.isEmpty() ? Map.<String,Map<String,Object>>of() : sourceValues.presentationSources(dynamic);
        var contexts=new HashMap<UUID,AssignmentTaskStateRepository.CaseContext>();
        var startUsers=assembler.prewarmUserDisplayNames(new ArrayList<>(active.values()));
        for (Task task:active.values()) {
            var info=assembler.convertFlowableTaskToTaskInfo(task,startUsers);
            // A holder is the native assignee, not the legacy INITIATOR display completion.
            info.setCurrentAssignee(task.getAssignee());
            if ((task.getAssignee()==null || task.getAssignee().isBlank())
                    && AssigneeType.fromCode(info.getBpmnAssigneeType())==AssigneeType.PROCESS_INITIATOR) {
                info.setCandidateUserIds(tasks.getIdentityLinksForTask(task.getId()).stream()
                        .filter(link->"candidate".equals(link.getType()) && link.getUserId()!=null)
                        .map(org.flowable.identitylink.api.IdentityLink::getUserId).distinct().toList());
            }
            info.setVariables(null);
            result.add(new Entry(info,scope(info,projections.get(task.getId()),facts.get(task.getId()),contexts)));
        }
        var missing=request.taskIds().stream().filter(id->!active.containsKey(id)).toList();
        if (!missing.isEmpty()) for (var task:history.createHistoricTaskInstanceQuery().taskIds(missing).finished().list()) {
            var info=assembler.buildTaskInfoFromHistoricTask(task); info.setVariables(null);
            result.add(new Entry(info,null));
        }
        return List.copyOf(result);
    }
    private Scope scope(TaskListResult.TaskInfo info,AssignmentTaskStateRepository.State state,
                        Map<String,Object> values,Map<UUID,AssignmentTaskStateRepository.CaseContext> contexts) {
        if (info.getCurrentAssignee()!=null && !info.getCurrentAssignee().isBlank()) return null;
        Scope primary=state==null ? primary(info) : currentSource(info,state,values,contexts);
        if (state==null || !"BACKUP".equals(state.poolSource())) return primary;
        var backup=definitions.node(info.getProcessDefinitionId(),info.getTaskDefinitionKey()).backup();
        if (!backup.enabled() || backup.mode()!=com.platform.common.assignment.AssignmentExclusionPolicy.Mode.BU_ROLE) return null;
        String bu=switch (backup.buSource()) {
            case FIXED_BU -> backup.businessUnitCode();
            case CASE_START_BU -> context(state,contexts).startBu();
            case NODE_ASSIGNMENT_BU -> primary!=null ? primary.businessUnit() : nodeBusinessUnit(info,state,values);
        };
        if (bu==null || bu.isBlank()) throw new IllegalStateException("Task assignment business unit is unavailable");
        return new Scope(bu,backup.roleCodes());
    }
    private AssignmentTaskStateRepository.CaseContext context(AssignmentTaskStateRepository.State state,
            Map<UUID,AssignmentTaskStateRepository.CaseContext> contexts) {
        return contexts.computeIfAbsent(state.caseId(),states::caseContext);
    }
    private Scope currentSource(TaskListResult.TaskInfo info,AssignmentTaskStateRepository.State state,
            Map<String,Object> values,Map<UUID,AssignmentTaskStateRepository.CaseContext> contexts) {
        var source=state.sourceSpec();
        if (source==null) throw new IllegalStateException("Task assignment source is unavailable");
        if ("BU_ROLE".equals(info.getDelegatedTargetType())) return primary(info);
        if (source.kind()!=com.workflow.assignment.AssignmentSourceSpec.Kind.ROW
                && source.kind()!=com.workflow.assignment.AssignmentSourceSpec.Kind.MANUAL) return primary(info);
        if (values==null) throw new IllegalStateException("Dynamic assignment values are unavailable");
        String roleField=source.kind()==com.workflow.assignment.AssignmentSourceSpec.Kind.ROW ? source.roleField()
                : field(source.manualRoleVariable(),"manualAssignRole_",info);
        if (source.kind()==com.workflow.assignment.AssignmentSourceSpec.Kind.MANUAL
                && com.workflow.listener.AssigneeUserIdNormalizer.normalizeFlowableUserIdValue(
                values.get(field(source.manualUserVariable(),"manualAssignee_",info)))!=null) return null;
        var roles=roleField==null ? List.<String>of() : com.workflow.util.AssigneeRoleIdsSupport.resolveRoleCodesFromItem(values,roleField);
        if (roles.isEmpty()) return null;
        String buField=source.kind()==com.workflow.assignment.AssignmentSourceSpec.Kind.ROW ? source.buField()
                : field(source.manualBuVariable(),"manualAssignBu_",info);
        Object raw=buField==null ? null : values.get(buField);
        String bu=raw instanceof String code && !code.isBlank() ? code.strip() : null;
        if (bu==null && source.kind()==com.workflow.assignment.AssignmentSourceSpec.Kind.ROW) bu=context(state,contexts).startBu();
        if (bu==null) throw new IllegalStateException("Dynamic assignment business unit is unavailable");
        return new Scope(bu,roles);
    }
    private String nodeBusinessUnit(TaskListResult.TaskInfo info,AssignmentTaskStateRepository.State state,Map<String,Object> values) {
        var source=state.sourceSpec();
        if (source==null) throw new IllegalStateException("Task assignment source is unavailable");
        Object value=switch (source.kind()) {
            case ROW -> values==null || source.buField()==null ? null : values.get(source.buField());
            case MANUAL -> values==null ? null : values.get(field(source.manualBuVariable(),"manualAssignBu_",info));
            default -> source.buCode();
        };
        if (!(value instanceof String code) || code.isBlank()) throw new IllegalStateException("Task assignment business unit is unavailable");
        return code.strip();
    }
    private String field(String configured,String prefix,TaskListResult.TaskInfo info) {
        return configured==null || configured.isBlank() ? prefix+info.getTaskDefinitionKey() : configured;
    }
    private Scope primary(TaskListResult.TaskInfo task) {
        if ("BU_ROLE".equals(task.getDelegatedTargetType())) return new Scope(task.getDelegatedBuCode(),List.of(task.getDelegatedRoleCode()));
        if ("role".equalsIgnoreCase(task.getMiAssigneeMode())) {
            var saved=extended.findByTaskIdAndIsDeletedFalse(task.getTaskId()).orElseThrow(()->new IllegalStateException("MI assignment metadata is unavailable"));
            try {
                var roles=com.platform.common.util.JsonUtils.getObjectMapper().readTree(saved.getExtendedProperties()).path("roleCodes");
                if (!roles.isArray() || roles.isEmpty()) throw new IllegalStateException("MI assignment roles are unavailable");
                var values=new ArrayList<String>();
                for (var role:roles) { if (!role.isTextual() || role.asText().isBlank()) throw new IllegalStateException("Invalid MI assignment role"); values.add(role.asText()); }
                return new Scope(task.getMiBusinessUnitCode(),List.copyOf(values));
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Invalid MI assignment metadata",error); }
        }
        if (AssigneeType.fromCode(task.getBpmnAssigneeType())==AssigneeType.BU_ROLE)
            return new Scope(task.getBpmnBusinessUnitId(),task.getBpmnRoleIds());
        return null;
    }
}
