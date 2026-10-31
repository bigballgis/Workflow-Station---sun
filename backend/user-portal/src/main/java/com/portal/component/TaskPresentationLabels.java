package com.portal.component;

import com.portal.client.AssignmentEngineTransport;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.TaskInfo;
import com.portal.service.UserDisplayNameResolver;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Presentation is independent of stored Owner values and workflow permissions. */
@Component
public class TaskPresentationLabels {
    private static final ParameterizedTypeReference<ApiResponse<List<Map<String,Object>>>> TYPE=new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport engine;
    private final UserDisplayNameResolver users;
    private final NamedParameterJdbcTemplate jdbc;
    public TaskPresentationLabels(AssignmentEngineTransport engine,UserDisplayNameResolver users,JdbcTemplate jdbc) {
        this.engine=engine; this.users=users; this.jdbc=new NamedParameterJdbcTemplate(jdbc);
    }
    public record Display(String taskId,String processId,String stage,String assignee,List<String> userIds) { }
    public List<Display> read(List<String> processes,List<String> tasks) {
        if (processes.isEmpty() && tasks.isEmpty()) return List.of();
        var entries=engine.post(List.of("task-presentations"),Map.of("processIds",processes,"taskIds",tasks),TYPE,false);
        if (entries==null) throw new IllegalStateException("Task presentation response is unavailable");
        var facts=new ArrayList<Fact>();
        for (var entry:entries) facts.add(fact(entry));
        var keys=new LinkedHashSet<String>();
        facts.forEach(f->{ if (present(f.task().getAssignee())) keys.add(f.task().getAssignee()); else if(f.task().getCandidateUserIds()!=null) keys.addAll(f.task().getCandidateUserIds()); });
        var names=users.resolveBatch(keys);
        var aliases=identityAliases(keys);
        var buNames=directory(facts,true); var roleNames=directory(facts,false);
        return facts.stream().map(f->new Display(f.task().getTaskId(),f.task().getProcessInstanceId(),
                f.task().getCurrentStepName(),label(f,names,buNames,roleNames),identities(f,aliases))).toList();
    }
    public void annotate(Collection<TaskInfo> tasks) {
        if (tasks.isEmpty()) return;
        var displays=read(List.of(),tasks.stream().map(TaskInfo::getTaskId).filter(Objects::nonNull).distinct().toList());
        var indexed=new HashMap<String,Display>(); displays.forEach(d->indexed.put(d.taskId(),d));
        tasks.forEach(task->{ var display=indexed.get(task.getTaskId()); if (display!=null) {
            task.setCurrentStepName(display.stage()); task.setAssigneeDisplay(display.assignee());
        } });
    }
    private record Fact(TaskInfo task,String bu,List<String> roles) { }
    private List<String> identities(Fact fact,Map<String,List<String>> aliases) {
        var task=fact.task();
        var ids=present(task.getAssignee()) ? List.of(task.getAssignee())
                : task.getCandidateUserIds()==null ? List.<String>of() : task.getCandidateUserIds();
        return ids.stream().flatMap(id->aliases.getOrDefault(id,List.of(id)).stream()).distinct().toList();
    }
    private Map<String,List<String>> identityAliases(Set<String> keys) {
        if (keys.isEmpty()) return Map.of();
        var byId=new HashMap<String,List<String>>(); var byUsername=new HashMap<String,List<String>>(); var byEmployee=new HashMap<String,List<String>>();
        var ordered=new ArrayList<>(keys);
        for (int offset=0;offset<ordered.size();offset+=1000) {
            var batch=ordered.subList(offset,Math.min(offset+1000,ordered.size()));
            jdbc.query("SELECT id,username,employee_id FROM sys_users WHERE id IN (:keys) OR username IN (:keys) OR employee_id IN (:keys)",
                    Map.of("keys",batch),rs->{
                        String id=rs.getString("id"),username=rs.getString("username"),employee=rs.getString("employee_id");
                        var values=java.util.stream.Stream.of(id,username,employee).filter(this::present).distinct().toList();
                        byId.put(id,values); if (present(username)) byUsername.put(username,values); if (present(employee)) byEmployee.put(employee,values);
                    });
        }
        var result=new HashMap<String,List<String>>();
        keys.forEach(key->result.put(key,byId.containsKey(key) ? byId.get(key)
                : byUsername.containsKey(key) ? byUsername.get(key) : byEmployee.getOrDefault(key,List.of(key))));
        return result;
    }
    @SuppressWarnings("unchecked")
    private Fact fact(Map<String,Object> entry) {
        if (!(entry.get("task") instanceof Map<?,?> raw)) throw new IllegalStateException("Malformed task presentation");
        var task=EngineTaskMapper.convertMapToTaskInfo((Map<String,Object>)raw);
        var scope=entry.get("scope");
        if (scope==null) return new Fact(task,null,List.of());
        if (!(scope instanceof Map<?,?> map) || !(map.get("businessUnit") instanceof String bu) || bu.isBlank()
                || !(map.get("roles") instanceof List<?> roles) || roles.isEmpty()
                || roles.stream().anyMatch(role->!(role instanceof String s) || s.isBlank())) throw new IllegalStateException("Malformed task assignment scope");
        return new Fact(task,bu,roles.stream().map(String.class::cast).toList());
    }
    private Map<String,String> directory(List<Fact> facts,boolean businessUnits) {
        var keys=new LinkedHashSet<String>();
        facts.forEach(f->{ if (present(f.bu())) { if (businessUnits) keys.add(f.bu()); else keys.addAll(f.roles()); } });
        if (keys.isEmpty()) return Map.of();
        // Only platform metadata table names are selected here; identities remain bound parameters.
        String sql=businessUnits ? "SELECT id,code,name FROM sys_business_units WHERE id IN (:keys) OR code IN (:keys)"
                : "SELECT id,code,name FROM sys_roles WHERE id IN (:keys) OR code IN (:keys)";
        Map<String,String> result=new HashMap<>();
        jdbc.query(sql,Map.of("keys",keys),rs->{String name=rs.getString("name"); result.put(rs.getString("id"),name); result.put(rs.getString("code"),name);});
        return result;
    }
    private String label(Fact fact,Map<String,String> names,Map<String,String> units,Map<String,String> roles) {
        var task=fact.task();
        if (present(task.getAssignee())) return names.getOrDefault(task.getAssignee(),task.getAssignee());
        // FALLBACK(ux): a configured code remains meaningful when its directory label was removed.
        if (present(fact.bu())) return units.getOrDefault(fact.bu(),fact.bu())+" + "+String.join(" / ",fact.roles().stream().map(role->roles.getOrDefault(role,role)).toList());
        if (task.getCandidateUserIds()==null) return "";
        return String.join(", ",task.getCandidateUserIds().stream().map(id->names.getOrDefault(id,id)).distinct().toList());
    }
    private boolean present(String value) { return value!=null && !value.isBlank(); }
}
