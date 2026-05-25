package com.developer.service;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表设计字段重命名 → 表单设计器同步器。
 *
 * <p>Table Design 中的字段 {@code fieldName} / {@code description}（Display Name）
 * 一旦变更，所有引用该字段的表单画布（form-create rule）的 {@code field} / {@code title}
 * 必须随动；此外 Task Form 的 {@code fieldPermissions} 以 {@code fieldName} 为 key，
 * 也需要重新映射。
 *
 * <p>仅在 {@link com.developer.component.impl.TableDesignComponentImpl#update} 的事务内调用，
 * 保证表结构与所有表单的可见命名在同一事务中完成。
 *
 * <p>遍历范围：
 * <ul>
 *   <li>表单顶层 {@code configJson.rule}（递归 {@code children}）；</li>
 *   <li>对当前表的 SUB / RELATED 绑定，仅遍历 {@code configJson.subForms[bindingId].rule}，
 *       避免把同名字段在其他子表的子表单里误改。</li>
 * </ul>
 */
@Slf4j
public final class FormConfigFieldRenamer {

    private FormConfigFieldRenamer() {}

    /** 单个字段在一次保存中的命名变更。 */
    public record Rename(String oldFieldName,
                         String newFieldName,
                         String oldDescription,
                         String newDescription) {

        public boolean fieldNameChanged() {
            return !Objects.equals(oldFieldName, newFieldName);
        }

        public boolean descriptionChanged() {
            return !Objects.equals(oldDescription, newDescription);
        }
    }

    /**
     * @return 实际发生变更的 {@link FormDefinition}（其 {@code configJson} / {@code fieldPermissions} 已就地修改）。
     */
    public static List<FormDefinition> apply(TableDefinition table,
                                             List<FormDefinition> forms,
                                             List<Rename> renames) {
        List<FormDefinition> dirty = new ArrayList<>();
        if (renames == null || renames.isEmpty() || forms == null || forms.isEmpty() || table == null) {
            return dirty;
        }

        Long tableId = table.getId();
        Map<String, Rename> byOldFieldName = new LinkedHashMap<>();
        for (Rename r : renames) {
            if (r.oldFieldName() == null || r.oldFieldName().isBlank()) continue;
            byOldFieldName.put(r.oldFieldName(), r);
        }
        if (byOldFieldName.isEmpty()) return dirty;

        for (FormDefinition form : forms) {
            boolean changed = applyToForm(form, tableId, byOldFieldName);
            if (changed) dirty.add(form);
        }
        return dirty;
    }

    private static boolean applyToForm(FormDefinition form,
                                       Long tableId,
                                       Map<String, Rename> byOldFieldName) {
        if (form == null) return false;
        Map<String, Object> config = form.getConfigJson();
        if (config == null) return false;
        // 复制一层根 Map，确保 Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)} 字段
        // 在引用层级即可被识别为 dirty；子层 Map / List 仍可就地修改，最终在 setConfigJson 时回写。
        Map<String, Object> next = new LinkedHashMap<>(config);

        boolean changed = false;

        // 1) 主画布 rule —— 当表单的 PRIMARY 绑定或遗留 boundTable 指向当前表时遍历
        boolean appliesToMain = false;
        TableDefinition legacyBound = form.getBoundTable();
        if (legacyBound != null && Objects.equals(legacyBound.getId(), tableId)) {
            appliesToMain = true;
        }
        for (FormTableBinding b : safeBindings(form)) {
            if (b.getBindingType() == BindingType.PRIMARY
                    && b.getTable() != null
                    && Objects.equals(b.getTable().getId(), tableId)) {
                appliesToMain = true;
                break;
            }
        }
        if (appliesToMain) {
            Object rule = next.get("rule");
            if (walkRuleNodes(rule, byOldFieldName)) changed = true;
        }

        // 2) 子表单 rule —— 仅遍历指向当前表的 SUB/RELATED 绑定对应的 subForms[bindingId]
        Object subFormsRaw = next.get("subForms");
        if (subFormsRaw instanceof Map<?, ?> subForms) {
            for (FormTableBinding b : safeBindings(form)) {
                BindingType t = b.getBindingType();
                if (t != BindingType.SUB && t != BindingType.RELATED) continue;
                if (b.getTable() == null || !Objects.equals(b.getTable().getId(), tableId)) continue;
                Object sub = lookup(subForms, b.getId());
                if (sub instanceof Map<?, ?> subMap) {
                    Object subRule = subMap.get("rule");
                    if (walkRuleNodes(subRule, byOldFieldName)) changed = true;
                }
            }
        }

        // 3) 仅对主表绑定生效的 fieldPermissions（Task Form 字段权限）—— 以 fieldName 为 key 重映射
        if (appliesToMain) {
            Map<String, String> perms = form.getFieldPermissions();
            if (perms != null && !perms.isEmpty()) {
                Map<String, String> nextPerms = new LinkedHashMap<>(perms);
                boolean permChanged = false;
                for (Rename r : byOldFieldName.values()) {
                    if (!r.fieldNameChanged()) continue;
                    if (!nextPerms.containsKey(r.oldFieldName())) continue;
                    String value = nextPerms.remove(r.oldFieldName());
                    nextPerms.put(r.newFieldName(), value);
                    permChanged = true;
                }
                if (permChanged) {
                    form.setFieldPermissions(nextPerms);
                    changed = true;
                }
            }
        }

        if (changed) {
            // Re-assign root configJson so Hibernate definitely detects the dirty JSON column.
            form.setConfigJson(next);
        }
        return changed;
    }

    private static List<FormTableBinding> safeBindings(FormDefinition form) {
        List<FormTableBinding> list = form.getTableBindings();
        return list != null ? list : List.of();
    }

    /**
     * 表单 jsonb 反序列化后 Map 的 key 可能是 {@link String}（多数情况）或数值类型。
     * 这里兼容两种写法，按字符串等价匹配。
     */
    private static Object lookup(Map<?, ?> map, Long bindingId) {
        if (bindingId == null) return null;
        String want = String.valueOf(bindingId);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && want.equals(String.valueOf(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 递归遍历 form-create 节点（数组或单节点），对 {@code field} 命中 {@code byOldFieldName}
     * 的节点重写 {@code field}，并同步 {@code title} 为新的 Display Name。
     */
    @SuppressWarnings("unchecked")
    private static boolean walkRuleNodes(Object node, Map<String, Rename> byOldFieldName) {
        if (node == null) return false;
        if (node instanceof List<?> list) {
            boolean changed = false;
            for (Object child : list) {
                if (walkRuleNodes(child, byOldFieldName)) changed = true;
            }
            return changed;
        }
        if (!(node instanceof Map)) return false;

        Map<String, Object> n = (Map<String, Object>) node;
        boolean changed = false;

        Object fieldVal = n.get("field");
        if (fieldVal instanceof String oldField) {
            Rename r = byOldFieldName.get(oldField);
            if (r != null) {
                if (r.fieldNameChanged()) {
                    n.put("field", r.newFieldName());
                    changed = true;
                }
                // 用户要求：只要 Display Name 或 Field Name 任一变更，就把 title 同步为
                //   - 新 Display Name（若非空）
                //   - 否则回退到新 Field Name
                // 与 FormDesigner.fieldToFormRule (`title: field.description || field.fieldName`) 一致。
                String nextTitle = !isBlank(r.newDescription())
                        ? r.newDescription()
                        : r.newFieldName();
                if (nextTitle != null) {
                    Object curTitle = n.get("title");
                    if (!Objects.equals(curTitle, nextTitle)) {
                        n.put("title", nextTitle);
                        changed = true;
                    }
                }
            }
        }

        Object children = n.get("children");
        if (children != null && walkRuleNodes(children, byOldFieldName)) {
            changed = true;
        }

        return changed;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
