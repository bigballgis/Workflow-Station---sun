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
 * 表设计字段变更 → 表单设计器同步器。
 *
 * <p>当 Table Design 中字段属性变更时，把可安全传播的部分同步到所有引用该表的
 * 表单画布（form-create rule）。同步范围（折中硬同步策略，避免抹掉设计师在画布上的手工调整）：
 * <ul>
 *   <li>{@code fieldName} → {@code rule.field} + {@code fieldPermissions} key（无条件）；</li>
 *   <li>{@code description}（Display Name）→ {@code rule.title}（无条件）；</li>
 *   <li>{@code length} → {@code props.maxlength}（仅当节点 {@code type=="input"} 且 dataType 仍是 VARCHAR）；</li>
 *   <li>{@code scale} → {@code props.precision}（仅当节点 {@code type=="inputNumber"} 且 dataType 是 DECIMAL）；</li>
 *   <li>{@code nullable} → {@code validate[].required} 的 add/remove（任意控件类型，但仅对应字段节点）。</li>
 * </ul>
 *
 * <p><b>显式不同步</b>：{@code dataType} 变更 <b>不</b> 改写画布节点的 {@code type}（避免抹掉设计师可能换成的
 * select/autoComplete/自定义控件）；{@code defaultValue} 不同步（已有数据风险）；{@code precision}（DECIMAL 总位数）
 * 不映射到画布（form-create 的 {@code props.precision} 对应小数位 scale，不是总位数）。
 *
 * <p>仅在 {@link com.developer.component.impl.TableDesignComponentImpl#update} 的事务内调用，
 * 保证表结构与所有表单的可见命名/约束在同一事务中完成。
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

    /**
     * 单个字段在一次保存中的属性变更快照（仅保存 form 端会消费的属性）。
     *
     * <p>{@code old*} 与 {@code new*} 各成对出现；任一对相等表示该维度未变更。
     * 历史命名沿用 {@code FieldChange}（早期版本叫 {@code Rename}，仅含 field/description）。
     */
    public record FieldChange(String oldFieldName,
                              String newFieldName,
                              String oldDescription,
                              String newDescription,
                              String oldDataType,
                              String newDataType,
                              Integer oldLength,
                              Integer newLength,
                              Integer oldScale,
                              Integer newScale,
                              Boolean oldNullable,
                              Boolean newNullable) {

        /** 仅同步 field/title 的旧版便利构造。 */
        public FieldChange(String oldFieldName, String newFieldName,
                           String oldDescription, String newDescription) {
            this(oldFieldName, newFieldName, oldDescription, newDescription,
                    null, null, null, null, null, null, null, null);
        }

        public boolean fieldNameChanged() { return !Objects.equals(oldFieldName, newFieldName); }
        public boolean descriptionChanged() { return !Objects.equals(oldDescription, newDescription); }
        public boolean lengthChanged() { return !Objects.equals(oldLength, newLength); }
        public boolean scaleChanged() { return !Objects.equals(oldScale, newScale); }
        public boolean nullableChanged() { return !Objects.equals(oldNullable, newNullable); }
    }

    /**
     * @return 实际发生变更的 {@link FormDefinition}（其 {@code configJson} / {@code fieldPermissions} 已就地修改）。
     */
    public static List<FormDefinition> apply(TableDefinition table,
                                             List<FormDefinition> forms,
                                             List<FieldChange> changes) {
        List<FormDefinition> dirty = new ArrayList<>();
        if (changes == null || changes.isEmpty() || forms == null || forms.isEmpty() || table == null) {
            return dirty;
        }

        Long tableId = table.getId();
        Map<String, FieldChange> byOldFieldName = new LinkedHashMap<>();
        for (FieldChange c : changes) {
            if (c == null || c.oldFieldName() == null || c.oldFieldName().isBlank()) continue;
            byOldFieldName.put(c.oldFieldName(), c);
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
                                       Map<String, FieldChange> byOldFieldName) {
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
                for (FieldChange r : byOldFieldName.values()) {
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
     * 递归遍历 form-create 节点（数组或单节点）。对 {@code field} 命中 {@code byOldFieldName}
     * 的节点：
     * <ul>
     *   <li>重写 {@code field}，同步 {@code title}（无条件）；</li>
     *   <li>根据节点当前 {@code type} 与新 dataType 守门后同步 {@code props.maxlength} / {@code props.precision}；</li>
     *   <li>根据 {@code nullable} 增删 {@code validate} 中的 {@code required} 项。</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static boolean walkRuleNodes(Object node, Map<String, FieldChange> byOldFieldName) {
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
            FieldChange c = byOldFieldName.get(oldField);
            if (c != null) {
                if (c.fieldNameChanged()) {
                    n.put("field", c.newFieldName());
                    changed = true;
                }
                // Title：与 FormDesigner.fieldToFormRule (`title: field.description || field.fieldName`) 一致：
                // 仅当 Display Name 或 Field Name 任一变更时回写；优先 Display Name，空则回退 Field Name。
                if (c.descriptionChanged() || c.fieldNameChanged()) {
                    String nextTitle = !isBlank(c.newDescription())
                            ? c.newDescription()
                            : c.newFieldName();
                    if (nextTitle != null) {
                        Object curTitle = n.get("title");
                        if (!Objects.equals(curTitle, nextTitle)) {
                            n.put("title", nextTitle);
                            changed = true;
                        }
                    }
                }

                // dataType 守门：取节点当前控件类型 + 新 dataType；
                // 设计师若已把 VARCHAR 的 input 换成 select，length 不会被回写。
                String currentControlType = stringValue(n.get("type"));
                String newDataType = upper(c.newDataType());

                if (c.lengthChanged()
                        && "input".equals(currentControlType)
                        && "VARCHAR".equals(newDataType)
                        && c.newLength() != null) {
                    if (writeProp(n, "maxlength", c.newLength())) changed = true;
                }

                if (c.scaleChanged()
                        && "inputNumber".equals(currentControlType)
                        && "DECIMAL".equals(newDataType)
                        && c.newScale() != null) {
                    if (writeProp(n, "precision", c.newScale())) changed = true;
                }

                if (c.nullableChanged() && c.newNullable() != null) {
                    String labelForMessage = !isBlank(c.newDescription())
                            ? c.newDescription()
                            : (c.newFieldName() != null ? c.newFieldName() : oldField);
                    if (syncRequiredValidate(n, c.newNullable(), labelForMessage)) {
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

    /**
     * 写入或更新节点 {@code props.<key>}。若 {@code props} 不存在则创建。
     * 当 props 不是 Map（结构异常）则放弃，避免破坏既有数据。
     *
     * @return 是否真的产生了变更
     */
    @SuppressWarnings("unchecked")
    private static boolean writeProp(Map<String, Object> node, String key, Object value) {
        Object propsObj = node.get("props");
        Map<String, Object> props;
        if (propsObj == null) {
            props = new LinkedHashMap<>();
            node.put("props", props);
        } else if (propsObj instanceof Map<?, ?> map) {
            props = (Map<String, Object>) map;
        } else {
            log.debug("Skipping props sync: node.props is {} not Map", propsObj.getClass().getSimpleName());
            return false;
        }
        Object existing = props.get(key);
        if (Objects.equals(existing, value)) return false;
        props.put(key, value);
        return true;
    }

    /**
     * 根据 nullable 在 {@code node.validate} 中增删 {@code required:true} 校验项。
     *
     * <ul>
     *   <li>{@code nullable == false} 且无 required 项 → 追加 {@code {required:true, message, trigger:'blur'}}；</li>
     *   <li>{@code nullable == true} 且存在 required 项 → 移除；</li>
     *   <li>仅触动 {@code required:true} 的校验项，其它（如 type/min/max 校验）保持不变。</li>
     * </ul>
     *
     * @return 是否真的产生了变更
     */
    @SuppressWarnings("unchecked")
    private static boolean syncRequiredValidate(Map<String, Object> node,
                                                Boolean newNullable,
                                                String label) {
        Object vObj = node.get("validate");
        List<Object> validate;
        if (vObj == null) {
            validate = new ArrayList<>();
        } else if (vObj instanceof List<?> list) {
            validate = new ArrayList<>(list);
        } else {
            log.debug("Skipping validate sync: node.validate is {} not List", vObj.getClass().getSimpleName());
            return false;
        }

        int requiredIdx = -1;
        for (int i = 0; i < validate.size(); i++) {
            Object item = validate.get(i);
            if (item instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("required"))) {
                requiredIdx = i;
                break;
            }
        }

        boolean wantRequired = Boolean.FALSE.equals(newNullable); // nullable=false → required
        if (wantRequired) {
            if (requiredIdx >= 0) return false; // 已经必填
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("required", true);
            // message 后端无 i18n 上下文：使用字段显示名作为简单占位，前端可在 Form Designer 里改。
            req.put("message", label != null ? label + " is required" : "Required");
            req.put("trigger", "blur");
            validate.add(req);
            node.put("validate", validate);
            return true;
        } else {
            // nullable=true → 不必填，移除 required 项
            if (requiredIdx < 0) return false;
            validate.remove(requiredIdx);
            node.put("validate", validate);
            return true;
        }
    }

    private static String stringValue(Object v) {
        return v instanceof String s ? s : null;
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
