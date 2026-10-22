package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubTableWriteDesignTest {
    @Test
    void taskStageUsesTheFormIdFrozenInPinnedBpmn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of(
                        """
                        {"formId":1,"formType":"PROCESS","configJson":{},"tableBindings":[]}
                        """,
                        """
                        {"formId":2,"formType":"TASK","configJson":{},"tableBindings":[
                          {"bindingId":202,"bindingType":"SUB","tableName":"files",
                           "filterFkFieldName":"party_ref","filterFkRefTableName":"parties",
                           "fkFillSources":[{"fieldId":12,"kind":"PRIMARY"}]}]}
                        """));
        when(jdbc.queryForList(contains("content_type = 'PROCESS'"), eq(String.class), eq("pin")))
                .thenReturn(List.of("""
                        <bpmn:userTask id="review"><bpmn:extensionElements>
                          <custom:properties><custom:property name="formId" value="2"/></custom:properties>
                        </bpmn:extensionElements></bpmn:userTask>
                        """));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE")))
                .thenReturn(List.of(
                        """
                        {"tableName":"files","tableType":"SUB","fields":[
                          {"fieldName":"file_key","isPrimaryKey":true},
                          {"id":11,"fieldName":"party_ref","isForeignKey":true},
                          {"id":12,"fieldName":"case_ref","isForeignKey":true}]}
                        """,
                        """
                        {"tableName":"parties","tableType":"SUB","fields":[
                          {"fieldName":"party_key","isPrimaryKey":true}]}
                        """));

        var rules = new SubTableWriteDesign(jdbc, new ObjectMapper()).resolve("pin", "review");

        assertThat(rules).containsOnlyKeys("202");
        assertThat(rules.get("202").filterField()).isEqualTo("party_ref");
        assertThat(rules.get("202").foreignKeyFields()).containsExactly("party_ref", "case_ref");
        assertThat(rules.get("202").explicitFillFields()).containsExactly("case_ref");
    }

    @Test
    void taskStageReadsFormIdFromDesignerValuesElement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of(
                        """
                        {"formId":1,"formType":"PROCESS","configJson":{},"tableBindings":[
                          {"bindingId":101,"bindingType":"SUB","tableName":"files",
                           "filterFkFieldName":"case_ref","filterFkRefTableName":"cases"}]}
                        """,
                        """
                        {"formId":2,"formType":"TASK","configJson":{},"tableBindings":[
                          {"bindingId":202,"bindingType":"SUB","tableName":"files",
                           "filterFkFieldName":"party_ref","filterFkRefTableName":"parties"}]}
                        """));
        when(jdbc.queryForList(contains("content_type = 'PROCESS'"), eq(String.class), eq("pin")))
                .thenReturn(List.of("""
                        <bpmn:userTask id="review"><bpmn:extensionElements>
                          <custom_1:properties>
                            <custom_1:values name="formId" value="2" />
                          </custom_1:properties>
                        </bpmn:extensionElements></bpmn:userTask>
                        """));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE")))
                .thenReturn(List.of(
                        """
                        {"tableName":"files","tableType":"SUB","fields":[
                          {"fieldName":"file_key","isPrimaryKey":true}]}
                        """,
                        """
                        {"tableName":"parties","tableType":"SUB","fields":[
                          {"fieldName":"party_key","isPrimaryKey":true}]}
                        """,
                        """
                        {"tableName":"cases","tableType":"MAIN","fields":[
                          {"fieldName":"case_key","isPrimaryKey":true}]}
                        """));

        var rules = new SubTableWriteDesign(jdbc, new ObjectMapper()).resolve("pin", "review");

        assertThat(rules).containsOnlyKeys("202");
        assertThat(rules.get("202").filterField()).isEqualTo("party_ref");
    }

    @Test
    void processStartUsesTaskSceneFormWhenMyRequestCopyIsPinnedToo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of(
                        """
                        {"formId":1,"formType":"PROCESS","scene":"TASK","configJson":{},"tableBindings":[
                          {"bindingId":101,"bindingType":"SUB","tableName":"files",
                           "filterFkFieldName":"case_ref","filterFkRefTableName":"cases"}]}
                        """,
                        """
                        {"formId":2,"formType":"PROCESS","scene":"REQUEST","configJson":{},"tableBindings":[
                          {"bindingId":202,"bindingType":"SUB","tableName":"files",
                           "filterFkFieldName":"case_ref","filterFkRefTableName":"cases"}]}
                        """));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE")))
                .thenReturn(List.of("""
                        {"tableName":"files","tableType":"SUB","fields":[
                          {"fieldName":"file_key","isPrimaryKey":true}]}
                        """, """
                        {"tableName":"cases","tableType":"MAIN","fields":[
                          {"fieldName":"case_key","isPrimaryKey":true}]}
                        """));

        var rules = new SubTableWriteDesign(jdbc, new ObjectMapper()).resolve("pin", null);

        assertThat(rules).containsOnlyKeys("101");
    }

    @Test
    void processStartStillRejectsTwoTaskScenePinnedForms() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of(
                        """
                        {"formId":1,"formType":"PROCESS","scene":"TASK","configJson":{},"tableBindings":[]}
                        """,
                        """
                        {"formId":2,"formType":"PROCESS","configJson":{},"tableBindings":[]}
                        """));

        assertThatThrownBy(() -> new SubTableWriteDesign(jdbc, new ObjectMapper()).resolve("pin", null))
                .isInstanceOf(PortalException.class).hasMessageContaining("Pinned form is missing or ambiguous");
    }

    @Test
    void deletedLiveBindingsAndChangedLiveKeysDoNotAffectPinnedRules() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of("""
                        {"formId":1,"formType":"PROCESS","configJson":{},"tableBindings":[
                          {"bindingId":101,"bindingType":"SUB","tableName":"FILES",
                           "filterFkFieldName":"case_ref","filterFkRefTableName":"CASES"}]}
                        """));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE")))
                .thenReturn(List.of("""
                        {"tableName":"files","tableType":"SUB","fields":[
                          {"fieldName":"file_key","isPrimaryKey":true}]}
                        """, """
                        {"tableName":"cases","tableType":"MAIN","fields":[
                          {"fieldName":"case_key","isPrimaryKey":true}]}
                        """));
        var rules = new SubTableWriteDesign(jdbc, new ObjectMapper()).resolve("pin", null);
        assertThat(rules.get("101").pk()).containsExactly("file_key");
        assertThat(rules.get("101").refPk()).containsExactly("case_key");
        assertThat(rules.get("101").filterField()).isEqualTo("case_ref");
        verify(jdbc, never()).queryForList(contains("dw_"), eq(String.class), any(Object[].class));
    }

    @Test
    void legacyFrozenBindingUsesItsConfiguredStructuralForeignKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of("""
                        {"formId":1,"formType":"PROCESS","configJson":{},"tableBindings":[
                          {"bindingId":101,"bindingType":"SUB","tableName":"files",
                           "foreignKeyField":"party_ref"}]}
                        """));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE")))
                .thenReturn(List.of("""
                        {"tableName":"files","tableType":"SUB","fields":[
                          {"fieldName":"file_key","isPrimaryKey":true},
                          {"fieldName":"party_ref","isForeignKey":true,"refTableName":"parties"}]}
                        """, """
                        {"tableName":"parties","tableType":"SUB","fields":[
                          {"fieldName":"party_key","isPrimaryKey":true}]}
                        """));

        var binding = new SubTableWriteDesign(jdbc, new ObjectMapper())
                .resolve("pin", null).get("101");

        assertThat(binding.filterField()).isEqualTo("party_ref");
        assertThat(binding.refTableName()).isEqualTo("parties");
        assertThat(binding.refPk()).containsExactly("party_key");
    }

    @Test
    void tableFactsDescribeEveryTableOfTheReleaseForMiSliceClassification() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of("""
                        {"formId":1,"formType":"PROCESS","configJson":{},"tableBindings":[
                          {"bindingId":101,"bindingType":"SUB","tableName":"Members","bindingLinkMode":"miParticipantRow"},
                          {"bindingId":102,"bindingType":"SUB","tableName":"files",
                           "filterFkFieldName":"case_ref","filterFkRefTableName":"cases"}]}
                        """, """
                        {"formId":2,"formType":"TASK","configJson":{},"tableBindings":[
                          {"bindingId":201,"bindingType":"SUB","tableName":"notes"}]}
                        """));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE")))
                .thenReturn(List.of("""
                        {"tableName":"cases","tableType":"MAIN","fields":[{"fieldName":"case_key","isPrimaryKey":true}]}
                        """, """
                        {"tableName":"members","tableType":"SUB","fields":[
                          {"fieldName":"member_key","isPrimaryKey":true},
                          {"fieldName":"case_ref","isForeignKey":true,"refTableName":"cases"}]}
                        """, """
                        {"tableName":"files","tableType":"SUB","fields":[
                          {"fieldName":"case_ref","isForeignKey":true,"refTableName":"cases"},
                          {"fieldName":"member_ref","isForeignKey":true,"refTableName":"members"}]}
                        """, """
                        {"tableName":"notes","tableType":"SUB","fields":[
                          {"fieldName":"member_ref","isForeignKey":true,"refTableName":"members"},
                          {"fieldName":"case_ref","isForeignKey":true,"refTableName":"cases"},
                          {"fieldName":"room_ref","isForeignKey":true,"refTableName":"rooms"}]}
                        """, """
                        {"tableName":"rooms","tableType":"RELATION","fields":[]}
                        """));

        var design = new SubTableWriteDesign(jdbc, new ObjectMapper());
        var facts = design.tableFacts("pin");

        assertThat(facts).containsOnlyKeys("dw:cases", "dw:members", "dw:files", "dw:notes", "dw:rooms");
        // An explicit MI Participant Row declaration on any of the release's forms.
        assertThat(facts.get("dw:members").participantRow()).isTrue();
        // A declared filter FK wins over the table's other FK columns.
        assertThat(facts.get("dw:files").foreignKeyTargetTypes()).containsExactly("MAIN");
        // Without one, every FK of the table counts — here they disagree. A relation-table target
        // is not a table type of the design-time FK model, so it never counts.
        assertThat(facts.get("dw:notes").foreignKeyTargetTypes()).containsExactly("SUB", "MAIN");
        assertThat(facts.get("dw:notes").foreignKeyFields()).containsExactly("member_ref", "case_ref", "room_ref");
        assertThat(facts.get("dw:rooms").relation()).isTrue();
        verify(jdbc, never()).queryForList(contains("dw_"), eq(String.class), any(Object[].class));
        // A published release never changes: its facts are computed once.
        assertThat(design.tableFacts("pin")).isSameAs(facts);
        verify(jdbc, times(1)).queryForList(anyString(), eq(String.class), eq("pin"), eq("DATA_TABLE"));
    }

    @Test
    void noPinnedReleaseHasNoTableFacts() {
        assertThat(new SubTableWriteDesign(mock(JdbcTemplate.class), new ObjectMapper()).tableFacts(" ")).isNull();
    }

    @Test
    void missingFrozenTableDoesNotFallBackToLiveDw() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pin"), eq("FORM")))
                .thenReturn(List.of("""
                        {"formId":1,"formType":"PROCESS","configJson":{},"tableBindings":[
                         {"bindingId":101,"bindingType":"SUB","tableName":"missing"}]}
                        """));
        assertThatThrownBy(() -> new SubTableWriteDesign(jdbc, new ObjectMapper()).resolve("pin", null))
                .isInstanceOf(PortalException.class).hasMessageContaining("Pinned table");
    }
}
