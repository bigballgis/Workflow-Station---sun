package com.portal.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * BPMN 解析服务
 * 用于解析 BPMN XML，获取流程节点信息和流转路径
 */
@Slf4j
@Service
public class BpmnParserService {

    /**
     * 用户任务节点信息
     */
    @Data
    @Builder
    public static class UserTaskInfo {
        private String taskId;
        private String taskName;
        private String assigneeType;
        private String assigneeValue;
        private String assigneeLabel;
        /** 与引擎 {@code AssigneeAnchor} 一致：INITIATOR / LAST_TASK_ASSIGNEE */
        private String assigneeAnchor;
        private String roleId;
        private String businessUnitId;
        private String assigneeVariable;
        private String manualAssignVariable;
        private String manualAssignBuVariable;
        private String manualAssignRoleVariable;
        private String subTableId;
        private String subTableName;
        private String candidateUsers;
        private String candidateGroups;
        private String formId;
        private String formName;
        private boolean formReadOnly;
        private List<String> actionIds;
        private List<String> outgoingFlows;
    }

    /**
     * 流程节点信息
     */
    @Data
    @Builder
    public static class FlowNodeInfo {
        private String nodeId;
        private String nodeName;
        private String nodeType; // startEvent, endEvent, userTask, exclusiveGateway, parallelGateway
        private List<String> incomingFlows;
        private List<String> outgoingFlows;
    }

    /**
     * 解析 BPMN XML 获取所有用户任务
     */
    public List<UserTaskInfo> parseUserTasks(String bpmnXml) {
        List<UserTaskInfo> tasks = new ArrayList<>();
        if (bpmnXml == null || bpmnXml.isEmpty()) {
            return tasks;
        }

        try {
            int searchStart = 0;
            while (true) {
                int userTaskStart = findUserTaskStart(bpmnXml, searchStart);
                if (userTaskStart == -1) break;

                int userTaskEnd = findUserTaskEnd(bpmnXml, userTaskStart);
                if (userTaskEnd == -1) break;

                String userTaskElement = bpmnXml.substring(userTaskStart, userTaskEnd);
                UserTaskInfo taskInfo = parseUserTaskElement(userTaskElement);
                if (taskInfo != null) {
                    tasks.add(taskInfo);
                }

                searchStart = userTaskEnd;
            }
        } catch (Exception e) {
            log.warn("Failed to parse BPMN user tasks: {}", e.getMessage());
        }

        return tasks;
    }

    /**
     * 根据当前节点ID获取下一个用户任务
     */
    public UserTaskInfo getNextUserTask(String bpmnXml, String currentNodeId, Map<String, Object> variables, String initiatorId) {
        if (bpmnXml == null || currentNodeId == null) {
            return null;
        }

        try {
            // 解析所有节点和连线
            Map<String, FlowNodeInfo> nodes = parseAllNodes(bpmnXml);
            Map<String, String[]> sequenceFlows = parseSequenceFlows(bpmnXml);

            // 从当前节点开始查找下一个用户任务
            return findNextUserTask(bpmnXml, currentNodeId, nodes, sequenceFlows, variables, initiatorId, new HashSet<>());
        } catch (Exception e) {
            log.warn("Failed to get next user task: {}", e.getMessage());
            return null;
        }
    }


    /**
     * 递归查找下一个用户任务
     */
    private UserTaskInfo findNextUserTask(String bpmnXml, String currentNodeId, 
                                          Map<String, FlowNodeInfo> nodes,
                                          Map<String, String[]> sequenceFlows,
                                          Map<String, Object> variables,
                                          String initiatorId,
                                          Set<String> visited) {
        if (visited.contains(currentNodeId)) {
            return null; // 防止循环
        }
        visited.add(currentNodeId);

        FlowNodeInfo currentNode = nodes.get(currentNodeId);
        if (currentNode == null) {
            return null;
        }

        // 获取当前节点的出口连线
        List<String> outgoingFlows = currentNode.getOutgoingFlows();
        if (outgoingFlows == null || outgoingFlows.isEmpty()) {
            return null;
        }

        for (String flowId : outgoingFlows) {
            String[] flow = sequenceFlows.get(flowId);
            if (flow == null || flow.length < 2) continue;

            String targetNodeId = flow[1];
            FlowNodeInfo targetNode = nodes.get(targetNodeId);
            if (targetNode == null) continue;

            String nodeType = targetNode.getNodeType();

            // 如果是用户任务，返回该任务信息
            if ("userTask".equals(nodeType)) {
                UserTaskInfo taskInfo = parseUserTaskById(bpmnXml, targetNodeId);
                if (taskInfo != null) {
                    // 解析处理人
                    resolveAssignee(taskInfo, variables, initiatorId);
                    return taskInfo;
                }
            }

            // 如果是结束事件，返回 null
            if ("endEvent".equals(nodeType)) {
                return null;
            }

            // 如果是网关，继续递归查找
            if (nodeType != null && nodeType.contains("Gateway")) {
                UserTaskInfo nextTask = findNextUserTask(bpmnXml, targetNodeId, nodes, sequenceFlows, variables, initiatorId, visited);
                if (nextTask != null) {
                    return nextTask;
                }
            }
        }

        return null;
    }

