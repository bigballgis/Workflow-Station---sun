package com.developer.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BpmnIdRewriter 单元测试 - 覆盖典型 BPMN 扩展属性形态。
 */
class BpmnIdRewriterTest {

    @Test
    void rewriteSubTableIdAndFormIdAndActionIds() {
        String xml = """
                <?xml version="1.0"?>
                <bpmn:userTask>
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="subTableId" value="13" />
                      <custom:property name="formId" value="11" />
                      <custom:property name="actionIds" value="[12,34]" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(13L, 113L),
                Map.of(11L, 111L),
                Map.of(12L, 112L, 34L, 134L));

        assertThat(rewritten).contains("name=\"subTableId\" value=\"113\"");
        assertThat(rewritten).contains("name=\"formId\" value=\"111\"");
        assertThat(rewritten).contains("name=\"actionIds\" value=\"[112,134]\"");
        assertThat(rewritten).doesNotContain("value=\"13\"");
        assertThat(rewritten).doesNotContain("value=\"11\"");
        assertThat(rewritten).doesNotContain("[12,34]");
    }

    @Test
    void supportsNamespaceVariantsAndValuesElement() {
        String xml = """
                <bpmn:userTask>
                  <custom_1:property name="formId" value="7"/>
                  <custom_1:values name="actionIds" value="[1,2,3]"/>
                </bpmn:userTask>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(),
                Map.of(7L, 700L),
                Map.of(1L, 10L, 2L, 20L, 3L, 30L));

