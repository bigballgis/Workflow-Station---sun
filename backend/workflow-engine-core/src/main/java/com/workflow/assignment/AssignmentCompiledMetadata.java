package com.workflow.assignment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentExclusionPolicy;
import com.platform.common.assignment.AssignmentExclusionPolicyCodec;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;

import java.util.LinkedHashMap;
import java.util.Map;

/** One decoder for the server's immutable runtime metadata, including damaged-default-policy detection. */
public record AssignmentCompiledMetadata(boolean legacy, boolean tracked, Map<String,String> users,
        Map<String,AssignmentExclusionPolicy> policies) {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public static AssignmentCompiledMetadata read(BpmnModel model) {
        if (model == null || model.getMainProcess() == null) throw new IllegalStateException("Frozen assignment definition is unavailable");
        var process = model.getMainProcess();
        String contract = BpmnExtensionUtils.getExtensionProperty(process, AssignmentDeploymentCompiler.CONTRACT);
        Map<String,AssignmentExclusionPolicy> policies = new LinkedHashMap<>();
        for (UserTask task : process.findFlowElementsOfType(UserTask.class, true)) {
            String raw = BpmnExtensionUtils.getExtensionProperty(task, "assignmentExclusion");
            if (raw != null) policies.put(task.getId(), AssignmentExclusionPolicyCodec.decode(raw));
        }
        if (contract == null) {
            if (policies.values().stream().anyMatch(AssignmentExclusionPolicy::enabled)) {
                throw new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
            }
            return new AssignmentCompiledMetadata(true, false, Map.of(), Map.copyOf(policies));
        }
        if (!"1".equals(contract)) throw new IllegalStateException("Unsupported assignment runtime contract");
        String tracking = BpmnExtensionUtils.getExtensionProperty(process, AssignmentDeploymentCompiler.TRACKING);
        if (!("true".equals(tracking) || "false".equals(tracking))) throw new IllegalStateException("Invalid compiled assignment tracking");
        boolean tracked = Boolean.parseBoolean(tracking);
        if (!tracked && policies.values().stream().anyMatch(AssignmentExclusionPolicy::enabled)) {
            throw new IllegalStateException("Assignment tracking contradicts its node policies");
        }
        return new AssignmentCompiledMetadata(false, tracked,
                users(BpmnExtensionUtils.getExtensionProperty(process, AssignmentDeploymentCompiler.USERS)), Map.copyOf(policies));
    }

    private static Map<String,String> users(String value) {
        if (value == null) throw new IllegalStateException("Compiled assignment user bindings are unavailable");
        try {
            var node = JSON.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalStateException("Invalid compiled assignment binding object");
            Map<String,String> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey().isBlank() || !entry.getValue().isTextual() || entry.getValue().textValue().isBlank()
                        || entry.getValue().textValue().length() > 64) throw new IllegalStateException("Invalid compiled assignment user binding");
                result.put(entry.getKey(), entry.getValue().textValue());
            });
            return Map.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid compiled assignment user bindings", e);
        }
    }
}