    /**
     * 解析所有节点
     */
    private Map<String, FlowNodeInfo> parseAllNodes(String bpmnXml) {
        Map<String, FlowNodeInfo> nodes = new HashMap<>();

        // 解析 startEvent
        parseNodesByType(bpmnXml, "startEvent", nodes);
        parseNodesByType(bpmnXml, "bpmn:startEvent", nodes);

        // 解析 endEvent
        parseNodesByType(bpmnXml, "endEvent", nodes);
        parseNodesByType(bpmnXml, "bpmn:endEvent", nodes);

        // 解析 userTask
        parseNodesByType(bpmnXml, "userTask", nodes);
        parseNodesByType(bpmnXml, "bpmn:userTask", nodes);

        // 解析网关
        parseNodesByType(bpmnXml, "exclusiveGateway", nodes);
        parseNodesByType(bpmnXml, "bpmn:exclusiveGateway", nodes);
        parseNodesByType(bpmnXml, "parallelGateway", nodes);
        parseNodesByType(bpmnXml, "bpmn:parallelGateway", nodes);
        parseNodesByType(bpmnXml, "inclusiveGateway", nodes);
        parseNodesByType(bpmnXml, "bpmn:inclusiveGateway", nodes);

        return nodes;
    }

    /**
     * 按类型解析节点
     */
    private void parseNodesByType(String bpmnXml, String tagName, Map<String, FlowNodeInfo> nodes) {
        int searchStart = 0;
        while (true) {
            int nodeStart = bpmnXml.indexOf("<" + tagName, searchStart);
            if (nodeStart == -1) break;

            int nodeEnd = findClosingTag(bpmnXml, nodeStart, tagName);
            if (nodeEnd == -1) {
                // 自闭合标签
                nodeEnd = bpmnXml.indexOf("/>", nodeStart);
                if (nodeEnd == -1) break;
                nodeEnd += 2;
            }

            String nodeElement = bpmnXml.substring(nodeStart, nodeEnd);
            String nodeId = extractAttribute(nodeElement, "id");
            String nodeName = extractAttribute(nodeElement, "name");

            if (nodeId != null) {
                // 解析 incoming 和 outgoing
                List<String> incoming = extractElements(nodeElement, "incoming");
                if (incoming.isEmpty()) {
                    incoming = extractElements(nodeElement, "bpmn:incoming");
                }
                List<String> outgoing = extractElements(nodeElement, "outgoing");
                if (outgoing.isEmpty()) {
                    outgoing = extractElements(nodeElement, "bpmn:outgoing");
                }

                String nodeType = tagName.replace("bpmn:", "");

                nodes.put(nodeId, FlowNodeInfo.builder()
                        .nodeId(nodeId)
                        .nodeName(nodeName)
                        .nodeType(nodeType)
                        .incomingFlows(incoming)
                        .outgoingFlows(outgoing)
                        .build());
            }

            searchStart = nodeEnd;
        }
    }


