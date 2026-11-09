package com.admin.component;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** BPMN extension properties are read whatever the order and quoting of their attributes (WP9 review). */
class PublishedContentsTest {

    @Test
    void propertiesAreReadWhateverTheAttributeOrderAndQuotes() {
        String bpmn = "<flowable:property name=\"assigneeType\" value=\"BU_ROLE\"/>"
                + "<custom:property value=\"FINANCE\" name=\"businessUnitId\"></custom:property>"
                + "<property  id=\"p3\"\n  value='R1, R2'\n  name='roleIds' />"
                + "<custom:properties><custom:property name=\"noValue\"/></custom:properties>";

        assertThat(PublishedContents.properties(bpmn)).containsExactly(
                Map.entry("assigneeType", "BU_ROLE"), Map.entry("businessUnitId", "FINANCE"),
                Map.entry("roleIds", "R1, R2"));
    }

    @Test
    void anAttributeEndingInNameIsNotTheName() {
        assertThat(PublishedContents.properties("<custom:property data-name=\"x\" name=\"roleId\" value=\"R1\"/>"))
                .containsExactly(Map.entry("roleId", "R1"));
    }

    @Test
    void referenceFindersMatchValueBeforeName() {
        String task = "<bpmn:userTask id=\"T1\"><bpmn:extensionElements><custom:properties>"
                + "<custom:property value=\"BU_ROLE\" name=\"assigneeType\"/>"
                + "<custom:property value=\"bu-fin\" name=\"assigneeValue\"/>"
                + "<custom:property value=\"MANAGER,CLERK\" name=\"roleIds\"/>"
                + "</custom:properties></bpmn:extensionElements></bpmn:userTask>";

        assertThat(BusinessUnitReferenceFinder.assignsToUnit(task, Set.of("bu-fin", "FINANCE"))).isTrue();
        assertThat(RoleReferenceFinder.assignsRole(task, Set.of("r-clerk", "CLERK"))).isTrue();
        assertThat(RoleReferenceFinder.assignsRole(task, Set.of("r-boss", "BOSS"))).isFalse();
    }
}
