package com.developer.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses BPMN XML into a main-process graph and optional scoped sub-process graphs (for debug simulation).
 */
final class BpmnGraphParser {

    private static final Pattern SUB_PROCESS_BLOCK = Pattern.compile(
            "<bpmn:subProcess\\b([^>]*)>([\\s\\S]*?)</bpmn:subProcess>",
            Pattern.DOTALL);
    private static final Pattern NODE_OPEN = Pattern.compile(
            "<bpmn:(\\w+)\\s+id=\"([^\"]+)\"(?:[^>]*name=\"([^\"]*)\")?[^>]*>",
            Pattern.DOTALL);
    private static final Pattern NODE_OPEN_ALT = Pattern.compile(
            "<bpmn:(\\w+)\\s+[^>]*id=\"([^\"]+)\"(?:[^>]*name=\"([^\"]*)\")?[^>]*>",
            Pattern.DOTALL);
    private static final Pattern SEQUENCE_FLOW = Pattern.compile(
            "<bpmn:sequenceFlow\\b([^>]*?)\\s*(?:/>|>([\\s\\S]*?)</bpmn:sequenceFlow>)",
            Pattern.DOTALL);
    private static final Pattern MI_LOOP = Pattern.compile(
            "<bpmn:multiInstanceLoopCharacteristics\\b([^>]*)>",
            Pattern.DOTALL);
    private static final Pattern IS_SEQUENTIAL = Pattern.compile(
            "isSequential=\"(true|false)\"");
    private static final Pattern FLOWABLE_COLLECTION = Pattern.compile(
            "<flowable:collection>([^<]+)</flowable:collection>");
    private static final Pattern FLOWABLE_ELEMENT_VAR = Pattern.compile(
            "<flowable:elementVariable>([^<]+)</flowable:elementVariable>");
    private static final Pattern COMPLETION_CONDITION = Pattern.compile(
            "<bpmn:completionCondition[^>]*>([\\s\\S]*?)</bpmn:completionCondition>",
            Pattern.DOTALL);
    private static final Pattern CUSTOM_PROPERTY = Pattern.compile(
            "<custom:property\\s+name=\"([^\"]+)\"\\s+value=\"([^\"]*)\"\\s*/>");
    private static final Pattern CUSTOM_PROPERTY_ALT = Pattern.compile(
            "<custom_1:property\\s+name=\"([^\"]+)\"\\s+value=\"([^\"]*)\"\\s*/>");

    private BpmnGraphParser() {
    }

    static ParsedBpmnGraph parse(String bpmnXml) {
        Map<String, SubProcessScope> subProcesses = new LinkedHashMap<>();

        Matcher subMatcher = SUB_PROCESS_BLOCK.matcher(bpmnXml);
        while (subMatcher.find()) {
            String attrs = subMatcher.group(1);
            String body = subMatcher.group(2);
            String id = readAttr(attrs, "id");
            String name = readAttr(attrs, "name");
            if (id == null || id.isBlank()) {
                continue;
            }
            SubProcessScope scope = parseSubProcessScope(id, name != null ? name : "", body);
            subProcesses.put(id, scope);
        }

        Set<String> nestedNodeIds = new LinkedHashSet<>();
        for (SubProcessScope scope : subProcesses.values()) {
            nestedNodeIds.addAll(scope.nodes().keySet());
        }

        Map<String, BpmnProcessSimulator.NodeInfo> mainNodes = parseNodes(bpmnXml, nestedNodeIds);
        Map<String, List<BpmnProcessSimulator.FlowEdge>> mainFlows = parseFlows(bpmnXml, nestedNodeIds);

        return new ParsedBpmnGraph(mainNodes, mainFlows, subProcesses);
    }

    private static SubProcessScope parseSubProcessScope(String id, String name, String body) {
        Map<String, BpmnProcessSimulator.NodeInfo> nodes = parseNodes(body, Set.of());
        Map<String, List<BpmnProcessSimulator.FlowEdge>> flows = parseFlows(body, Set.of());
        Optional<MiLoopConfig> mi = parseMiLoop(body);
        Long subTableId = parseSubTableId(body);
        return new SubProcessScope(id, name, nodes, flows, mi, subTableId);
    }

