package com.developer.service;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单元测试：Table Design 字段变更 → Form 画布同步。
 *
 * <p>覆盖：
 * <ul>
 *   <li>PRIMARY 绑定主画布 rule field/title 同步；</li>
 *   <li>SUB 绑定 subForms[bindingId].rule 同步；</li>
 *   <li>嵌套 children 节点递归；</li>
 *   <li>fieldPermissions 以 fieldName 为 key 重映射；</li>
 *   <li>绑定不指向当前表的表单不被改动；</li>
 *   <li>仅 Display Name 变更（fieldName 不变）也同步 title；</li>
 *   <li>length 变更：input + VARCHAR 才同步 maxlength（设计师换控件类型不被覆盖）；</li>
 *   <li>scale 变更：inputNumber + DECIMAL 才同步 props.precision；</li>
 *   <li>nullable 变更：增删 validate 里的 required 项，其它校验不被触动；</li>
 *   <li>dataType 变更：不强制改写画布节点的 type。</li>
 * </ul>
 */
@DisplayName("FormConfigFieldRenamer - field change propagation")
class FormConfigFieldRenamerTest {

    private static final Long TABLE_ID = 100L;
    private static final Long OTHER_TABLE_ID = 200L;
    private static final Long PRIMARY_BINDING_ID = 11L;
    private static final Long SUB_BINDING_ID = 22L;

