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
