package com.workflow.assignment;

import com.platform.common.assignment.AssignmentCallRowOutputs;
import com.platform.common.util.JsonUtils;
import com.platform.common.subtable.SubTableStoreKeys;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.CallActivity;
import java.util.ArrayList;
import java.util.List;

/** Reads the existing designer mapping contract from the caller's immutable deployed BPMN. */
public final class AssignmentCallOutputMapping {
    private AssignmentCallOutputMapping() { }
    record Plan(List<String> nativeTargets,String rowStore,List<AssignmentCallRowOutputs.Mapping> rows) { }
    public static void validate(CallActivity call) { read(call); }
    static Plan read(CallActivity call) {
        var targets=new ArrayList<String>();
        for (var output:call.getOutParameters()) {
            if (output.getTarget()==null || output.getTarget().isBlank()) throw invalid();
            targets.add(output.getTarget());
        }
        var rows=rows(BpmnExtensionUtils.getExtensionProperty(call,"callOutputMapping"));
        String table=BpmnExtensionUtils.getExtensionProperty(call,"callRowsTable");
        if (!rows.isEmpty() && (table==null || table.isBlank() || call.getLoopCharacteristics()==null)) throw invalid();
        return new Plan(List.copyOf(targets),table==null?null:SubTableStoreKeys.dwKey(table),rows);
    }
    private static List<AssignmentCallRowOutputs.Mapping> rows(String source) {
        if (source==null || source.isBlank()) return List.of(); // No output mappings are configured.
        try {
            var configured=JsonUtils.getObjectMapper().readTree(source);
            if (!configured.isArray()) throw invalid();
            var result=new ArrayList<AssignmentCallRowOutputs.Mapping>();
            for (var value:configured) {
                if (!value.isObject()) throw invalid();
                String from=text(value,"from"), to=text(value,"to");
                if (from==null && to==null) continue; // The designer permits an entirely empty added mapping row.
                if (from==null || to==null) throw invalid();
                if (to.startsWith("row.")) {
                    String field=to.substring(4);
                    if (!from.matches("[A-Za-z_][A-Za-z0-9_]*") || !field.matches("[A-Za-z_][A-Za-z0-9_]*")) throw invalid();
                    result.add(new AssignmentCallRowOutputs.Mapping(from,field));
                }
            }
            return List.copyOf(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { throw invalid(); }
    }
    private static String text(com.fasterxml.jackson.databind.JsonNode value,String key) {
        var field=value.get(key);
        if (field==null || field.isNull() || field.isTextual() && field.asText().isBlank()) return null;
        if (!field.isTextual()) throw invalid();
        return field.asText().trim();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported or malformed frozen call output mapping"); }
}
