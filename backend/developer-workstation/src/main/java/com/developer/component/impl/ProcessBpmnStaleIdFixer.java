package com.developer.component.impl;

import com.developer.enums.TableType;
import com.developer.util.ActionBindingLifecycle;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN 陈旧 ID 修正协作类。
 *
 * <p>从 {@link ProcessDesignComponentImpl} 拆出，落库前按名称在当前 FU 中重查 formId/subTableId/actionIds
 * （含流程级 {@code globalActionIds}），防止自动保存把旧 ID 持久化。Action 按有效 ID 保留身份，只有失效的旧引用才按名称恢复。</p>
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

        Map<String, String> actionNamesById = new HashMap<>();
        actionDefinitionRepository.findByFunctionUnitId(functionUnitId)
            .forEach(a -> actionNamesById.put(String.valueOf(a.getId()), a.getActionName()));

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

        return ActionBindingLifecycle.reconcile(result, actionNamesById, true);
    }
}