    @Test
    @DisplayName("PRIMARY 绑定：rule 顶层 field/title 同步，fieldPermissions 重映射")
    void appliesToPrimaryRuleAndFieldPermissions() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(
                node("amount", "Amount", "inputNumber"),
                node("note", "Note", "input")
        )));
        form.setConfigJson(config);
        form.setFieldPermissions(new LinkedHashMap<>(Map.of(
                "amount", "READONLY",
                "note", "EDITABLE"
        )));

        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(
                rename("amount", "amount_v2", "Amount", "Total Amount")
        ));

        assertThat(dirty).containsExactly(form);
        Map<String, Object> updated = form.getConfigJson();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rule = (List<Map<String, Object>>) updated.get("rule");
        assertThat(rule.get(0)).containsEntry("field", "amount_v2");
        assertThat(rule.get(0)).containsEntry("title", "Total Amount");
        assertThat(rule.get(1)).containsEntry("field", "note");
        assertThat(rule.get(1)).containsEntry("title", "Note");

        assertThat(form.getFieldPermissions())
                .doesNotContainKey("amount")
                .containsEntry("amount_v2", "READONLY")
                .containsEntry("note", "EDITABLE");
    }

    @Test
    @DisplayName("SUB 绑定：subForms[bindingId].rule 字段同步")
    void appliesToSubFormRule() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithSub(TABLE_ID, SUB_BINDING_ID);
        Map<String, Object> subRule = new LinkedHashMap<>();
        subRule.put("rule", new ArrayList<>(List.of(node("qty", "Qty", "inputNumber"))));
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put(String.valueOf(SUB_BINDING_ID), subRule);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>());
        config.put("subForms", subForms);
        form.setConfigJson(config);

        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(
                rename("qty", "quantity", "Qty", "Quantity")
        ));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var updatedSub = (Map<String, Object>) ((Map<?, ?>) form.getConfigJson().get("subForms"))
                .get(String.valueOf(SUB_BINDING_ID));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rule = (List<Map<String, Object>>) updatedSub.get("rule");
        assertThat(rule.get(0)).containsEntry("field", "quantity");
        assertThat(rule.get(0)).containsEntry("title", "Quantity");
    }

    @Test
    @DisplayName("嵌套 children：递归更新")
    void recursesIntoChildren() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "card");
        card.put("children", new ArrayList<>(List.of(node("amount", "Amount", "inputNumber"))));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(card)));
        form.setConfigJson(config);

        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(
                rename("amount", "amount2", "Amount", "Amount Due")
        ));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        @SuppressWarnings("unchecked")
        var children = (List<Map<String, Object>>) rule.get(0).get("children");
        assertThat(children.get(0)).containsEntry("field", "amount2");
        assertThat(children.get(0)).containsEntry("title", "Amount Due");
    }

    @Test
    @DisplayName("仅 Display Name 变更：title 同步，field 保持不变")
    void appliesDisplayNameOnlyChange() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(node("amount", "Amount", "inputNumber"))));
        form.setConfigJson(config);

        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(
                rename("amount", "amount", "Amount", "Total")
        ));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        assertThat(rule.get(0)).containsEntry("field", "amount");
        assertThat(rule.get(0)).containsEntry("title", "Total");
    }

    @Test
    @DisplayName("绑定不指向当前表：表单不被改动")
    void doesNotTouchUnrelatedForm() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition other = formWithPrimary(OTHER_TABLE_ID);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(node("amount", "Amount", "input"))));
        other.setConfigJson(config);

        var dirty = FormConfigFieldRenamer.apply(table, List.of(other), List.of(
                rename("amount", "amount_v2", "Amount", "Total")
        ));

        assertThat(dirty).isEmpty();
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) other.getConfigJson().get("rule");
        assertThat(rule.get(0)).containsEntry("field", "amount");
        assertThat(rule.get(0)).containsEntry("title", "Amount");
    }

    @Test
    @DisplayName("Display Name 清空：title 回退到 (新) Field Name")
    void blankDisplayNameFallsBackToFieldName() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(node("amount", "Old Title", "input"))));
        form.setConfigJson(config);

        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(
                rename("amount", "amount_v2", "Old Title", "")
        ));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        assertThat(rule.get(0)).containsEntry("field", "amount_v2");
        assertThat(rule.get(0)).containsEntry("title", "amount_v2");
    }

    // ─── property sync ────────────────────────────────────────────────────────

    @Test
    @DisplayName("length 变更（VARCHAR+input）：写入 props.maxlength")
    void syncsMaxlengthForVarcharInput() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> input = node("note", "Note", "input");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("maxlength", 255);
        input.put("props", props);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(input)));
        form.setConfigJson(config);

        var change = new FormConfigFieldRenamer.FieldChange(
                "note", "note", "Note", "Note",
                "VARCHAR", "VARCHAR",
                255, 512,
                null, null,
                true, true
        );
        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(change));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        @SuppressWarnings("unchecked")
        var p = (Map<String, Object>) rule.get(0).get("props");
        assertThat(p).containsEntry("maxlength", 512);
    }

    @Test
    @DisplayName("length 变更但控件已被换成 select：保持不变")
    void doesNotSyncMaxlengthWhenControlTypeOverridden() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        // 设计师把 input 换成了 select，maxlength 不再适用
        Map<String, Object> sel = node("note", "Note", "select");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("placeholder", "choose...");
        sel.put("props", props);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(sel)));
        form.setConfigJson(config);

        var change = new FormConfigFieldRenamer.FieldChange(
                "note", "note", "Note", "Note",
                "VARCHAR", "VARCHAR",
                255, 1024,
                null, null,
                true, true
        );
        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(change));

        assertThat(dirty).isEmpty();
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        @SuppressWarnings("unchecked")
        var p = (Map<String, Object>) rule.get(0).get("props");
        assertThat(p).doesNotContainKey("maxlength");
    }

    @Test
    @DisplayName("dataType 变更：不强制改写节点 type（避免抹掉自定义控件）")
    void doesNotFlipControlTypeWhenDataTypeChanges() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> input = node("note", "Note", "input");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(input)));
        form.setConfigJson(config);

        // VARCHAR → DATE，画布节点 type 应保持 "input"
        var change = new FormConfigFieldRenamer.FieldChange(
                "note", "note", "Note", "Note",
                "VARCHAR", "DATE",
                255, null,
                null, null,
                true, true
        );
        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(change));

        assertThat(dirty).isEmpty();
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        assertThat(rule.get(0)).containsEntry("type", "input");
    }

    @Test
    @DisplayName("scale 变更（DECIMAL+inputNumber）：写入 props.precision")
    void syncsPrecisionForDecimalInputNumber() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> num = node("amount", "Amount", "inputNumber");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("precision", 2);
        num.put("props", props);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(num)));
        form.setConfigJson(config);

        var change = new FormConfigFieldRenamer.FieldChange(
                "amount", "amount", "Amount", "Amount",
                "DECIMAL", "DECIMAL",
                null, null,
                2, 4,
                true, true
        );
        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(change));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        @SuppressWarnings("unchecked")
        var p = (Map<String, Object>) rule.get(0).get("props");
        assertThat(p).containsEntry("precision", 4);
    }

    @Test
    @DisplayName("nullable: true → false：追加 required 校验项，已有校验保留")
    void addsRequiredWhenNullableBecomesFalse() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> input = node("note", "Note", "input");
        // 已存在一个 max 长度校验，不应被触动
        Map<String, Object> maxRule = new LinkedHashMap<>();
        maxRule.put("max", 255);
        maxRule.put("message", "Too long");
        input.put("validate", new ArrayList<>(List.of(maxRule)));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(input)));
        form.setConfigJson(config);

        var change = new FormConfigFieldRenamer.FieldChange(
                "note", "note", "Note", "Note",
                "VARCHAR", "VARCHAR",
                255, 255,
                null, null,
                true, false
        );
        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(change));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        @SuppressWarnings("unchecked")
        var validate = (List<Map<String, Object>>) rule.get(0).get("validate");
        assertThat(validate).hasSize(2);
        // 原 max 校验保留
        assertThat(validate.get(0)).containsEntry("max", 255);
        // 新增 required 校验
        assertThat(validate.get(1)).containsEntry("required", true);
        assertThat(validate.get(1)).containsEntry("trigger", "blur");
    }

    @Test
    @DisplayName("nullable: false → true：移除 required 校验项，其它校验保留")
    void removesRequiredWhenNullableBecomesTrue() {
        TableDefinition table = table(TABLE_ID);
        FormDefinition form = formWithPrimary(TABLE_ID);
        Map<String, Object> input = node("note", "Note", "input");
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("required", true);
        req.put("message", "Note is required");
        req.put("trigger", "blur");
        Map<String, Object> maxRule = new LinkedHashMap<>();
        maxRule.put("max", 255);
        input.put("validate", new ArrayList<>(List.of(req, maxRule)));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", new ArrayList<>(List.of(input)));
        form.setConfigJson(config);

        var change = new FormConfigFieldRenamer.FieldChange(
                "note", "note", "Note", "Note",
                "VARCHAR", "VARCHAR",
                255, 255,
                null, null,
                false, true
        );
        var dirty = FormConfigFieldRenamer.apply(table, List.of(form), List.of(change));

        assertThat(dirty).containsExactly(form);
        @SuppressWarnings("unchecked")
        var rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        @SuppressWarnings("unchecked")
        var validate = (List<Map<String, Object>>) rule.get(0).get("validate");
        assertThat(validate).hasSize(1);
        // 仅保留 max 校验
        assertThat(validate.get(0)).containsEntry("max", 255);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** 只关心 field/title 的旧测试场景便利构造（其余属性视为未变）。 */
    private static FormConfigFieldRenamer.FieldChange rename(String oldField,
                                                             String newField,
                                                             String oldDesc,
                                                             String newDesc) {
        return new FormConfigFieldRenamer.FieldChange(oldField, newField, oldDesc, newDesc);
    }

    private static TableDefinition table(Long id) {
        TableDefinition t = new TableDefinition();
        t.setId(id);
        return t;
    }

    private static FormDefinition formWithPrimary(Long tableId) {
        FormDefinition f = new FormDefinition();
        f.setId(1L);
        FormTableBinding b = new FormTableBinding();
        b.setId(PRIMARY_BINDING_ID);
        b.setBindingType(BindingType.PRIMARY);
        b.setTable(table(tableId));
        List<FormTableBinding> bindings = new ArrayList<>();
        bindings.add(b);
        f.setTableBindings(bindings);
        return f;
    }

    private static FormDefinition formWithSub(Long tableId, Long bindingId) {
        FormDefinition f = new FormDefinition();
        f.setId(2L);
        FormTableBinding b = new FormTableBinding();
        b.setId(bindingId);
        b.setBindingType(BindingType.SUB);
        b.setTable(table(tableId));
        List<FormTableBinding> bindings = new ArrayList<>();
        bindings.add(b);
        f.setTableBindings(bindings);
        return f;
    }

    private static Map<String, Object> node(String field, String title, String type) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("type", type);
        n.put("field", field);
        n.put("title", title);
        return n;
    }
}
