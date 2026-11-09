package com.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiRoleRowPropertiesTest {

    @Test
    void aRoleRowNamesItsPool() {
        assertThat(MiRoleRowProperties.parse(
                "{\"assigneeMode\":\"role\",\"businessUnitCode\":\"FIN\",\"roleCodes\":[\"MGR\",\"CLERK\"]}"))
                .contains(new MiRoleRowProperties("FIN", java.util.List.of("MGR", "CLERK")));
    }

    @Test
    void otherRowsHaveNoPool() {
        assertThat(MiRoleRowProperties.parse("{\"assigneeMode\":\"user\"}")).isEmpty();
        assertThat(MiRoleRowProperties.parse(null)).isEmpty();
    }

    @Test
    void aRoleRowWithoutItsBuOrRolesIsCorrupt() {
        assertThatThrownBy(() -> MiRoleRowProperties.parse("{\"assigneeMode\":\"role\",\"roleCodes\":[]}"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MiRoleRowProperties.parse("{not json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
