package com.developer.util;

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
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> workingVars = variables != null ? new LinkedHashMap<>(variables) : new LinkedHashMap<>();

        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.put("error", "BPMN XML is empty");
            result.put("completed", false);
            result.put("steps", List.of());
            result.put("variables", workingVars);
            return result;
        }

        Map<String, NodeInfo> nodes = parseNodes(bpmnXml);
        Map<String, List<FlowEdge>> outgoing = parseOutgoingFlows(bpmnXml);
        Map<String, Object> processStructure = buildStructure(nodes, outgoing);

        Optional<String> startId = nodes.values().stream()
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

        List<Map<String, Object>> steps = new ArrayList<>();
        String currentId = startId.get();
        Map<String, Integer> visitCount = new HashMap<>();
        boolean completed = false;
        String error = null;

        while (currentId != null && steps.size() < MAX_STEPS) {
            NodeInfo node = nodes.get(currentId);
            if (node == null) {
                error = "Unknown node: " + currentId;
                break;
            }

            int visits = visitCount.merge(currentId, 1, Integer::sum);
            if (visits > 3) {
                error = "Possible cycle detected at node: " + node.displayName();
                break;
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("nodeId", node.id());
            step.put("nodeName", node.displayName());
            step.put("nodeType", node.type());
            step.put("variables", new LinkedHashMap<>(workingVars));
            step.put("message", describeNodeEntry(node.type()));
            steps.add(step);

            if ("endEvent".equals(node.type())) {
                completed = true;
                break;
            }

            List<FlowEdge> edges = outgoing.getOrDefault(currentId, List.of());
            if (edges.isEmpty()) {
                error = "No outgoing flow from node: " + node.displayName();
                break;
            }

            Optional<FlowEdge> nextEdge = selectNextFlow(node, edges, workingVars);
            if (nextEdge.isEmpty()) {
                error = "No matching outgoing flow from gateway: " + node.displayName();
                break;
            }

            currentId = nextEdge.get().targetId();
        }

        if (steps.size() >= MAX_STEPS && !completed) {
            error = "Simulation exceeded maximum step limit (" + MAX_STEPS + ")";
        }

        result.put("processStructure", processStructure);
        result.put("variables", workingVars);
        result.put("steps", steps);
        result.put("totalSteps", steps.size());
        result.put("completed", completed);
        if (error != null) {
            result.put("error", error);
        }
        return result;
    }

    private static String describeNodeEntry(String type) {
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

    private static Optional<FlowEdge> selectNextFlow(
            NodeInfo node,
            List<FlowEdge> edges,
            Map<String, Object> variables) {
        if (edges.size() == 1) {
            return Optional.of(edges.get(0));
        }

        boolean isGateway = node.type().endsWith("Gateway");
        if (!isGateway) {
            return Optional.of(edges.get(0));
        }

        FlowEdge defaultFlow = null;
        for (FlowEdge edge : edges) {
            if (edge.conditionExpression() == null || edge.conditionExpression().isBlank()) {
                if (defaultFlow == null) {
                    defaultFlow = edge;
                }
                continue;
            }
            if (evaluateSimpleCondition(edge.conditionExpression(), variables)) {
                return Optional.of(edge);
            }
        }
        return Optional.ofNullable(defaultFlow != null ? defaultFlow : edges.get(0));
    }

    static boolean evaluateSimpleCondition(String conditionExpression, Map<String, Object> variables) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return true;
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
                return equalsVariable(varValue, rightValue);
            }
        }

        if (expression.contains("!=")) {
            String[] parts = expression.split("!=", 2);
            if (parts.length == 2) {
                String leftVar = parts[0].trim();
                String rightValue = parts[1].trim().replace("'", "").replace("\"", "");
                Object varValue = variables.get(leftVar);
                return !equalsVariable(varValue, rightValue);
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
                        return false;
                    }
                    try {
                        double leftNum = toDouble(varValue);
                        double rightNum = Double.parseDouble(rightLiteral);
                        return switch (op) {
                            case "<=" -> leftNum <= rightNum;
                            case ">=" -> leftNum >= rightNum;
                            case "<" -> leftNum < rightNum;
                            case ">" -> leftNum > rightNum;
                            default -> false;
                        };
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
        }

        Object varValue = variables.get(expression);
        if (varValue instanceof Boolean bool) {
            return bool;
        }
        return true;
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

    private static Map<String, NodeInfo> parseNodes(String bpmnXml) {
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

    private static Map<String, List<FlowEdge>> parseOutgoingFlows(String bpmnXml) {
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
            addFlow(outgoing, flowId, source, target, condition);
        }
        return outgoing;
    }

    private static String readAttr(String attrs, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attrs);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void addFlow(
            Map<String, List<FlowEdge>> outgoing,
            String flowId,
            String source,
            String target,
            String condition) {
        outgoing.computeIfAbsent(source, k -> new ArrayList<>())
                .add(new FlowEdge(flowId, source, target, condition));
    }

    private static String extractCondition(String flowBody) {
        if (flowBody == null || flowBody.isBlank()) {
            return null;
        }
        Matcher condMatcher = CONDITION_PATTERN.matcher(flowBody);
        return condMatcher.find() ? condMatcher.group(1).trim() : null;
    }

    private static Map<String, Object> buildStructure(
            Map<String, NodeInfo> nodes,
            Map<String, List<FlowEdge>> outgoing) {
        List<Map<String, String>> nodeList = new ArrayList<>();
        for (NodeInfo node : nodes.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", node.id());
            item.put("type", node.type());
            item.put("name", node.displayName());
            nodeList.add(item);
        }

        List<Map<String, String>> flowList = new ArrayList<>();
        for (List<FlowEdge> edges : outgoing.values()) {
            for (FlowEdge edge : edges) {
                Map<String, String> flow = new LinkedHashMap<>();
                flow.put("id", edge.flowId());
                flow.put("source", edge.sourceId());
                flow.put("target", edge.targetId());
                flowList.add(flow);
            }
        }

        Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("nodes", nodeList);
        structure.put("flows", flowList);
        return structure;
    }

    private record NodeInfo(String id, String type, String name) {
        String displayName() {
            return name != null && !name.isBlank() ? name : id;
        }
    }

    private record FlowEdge(String flowId, String sourceId, String targetId, String conditionExpression) {
    }
}
