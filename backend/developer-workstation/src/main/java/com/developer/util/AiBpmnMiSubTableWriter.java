package com.developer.util;

import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import com.developer.exception.AiGenerationException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds persisted sub-table IDs to the user tasks of AI-generated multi-instance sub-processes.
 *
 * <p>Same gap as {@link AiBpmnFormBindingWriter}: the model can name the sub table, but
 * {@code subTableId} is assigned by the database, so it cannot be part of the generated payload.
 * Without it {@link com.developer.component.impl.ProcessBpmnValidator} rejects the function unit
 * at deploy time with {@code MISSING_SUBTABLE_ID}.</p>
 */
@Slf4j
public final class AiBpmnMiSubTableWriter {

    private AiBpmnMiSubTableWriter() {
    }

    /**
     * Resolve every MI user task's {@code subTableName} against the persisted tables and write the
     * matching {@code subTableId}. Tasks whose table cannot be resolved are left untouched so the
     * deploy-time validator still reports them.
     */
    public static String bindMiSubTables(String bpmnXml, Collection<TableDefinition> tables) {
        if (bpmnXml == null || bpmnXml.isBlank() || tables == null || tables.isEmpty()) {
            return bpmnXml;
        }

        Map<String, TableDefinition> byName = new LinkedHashMap<>();
        for (TableDefinition table : tables) {
            if (table != null && table.getId() != null && table.getTableName() != null) {
                byName.put(table.getTableName().trim(), table);
            }
        }
        if (byName.isEmpty()) {
            return bpmnXml;
        }

        try {
            Document document = AiBpmnMiTaskScanner.parseSecurely(bpmnXml);
            boolean changed = false;
            for (AiBpmnMiTaskScanner.MiTask task : AiBpmnMiTaskScanner.scan(document)) {
                String subTableName = task.property("subTableName");
                if (subTableName == null) {
                    continue;
                }
                TableDefinition table = byName.get(subTableName);
                if (table == null) {
                    log.warn("MI user task references sub table '{}' which the generated data does not "
                            + "contain — leaving subTableId unset", subTableName);
                    continue;
                }
                if (table.getTableType() != TableType.SUB) {
                    log.warn("MI user task references table '{}' of type {} — deploy validation expects a "
                            + "SUB table", subTableName, table.getTableType());
                }
                Element properties = AiBpmnMiTaskScanner.ensureCustomProperties(document, task.userTask());
                AiBpmnMiTaskScanner.putProperty(document, properties, "subTableId", String.valueOf(table.getId()));
                changed = true;
                log.info("Bound MI user task '{}' to persisted sub table '{}' (id {})",
                        task.userTask().getAttribute("id"), subTableName, table.getId());
            }
            return changed ? AiBpmnMiTaskScanner.serialize(document) : bpmnXml;
        } catch (Exception e) {
            // 与 AiBpmnFormBindingWriter 同因：apply 阶段的 bpmnXml 来自客户端回传的 body，
            // 没再过 AiResponseParser，必须以 AI_* 码 fail loud，否则会被 GlobalExceptionHandler
            // 当成 400 VAL_INVALID_ARGUMENT 记 warn。栈在这里先落一次。
            log.error("Failed to bind AI-generated sub tables to multi-instance BPMN tasks", e);
            throw new AiGenerationException("AI_BPMN_MI_BINDING_FAILED",
                    "Failed to bind AI-generated sub tables to multi-instance BPMN tasks: " + e.getMessage());
        }
    }
}
