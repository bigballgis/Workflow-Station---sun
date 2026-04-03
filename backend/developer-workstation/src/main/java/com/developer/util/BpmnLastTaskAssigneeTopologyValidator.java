package com.developer.util;

import com.developer.dto.ValidationResult;

import java.util.Locale;

/**
 * 校验 BPMN：扩展属性 assigneeAnchor 为「上一完成任务」时，对应 userTask 必须恰好有一条顺序流入线
 * （与 assignee-type-convergence.md / 设计器 bpmnAssigneeTopology 一致）。
 * <p>基于字符串扫描，避免引入需防 XXE 的 DOM 解析。</p>
 */
public final class BpmnLastTaskAssigneeTopologyValidator {

    private BpmnLastTaskAssigneeTopologyValidator() {
    }

    public static ValidationResult validate(String bpmnXml) {
        ValidationResult result = new ValidationResult();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return result;
        }

        int searchStart = 0;
        while (true) {
            int userTaskStart = findUserTaskStart(bpmnXml, searchStart);
            if (userTaskStart == -1) {
                break;
            }
            int userTaskEnd = findUserTaskEnd(bpmnXml, userTaskStart);
            if (userTaskEnd == -1) {
                break;
            }
            String block = bpmnXml.substring(userTaskStart, userTaskEnd);
            String taskId = extractAttribute(block, "id");
            String taskName = extractAttribute(block, "name");
            String anchor = extractCustomProperty(block, "assigneeAnchor");
            if (!isLastTaskAssigneeAnchor(anchor)) {
                searchStart = userTaskEnd;
                continue;
            }
            int incoming = countIncomingSequenceFlowRefs(block);
            if (incoming != 1) {
                String label = (taskName != null && !taskName.isBlank()) ? taskName : (taskId != null ? taskId : "?");
                result.addError(
                        "LAST_TASK_ANCHOR_NOT_SINGLE_INCOMING",
                        String.format(
                                "User task \"%s\": LAST_TASK_ASSIGNEE anchor requires exactly one incoming sequence flow (found %d).",
                                label,
                                incoming),
                        taskId);
            }
            searchStart = userTaskEnd;
        }
        return result;
    }

    private static boolean isLastTaskAssigneeAnchor(String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return false;
        }
        String u = anchor.trim().toUpperCase(Locale.ROOT);
        return "LAST_TASK_ASSIGNEE".equals(u) || "LAST".equals(u) || "CURRENT".equals(u);
    }

    /**
     * BPMN 2.0：指向该节点的 sequenceFlow 在 userTask 下以 {@code <incoming>} 引用。
     */
    private static int countIncomingSequenceFlowRefs(String userTaskElement) {
        int count = 0;
        int pos = 0;
        while (pos < userTaskElement.length()) {
            int i = indexOfIncomingOpen(userTaskElement, pos);
            if (i == -1) {
                break;
            }
            count++;
            pos = i + 1;
        }
        return count;
    }

    private static int indexOfIncomingOpen(String s, int from) {
        String[] needles = {"<bpmn:incoming", "<incoming"};
        int best = -1;
        for (String n : needles) {
            int idx = s.indexOf(n, from);
            if (idx != -1 && (best == -1 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static int findUserTaskStart(String bpmnXml, int searchStart) {
        int start = bpmnXml.indexOf("<userTask", searchStart);
        if (start == -1) {
            start = bpmnXml.indexOf("<bpmn:userTask", searchStart);
        }
        return start;
    }

    private static int findUserTaskEnd(String bpmnXml, int userTaskStart) {
        int end = findClosingTag(bpmnXml, userTaskStart, "userTask");
        if (end == -1) {
            end = findClosingTag(bpmnXml, userTaskStart, "bpmn:userTask");
        }
        if (end == -1) {
            end = bpmnXml.indexOf("/>", userTaskStart);
            if (end != -1) {
                end += 2;
            }
        }
        return end;
    }

    private static int findClosingTag(String xml, int startIndex, String tagName) {
        String closingTag = "</" + tagName + ">";
        int closingIndex = xml.indexOf(closingTag, startIndex);
        if (closingIndex != -1) {
            return closingIndex + closingTag.length();
        }
        return -1;
    }

    private static String extractAttribute(String element, String attrName) {
        String pattern1 = attrName + "=\"";
        int start = element.indexOf(pattern1);
        if (start != -1) {
            start += pattern1.length();
            int end = element.indexOf("\"", start);
            if (end != -1) {
                return element.substring(start, end);
            }
        }
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

    private static String extractCustomProperty(String element, String propertyName) {
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
            return null;
        }
    }
}
