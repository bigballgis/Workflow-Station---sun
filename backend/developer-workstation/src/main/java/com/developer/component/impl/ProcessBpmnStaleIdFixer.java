package com.developer.component.impl;

import com.developer.enums.TableType;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN 陈旧 ID 修正协作类。
 *
 * <p>从 {@link ProcessDesignComponentImpl} 拆出，落库前按名称在当前 FU 中重查 formId/subTableId/actionIds，
 * 防止自动保存把旧 ID 持久化。正则与 BPMN 属性结构逐字保留，行为零变化。</p>
 */
@Component
@Slf4j
public class ProcessBpmnStaleIdFixer {

    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;

    public ProcessBpmnStaleIdFixer(
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            ActionDefinitionRepository actionDefinitionRepository) {
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.actionDefinitionRepository = actionDefinitionRepository;
    }

    /**
     * Fix stale form/table/action IDs by looking up names in the current FU.
     * Called before saving to prevent auto-save from persisting old IDs.
     */
    String fixStaleIds(Long functionUnitId, String bpmnXml) {
        // Build name→id maps
        Map<String, String> formNameToId = new HashMap<>();
        formDefinitionRepository.findByFunctionUnitId(functionUnitId)
            .forEach(f -> formNameToId.put(f.getFormName(), String.valueOf(f.getId())));

        Map<String, String> subTableNameToId = new HashMap<>();
        tableDefinitionRepository.findByFunctionUnitId(functionUnitId)
            .forEach(t -> { if (t.getTableType() == TableType.SUB) subTableNameToId.put(t.getTableName(), String.valueOf(t.getId())); });

        Map<String, String> actionNameToId = new HashMap<>();
        actionDefinitionRepository.findByFunctionUnitId(functionUnitId)
            .forEach(a -> actionNameToId.put(a.getActionName(), String.valueOf(a.getId())));

        String result = bpmnXml;

        // Fix formId: find name="formName" value="X" ... name="formId" value="Y"
        // Replace Y with the correct ID for form named X
        Pattern formPattern = Pattern.compile(
            "(name=\"formName\"\\s+value=\"([^\"]*)\"[^/]*/\\s*>\\s*<custom:property\\s+name=\"formId\"\\s+value=\")([^\"]*)(\")",
            Pattern.DOTALL);
        Matcher fm = formPattern.matcher(result);
        StringBuffer fsb = new StringBuffer();
        while (fm.find()) {
            String formName = fm.group(2);
            String oldId = fm.group(3);
            String newId = formNameToId.getOrDefault(formName, oldId);
            fm.appendReplacement(fsb, "$1" + newId + "$4");
        }
        fm.appendTail(fsb);
        result = fsb.toString();

        // Fix subTableId: name="subTableName" value="X" ... name="subTableId" value="Y"
        Pattern stPattern = Pattern.compile(
            "(name=\"subTableName\"\\s+value=\"([^\"]*)\"[^/]*/\\s*>\\s*<custom:property\\s+name=\"subTableId\"\\s+value=\")([^\"]*)(\")",
            Pattern.DOTALL);
        Matcher sm = stPattern.matcher(result);
        StringBuffer ssb = new StringBuffer();
        while (sm.find()) {
            String tableName = sm.group(2);
            String oldId = sm.group(3);
            String newId = subTableNameToId.getOrDefault(tableName, oldId);
            sm.appendReplacement(ssb, "$1" + newId + "$4");
        }
        sm.appendTail(ssb);
        result = ssb.toString();

        // Fix actionIds: parse actionNames by position and remap each actionId
        // BPMN stores: actionNames="[&quot;Approve&quot;,&quot;reject&quot;]" and actionIds="[47,48]"
        Pattern actionBlockPattern = Pattern.compile(
            "(<bpmn:userTask[^>]*>.*?</bpmn:userTask>)", Pattern.DOTALL);
        Matcher abm = actionBlockPattern.matcher(result);
        StringBuffer asb = new StringBuffer();
        while (abm.find()) {
            String block = abm.group(1);
            block = fixActionIdsInBlock(block, actionNameToId);
            abm.appendReplacement(asb, Matcher.quoteReplacement(block));
        }
        abm.appendTail(asb);

        return asb.toString();
    }

    private String fixActionIdsInBlock(String block, Map<String, String> actionNameToId) {
        // Extract actionNames and actionIds
        Pattern namesPattern = Pattern.compile("name=\"actionNames\"\\s+value=\"([^\"]*)\"");
        Pattern idsPattern = Pattern.compile("name=\"actionIds\"\\s+value=\"([^\"]*)\"");
        Matcher nm = namesPattern.matcher(block);
        Matcher im = idsPattern.matcher(block);
        if (!nm.find() || !im.find()) return block;

        String actionNamesRaw = nm.group(1);
        String actionIdsRaw = im.group(1);

        // Parse actionNames: ["Approve","reject"] or [&quot;Approve&quot;,...]
        List<String> names = parseBpmnJsonArray(actionNamesRaw);
        // Parse actionIds: [47,48]
        List<String> ids = parseBpmnJsonArray(actionIdsRaw);

        if (names.size() != ids.size()) return block;

        List<String> newIds = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String idStr = ids.get(i);
            String correctId = actionNameToId.get(name);
            if (correctId != null && !correctId.equals(idStr)) {
                newIds.add(correctId);
                changed = true;
            } else {
                newIds.add(idStr);
            }
        }
        if (!changed) return block;

        String newActionIds = "[" + String.join(",", newIds) + "]";
        return block.replace(
            "name=\"actionIds\" value=\"" + actionIdsRaw + "\"",
            "name=\"actionIds\" value=\"" + newActionIds + "\"");
    }

    private List<String> parseBpmnJsonArray(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        // Strip brackets
        String content = raw.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        for (String part : content.split(",")) {
            String item = part.trim();
            // Unescape XML entities
            item = item.replace("&quot;", "").replace("&#34;", "")
                       .replace("\"", "");
            if (!item.isEmpty()) result.add(item);
        }
        return result;
    }
}
