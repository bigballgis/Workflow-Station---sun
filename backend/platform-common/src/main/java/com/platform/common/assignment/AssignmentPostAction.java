package com.platform.common.assignment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable completion mail context and service-only delivery identifiers. */
public final class AssignmentPostAction {
    private AssignmentPostAction() { }
    public record Plan(String actionId,String actionName,String actionType,String comment,List<Map<String,Object>> rules) {
        public Plan {
            if (actionId==null || actionId.isBlank() || actionType==null || actionType.isBlank() || rules==null || rules.isEmpty()) {
                throw new IllegalArgumentException("Missing frozen post-action mail configuration");
            }
            rules=List.copyOf(rules);
        }
    }
    public record Enqueue(@NotNull UUID operationId,@Min(0) int ruleIndex) { }
    public record SnapshotRequest(@NotNull UUID operationId,@Min(0) int ruleIndex,String eventId,String processInstanceId,String actor) { }
    public record Snapshot(SnapshotRequest request,Map<String,Object> variables) { }
    public static String eventId(UUID operation,int ruleIndex) {
        if (operation==null || ruleIndex<0) throw new IllegalArgumentException("Missing completion mail identity");
        return UUID.nameUUIDFromBytes((operation+"/mail/"+ruleIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public static Map<String,Object> payload(Map<String,Object> rule) {
        Map<String,Object> result=new LinkedHashMap<>();
        for (String key:List.of("connectionId","emailTo","emailCc","emailBcc","emailFrom","emailTemplateId","emailAttachments")) {
            if (rule.get(key)!=null) result.put(key,rule.get(key));
        }
        result.put("enabled",true);
        if (rule.get("id")!=null) result.put("ruleId",rule.get("id").toString());
        if (rule.get("name")!=null && !rule.get("name").toString().isBlank()) result.put("ruleName",rule.get("name").toString());
        return result;
    }
}
