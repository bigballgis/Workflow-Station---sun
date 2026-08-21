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
 * Table Design field changes propagated into form designer configs.
 *
 * <p>When Table Design field attributes change, safely sync what we can into every canvas ({@code form-create rule})
 * referencing that table. Scope (hard sync balance to avoid wiping designer tweaks):
 * <ul>
 *   <li>{@code fieldName} → {@code rule.field} + {@code fieldPermissions} keys (always);</li>
 *   <li>{@code displayName} → {@code rule.title} (always);</li>
 *   <li>{@code length} → {@code props.maxlength} ({@code type=="input"} and dataType still VARCHAR);</li>
 *   <li>{@code scale} → {@code props.precision} ({@code type=="inputNumber"} and dataType DECIMAL);</li>
 *   <li>{@code nullable} → add/remove {@code validate[].required} for the matching control node.</li>
 * </ul>
 *
 * <p><b>Explicitly not synced:</b> {@code dataType} never rewrites canvas {@code type} (avoid clobbering
 * select/autoComplete/etc.); {@code defaultValue} stays untouched (risk to existing payloads);
 * DECIMAL {@code precision} total digits never map into canvas ({@code props.precision} mirrors scale only).
 *
 * <p>Invoked inside {@link com.developer.component.impl.TableDesignComponentImpl#update}'s transaction so
 * schemas and canvases remain consistent atomically.
 *
 * <p>Traversal scope:
 * <ul>
 *   <li>Top-level {@code configJson.rule} (recursive {@code children});</li>
 *   <li>For SUB/RELATED bindings to the edited table only, recurse {@code configJson.subForms[bindingId].rule}
 *       so unrelated sub-forms with same field labels stay untouched.</li>
 * </ul>
 */
@Slf4j
public final class FormConfigFieldRenamer {

    private FormConfigFieldRenamer() {}

    /**
     * Captures per-field attribute deltas used by form sync for one save.
     *
     * <p>Each {@code old*}/{@code new*} pair lines up; equal pairs mean no change on that axis.
     * Kept name {@code FieldChange} for history (older builds only renamed field/displayName).
     */
    public record FieldChange(String oldFieldName,
                              String newFieldName,
                              String oldDisplayName,
                              String newDisplayName,
                              String oldDataType,
                              String newDataType,
                              Integer oldLength,
                              Integer newLength,
                              Integer oldScale,
                              Integer newScale,
                              Boolean oldNullable,
                              Boolean newNullable) {

        /** Legacy convenience ctor syncing only field/title dimensions. */
        public FieldChange(String oldFieldName, String newFieldName,
                           String oldDisplayName, String newDisplayName) {
            this(oldFieldName, newFieldName, oldDisplayName, newDisplayName,
                    null, null, null, null, null, null, null, null);
        }

        public boolean fieldNameChanged() { return !Objects.equals(oldFieldName, newFieldName); }
        public boolean displayNameChanged() { return !Objects.equals(oldDisplayName, newDisplayName); }
        public boolean lengthChanged() { return !Objects.equals(oldLength, newLength); }
        public boolean scaleChanged() { return !Objects.equals(oldScale, newScale); }
        public boolean nullableChanged() { return !Objects.equals(oldNullable, newNullable); }
    }

    /**
     * @return {@link FormDefinition} instances whose {@code configJson} / {@code fieldPermissions} were mutated in place.
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
        // Shallow-clone root map so Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)} sees the column as dirty;
        // nested maps/lists may still be mutated in place before setConfigJson.
        Map<String, Object> next = new LinkedHashMap<>(config);

        boolean changed = false;

        // 1) Main canvas rule — traverse when PRIMARY binding (or legacy boundTable) targets this table.
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

        // 2) Sub-form rules — only SUB/RELATED/ACTION bindings that point at this table via subForms[bindingId].
        Object subFormsRaw = next.get("subForms");
        if (subFormsRaw instanceof Map<?, ?> subForms) {
            for (FormTableBinding b : safeBindings(form)) {
                BindingType t = b.getBindingType();
                if (t != BindingType.SUB && t != BindingType.RELATED && t != BindingType.ACTION) continue;
                if (b.getTable() == null || !Objects.equals(b.getTable().getId(), tableId)) continue;
                Object sub = lookup(subForms, b.getId());
                if (sub instanceof Map<?, ?> subMap) {
                    Object subRule = subMap.get("rule");
                    if (walkRuleNodes(subRule, byOldFieldName)) changed = true;
                }
            }
        }

        // 3) fieldPermissions (task form) remapped by fieldName for main-table bindings only.
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
     * Form JSON map keys may deserialize as {@link String} (typical) or numeric types; match by string equality.
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
     * Recursively walks form-create nodes (array or singleton). For nodes whose {@code field} matches
     * {@code byOldFieldName}:
     * <ul>
     *   <li>Rewrite {@code field}, sync {@code title} (always);</li>
     *   <li>After guard checks on control {@code type} + new dataType, sync {@code props.maxlength}/{@code props.precision};</li>
     *   <li>Add/remove {@code required} entries inside {@code validate} from {@code nullable}.</li>
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
                // Title mirrors FormDesigner.fieldToFormRule (`title: field.displayName || field.fieldName`):
                // rewrite when display name or field name changes; prefer display name, fall back to field name.
                if (c.displayNameChanged() || c.fieldNameChanged()) {
                    String nextTitle = !isBlank(c.newDisplayName())
                            ? c.newDisplayName()
                            : c.newFieldName();
                    if (nextTitle != null) {
                        Object curTitle = n.get("title");
                        if (!Objects.equals(curTitle, nextTitle)) {
                            n.put("title", nextTitle);
                            changed = true;
                        }
                    }
                }

                // dataType guard: use current control type + new dataType;
                // if designer swapped VARCHAR input→select we do not overwrite length.
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
                    String labelForMessage = !isBlank(c.newDisplayName())
                            ? c.newDisplayName()
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
     * Writes {@code props.<key>}, creating {@code props} map if missing.
     * Aborts if {@code props} is not a map to avoid corrupting malformed nodes.
     *
     * @return true when a value changed
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
     * Adds/removes {@code required:true} entries inside {@code node.validate}.
     *
     * <ul>
     *   <li>{@code nullable == false} and no existing required rule → append {@code {required:true, message, trigger:'blur'}};</li>
     *   <li>{@code nullable == true} with existing required rule → remove it;</li>
     *   <li>Other validators (type/min/max) stay untouched.</li>
     * </ul>
     *
     * @return true when validators changed
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
            if (requiredIdx >= 0) return false; // already required
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("required", true);
            // Backend has no i18n here: use display label as placeholder; designers can refine in Form Designer.
            req.put("message", label != null ? label + " is required" : "Required");
            req.put("trigger", "blur");
            validate.add(req);
            node.put("validate", validate);
            return true;
        } else {
            // nullable=true → optional, strip required validator
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