    private static Optional<MiLoopConfig> parseMiLoop(String body) {
        Matcher miMatcher = MI_LOOP.matcher(body);
        if (!miMatcher.find()) {
            return Optional.empty();
        }
        String loopAttrs = miMatcher.group(1) != null ? miMatcher.group(1) : "";
        boolean sequential = true;
        Matcher seqMatcher = IS_SEQUENTIAL.matcher(body);
        if (seqMatcher.find()) {
            sequential = "true".equalsIgnoreCase(seqMatcher.group(1));
        }
        String collection = matchFirst(FLOWABLE_COLLECTION, body).orElse(readAttr(loopAttrs, "flowable:collection"));
        String elementVar = matchFirst(FLOWABLE_ELEMENT_VAR, body)
                .orElse(Optional.ofNullable(readAttr(loopAttrs, "flowable:elementVariable")).orElse("currentItem"));
        if (collection == null || collection.isBlank()) {
            return Optional.empty();
        }
        String completionCondition = matchFirst(COMPLETION_CONDITION, body).orElse(null);
        if (completionCondition != null) {
            completionCondition = completionCondition.trim();
            if (completionCondition.isEmpty()) {
                completionCondition = null;
            }
        }
        return Optional.of(new MiLoopConfig(collection.trim(), elementVar.trim(), sequential, completionCondition));
    }

    private static Long parseSubTableId(String body) {
        Map<String, String> props = parseExtensionProperties(body);
        String raw = props.get("subTableId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> parseExtensionProperties(String xml) {
        Map<String, String> props = new LinkedHashMap<>();
        for (Pattern pattern : List.of(CUSTOM_PROPERTY, CUSTOM_PROPERTY_ALT)) {
            Matcher matcher = pattern.matcher(xml);
            while (matcher.find()) {
                props.putIfAbsent(matcher.group(1), matcher.group(2));
            }
        }
        return props;
    }

    private static Map<String, BpmnProcessSimulator.NodeInfo> parseNodes(String xml, Set<String> excludeIds) {
        Map<String, BpmnProcessSimulator.NodeInfo> nodes = new LinkedHashMap<>();
        collectNodes(xml, excludeIds, nodes, NODE_OPEN);
        collectNodes(xml, excludeIds, nodes, NODE_OPEN_ALT);
        return nodes;
    }

    private static void collectNodes(
            String xml,
            Set<String> excludeIds,
            Map<String, BpmnProcessSimulator.NodeInfo> nodes,
            Pattern pattern) {
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String type = matcher.group(1);
            String id = matcher.group(2);
            String name = matcher.group(3);
            if (type == null || id == null || shouldSkipType(type) || excludeIds.contains(id)) {
                continue;
            }
            nodes.putIfAbsent(id, new BpmnProcessSimulator.NodeInfo(id, type, name != null ? name : ""));
        }
    }

    private static Map<String, List<BpmnProcessSimulator.FlowEdge>> parseFlows(String xml, Set<String> excludeIds) {
        Map<String, List<BpmnProcessSimulator.FlowEdge>> outgoing = new LinkedHashMap<>();
        Matcher flowMatcher = SEQUENCE_FLOW.matcher(xml);
        while (flowMatcher.find()) {
            String attrs = flowMatcher.group(1);
            String body = flowMatcher.group(2);
            String flowId = readAttr(attrs, "id");
            String source = readAttr(attrs, "sourceRef");
            String target = readAttr(attrs, "targetRef");
            if (flowId == null || source == null || target == null) {
                continue;
            }
            if (excludeIds.contains(source) || excludeIds.contains(target)) {
                continue;
            }
            String condition = body != null ? extractCondition(body) : null;
            outgoing.computeIfAbsent(source, k -> new ArrayList<>())
                    .add(new BpmnProcessSimulator.FlowEdge(flowId, source, target, condition));
        }
        return outgoing;
    }

    private static boolean shouldSkipType(String type) {
        return "sequenceFlow".equals(type) || "process".equals(type) || "definitions".equals(type);
    }

    private static String readAttr(String attrs, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attrs);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Optional<String> matchFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }

    private static String extractCondition(String flowBody) {
        if (flowBody == null || flowBody.isBlank()) {
            return null;
        }
        Pattern conditionPattern = Pattern.compile(
                "<(?:bpmn:)?conditionExpression[^>]*>([\\s\\S]*?)</(?:bpmn:)?conditionExpression>");
        Matcher condMatcher = conditionPattern.matcher(flowBody);
        return condMatcher.find() ? condMatcher.group(1).trim() : null;
    }

    record ParsedBpmnGraph(
            Map<String, BpmnProcessSimulator.NodeInfo> mainNodes,
            Map<String, List<BpmnProcessSimulator.FlowEdge>> mainOutgoing,
            Map<String, SubProcessScope> subProcesses) {
    }

    record SubProcessScope(
            String id,
            String name,
            Map<String, BpmnProcessSimulator.NodeInfo> nodes,
            Map<String, List<BpmnProcessSimulator.FlowEdge>> outgoing,
            Optional<MiLoopConfig> miLoop,
            Long subTableId) {
    }

    record MiLoopConfig(
            String collectionVariable,
            String elementVariable,
            boolean sequential,
            String completionCondition) {
    }
}
