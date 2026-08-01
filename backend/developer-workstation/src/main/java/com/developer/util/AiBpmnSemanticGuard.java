package com.developer.util;

import com.developer.exception.AiGenerationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台语义层的 BPMN 守门：先确定性修复，修不了的才拒绝。
 *
 * <p>{@code AiResponseParser} 原有的 BPMN 校验只管结构（连通性、DI、阶段绑定指向真实 userTask）。
 * 结构合法但语义跑不动的产物照样能存进去——审批分枝直接挂在 userTask 上而没有排他网关、
 * 条件写成 {@code ${decision == 'approved'}}（运行时只会写 yes/no，该分支永远不成立）、
 * 整个功能单元没有 PROCESS_SUBMIT 于是发起人没有提交按钮。这三类以前只靠提示词约束，
 * 模型不遵守时无人拦截。</p>
 *
 * <p>本类在解析阶段、写库之前介入，四条规则各自"能修就修"：
 * <ol>
 *   <li>R1 审批分枝必须经过 {@code bpmn:exclusiveGateway}——userTask 出现多条出向流或出向流带条件时，
 *       插入网关并把条件流改挂到网关上（含 DI 图形/连线）。</li>
 *   <li>R2 审批条件只认运行时真正写入的变量值——{@code TaskApprovalCompletionComponent} 对 APPROVE
 *       写 {@code decision=yes}、REJECT 写 {@code decision=no}，其余写法（approved/rejected/APPROVE/REJECT/
 *       true/false…）统一归一到 {@code ${decision == 'yes'|'no'}}。</li>
 *   <li>R3 有 userTask 就必须有 PROCESS_SUBMIT 动作——缺失时按平台契约补一个挂在第一个 userTask 上。</li>
 *   <li>R4 {@code stageIds} 必须指向真实 userTask——只在意图无歧义时修（部分合法则丢掉非法项；
 *       PROCESS_SUBMIT 全非法则归到第一个 userTask）。其余情况仍由 {@code AiResponseParser} 的
 *       {@code AI_ACTION_STAGE_BINDING_INVALID} 拒掉。</li>
 * </ol>
 *
 * <p>修不了的一律抛 {@link AiGenerationException}，错误码进
 * {@code AiGenerationServiceImpl.REPAIRABLE_ERROR_CODES}，走"把校验器原话喂回模型重生成一次"那条路。
 * 每一处实际改动都会记进 {@link Result#repairs()} 并由调用方 warn 出来——按错误处理治理红线 1，
 * 自动修复不等于静默兜底，改了什么必须留痕。</p>
 */
public final class AiBpmnSemanticGuard {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DC_NAMESPACE = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String DI_NAMESPACE = "http://www.omg.org/spec/DD/20100524/DI";

    /** 运行时唯一会被写入的审批结果变量与取值，见 {@code TaskApprovalCompletionComponent#handleApproval}。 */
    private static final String CANONICAL_DECISION_VARIABLE = "decision";
    private static final String CANONICAL_APPROVED = "yes";
    private static final String CANONICAL_REJECTED = "no";

    /**
     * 会被当成"审批结果判断"的变量名（小写比对）。刻意不收 {@code result}/{@code status} 这类过泛的名字——
     * {@code ${status == 'urgent'}} 是正常业务条件，误判会把能跑的流程判死。
     */
    private static final Set<String> APPROVAL_VARIABLES = Set.of(
            "decision", "approvaldecision", "approvalresult", "approvalstatus",
            "approved", "approveresult", "action", "outcome");

    private static final Set<String> APPROVED_LITERALS = Set.of(
            "yes", "y", "true", "approve", "approved", "approval", "pass", "passed",
            "ok", "accept", "accepted", "agree", "agreed", "1");
    private static final Set<String> REJECTED_LITERALS = Set.of(
            "no", "n", "false", "reject", "rejected", "rejection", "fail", "failed",
            "deny", "denied", "refuse", "refused", "disagree", "0");

    /** {@code ${var == 'literal'}} / {@code #{var != literal}}，整条表达式匹配才改写。 */
    private static final Pattern COMPARISON_CONDITION = Pattern.compile(
            "^\\s*[$#]\\{\\s*(\\w+)\\s*(==|!=)\\s*(['\"]?)([A-Za-z0-9_]+)\\3\\s*\\}\\s*$");
    /** {@code ${var.equals('literal')}}，可带前置 {@code !}。 */
    private static final Pattern EQUALS_CONDITION = Pattern.compile(
            "^\\s*[$#]\\{\\s*(!?)\\s*(\\w+)\\s*\\.equals\\(\\s*['\"]([A-Za-z0-9_]+)['\"]\\s*\\)\\s*\\}\\s*$");

    private AiBpmnSemanticGuard() {
    }

    /**
     * 修复后的产物。
     *
     * @param bpmnXml 修复后的 BPMN；未发生改动时与入参逐字相同（不重新序列化，避免无谓的格式漂移）
     * @param repairs 实际改动清单，人类可读，供调用方 warn 与回传前端
     */
    public record Result(String bpmnXml, List<String> repairs) {
    }

    /**
     * 先修再验。{@code generatedData} 中的 {@code actionDefinitions} 会被就地修正（R3/R4）。
     *
     * @param generatedData 本轮解析出的生成数据（可能只含 processDefinition，见范围化重生成）
     * @param bpmnXml       已完成结构校验前处理的 BPMN XML
     * @return 修复后的 BPMN 与改动清单
     * @throws AiGenerationException 存在无法确定性修复的语义违规
     */
    public static Result enforce(Map<String, Object> generatedData, String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return new Result(bpmnXml, List.of());
        }

        List<String> repairs = new ArrayList<>();
        Document document;
        try {
            document = parseSecurely(bpmnXml);
        } catch (Exception e) {
            throw new AiGenerationException("AI_BPMN_INVALID_XML",
                    "AI generated BPMN XML could not be parsed for semantic validation: " + e.getMessage());
        }

        boolean changed = normalizeApprovalConditions(document, repairs);
        changed |= gateApprovalBranches(document, repairs);

        List<String> orderedUserTaskIds = orderedUserTaskIds(document);
        repairActionStageBindings(generatedData, orderedUserTaskIds, repairs);
        ensureSubmitAction(generatedData, orderedUserTaskIds, repairs);

        String resultXml = bpmnXml;
        if (changed) {
            try {
                resultXml = serialize(document);
            } catch (Exception e) {
                throw new AiGenerationException("AI_BPMN_INVALID_XML",
                        "Semantically repaired BPMN could not be serialized: " + e.getMessage());
            }
        }
        return new Result(resultXml, List.copyOf(repairs));
    }

    // ==================== R2 审批条件变量值 ====================

    /**
     * 把审批条件归一到 {@code ${decision == 'yes'|'no'}}。
     *
     * <p>只动"变量名像审批结果"的整条表达式：{@code ${amount > 1000}} 这类业务条件、
     * {@code ${decision == 'yes' && amount > 100}} 这类复合表达式都原样保留（正则整条锚定，匹配不上就不碰）。
     * 变量是 {@code decision} 但取值既不是 yes/no 也不是任何可识别的审批词时无法确定意图，直接拒绝。</p>
     */
    private static boolean normalizeApprovalConditions(Document document, List<String> repairs) {
        NodeList conditions = document.getElementsByTagNameNS("*", "conditionExpression");
        boolean changed = false;
        for (int i = 0; i < conditions.getLength(); i++) {
            Element condition = (Element) conditions.item(i);
            String original = condition.getTextContent();
            if (original == null || original.isBlank()) {
                continue;
            }
            String rewritten = canonicalApprovalCondition(original, flowIdOf(condition));
            if (rewritten != null && !rewritten.equals(original.trim())) {
                condition.setTextContent(rewritten);
                repairs.add("rewrote condition on sequenceFlow '" + flowIdOf(condition) + "' from "
                        + original.trim() + " to " + rewritten
                        + " (runtime only ever writes decision=yes for APPROVE/PROCESS_SUBMIT and decision=no for REJECT/PROCESS_REJECT)");
                changed = true;
            }
        }
        return changed;
    }

    /**
     * @return 归一后的表达式；无需改动或不属于审批条件时返回 null
     * @throws AiGenerationException 变量是审批结果变量但取值无法映射到 approve/reject
     */
    private static String canonicalApprovalCondition(String expression, String flowId) {
        String variable;
        String literal;
        boolean negated;

        Matcher comparison = COMPARISON_CONDITION.matcher(expression);
        Matcher equalsCall = EQUALS_CONDITION.matcher(expression);
        if (comparison.matches()) {
            variable = comparison.group(1);
            negated = "!=".equals(comparison.group(2));
            literal = comparison.group(4);
        } else if (equalsCall.matches()) {
            negated = "!".equals(equalsCall.group(1));
            variable = equalsCall.group(2);
            literal = equalsCall.group(3);
        } else {
            return null;
        }

        String variableKey = variable.toLowerCase(Locale.ROOT);
        String literalKey = literal.toLowerCase(Locale.ROOT);
        boolean approvalVariable = APPROVAL_VARIABLES.contains(variableKey);
        boolean approvalLiteral = APPROVED_LITERALS.contains(literalKey) || REJECTED_LITERALS.contains(literalKey);

        if (!approvalVariable) {
            return null;
        }
        if (!approvalLiteral) {
            // decision 只由平台写入，取值必然是 yes/no；比成别的字面量意味着该分枝永远不成立。
            // 其它审批别名（action/outcome…）配非审批字面量则可能是正常业务条件，放行。
            if (CANONICAL_DECISION_VARIABLE.equals(variableKey)) {
                throw new AiGenerationException("AI_BPMN_DECISION_VALUE_INVALID",
                        "AI generated sequenceFlow '" + flowId + "' branches on " + expression.trim()
                                + ", but the platform only ever writes decision=yes (APPROVE/PROCESS_SUBMIT) or"
                                + " decision=no (REJECT/PROCESS_REJECT). That branch can never be taken.");
            }
            return null;
        }

        boolean approvedBranch = APPROVED_LITERALS.contains(literalKey) != negated;
        return "${" + CANONICAL_DECISION_VARIABLE + " == '"
                + (approvedBranch ? CANONICAL_APPROVED : CANONICAL_REJECTED) + "'}";
    }

    private static String flowIdOf(Element condition) {
        Node parent = condition.getParentNode();
        return parent instanceof Element flow ? flow.getAttribute("id") : "";
    }

    // ==================== R1 审批分枝必须经过排他网关 ====================

    /**
     * userTask 出现多条出向流、或唯一出向流带条件时，在其后插入 {@code bpmn:exclusiveGateway}：
     * userTask 无条件流向网关，原来的出向流改从网关出发，条件保持不变。
     *
     * <p>顺带把网关的默认流补上：分枝里若恰有一条无条件流，把它标成 {@code default}。否则它在文档顺序里
     * 排在条件流前面时会被无条件命中，审批结果再对也走错分支。</p>
     */
    private static boolean gateApprovalBranches(Document document, List<String> repairs) {
        Map<String, List<Element>> outgoingBySource = outgoingFlowsBySource(document);
        Set<String> usedIds = allElementIds(document);
        boolean changed = false;

        NodeList userTasks = document.getElementsByTagNameNS("*", "userTask");
        List<Element> tasks = new ArrayList<>();
        for (int i = 0; i < userTasks.getLength(); i++) {
            tasks.add((Element) userTasks.item(i));
        }

        for (Element task : tasks) {
            String taskId = task.getAttribute("id");
            List<Element> outgoing = outgoingBySource.getOrDefault(taskId, List.of());
            if (outgoing.isEmpty()) {
                continue;
            }
            boolean branching = outgoing.size() > 1;
            boolean conditional = outgoing.stream().anyMatch(flow -> conditionOf(flow) != null);
            if (!branching && !conditional) {
                // 唯一且无条件的出向流就是正确形态，无论下游是不是网关。
                continue;
            }

            insertGatewayAfter(document, task, outgoing, usedIds, repairs);
            changed = true;
        }

        changed |= assignGatewayDefaultFlows(document, repairs);
        return changed;
    }

    private static void insertGatewayAfter(Document document, Element task, List<Element> outgoing,
                                           Set<String> usedIds, List<String> repairs) {
        String taskId = task.getAttribute("id");
        String gatewayId = uniqueId("Gateway_" + taskId, usedIds);
        String linkFlowId = uniqueId("Flow_" + taskId + "_to_" + gatewayId, usedIds);
        Node parent = task.getParentNode();
        boolean documentUsesDirectionalRefs =
                hasDirectionalRefs(task, "outgoing") || hasDirectionalRefs(task, "incoming");

        Element gateway = document.createElementNS(BPMN_NAMESPACE,
                qualify(task.getPrefix(), "exclusiveGateway"));
        gateway.setAttribute("id", gatewayId);
        gateway.setAttribute("name", "");
        parent.insertBefore(gateway, task.getNextSibling());

        Element linkFlow = document.createElementNS(BPMN_NAMESPACE,
                qualify(task.getPrefix(), "sequenceFlow"));
        linkFlow.setAttribute("id", linkFlowId);
        linkFlow.setAttribute("sourceRef", taskId);
        linkFlow.setAttribute("targetRef", gatewayId);
        parent.appendChild(linkFlow);

        for (Element flow : outgoing) {
            flow.setAttribute("sourceRef", gatewayId);
        }

        // <bpmn:incoming>/<bpmn:outgoing> 只在文档本来就写了的时候维护，避免给一份不用这种写法的
        // BPMN 引入半套引用。
        List<String> gatewayOutgoingIds = outgoing.stream().map(flow -> flow.getAttribute("id")).toList();
        if (documentUsesDirectionalRefs) {
            replaceDirectionalRefs(document, task, "outgoing", List.of(linkFlowId));
            appendDirectionalRefs(document, gateway, task.getPrefix(), "incoming", List.of(linkFlowId));
            appendDirectionalRefs(document, gateway, task.getPrefix(), "outgoing", gatewayOutgoingIds);
        }

        addGatewayDiagram(document, task, gateway, linkFlowId, outgoing);

        repairs.add("inserted bpmn:exclusiveGateway '" + gatewayId + "' after bpmn:userTask '" + taskId
                + "'; its branch flows " + gatewayOutgoingIds
                + " now leave the gateway instead of the task (approval branches must be gated)");
    }

    /**
     * 网关有多条出向流、恰有一条不带条件、且没写 {@code default} 时，把那条标成默认流。
     * 保证"条件都不成立"时有确定去向，也保证无条件流不会因为文档顺序抢先命中。
     */
    private static boolean assignGatewayDefaultFlows(Document document, List<String> repairs) {
        Map<String, List<Element>> outgoingBySource = outgoingFlowsBySource(document);
        NodeList gateways = document.getElementsByTagNameNS("*", "exclusiveGateway");
        boolean changed = false;
        for (int i = 0; i < gateways.getLength(); i++) {
            Element gateway = (Element) gateways.item(i);
            if (!gateway.getAttribute("default").isBlank()) {
                continue;
            }
            List<Element> outgoing = outgoingBySource.getOrDefault(gateway.getAttribute("id"), List.of());
            if (outgoing.size() < 2) {
                continue;
            }
            List<Element> unconditional = outgoing.stream().filter(flow -> conditionOf(flow) == null).toList();
            if (unconditional.size() != 1) {
                continue;
            }
            String defaultFlowId = unconditional.get(0).getAttribute("id");
            gateway.setAttribute("default", defaultFlowId);
            repairs.add("marked sequenceFlow '" + defaultFlowId + "' as the default flow of gateway '"
                    + gateway.getAttribute("id") + "' (an unconditional branch would otherwise win on document order)");
            changed = true;
        }
        return changed;
    }

    // ==================== R1 的 DI 维护 ====================

    /**
     * 给新网关补 BPMNShape，给 userTask→网关补 BPMNEdge，并把被改挂的分枝连线起点移到网关边界。
     *
     * <p>找不到 BPMNPlane 或找不到该 userTask 的 BPMNShape 时跳过——那份 DI 本来就不完整，
     * {@code AiResponseParser} 的 {@code AI_BPMN_MISSING_DI} 会先一步拒绝，这里不必也不该猜坐标。</p>
     */
    private static void addGatewayDiagram(Document document, Element task, Element gateway,
                                          String linkFlowId, List<Element> movedFlows) {
        Element plane = firstElement(document, "BPMNPlane");
        if (plane == null) {
            return;
        }
        Element taskShape = findDiElement(document, "BPMNShape", task.getAttribute("id"));
        double[] taskBounds = taskShape != null ? readBounds(taskShape) : null;
        if (taskBounds == null) {
            return;
        }

        double gatewayWidth = 50;
        double gatewayHeight = 50;
        double gatewayX = taskBounds[0] + taskBounds[2] + 40;
        double gatewayY = taskBounds[1] + (taskBounds[3] - gatewayHeight) / 2;
        appendShape(document, plane, gateway.getAttribute("id"), gatewayX, gatewayY, gatewayWidth, gatewayHeight);

        double taskExitX = taskBounds[0] + taskBounds[2];
        double taskExitY = taskBounds[1] + taskBounds[3] / 2;
        double gatewayEntryX = gatewayX;
        double gatewayEntryY = gatewayY + gatewayHeight / 2;
        appendEdge(document, plane, linkFlowId,
                taskExitX, taskExitY, gatewayEntryX, gatewayEntryY);

        double gatewayExitX = gatewayX + gatewayWidth;
        double gatewayExitY = gatewayY + gatewayHeight / 2;
        for (Element flow : movedFlows) {
            Element edge = findDiElement(document, "BPMNEdge", flow.getAttribute("id"));
            if (edge != null) {
                moveEdgeOrigin(edge, gatewayExitX, gatewayExitY);
            }
        }
    }

    /** 把连线的第一个 waypoint 挪到网关出口；与末点重合会让 DI 校验判成零长边，故错开 1px。 */
    private static void moveEdgeOrigin(Element edge, double x, double y) {
        NodeList waypoints = edge.getElementsByTagNameNS("*", "waypoint");
        if (waypoints.getLength() < 2) {
            return;
        }
        Element first = (Element) waypoints.item(0);
        Element last = (Element) waypoints.item(waypoints.getLength() - 1);
        double resolvedX = x;
        if (formatCoordinate(x).equals(last.getAttribute("x"))
                && formatCoordinate(y).equals(last.getAttribute("y"))) {
            resolvedX = x - 1;
        }
        first.setAttribute("x", formatCoordinate(resolvedX));
        first.setAttribute("y", formatCoordinate(y));
    }

    private static void appendShape(Document document, Element plane, String bpmnElementId,
                                    double x, double y, double width, double height) {
        String bpmndiPrefix = prefixOrDefault(plane, "bpmndi");
        Element existingBounds = firstElement(document, "Bounds");
        String dcPrefix = existingBounds != null ? prefixOrDefault(existingBounds, "dc") : "dc";

        ensureNamespace(document, bpmndiPrefix, BPMNDI_NAMESPACE);
        ensureNamespace(document, dcPrefix, DC_NAMESPACE);

        Element shape = document.createElementNS(BPMNDI_NAMESPACE, qualify(bpmndiPrefix, "BPMNShape"));
        shape.setAttribute("id", bpmnElementId + "_di");
        shape.setAttribute("bpmnElement", bpmnElementId);
        shape.setAttribute("isMarkerVisible", "true");

        Element bounds = document.createElementNS(DC_NAMESPACE, qualify(dcPrefix, "Bounds"));
        bounds.setAttribute("x", formatCoordinate(x));
        bounds.setAttribute("y", formatCoordinate(y));
        bounds.setAttribute("width", formatCoordinate(width));
        bounds.setAttribute("height", formatCoordinate(height));
        shape.appendChild(bounds);
        plane.appendChild(shape);
    }

    private static void appendEdge(Document document, Element plane, String bpmnElementId,
                                   double fromX, double fromY, double toX, double toY) {
        String bpmndiPrefix = prefixOrDefault(plane, "bpmndi");
        Element existingWaypoint = firstElement(document, "waypoint");
        String diPrefix = existingWaypoint != null ? prefixOrDefault(existingWaypoint, "di") : "di";

        ensureNamespace(document, bpmndiPrefix, BPMNDI_NAMESPACE);
        ensureNamespace(document, diPrefix, DI_NAMESPACE);

        Element edge = document.createElementNS(BPMNDI_NAMESPACE, qualify(bpmndiPrefix, "BPMNEdge"));
        edge.setAttribute("id", bpmnElementId + "_di");
        edge.setAttribute("bpmnElement", bpmnElementId);
        edge.appendChild(waypoint(document, diPrefix, fromX, fromY));
        edge.appendChild(waypoint(document, diPrefix, toX, toY));
        plane.appendChild(edge);
    }

    private static Element waypoint(Document document, String diPrefix, double x, double y) {
        Element waypoint = document.createElementNS(DI_NAMESPACE, qualify(diPrefix, "waypoint"));
        waypoint.setAttribute("x", formatCoordinate(x));
        waypoint.setAttribute("y", formatCoordinate(y));
        return waypoint;
    }

    private static double[] readBounds(Element shape) {
        NodeList boundsList = shape.getElementsByTagNameNS("*", "Bounds");
        if (boundsList.getLength() == 0) {
            return null;
        }
        Element bounds = (Element) boundsList.item(0);
        try {
            return new double[]{
                    Double.parseDouble(bounds.getAttribute("x")),
                    Double.parseDouble(bounds.getAttribute("y")),
                    Double.parseDouble(bounds.getAttribute("width")),
                    Double.parseDouble(bounds.getAttribute("height"))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Element findDiElement(Document document, String localName, String bpmnElementId) {
        NodeList candidates = document.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < candidates.getLength(); i++) {
            Element candidate = (Element) candidates.item(i);
            if (bpmnElementId.equals(candidate.getAttribute("bpmnElement"))) {
                return candidate;
            }
        }
        return null;
    }

    private static String formatCoordinate(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ==================== R3 / R4 动作与阶段绑定 ====================

    /**
     * {@code stageIds} 指向不存在的 userTask 时只在意图无歧义的情况下修：部分合法就丢掉非法项；
     * PROCESS_SUBMIT 全非法则归到第一个 userTask（平台契约写死）。
     *
     * <p>其余情况（动作语义未知）不猜——留给 {@code AiResponseParser} 的
     * {@code AI_ACTION_STAGE_BINDING_INVALID} 拒掉并让模型重生成。猜错等于把审批按钮挂到提交环节上，
     * 比失败更难被发现。</p>
     */
    private static void repairActionStageBindings(Map<String, Object> generatedData,
                                                  List<String> orderedUserTaskIds, List<String> repairs) {
        if (!(generatedData.get("actionDefinitions") instanceof List<?> actions) || orderedUserTaskIds.isEmpty()) {
            return;
        }
        Set<String> validIds = new LinkedHashSet<>(orderedUserTaskIds);

        // 改动一律走"重建一份可变副本再整体回填"：调用方传进来的 map/list 未必可变
        // （测试夹具就用 Map.of 构造），就地 put 会撞 UnsupportedOperationException。
        List<Object> rebuilt = new ArrayList<>(actions);
        boolean changed = false;

        for (int i = 0; i < rebuilt.size(); i++) {
            if (!(rebuilt.get(i) instanceof Map<?, ?> raw)) {
                continue;
            }
            if (!(raw.get("stageIds") instanceof List<?> stageIds)) {
                continue;
            }
            String actionName = raw.get("actionName") instanceof String name ? name : "";
            List<String> kept = new ArrayList<>();
            List<String> dropped = new ArrayList<>();
            for (Object stageIdObj : stageIds) {
                String stageId = stageIdObj instanceof String id ? id.trim() : "";
                if (validIds.contains(stageId)) {
                    kept.add(stageId);
                } else {
                    dropped.add(stageId);
                }
            }
            if (dropped.isEmpty()) {
                continue;
            }

            if (kept.isEmpty()) {
                String fallback = resolveUnambiguousStage(raw, orderedUserTaskIds);
                if (fallback == null) {
                    continue;
                }
                kept.add(fallback);
            }
            Map<String, Object> repaired = new LinkedHashMap<>();
            raw.forEach((key, value) -> repaired.put(String.valueOf(key), value));
            repaired.put("stageIds", kept);
            rebuilt.set(i, repaired);
            changed = true;
            repairs.add("action '" + actionName + "' referenced non-existent user tasks " + dropped
                    + "; stageIds is now " + kept);
        }

        if (changed) {
            generatedData.put("actionDefinitions", rebuilt);
        }
    }

    /**
     * @return 唯一合理的 userTask id；无法唯一确定时返回 null
     *
     * <p>只有 PROCESS_SUBMIT 的归属是平台契约写死的（第一个 userTask）。其余动作即便全流程只有一个
     * userTask 也不自动归位——"动作指向不存在的节点"是模型跑偏的信号，退回去重生成比把按钮挪到
     * 唯一剩下的任务上更安全，那个唯一任务未必是模型本来想挂的地方。</p>
     */
    private static String resolveUnambiguousStage(Map<?, ?> action, List<String> orderedUserTaskIds) {
        String actionType = action.get("actionType") instanceof String type ? type.trim() : "";
        return "PROCESS_SUBMIT".equals(actionType) ? orderedUserTaskIds.get(0) : null;
    }

    /**
     * 有 userTask 就必须有 PROCESS_SUBMIT——否则发起人在流程首个环节没有提交按钮，流程根本推不动。
     *
     * <p>只在本轮确实产出 {@code actionDefinitions} 时介入：范围化重生成可能只回 processDefinition，
     * 那时候动作还在库里，凭空补一个反而制造重复。</p>
     */
    private static void ensureSubmitAction(Map<String, Object> generatedData,
                                           List<String> orderedUserTaskIds, List<String> repairs) {
        if (!(generatedData.get("actionDefinitions") instanceof List<?> actions) || orderedUserTaskIds.isEmpty()) {
            return;
        }
        Set<String> existingNames = new LinkedHashSet<>();
        for (Object actionObj : actions) {
            if (!(actionObj instanceof Map<?, ?> action)) {
                continue;
            }
            if (action.get("actionName") instanceof String name) {
                existingNames.add(name.trim());
            }
            if (action.get("actionType") instanceof String type && "PROCESS_SUBMIT".equals(type.trim())) {
                return;
            }
        }

        String firstUserTaskId = orderedUserTaskIds.get(0);
        String actionName = uniqueId("submit_request", existingNames);
        Map<String, Object> submit = new LinkedHashMap<>();
        submit.put("actionName", actionName);
        submit.put("actionType", "PROCESS_SUBMIT");
        submit.put("description", "Submit the request to start the approval process");
        submit.put("isDefault", Boolean.TRUE);
        submit.put("icon", null);
        submit.put("buttonColor", null);
        submit.put("configJson", null);
        submit.put("stageIds", new ArrayList<>(List.of(firstUserTaskId)));

        // 不直接 add 进解析出来的 list：Jackson 给的是 ArrayList，但没有契约保证，
        // 换一份可变副本回填最省心。
        List<Object> expanded = new ArrayList<>(actions);
        expanded.add(submit);
        generatedData.put("actionDefinitions", expanded);

        repairs.add("no PROCESS_SUBMIT action was generated; added '" + actionName + "' on the first bpmn:userTask '"
                + firstUserTaskId + "' so the initiator has a submit button");
    }

    /** userTask id，按从 startEvent 出发的广度优先顺序；不可达的按文档顺序补在后面。 */
    private static List<String> orderedUserTaskIds(Document document) {
        Set<String> userTaskIds = new LinkedHashSet<>();
        NodeList userTasks = document.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            String id = ((Element) userTasks.item(i)).getAttribute("id");
            if (!id.isBlank()) {
                userTaskIds.add(id);
            }
        }
        if (userTaskIds.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        NodeList flows = document.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            outgoing.computeIfAbsent(flow.getAttribute("sourceRef"), ignored -> new ArrayList<>())
                    .add(flow.getAttribute("targetRef"));
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        NodeList startEvents = document.getElementsByTagNameNS("*", "startEvent");
        for (int i = 0; i < startEvents.getLength(); i++) {
            queue.add(((Element) startEvents.item(i)).getAttribute("id"));
        }

        Set<String> visited = new LinkedHashSet<>();
        List<String> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (userTaskIds.contains(current)) {
                ordered.add(current);
            }
            queue.addAll(outgoing.getOrDefault(current, List.of()));
        }
        for (String id : userTaskIds) {
            if (!ordered.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    // ==================== DOM 小工具 ====================

    private static Map<String, List<Element>> outgoingFlowsBySource(Document document) {
        Map<String, List<Element>> bySource = new LinkedHashMap<>();
        NodeList flows = document.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            String sourceRef = flow.getAttribute("sourceRef");
            if (!sourceRef.isBlank()) {
                bySource.computeIfAbsent(sourceRef, ignored -> new ArrayList<>()).add(flow);
            }
        }
        return bySource;
    }

    private static Element conditionOf(Element flow) {
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && "conditionExpression".equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    private static boolean hasDirectionalRefs(Element node, String localName) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && localName.equals(localName(child))) {
                return true;
            }
        }
        return false;
    }

    private static void replaceDirectionalRefs(Document document, Element node, String localName,
                                               List<String> flowIds) {
        if (!hasDirectionalRefs(node, localName)) {
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            if (children.item(i) instanceof Element child && localName.equals(localName(child))) {
                node.removeChild(child);
            }
        }
        appendDirectionalRefs(document, node, node.getPrefix(), localName, flowIds);
    }

    private static void appendDirectionalRefs(Document document, Element node, String prefix,
                                              String localName, List<String> flowIds) {
        for (String flowId : flowIds) {
            Element ref = document.createElementNS(BPMN_NAMESPACE, qualify(prefix, localName));
            ref.setTextContent(flowId);
            node.appendChild(ref);
        }
    }

    private static Set<String> allElementIds(Document document) {
        Set<String> ids = new LinkedHashSet<>();
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            String id = ((Element) elements.item(i)).getAttribute("id");
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String uniqueId(String preferred, Set<String> used) {
        String candidate = preferred;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = preferred + "_" + suffix++;
        }
        return candidate;
    }

    private static Element firstElement(Document document, String localName) {
        NodeList elements = document.getElementsByTagNameNS("*", localName);
        return elements.getLength() > 0 ? (Element) elements.item(0) : null;
    }

    private static String prefixOrDefault(Element element, String fallback) {
        String prefix = element.getPrefix();
        return prefix == null || prefix.isBlank() ? fallback : prefix;
    }

    private static void ensureNamespace(Document document, String prefix, String namespaceUri) {
        Element root = document.getDocumentElement();
        if (!root.hasAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix)) {
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + prefix, namespaceUri);
        }
    }

    private static String qualify(String prefix, String localName) {
        return prefix == null || prefix.isBlank() ? localName : prefix + ":" + localName;
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private static Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