    /**
     * 解析所有顺序流
     */
    private Map<String, String[]> parseSequenceFlows(String bpmnXml) {
        Map<String, String[]> flows = new HashMap<>();
        int searchStart = 0;

        while (true) {
            int flowStart = bpmnXml.indexOf("<sequenceFlow", searchStart);
            if (flowStart == -1) {
                flowStart = bpmnXml.indexOf("<bpmn:sequenceFlow", searchStart);
            }
            if (flowStart == -1) break;

            int flowEnd = bpmnXml.indexOf("/>", flowStart);
            int flowEndTag = bpmnXml.indexOf("</sequenceFlow>", flowStart);
            if (flowEndTag == -1) {
                flowEndTag = bpmnXml.indexOf("</bpmn:sequenceFlow>", flowStart);
            }

            if (flowEnd == -1 && flowEndTag == -1) break;

            int actualEnd = flowEnd != -1 ? flowEnd + 2 : flowEndTag + 15;
            if (flowEndTag != -1 && flowEndTag < flowEnd) {
                actualEnd = flowEndTag + 20;
            }

            String flowElement = bpmnXml.substring(flowStart, actualEnd);
            String flowId = extractAttribute(flowElement, "id");
            String sourceRef = extractAttribute(flowElement, "sourceRef");
            String targetRef = extractAttribute(flowElement, "targetRef");

            if (flowId != null && sourceRef != null && targetRef != null) {
                flows.put(flowId, new String[]{sourceRef, targetRef});
            }

            searchStart = actualEnd;
        }

        return flows;
    }

    /**
     * 根据 ID 解析用户任务
     */
    private UserTaskInfo parseUserTaskById(String bpmnXml, String taskId) {
        int searchStart = 0;
        while (true) {
            int userTaskStart = findUserTaskStart(bpmnXml, searchStart);
            if (userTaskStart == -1) break;

            int userTaskEnd = findUserTaskEnd(bpmnXml, userTaskStart);
            if (userTaskEnd == -1) break;

            String userTaskElement = bpmnXml.substring(userTaskStart, userTaskEnd);
            String id = extractAttribute(userTaskElement, "id");

            if (taskId.equals(id)) {
                return parseUserTaskElement(userTaskElement);
            }

            searchStart = userTaskEnd;
        }
        return null;
    }

    /**
     * 解析用户任务元素
     */
    private UserTaskInfo parseUserTaskElement(String userTaskElement) {
        String taskId = extractAttribute(userTaskElement, "id");
        String taskName = extractAttribute(userTaskElement, "name");

        if (taskId == null) return null;

        // 解析 custom:properties（与 developer-workstation 设计器 / workflow-engine TaskAssignmentListener 对齐）
        String assigneeType = extractCustomProperty(userTaskElement, "assigneeType");
        String assigneeValue = extractCustomProperty(userTaskElement, "assigneeValue");
        String assigneeLabel = extractCustomProperty(userTaskElement, "assigneeLabel");
        String assigneeAnchor = extractCustomProperty(userTaskElement, "assigneeAnchor");
        String roleId = extractCustomProperty(userTaskElement, "roleId");
        String businessUnitId = extractCustomProperty(userTaskElement, "businessUnitId");
        String assigneeVariable = extractCustomProperty(userTaskElement, "assigneeVariable");
        String manualAssignVariable = extractCustomProperty(userTaskElement, "manualAssignVariable");
        String manualAssignBuVariable = extractCustomProperty(userTaskElement, "manualAssignBuVariable");
        String manualAssignRoleVariable = extractCustomProperty(userTaskElement, "manualAssignRoleVariable");
        String subTableId = extractCustomProperty(userTaskElement, "subTableId");
        String subTableName = extractCustomProperty(userTaskElement, "subTableName");
        String formId = extractCustomProperty(userTaskElement, "formId");
        String formName = extractCustomProperty(userTaskElement, "formName");
        String formReadOnlyStr = extractCustomProperty(userTaskElement, "formReadOnly");
        String actionIdsStr = extractCustomProperty(userTaskElement, "actionIds");

        // 解析标准属性
        String candidateUsers = extractAttribute(userTaskElement, "flowable:candidateUsers");
        if (candidateUsers == null) {
            candidateUsers = extractAttribute(userTaskElement, "camunda:candidateUsers");
        }
        String candidateGroups = extractAttribute(userTaskElement, "flowable:candidateGroups");
        if (candidateGroups == null) {
            candidateGroups = extractAttribute(userTaskElement, "camunda:candidateGroups");
        }

        // 解析 outgoing
        List<String> outgoing = extractElements(userTaskElement, "outgoing");
        if (outgoing.isEmpty()) {
            outgoing = extractElements(userTaskElement, "bpmn:outgoing");
        }

        // 解析 actionIds
        List<String> actionIds = new ArrayList<>();
        if (actionIdsStr != null && !actionIdsStr.isEmpty()) {
            actionIdsStr = actionIdsStr.replace("[", "").replace("]", "");
            for (String id : actionIdsStr.split(",")) {
                actionIds.add(id.trim());
            }
        }

        return UserTaskInfo.builder()
                .taskId(taskId)
                .taskName(taskName)
                .assigneeType(assigneeType)
                .assigneeValue(assigneeValue)
                .assigneeLabel(assigneeLabel)
                .assigneeAnchor(assigneeAnchor)
                .roleId(roleId)
                .businessUnitId(businessUnitId)
                .assigneeVariable(assigneeVariable)
                .manualAssignVariable(manualAssignVariable)
                .manualAssignBuVariable(manualAssignBuVariable)
                .manualAssignRoleVariable(manualAssignRoleVariable)
                .subTableId(subTableId)
                .subTableName(subTableName)
                .candidateUsers(candidateUsers)
                .candidateGroups(candidateGroups)
                .formId(formId)
                .formName(formName)
                .formReadOnly("true".equals(formReadOnlyStr))
                .actionIds(actionIds)
                .outgoingFlows(outgoing)
                .build();
    }


