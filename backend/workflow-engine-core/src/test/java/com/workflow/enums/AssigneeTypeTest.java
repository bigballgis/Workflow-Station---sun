package com.workflow.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssigneeType Enum Tests")
class AssigneeTypeTest {

    @Test
    void shouldHaveExpectedTypes() {
        assertThat(AssigneeType.values()).containsExactlyInAnyOrder(
                AssigneeType.PROCESS_INITIATOR,
                AssigneeType.ENTITY_MANAGER,
                AssigneeType.FUNCTIONAL_MANAGER,
                AssigneeType.HIERARCHY_ROLE,
                AssigneeType.BU_ROLE,
                AssigneeType.MANUAL_ASSIGN,
                AssigneeType.ASSIGNEE_FROM_VARIABLE,
                AssigneeType.ELEMENT_VARIABLE
        );
    }

    @Test
    void fromCodeLegacyInitiator() {
        assertThat(AssigneeType.fromCode("INITIATOR")).isEqualTo(AssigneeType.PROCESS_INITIATOR);
    }

    @Test
    void fromCodeLegacyHierarchy() {
        assertThat(AssigneeType.fromCode("INITIATOR_BU_ROLE")).isEqualTo(AssigneeType.HIERARCHY_ROLE);
        assertThat(AssigneeType.fromCode("CURRENT_BU_ROLE")).isEqualTo(AssigneeType.HIERARCHY_ROLE);
        assertThat(AssigneeType.fromCode("DEPT_OTHERS")).isEqualTo(AssigneeType.HIERARCHY_ROLE);
    }

    @Test
    void fromCodeDeprecatedUnbounded() {
        assertThat(AssigneeType.fromCode("BU_UNBOUNDED_ROLE")).isNull();
        assertThat(AssigneeType.fromCode("VIRTUAL_GROUP")).isNull();
    }

    @Test
    void listenerOnlyFlags() {
        assertThat(AssigneeType.MANUAL_ASSIGN.isListenerOnly()).isTrue();
        assertThat(AssigneeType.HIERARCHY_ROLE.isListenerOnly()).isFalse();
    }
}
