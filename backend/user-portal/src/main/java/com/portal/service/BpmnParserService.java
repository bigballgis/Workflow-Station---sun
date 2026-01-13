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

        // 解析 custom:properties
        String assigneeType = extractCustomProperty(userTaskElement, "assigneeType");
        String assigneeValue = extractCustomProperty(userTaskElement, "assigneeValue");
        String assigneeLabel = extractCustomProperty(userTaskElement, "assigneeLabel");
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
     * 解析处理人信息
     * 使用新的7种标准分配类型
     */
    public void resolveAssignee(UserTaskInfo taskInfo, Map<String, Object> variables, String initiatorId) {
        if (taskInfo == null || taskInfo.getAssigneeType() == null) {
            return;
        }

        String assigneeType = taskInfo.getAssigneeType().toUpperCase();

        switch (assigneeType) {
            // 1. 流程发起人 - 直接分配
            case "INITIATOR":
                taskInfo.setAssigneeValue(initiatorId);
                break;
            // 2. 实体经理 - 直接分配（由 workflow-engine-core 的 TaskAssigneeResolver 解析）
            case "ENTITY_MANAGER":
                // 标记需要由 workflow-engine-core 解析
                break;
            // 3. 职能经理 - 直接分配（由 workflow-engine-core 的 TaskAssigneeResolver 解析）
            case "FUNCTION_MANAGER":
                // 标记需要由 workflow-engine-core 解析
                break;
            // 4. 本部门其他人 - 需要认领（由 workflow-engine-core 的 TaskAssigneeResolver 解析）
            case "DEPT_OTHERS":
                // 标记需要认领
                break;
            // 5. 上级部门 - 需要认领（由 workflow-engine-core 的 TaskAssigneeResolver 解析）
            case "PARENT_DEPT":
                // 标记需要认领
                break;
            // 6. 指定部门 - 需要认领
            case "FIXED_DEPT":
                // assigneeValue 已经包含部门ID，标记需要认领
                break;
            // 7. 虚拟组 - 需要认领
            case "VIRTUAL_GROUP":
                // assigneeValue 已经包含虚拟组ID，设置为候选组
                if (taskInfo.getAssigneeValue() != null) {
                    taskInfo.setCandidateGroups(taskInfo.getAssigneeValue());
                }
                break;
            default:
                log.warn("Unknown assignee type: {}", assigneeType);
        }
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
        return "INITIATOR".equalsIgnoreCase(type) || "initiator".equals(type);
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