        assertThat(rewritten).contains("name=\"formId\" value=\"700\"");
        assertThat(rewritten).contains("name=\"actionIds\" value=\"[10,20,30]\"");
    }

    @Test
    void preservesUnmappedIds() {
        String xml = "<custom:property name=\"formId\" value=\"7\" />";

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(99L, 100L), Map.of());

        // 99 不在源中，7 不在映射中，保持原样
        assertThat(rewritten).isEqualTo(xml);
    }

    @Test
    void preservesNonIdProperties() {
        String xml = """
                <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                <custom:property name="subTableName" value="participants" />
                <custom:property name="rowIdVariable" value="currentItem.rowId" />
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(1L, 2L), Map.of(3L, 4L), Map.of(5L, 6L));

        assertThat(rewritten).isEqualTo(xml);
    }

    @Test
    void doesNotRewriteBpmnElementIdAttributes() {
        // BPMN 节点 id 形如 id="MultiInstance_SubTable_13"，含 13 但不在 custom:property 内，不能被改
        String xml = """
                <bpmn:subProcess id="MultiInstance_SubTable_13">
                  <bpmn:userTask id="MI_UserTask_13">
                    <custom:property name="subTableId" value="13" />
                  </bpmn:userTask>
                </bpmn:subProcess>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(13L, 200L), Map.of(), Map.of());

        assertThat(rewritten).contains("id=\"MultiInstance_SubTable_13\"");
        assertThat(rewritten).contains("id=\"MI_UserTask_13\"");
        assertThat(rewritten).contains("name=\"subTableId\" value=\"200\"");
    }

    @Test
    void handlesBase64EncodedXml() {
        String xml = "<custom:property name=\"formId\" value=\"7\" />";
        String encoded = XmlEncodingUtil.encode(xml);

        String rewritten = BpmnIdRewriter.rewrite(
                encoded, Map.of(), Map.of(7L, 70L), Map.of());

        assertThat(XmlEncodingUtil.smartDecode(rewritten))
                .contains("name=\"formId\" value=\"70\"");
        // 输出仍是 Base64 编码（不以 < 开头）
        assertThat(rewritten).doesNotStartWith("<");
    }

    @Test
    void handlesNullAndBlankInputs() {
        assertThat(BpmnIdRewriter.rewrite(null, Map.of(1L, 2L), Map.of(), Map.of())).isNull();
        assertThat(BpmnIdRewriter.rewrite("", Map.of(1L, 2L), Map.of(), Map.of())).isEmpty();
        assertThat(BpmnIdRewriter.rewrite("   ", Map.of(1L, 2L), Map.of(), Map.of())).isEqualTo("   ");
    }

    @Test
    void emptyMappingsReturnInputUnchanged() {
        String xml = "<custom:property name=\"subTableId\" value=\"13\" />";
        assertThat(BpmnIdRewriter.rewrite(xml, Map.of(), Map.of(), Map.of()))
                .isEqualTo(xml);
        assertThat(BpmnIdRewriter.rewrite(xml, null, null, null))
                .isEqualTo(xml);
    }

    @Test
    void supportsSingleQuotesAndAttributeOrder() {
        String xml = "<custom:property value='13' name='subTableId' />";

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(13L, 200L), Map.of(), Map.of());

        assertThat(rewritten).contains("value='200'");
        assertThat(rewritten).contains("name='subTableId'");
        assertThat(rewritten).doesNotContain("value='13'");
    }

    @Test
    void rewriteTableIdAlias() {
        String xml = "<custom:property name=\"tableId\" value=\"5\" />";

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(5L, 50L), Map.of(), Map.of());

        assertThat(rewritten).contains("name=\"tableId\" value=\"50\"");
    }

    /**
     * 关键回归：源 BPMN 中 subTableId 与 subTableName 不一致（设计器 bug 历史脏数据），
     * 例如 subTableName="subtable" 但 subTableId 仍为旧 MAIN 表 id=20。
     * 仅按 ID 兜底会得到错误的 MAIN 表 id（35），导致部署校验报 INVALID_TABLE_TYPE。
     * 名字优先策略应解析到克隆侧名为 "subtable" 的 SUB 表（36），让克隆产物自洽。
     */
    @Test
    void resolvesSubTableIdByNameWhenSourceMismatch() {
        String xml = """
                <custom:properties>
                  <custom:property name="subTableName" value="subtable" />
                  <custom:property name="subTableId" value="20" />
                  <custom:property name="assigneeField" value="assignee" />
                </custom:properties>
                """;

        // 源 ID 映射：20→35（MAIN "Test"），21→36（SUB "subtable"）
        Map<Long, Long> tableIdMapping = Map.of(20L, 35L, 21L, 36L);
        // 克隆侧名字映射：subtable→36（SUB 表）
        Map<String, Long> clonedTableNameToId = Map.of("Test", 35L, "subtable", 36L);

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                tableIdMapping,
                Map.of(),
                Map.of(),
                clonedTableNameToId,
                Map.of());

        assertThat(rewritten)
                .as("名字优先：subTableId 应被改写为 36（SUB 表 'subtable'），而非 35（MAIN 表 'Test'）")
                .contains("name=\"subTableId\" value=\"36\"")
                .doesNotContain("name=\"subTableId\" value=\"35\"")
                .doesNotContain("name=\"subTableId\" value=\"20\"");
        assertThat(rewritten).contains("name=\"subTableName\" value=\"subtable\"");
    }

    /**
     * Clone renames tables (uk_dw_table_name is global). The BPMN's subTableName/tableName VALUES must be
     * rewritten to the renamed clone tables, else MI runtime (loadSubTableRow(subTableName,...)) reads the
     * SOURCE table's data. subTableId resolution still keys off the SOURCE name present at resolution time.
     */
    @Test
    void rewritesTableNameValuesToRenamedCloneTables() {
        String xml = """
                <custom:properties>
                  <custom:property name="subTableName" value="participants" />
                  <custom:property name="subTableId" value="20" />
                  <custom:property name="tableName" value="main" />
                  <custom:property name="tableId" value="10" />
                </custom:properties>
                """;

        Map<Long, Long> tableIdMapping = Map.of(20L, 220L, 10L, 110L);
        Map<String, Long> clonedTableNameToId = Map.of("participants", 220L, "main", 110L);
        Map<String, String> sourceToNewTableName = Map.of("participants", "participants_copy", "main", "main_copy");

        String rewritten = BpmnIdRewriter.rewrite(
                xml, tableIdMapping, Map.of(), Map.of(),
                clonedTableNameToId, Map.of(), sourceToNewTableName);

        assertThat(rewritten)
                .contains("name=\"subTableName\" value=\"participants_copy\"")
                .contains("name=\"tableName\" value=\"main_copy\"")
                .contains("name=\"subTableId\" value=\"220\"")
                .contains("name=\"tableId\" value=\"110\"")
                .doesNotContain("value=\"participants\"")
                .doesNotContain("value=\"main\"");
    }

    @Test
    void keepsTableNameValuesWhenNoRenameMapping() {
        String xml = """
                <custom:properties>
                  <custom:property name="subTableName" value="participants" />
                  <custom:property name="subTableId" value="20" />
                </custom:properties>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(20L, 220L), Map.of(), Map.of(),
                Map.of("participants", 220L), Map.of(), Map.of());

        assertThat(rewritten)
                .as("empty rename map → subTableName value untouched")
                .contains("name=\"subTableName\" value=\"participants\"")
                .contains("name=\"subTableId\" value=\"220\"");
    }

    @Test
    void resolvesFormIdByNameWhenSourceMismatch() {
        String xml = """
                <custom:properties>
                  <custom:property name="formId" value="18" />
                  <custom:property name="formName" value="subform_copy" />
                </custom:properties>
                """;

        // 源 ID 映射：18→31（"y"），19→32（"subform"），20→33（"subform_copy"）
        Map<Long, Long> formIdMapping = Map.of(18L, 31L, 19L, 32L, 20L, 33L);
        Map<String, Long> clonedFormNameToId = Map.of("y", 31L, "subform", 32L, "subform_copy", 33L);

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(),
                formIdMapping,
                Map.of(),
                Map.of(),
                clonedFormNameToId);

        assertThat(rewritten)
                .as("formId 应跟随 formName='subform_copy' 解析到 33，而非按旧 ID 18→31")
                .contains("name=\"formId\" value=\"33\"")
                .doesNotContain("name=\"formId\" value=\"31\"")
                .doesNotContain("name=\"formId\" value=\"18\"");
    }

    @Test
    void remapsRequestFormIdIndependentlyOfFormIdByName() {
        // Cross-env import: same userTask carries To Do (formId) and My Requests (requestFormId).
        // Source requestFormId=806 is not in the target FU, so the designer would show the raw
        // number unless we remap by requestFormName — not by the sibling formName.
        String xml = """
                <custom:properties>
                  <custom:property name="formId" value="100" />
                  <custom:property name="formName" value="ACQ case form" />
                  <custom:property name="requestFormId" value="806" />
                  <custom:property name="requestFormName" value="ACQ my request" />
                </custom:properties>
                """;

        Map<Long, Long> formIdMapping = Map.of(100L, 501L, 200L, 502L);
        Map<String, Long> importedFormNameToId = Map.of(
                "ACQ case form", 501L,
                "ACQ my request", 502L);

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(),
                formIdMapping,
                Map.of(),
                Map.of(),
                importedFormNameToId);

        assertThat(rewritten)
                .as("To Do formId 按 formName 解析")
                .contains("name=\"formId\" value=\"501\"")
                .as("My Requests requestFormId 必须按 requestFormName 解析，不能留下源环境 806")
                .contains("name=\"requestFormId\" value=\"502\"")
                .doesNotContain("name=\"requestFormId\" value=\"806\"")
                .as("不能误用 To Do 的 formName 去改 requestFormId")
                .doesNotContain("name=\"requestFormId\" value=\"501\"");
    }

    @Test
    void resolvesRequestFormIdByNameWhenSourceIdMappingWouldPickWrongForm() {
        String xml = """
                <custom:properties>
                  <custom:property name="requestFormId" value="18" />
                  <custom:property name="requestFormName" value="subform_copy" />
                </custom:properties>
                """;

        Map<Long, Long> formIdMapping = Map.of(18L, 31L, 19L, 32L, 20L, 33L);
        Map<String, Long> clonedFormNameToId = Map.of("y", 31L, "subform", 32L, "subform_copy", 33L);

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(),
                formIdMapping,
                Map.of(),
                Map.of(),
                clonedFormNameToId);

        assertThat(rewritten)
                .as("requestFormId 应跟随 requestFormName='subform_copy' 解析到 33，而非按旧 ID 18→31")
                .contains("name=\"requestFormId\" value=\"33\"")
                .doesNotContain("name=\"requestFormId\" value=\"31\"")
                .doesNotContain("name=\"requestFormId\" value=\"18\"");
    }

    @Test
    void remapsUnwrappedRequestFormIdByIdMapping() {
        String xml = "<custom:property name=\"requestFormId\" value=\"806\" />";

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(806L, 502L), Map.of());

        assertThat(rewritten).contains("name=\"requestFormId\" value=\"502\"");
    }

    @Test
    void remapsProcessGlobalActionIdsIndependentlyOfNodeActionIds() {
        String xml = """
                <bpmn:process id="p1">
                  <custom:properties>
                    <custom:property name="globalActionIds" value="[50]" />
                    <custom:property name="globalActionNames" value="[&quot;Save&quot;]" />
                  </custom:properties>
                  <bpmn:userTask id="task1">
                    <custom:properties>
                      <custom:property name="actionIds" value="[51]" />
                    </custom:properties>
                  </bpmn:userTask>
                </bpmn:process>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(), Map.of(50L, 1200L, 51L, 1201L));

        assertThat(rewritten)
                .as("Process Global 写在 globalActionIds，导入必须按动作 id mapping 改写，否则设计器对不上新 id 会显示 Bind to Node")
                .contains("name=\"globalActionIds\" value=\"[1200]\"")
                .contains("name=\"actionIds\" value=\"[1201]\"")
                .doesNotContain("value=\"[50]\"")
                .doesNotContain("value=\"[51]\"");
    }

    @Test
    void remapsUnwrappedGlobalActionIdsByIdMapping() {
        String xml = "<custom:property name=\"globalActionIds\" value=\"[50,51]\" />";

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(), Map.of(50L, 1200L, 51L, 1201L));

        assertThat(rewritten).contains("name=\"globalActionIds\" value=\"[1200,1201]\"");
    }

    @Test
    void fallsBackToIdMappingWhenNameNotInClonedSide() {
        String xml = """
                <custom:properties>
                  <custom:property name="subTableName" value="ghost" />
                  <custom:property name="subTableId" value="20" />
                </custom:properties>
                """;

        Map<Long, Long> tableIdMapping = Map.of(20L, 35L);
        Map<String, Long> clonedTableNameToId = Map.of("subtable", 36L);

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                tableIdMapping,
                Map.of(),
                Map.of(),
                clonedTableNameToId,
                Map.of());

        assertThat(rewritten)
                .as("克隆侧不存在名为 'ghost' 的表，按旧 ID 兜底为 35")
                .contains("name=\"subTableId\" value=\"35\"");
    }

    @Test
    void usesIdMappingWhenNoNameAttributePresent() {
        String xml = """
                <custom:properties>
                  <custom:property name="subTableId" value="13" />
                </custom:properties>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(13L, 113L),
                Map.of(),
                Map.of(),
                Map.of("subtable", 999L),
                Map.of());

        assertThat(rewritten)
                .as("没有 subTableName，仅 ID 映射生效")
                .contains("name=\"subTableId\" value=\"113\"");
    }

    @Test
    void nameBasedLookupAppliesOnlyWithinSameBlock() {
        // 第一个块用 subTableName=A（克隆侧 A 不存在，无法名字解析），仅 ID 兜底
        // 第二个块用 subTableName=B（命中名字解析）
        // 名字解析必须按块隔离，不能让块 A 的 subTableId 被块 B 的 name 错误解析
        String xml = """
                <custom:properties>
                  <custom:property name="subTableName" value="ghost" />
                  <custom:property name="subTableId" value="20" />
                </custom:properties>
                <other-tag/>
                <custom:properties>
                  <custom:property name="subTableName" value="subtable" />
                  <custom:property name="subTableId" value="20" />
                </custom:properties>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(20L, 35L),
                Map.of(),
                Map.of(),
                Map.of("subtable", 36L),
                Map.of());

        // 第一个块：ghost 找不到 → ID 兜底 → 35
        // 第二个块：subtable 命中 → 名字解析 → 36
        // 用 indexOf 判断顺序，确保两个块各自得到正确结果
        int firstBlockEnd = rewritten.indexOf("</custom:properties>");
        int secondBlockStart = rewritten.indexOf("<custom:properties>", firstBlockEnd);
        String firstBlock = rewritten.substring(0, firstBlockEnd);
        String secondBlock = rewritten.substring(secondBlockStart);

        assertThat(firstBlock).contains("name=\"subTableId\" value=\"35\"");
        assertThat(secondBlock).contains("name=\"subTableId\" value=\"36\"");
    }

    @Test
    void remapsSendTaskConnectionIdWhenValueIsConnectionUid() {
        String xml = """
                <bpmn:sendTask id="SendTask_1">
                  <custom:properties>
                    <custom:property name="connectionId" value="377e4f9a-4435-4416-b510-448e9dd0a92b" />
                    <custom:property name="emailTemplateId" value="12" />
                  </custom:properties>
                </bpmn:sendTask>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(5L, 50L),
                Map.of(12L, 120L),
                Map.of("377e4f9a-4435-4416-b510-448e9dd0a92b", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

        assertThat(rewritten)
                .contains("name=\"connectionId\" value=\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"")
                .contains("name=\"emailTemplateId\" value=\"120\"")
                .doesNotContain("377e4f9a-4435-4416-b510-448e9dd0a92b");
    }

    @Test
    void keepsNumericConnectionIdMappingWhenUidMapMisses() {
        String xml = """
                <custom:properties>
                  <custom:property name="connectionId" value="5" />
                </custom:properties>
                """;

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(5L, 50L), Map.of(), Map.of("other-uid", "new-uid"));

        assertThat(rewritten).contains("name=\"connectionId\" value=\"50\"");
    }
}
