package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleColumnSpecTest {

    @Test
    void systemTabCodesAreTheFourPlatformRoles() {
        assertThat(RoleColumnSpec.SYSTEM_ROLE_LIST_CODES)
                .containsExactlyInAnyOrder("SYS_ADMIN", "AUDITOR", "TECH_LEAD", "DEVELOPER");
    }

    private static ListColumnMeta column(String field) {
        return RoleColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