    /**
     * 门户侧对「下一任务处理人」的尽力预览：与 workflow-engine 收敛后的 AssigneeType 语义对齐；
     * 需管理端/引擎完整解析的类型在此不填 assigneeValue（运行时由监听器处理）。
     */
    public void resolveAssignee(UserTaskInfo taskInfo, Map<String, Object> variables, String initiatorId) {
        if (taskInfo == null || taskInfo.getAssigneeType() == null) {
            return;
        }

        String raw = taskInfo.getAssigneeType().trim();
        String assigneeType = raw.toUpperCase(Locale.ROOT);

        switch (assigneeType) {
            case "INITIATOR":
            case "PROCESS_INITIATOR":
                taskInfo.setAssigneeValue(initiatorId);
                break;
            case "ENTITY_MANAGER":
            case "FUNCTION_MANAGER":
            case "FUNCTIONAL_MANAGER":
                // 锚点为上一完成任务时门户无 History，无法预览
                if (isLastTaskAssigneeAnchor(taskInfo.getAssigneeAnchor())) {
                    break;
                }
                break;
            case "HIERARCHY_ROLE":
            case "BU_ROLE":
            case "FIXED_BU_ROLE":
            case "CURRENT_BU_ROLE":
            case "CURRENT_PARENT_BU_ROLE":
            case "INITIATOR_BU_ROLE":
            case "INITIATOR_PARENT_BU_ROLE":
            case "DEPT_OTHERS":
            case "PARENT_DEPT":
            case "FIXED_DEPT":
                break;
            case "MANUAL_ASSIGN":
                applyManualAssignPreview(taskInfo, variables);
                break;
            case "ASSIGNEE_FROM_VARIABLE":
                applyAssigneeFromVariablePreview(taskInfo, variables);
                break;
            case "ELEMENT_VARIABLE":
                break;
            case "VIRTUAL_GROUP":
            case "BU_UNBOUNDED_ROLE":
                if (taskInfo.getAssigneeValue() != null && !taskInfo.getAssigneeValue().isBlank()) {
                    taskInfo.setCandidateGroups(taskInfo.getAssigneeValue());
                }
                break;
            default:
                log.debug("Portal assignee preview: no local resolution for type {}", assigneeType);
        }
    }

