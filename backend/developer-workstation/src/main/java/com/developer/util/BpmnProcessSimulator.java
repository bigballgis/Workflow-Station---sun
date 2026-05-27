package com.developer.util;

import com.developer.entity.FieldDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight BPMN path simulator for process design debug (no Flowable runtime).
 * Supports multi-instance sub-process expansion with mock collection rows.
 */
public final class BpmnProcessSimulator {

    private static final Pattern NODE_PATTERN = Pattern.compile(
            "<bpmn:(\\w+)\\s+id=\"([^\"]+)\"(?:[^>]*name=\"([^\"]*)\")?[^>]*>|"
                    + "<bpmn:(\\w+)\\s+[^>]*id=\"([^\"]+)\"(?:[^>]*name=\"([^\"]*)\")?[^>]*>",
            Pattern.DOTALL);
    private static final Pattern SEQUENCE_FLOW_PATTERN = Pattern.compile(
            "<bpmn:sequenceFlow\\b([^>]*?)\\s*(?:/>|>([\\s\\S]*?)</bpmn:sequenceFlow>)",
            Pattern.DOTALL);
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "<(?:bpmn:)?conditionExpression[^>]*>([\\s\\S]*?)</(?:bpmn:)?conditionExpression>");

    private static final int MAX_STEPS = 500;

    private BpmnProcessSimulator() {
    }

    public static Map<String, Object> simulate(String bpmnXml, Map<String, Object> variables) {
        return simulate(bpmnXml, variables, Map.of());
    }

    /**
     * @param fieldsByTableId sub-table field definitions keyed by table id (for MI mock collection generation)
     */
    public static Map<String, Object> simulate(
            String bpmnXml,
            Map<String, Object> variables,
            Map<Long, List<FieldDefinition>> fieldsByTableId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> workingVars = variables != null ? new LinkedHashMap<>(variables) : new LinkedHashMap<>();
        Map<Long, List<FieldDefinition>> fieldLookup = fieldsByTableId != null ? fieldsByTableId : Map.of();

        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.put("error", "BPMN XML is empty");
            result.put("completed", false);
            result.put("steps", List.of());
            result.put("variables", workingVars);
            return result;
        }

        BpmnGraphParser.ParsedBpmnGraph graph = BpmnGraphParser.parse(bpmnXml);
        Map<String, String> gatewayDefaultFlowIds = parseGatewayDefaultFlowIds(bpmnXml);
        Map<String, Object> processStructure = buildStructure(graph);

        Optional<String> startId = graph.mainNodes().values().stream()
                .filter(n -> "startEvent".equals(n.type()))
                .map(NodeInfo::id)
                .findFirst();

        if (startId.isEmpty()) {
            result.put("error", "Process is missing a start event");
            result.put("completed", false);
            result.put("steps", List.of());
            result.put("processStructure", processStructure);
            result.put("variables", workingVars);
            return result;
        }

        SimulationState state = new SimulationState();
        walkMainPath(
                graph,
                fieldLookup,
                startId.get(),
                workingVars,
                state,
                null,
                gatewayDefaultFlowIds);

        if (state.steps.size() >= MAX_STEPS && !state.completed) {
            state.error = "Simulation exceeded maximum step limit (" + MAX_STEPS + ")";
        }

        result.put("processStructure", processStructure);
        result.put("variables", workingVars);
        result.put("steps", state.steps);
        result.put("totalSteps", state.steps.size());
        result.put("completed", state.completed);
        if (state.error != null) {
            result.put("error", state.error);
        }
        if (!state.generatedCollections.isEmpty()) {
            result.put("generatedCollections", state.generatedCollections);
        }
        if (!state.warnings.isEmpty()) {
            result.put("warnings", state.warnings);
        }
        return result;
    }

    private static void walkMainPath(
            BpmnGraphParser.ParsedBpmnGraph graph,
            Map<Long, List<FieldDefinition>> fieldsByTableId,
            String currentId,
            Map<String, Object> workingVars,
            SimulationState state,
            String miVisitScope,
            Map<String, String> gatewayDefaultFlowIds) {
        while (currentId != null && state.steps.size() < MAX_STEPS && state.error == null) {
            NodeInfo node = graph.mainNodes().get(currentId);
            if (node == null) {
                state.error = "Unknown node: " + currentId;
                return;
            }

            String visitKey = miVisitScope != null ? miVisitScope + ":" + currentId : currentId;
            int visits = state.visitCount.merge(visitKey, 1, Integer::sum);
            if (visits > 3) {
                state.error = "Possible cycle detected at node: " + node.displayName();
                return;
            }

            if ("subProcess".equals(node.type())) {
                BpmnGraphParser.SubProcessScope scope = graph.subProcesses().get(node.id());
                if (scope != null && scope.miLoop().isPresent()) {
                    expandMultiInstanceSubProcess(scope, fieldsByTableId, workingVars, state, gatewayDefaultFlowIds);
                    currentId = nextMainNodeAfter(graph, node.id());
                    continue;
                }
            }

            addStep(state, node, workingVars, null);

            if ("endEvent".equals(node.type())) {
                state.completed = true;
                return;
            }

            StepTransition transition = selectNextNodeId(
                    node,
                    graph.mainOutgoing().getOrDefault(currentId, List.of()),
                    workingVars,
                    state,
                    gatewayDefaultFlowIds.get(node.id()));
            attachGatewayEvalToLatestStep(state, transition.gatewayEval());
            currentId = transition.nextNodeId();
        }
    }

    private static String nextMainNodeAfter(BpmnGraphParser.ParsedBpmnGraph graph, String subProcessId) {
        List<FlowEdge> edges = graph.mainOutgoing().getOrDefault(subProcessId, List.of());
        if (edges.isEmpty()) {
            return null;
        }
        return edges.get(0).targetId();
    }

    private static void expandMultiInstanceSubProcess(
            BpmnGraphParser.SubProcessScope scope,
            Map<Long, List<FieldDefinition>> fieldsByTableId,
            Map<String, Object> workingVars,
            SimulationState state,
            Map<String, String> gatewayDefaultFlowIds) {
        BpmnGraphParser.MiLoopConfig mi = scope.miLoop().orElseThrow();
        List<Map<String, Object>> collection = resolveCollection(
                mi.collectionVariable(),
                scope.subTableId(),
                fieldsByTableId,
                workingVars,
                state);

        if (collection.isEmpty()) {
            state.error = "Multi-instance collection is empty: " + mi.collectionVariable();
            return;
        }

        workingVars.put(mi.collectionVariable(), collection);

        boolean parallelMode = !mi.sequential();
        int totalInstances = collection.size();
        int nrOfCompletedInstances = 0;

        NodeInfo subProcessNode = new NodeInfo(scope.id(), "subProcess", scope.name());
        Map<String, Object> subProcessMi = baseMiContext(scope, mi, null, 0, totalInstances);
        subProcessMi.put("phase", "enter");
        subProcessMi.put("parallelMode", parallelMode);
        addStep(state, subProcessNode, workingVars, subProcessMi);

        int instancesToWalk = parallelMode ? 1 : totalInstances;
        for (int i = 0; i < instancesToWalk && state.steps.size() < MAX_STEPS && state.error == null; i++) {
            Map<String, Object> item = collection.get(i);
            Map<String, Object> instanceVars = new LinkedHashMap<>(workingVars);
            instanceVars.put(mi.elementVariable(), item);
            instanceVars.put("nrOfInstances", totalInstances);
            instanceVars.put("nrOfCompletedInstances", nrOfCompletedInstances);

            Map<String, Object> instanceMi = baseMiContext(scope, mi, item, i + 1, totalInstances);
            instanceMi.put("phase", "instance");
            instanceMi.put("parallelMode", parallelMode);

            walkInnerScope(scope, instanceVars, state, instanceMi, i + 1, gatewayDefaultFlowIds);

            nrOfCompletedInstances++;
            instanceVars.put("nrOfCompletedInstances", nrOfCompletedInstances);

            if (!parallelMode && shouldCompleteMultiInstance(mi.completionCondition(), nrOfCompletedInstances, totalInstances)) {
                break;
            }
        }

        Map<String, Object> exitMi = baseMiContext(scope, mi, null, nrOfCompletedInstances, totalInstances);
        exitMi.put("phase", "exit");
        exitMi.put("parallelMode", parallelMode);
        exitMi.put("completedInstances", nrOfCompletedInstances);
        addStep(state, subProcessNode, workingVars, exitMi);
    }

    /**
     * Evaluates common Flowable multi-instance completion expressions for debug simulation.
     */
    static boolean shouldCompleteMultiInstance(String completionCondition, int nrOfCompletedInstances, int nrOfInstances) {
        if (completionCondition == null || completionCondition.isBlank()) {
            return false;
        }
        if (nrOfInstances <= 0 || nrOfCompletedInstances <= 0) {
            return false;
        }

        String expression = completionCondition.trim();
        if (expression.startsWith("${") && expression.endsWith("}")) {
            expression = expression.substring(2, expression.length() - 1).trim();
        }

        Map<String, Object> miVars = Map.of(
                "nrOfCompletedInstances", nrOfCompletedInstances,
                "nrOfInstances", nrOfInstances);

        Matcher ratioEq = Pattern.compile(
                "nrOfCompletedInstances\\s*/\\s*nrOfInstances\\s*==\\s*([0-9.]+)").matcher(expression);
        if (ratioEq.find()) {
            double target = Double.parseDouble(ratioEq.group(1));
            return Math.abs(((double) nrOfCompletedInstances / nrOfInstances) - target) < 0.0001;
        }

        Matcher ratioGte = Pattern.compile(
                "nrOfCompletedInstances\\s*/\\s*nrOfInstances\\s*>=\\s*([0-9.]+)").matcher(expression);
        if (ratioGte.find()) {
            double target = Double.parseDouble(ratioGte.group(1));
            return ((double) nrOfCompletedInstances / nrOfInstances) >= target - 0.0001;
        }

        if (expression.contains("nrOfCompletedInstances") && expression.contains("nrOfInstances")) {
            String normalized = expression
                    .replace("nrOfCompletedInstances", String.valueOf(nrOfCompletedInstances))
                    .replace("nrOfInstances", String.valueOf(nrOfInstances));
            if (normalized.contains("==")) {
                String[] parts = normalized.split("==", 2);
                if (parts.length == 2) {
                    try {
                        double left = Double.parseDouble(parts[0].trim());
                        double right = Double.parseDouble(parts[1].trim());
                        return Math.abs(left - right) < 0.0001;
                    } catch (NumberFormatException ignored) {
                        return evaluateSimpleCondition(expression, miVars);
                    }
                }
            }
        }

        return evaluateSimpleCondition(expression, miVars);
    }

    private static Map<String, Object> baseMiContext(
            BpmnGraphParser.SubProcessScope scope,
            BpmnGraphParser.MiLoopConfig mi,
            Map<String, Object> currentItem,
            int instanceIndex,
            int totalInstances) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("subProcessId", scope.id());
        ctx.put("subProcessName", scope.name());
        ctx.put("collectionVariable", mi.collectionVariable());
        ctx.put("elementVariable", mi.elementVariable());
        ctx.put("sequential", mi.sequential());
        ctx.put("parallelMode", !mi.sequential());
        if (mi.completionCondition() != null && !mi.completionCondition().isBlank()) {
            ctx.put("completionCondition", mi.completionCondition());
        }
        ctx.put("instanceIndex", instanceIndex);
        ctx.put("totalInstances", totalInstances);
        if (scope.subTableId() != null) {
            ctx.put("subTableId", scope.subTableId());
        }
        if (currentItem != null) {
            ctx.put("currentItem", currentItem);
        }
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resolveCollection(
            String collectionVariable,
            Long subTableId,
            Map<Long, List<FieldDefinition>> fieldsByTableId,
            Map<String, Object> workingVars,
            SimulationState state) {
        Object raw = workingVars.get(collectionVariable);
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    normalized.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        List<FieldDefinition> fields = subTableId != null
                ? fieldsByTableId.getOrDefault(subTableId, List.of())
                : List.of();
        int count = DebugMockCollectionGenerator.defaultInstanceCount();
        List<Map<String, Object>> generated = DebugMockCollectionGenerator.generate(fields, count);
        workingVars.put(collectionVariable, generated);
        state.generatedCollections.put(collectionVariable, Map.of(
                "subTableId", subTableId,
                "instanceCount", generated.size(),
                "source", "autoFromSubTableFields"
        ));
        return generated;
    }

    private static void walkInnerScope(
            BpmnGraphParser.SubProcessScope scope,
            Map<String, Object> instanceVars,
            SimulationState state,
            Map<String, Object> instanceMi,
            int instanceNumber,
            Map<String, String> gatewayDefaultFlowIds) {
        Optional<String> innerStart = scope.nodes().values().stream()
                .filter(n -> "startEvent".equals(n.type()))
                .map(NodeInfo::id)
                .findFirst();
        if (innerStart.isEmpty()) {
            state.error = "Multi-instance sub-process is missing a start event: " + scope.name();
            return;
        }

        String currentId = innerStart.get();
        String visitScope = scope.id() + "#" + instanceNumber;

        while (currentId != null && state.steps.size() < MAX_STEPS && state.error == null) {
            NodeInfo node = scope.nodes().get(currentId);
            if (node == null) {
                state.error = "Unknown inner node: " + currentId;
                return;
            }

            String visitKey = visitScope + ":" + currentId;
            int visits = state.visitCount.merge(visitKey, 1, Integer::sum);
            if (visits > 3) {
                state.error = "Possible cycle detected at inner node: " + node.displayName();
                return;
            }

            addStep(state, node, instanceVars, instanceMi);

            if ("endEvent".equals(node.type())) {
                return;
            }

            StepTransition transition = selectNextNodeId(
                    node,
                    scope.outgoing().getOrDefault(currentId, List.of()),
                    instanceVars,
                    state,
                    gatewayDefaultFlowIds.get(node.id()));
            attachGatewayEvalToLatestStep(state, transition.gatewayEval());
            currentId = transition.nextNodeId();
        }
    }

    private static void addStep(
            SimulationState state,
            NodeInfo node,
            Map<String, Object> variables,
            Map<String, Object> miContext) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("nodeId", node.id());
        step.put("nodeName", node.displayName());
        step.put("nodeType", node.type());
        step.put("variables", new LinkedHashMap<>(variables));
        step.put("message", describeNodeEntry(node.type(), miContext));
        if (miContext != null && !miContext.isEmpty()) {
            step.put("miContext", new LinkedHashMap<>(miContext));
        }
        state.steps.add(step);
    }

    private static String describeNodeEntry(String type, Map<String, Object> miContext) {
        if (miContext != null && "subProcess".equals(type)) {
            String phase = String.valueOf(miContext.getOrDefault("phase", ""));
            boolean parallel = Boolean.TRUE.equals(miContext.get("parallelMode"));
            return switch (phase) {
                case "enter" -> parallel
                        ? "Entering parallel multi-instance sub-process (preview one instance at a time)"
                        : "Entering multi-instance sub-process";
                case "exit" -> "Multi-instance sub-process completed";
                default -> "Entered sub-process";
            };
        }
        if (miContext != null && miContext.get("instanceIndex") instanceof Number idx) {
            Object total = miContext.get("totalInstances");
            return "Multi-instance step (instance " + idx + "/" + total + ")";
        }
        return switch (type) {
            case "startEvent" -> "Process started";
            case "endEvent" -> "Process ended";
            case "userTask" -> "Entered user task";
            case "serviceTask" -> "Entered service task";
            case "scriptTask" -> "Entered script task";
            case "businessRuleTask" -> "Entered business rule task";
            case "exclusiveGateway" -> "Evaluating exclusive gateway";
            case "parallelGateway" -> "Entered parallel gateway";
            case "inclusiveGateway" -> "Evaluating inclusive gateway";
            case "subProcess" -> "Entered sub-process";
            case "callActivity" -> "Entered call activity";
            default -> "Entered " + type;
        };
    }

    private static StepTransition selectNextNodeId(
            NodeInfo node,
            List<FlowEdge> edges,
            Map<String, Object> workingVars,
            SimulationState state,
            String defaultFlowId) {
        if (edges.isEmpty()) {
            state.error = "No outgoing flow from node: " + node.displayName();
            return StepTransition.of(null, null);
        }
        FlowSelection selection = selectNextFlow(node, edges, workingVars, defaultFlowId);
        if (selection.selectedEdge() == null) {
            state.error = "No matching outgoing flow from gateway: " + node.displayName();
            return StepTransition.of(null, selection.gatewayEval());
        }
        if (selection.warning() != null) {
            state.warnings.add(selection.warning());
        }
        return StepTransition.of(selection.selectedEdge().targetId(), selection.gatewayEval());
    }

    static FlowSelection selectNextFlow(
            NodeInfo node,
            List<FlowEdge> edges,
            Map<String, Object> variables,
            String explicitDefaultFlowId) {
        if (edges.size() == 1) {
            return FlowSelection.of(edges.get(0), null, null);
        }

        boolean isGateway = node.type().endsWith("Gateway");
        if (!isGateway) {
            return FlowSelection.of(edges.get(0), null, null);
        }

        FlowEdge defaultFlow = null;
        if (explicitDefaultFlowId != null && !explicitDefaultFlowId.isBlank()) {
            for (FlowEdge edge : edges) {
                if (explicitDefaultFlowId.equals(edge.flowId())) {
                    defaultFlow = edge;
                    break;
                }
            }
        }
        List<Map<String, Object>> evaluations = new ArrayList<>();
        for (FlowEdge edge : edges) {
            if (edge.conditionExpression() == null || edge.conditionExpression().isBlank()) {
                if (defaultFlow == null) {
                    defaultFlow = edge;
                }
                continue;
            }
            ConditionEvaluation condition = evaluateSimpleConditionWithReason(edge.conditionExpression(), variables);
            Map<String, Object> eval = new LinkedHashMap<>();
            eval.put("flowId", edge.flowId());
            eval.put("condition", edge.conditionExpression());
            eval.put("result", condition.result());
            eval.put("reason", condition.reason());
            evaluations.add(eval);
            if (condition.result()) {
                return FlowSelection.of(edge, buildGatewayEval(node, defaultFlow, evaluations, edge.flowId()), null);
            }
        }
        FlowEdge fallback = defaultFlow != null ? defaultFlow : edges.get(0);
        Map<String, Object> warning = null;
        if (defaultFlow == null) {
            warning = Map.of(
                    "code", "BIZ_DEBUG_GATEWAY_EXPRESSION_UNSUPPORTED",
                    "message", "Gateway " + node.id() + " fallback to first outgoing flow");
        }
        return FlowSelection.of(
                fallback,
                buildGatewayEval(node, defaultFlow, evaluations, fallback.flowId()),
                warning);
    }

    private static Map<String, Object> buildGatewayEval(
            NodeInfo node,
            FlowEdge defaultFlow,
            List<Map<String, Object>> evaluations,
            String selectedFlowId) {
        Map<String, Object> gatewayEval = new LinkedHashMap<>();
        gatewayEval.put("gatewayId", node.id());
        gatewayEval.put("gatewayType", node.type());
        if (defaultFlow != null) {
            gatewayEval.put("defaultFlowId", defaultFlow.flowId());
        }
        gatewayEval.put("evaluations", evaluations);
        gatewayEval.put("selectedFlowId", selectedFlowId);
        return gatewayEval;
    }

    private static void attachGatewayEvalToLatestStep(SimulationState state, Map<String, Object> gatewayEval) {
        if (gatewayEval == null || state.steps.isEmpty()) {
            return;
        }
        state.steps.get(state.steps.size() - 1).put("gatewayEval", gatewayEval);
    }

    static boolean evaluateSimpleCondition(String conditionExpression, Map<String, Object> variables) {
        return evaluateSimpleConditionWithReason(conditionExpression, variables).result();
    }

    static ConditionEvaluation evaluateSimpleConditionWithReason(String conditionExpression, Map<String, Object> variables) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return new ConditionEvaluation(true, "EMPTY_CONDITION");
        }

        String expression = conditionExpression.trim();
        if (expression.startsWith("${") && expression.endsWith("}")) {
            expression = expression.substring(2, expression.length() - 1).trim();
        }

        if (expression.contains("==")) {
            String[] parts = expression.split("==", 2);
            if (parts.length == 2) {
                String leftVar = parts[0].trim();
                String rightValue = parts[1].trim().replace("'", "").replace("\"", "");
                Object varValue = variables.get(leftVar);
                boolean result = equalsVariable(varValue, rightValue);
                return new ConditionEvaluation(result, leftVar + "=" + String.valueOf(varValue) + " == " + rightValue);
            }
        }

        if (expression.contains("!=")) {
            String[] parts = expression.split("!=", 2);
            if (parts.length == 2) {
                String leftVar = parts[0].trim();
                String rightValue = parts[1].trim().replace("'", "").replace("\"", "");
                Object varValue = variables.get(leftVar);
                boolean result = !equalsVariable(varValue, rightValue);
                return new ConditionEvaluation(result, leftVar + "=" + String.valueOf(varValue) + " != " + rightValue);
            }
        }

        String[] numericOperators = {"<=", ">=", "<", ">"};
        for (String op : numericOperators) {
            if (expression.contains(op)) {
                String[] parts = expression.split(Pattern.quote(op), 2);
                if (parts.length == 2) {
                    String leftVar = parts[0].trim();
                    String rightLiteral = parts[1].trim();
                    Object varValue = variables.get(leftVar);
                    if (varValue == null) {
                        return new ConditionEvaluation(false, leftVar + " is null");
                    }
                    try {
                        double leftNum = toDouble(varValue);
                        double rightNum = Double.parseDouble(rightLiteral);
                        boolean result = switch (op) {
                            case "<=" -> leftNum <= rightNum;
                            case ">=" -> leftNum >= rightNum;
                            case "<" -> leftNum < rightNum;
                            case ">" -> leftNum > rightNum;
                            default -> false;
                        };
                        return new ConditionEvaluation(result, leftVar + "=" + leftNum + " " + op + " " + rightNum);
                    } catch (NumberFormatException e) {
                        return new ConditionEvaluation(false, "UNSUPPORTED_EXPRESSION");
                    }
                }
            }
        }

        Object varValue = variables.get(expression);
        if (varValue instanceof Boolean bool) {
            return new ConditionEvaluation(bool, expression + "=" + bool);
        }
        return new ConditionEvaluation(true, "UNSUPPORTED_EXPRESSION");
    }

    private static boolean equalsVariable(Object varValue, String rightLiteral) {
        String right = rightLiteral.trim();
        if ("true".equalsIgnoreCase(right)) {
            return Boolean.TRUE.equals(varValue) || "true".equalsIgnoreCase(String.valueOf(varValue));
        }
        if ("false".equalsIgnoreCase(right)) {
            return Boolean.FALSE.equals(varValue) || "false".equalsIgnoreCase(String.valueOf(varValue));
        }
        return right.equals(String.valueOf(varValue));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static Map<String, Object> buildStructure(BpmnGraphParser.ParsedBpmnGraph graph) {
        List<Map<String, String>> nodeList = new ArrayList<>();
        for (NodeInfo node : graph.mainNodes().values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", node.id());
            item.put("type", node.type());
            item.put("name", node.displayName());
            nodeList.add(item);
        }
        for (BpmnGraphParser.SubProcessScope scope : graph.subProcesses().values()) {
            for (NodeInfo node : scope.nodes().values()) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("id", node.id());
                item.put("type", node.type());
                item.put("name", node.displayName());
                item.put("parentSubProcessId", scope.id());
                nodeList.add(item);
            }
        }

        List<Map<String, String>> flowList = new ArrayList<>();
        for (List<FlowEdge> edges : graph.mainOutgoing().values()) {
            for (FlowEdge edge : edges) {
                Map<String, String> flow = new LinkedHashMap<>();
                flow.put("id", edge.flowId());
                flow.put("source", edge.sourceId());
                flow.put("target", edge.targetId());
                flowList.add(flow);
            }
        }
        for (BpmnGraphParser.SubProcessScope scope : graph.subProcesses().values()) {
            for (List<FlowEdge> edges : scope.outgoing().values()) {
                for (FlowEdge edge : edges) {
                    Map<String, String> flow = new LinkedHashMap<>();
                    flow.put("id", edge.flowId());
                    flow.put("source", edge.sourceId());
                    flow.put("target", edge.targetId());
                    flow.put("parentSubProcessId", scope.id());
                    flowList.add(flow);
                }
            }
        }

        Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("nodes", nodeList);
        structure.put("flows", flowList);
        return structure;
    }

    /** Fallback flat parser for legacy callers / tests without sub-process scoping. */
    static Map<String, NodeInfo> parseFlatNodes(String bpmnXml) {
        Map<String, NodeInfo> nodes = new LinkedHashMap<>();
        Matcher matcher = NODE_PATTERN.matcher(bpmnXml);
        while (matcher.find()) {
            String type = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
            String id = matcher.group(2) != null ? matcher.group(2) : matcher.group(5);
            String name = matcher.group(3) != null ? matcher.group(3) : matcher.group(6);
            if (type == null || id == null || "sequenceFlow".equals(type) || "process".equals(type)
                    || "definitions".equals(type)) {
                continue;
            }
            nodes.putIfAbsent(id, new NodeInfo(id, type, name != null ? name : ""));
        }
        return nodes;
    }

    static Map<String, List<FlowEdge>> parseFlatOutgoingFlows(String bpmnXml) {
        Map<String, List<FlowEdge>> outgoing = new LinkedHashMap<>();
        Matcher flowMatcher = SEQUENCE_FLOW_PATTERN.matcher(bpmnXml);
        while (flowMatcher.find()) {
            String attrs = flowMatcher.group(1);
            String body = flowMatcher.group(2);
            String flowId = readAttr(attrs, "id");
            String source = readAttr(attrs, "sourceRef");
            String target = readAttr(attrs, "targetRef");
            if (flowId == null || source == null || target == null) {
                continue;
            }
            String condition = body != null ? extractCondition(body) : null;
            outgoing.computeIfAbsent(source, k -> new ArrayList<>())
                    .add(new FlowEdge(flowId, source, target, condition));
        }
        return outgoing;
    }

    private static String readAttr(String attrs, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attrs);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractCondition(String flowBody) {
        if (flowBody == null || flowBody.isBlank()) {
            return null;
        }
        Matcher condMatcher = CONDITION_PATTERN.matcher(flowBody);
        return condMatcher.find() ? condMatcher.group(1).trim() : null;
    }

    private static Map<String, String> parseGatewayDefaultFlowIds(String bpmnXml) {
        Map<String, String> defaults = new LinkedHashMap<>();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return defaults;
        }
        Pattern gatewayPattern = Pattern.compile("<bpmn:(?:exclusiveGateway|inclusiveGateway|parallelGateway|eventBasedGateway|complexGateway)\\b([^>]*)>");
        Matcher matcher = gatewayPattern.matcher(bpmnXml);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            if (attrs == null) {
                continue;
            }
            String id = readAttr(attrs, "id");
            String defaultFlowId = readAttr(attrs, "default");
            if (id != null && defaultFlowId != null && !defaultFlowId.isBlank()) {
                defaults.put(id, defaultFlowId);
            }
        }
        return defaults;
    }

    private static final class SimulationState {
        private final List<Map<String, Object>> steps = new ArrayList<>();
        private final Map<String, Integer> visitCount = new HashMap<>();
        private final Map<String, Object> generatedCollections = new LinkedHashMap<>();
        private final List<Map<String, Object>> warnings = new ArrayList<>();
        private boolean completed;
        private String error;
    }

    record NodeInfo(String id, String type, String name) {
        String displayName() {
            return name != null && !name.isBlank() ? name : id;
        }
    }

    record FlowEdge(String flowId, String sourceId, String targetId, String conditionExpression) {
    }

    record ConditionEvaluation(boolean result, String reason) {
    }

    record FlowSelection(FlowEdge selectedEdge, Map<String, Object> gatewayEval, Map<String, Object> warning) {
        static FlowSelection of(FlowEdge selectedEdge, Map<String, Object> gatewayEval, Map<String, Object> warning) {
            return new FlowSelection(selectedEdge, gatewayEval, warning);
        }
    }

    record StepTransition(String nextNodeId, Map<String, Object> gatewayEval) {
        static StepTransition of(String nextNodeId, Map<String, Object> gatewayEval) {
            return new StepTransition(nextNodeId, gatewayEval);
        }
    }
}