    private static boolean isLastTaskAssigneeAnchor(String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return false;
        }
        String u = anchor.trim().toUpperCase(Locale.ROOT);
        return "LAST_TASK_ASSIGNEE".equals(u) || "LAST".equals(u) || "CURRENT".equals(u);
    }

    private static void applyManualAssignPreview(UserTaskInfo taskInfo, Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        String defKey = taskInfo.getTaskId() != null ? taskInfo.getTaskId() : "";
        String userVar = firstNonBlank(taskInfo.getManualAssignVariable(), "manualAssignee_" + defKey);
        String resolved = firstString(variables.get(userVar));
        if (resolved != null) {
            taskInfo.setAssigneeValue(firstIdFromList(resolved));
        }
    }

    private static void applyAssigneeFromVariablePreview(UserTaskInfo taskInfo, Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        String varName = taskInfo.getAssigneeVariable();
        if (varName == null || varName.isBlank()) {
            return;
        }
        String resolved = firstString(variables.get(varName.trim()));
        if (resolved != null) {
            taskInfo.setAssigneeValue(firstIdFromList(resolved));
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b;
    }

    private static String firstString(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** 逗号分隔时取第一个 ID，供门户预览 */
    private static String firstIdFromList(String commaSeparated) {
        if (commaSeparated == null) {
            return null;
        }
        int idx = commaSeparated.indexOf(',');
        return idx < 0 ? commaSeparated.trim() : commaSeparated.substring(0, idx).trim();
    }

    // ==================== 辅助方法 ====================

    private int findUserTaskStart(String bpmnXml, int searchStart) {
        int start = bpmnXml.indexOf("<userTask", searchStart);
        if (start == -1) {
            start = bpmnXml.indexOf("<bpmn:userTask", searchStart);
        }
        return start;
    }

    private int findUserTaskEnd(String bpmnXml, int userTaskStart) {
        int end = findClosingTag(bpmnXml, userTaskStart, "userTask");
        if (end == -1) {
            end = findClosingTag(bpmnXml, userTaskStart, "bpmn:userTask");
        }
        if (end == -1) {
            // 自闭合标签
            end = bpmnXml.indexOf("/>", userTaskStart);
            if (end != -1) {
                end += 2;
            }
        }
        return end;
    }

    private int findClosingTag(String xml, int startIndex, String tagName) {
        String closingTag = "</" + tagName + ">";
        int closingIndex = xml.indexOf(closingTag, startIndex);
        if (closingIndex != -1) {
            return closingIndex + closingTag.length();
        }
        return -1;
    }

    private String extractAttribute(String element, String attrName) {
        String pattern1 = attrName + "=\"";
        int start = element.indexOf(pattern1);
        if (start != -1) {
            start += pattern1.length();
            int end = element.indexOf("\"", start);
            if (end != -1) {
                return element.substring(start, end);
            }
        }
        // 尝试单引号
        String pattern2 = attrName + "='";
        start = element.indexOf(pattern2);
        if (start != -1) {
            start += pattern2.length();
            int end = element.indexOf("'", start);
            if (end != -1) {
                return element.substring(start, end);
            }
        }
        return null;
    }

    private String extractCustomProperty(String element, String propertyName) {
        try {
            String searchPattern = "name=\"" + propertyName + "\"";
            int propIndex = element.indexOf(searchPattern);
            if (propIndex == -1) {
                return null;
            }

            int lineStart = element.lastIndexOf("<", propIndex);
            int lineEnd = element.indexOf("/>", propIndex);
            if (lineEnd == -1) {
                lineEnd = element.indexOf(">", propIndex);
            }

            if (lineStart == -1 || lineEnd == -1) {
                return null;
            }

            String propertyTag = element.substring(lineStart, lineEnd);
            return extractAttribute(propertyTag, "value");
        } catch (Exception e) {
            log.warn("Failed to extract custom property {}: {}", propertyName, e.getMessage());
            return null;
        }
    }

    private List<String> extractElements(String element, String tagName) {
        List<String> values = new ArrayList<>();
        int searchStart = 0;

        while (true) {
            int start = element.indexOf("<" + tagName + ">", searchStart);
            if (start == -1) break;

            start += tagName.length() + 2;
            int end = element.indexOf("</" + tagName + ">", start);
            if (end == -1) break;

            values.add(element.substring(start, end).trim());
            searchStart = end;
        }

        return values;
    }

    /**
     * 判断是否为发起人任务（第一个用户任务）
     */
    public boolean isInitiatorTask(UserTaskInfo taskInfo) {
        if (taskInfo == null) return false;
        String type = taskInfo.getAssigneeType();
        if (type == null) return false;
        return "INITIATOR".equalsIgnoreCase(type)
                || "PROCESS_INITIATOR".equalsIgnoreCase(type)
                || "initiator".equals(type);
    }

    /**
     * 获取第一个审批任务（跳过发起人任务）
     */
    public UserTaskInfo getFirstApprovalTask(String bpmnXml, Map<String, Object> variables, String initiatorId) {
        List<UserTaskInfo> tasks = parseUserTasks(bpmnXml);
        
        for (int i = 0; i < tasks.size(); i++) {
            UserTaskInfo task = tasks.get(i);
            // 跳过发起人任务
            if (!isInitiatorTask(task) || i > 0) {
                resolveAssignee(task, variables, initiatorId);
                return task;
            }
        }
        
        return null;
    }
}
