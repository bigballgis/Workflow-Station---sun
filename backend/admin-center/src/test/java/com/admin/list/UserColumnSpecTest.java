package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserColumnSpecTest {

    @Test
    void statusOptionsAreStoredEnumValues() {
        assertThat(column("status").options()).extracting(ListColumnMeta.Option::value)
                .containsExactly("ACTIVE", "INACTIVE", "LOCKED");
    }

    @Test
    void filtersCompileAgainstThePhysicalUserTable() {
        List<Object> params = new ArrayList<>();
        String where = UserColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("username", "contains", "ann", null)), params);
        assertThat(where).contains("su.username ILIKE ?");
        assertThat(params).containsExactly("%ann%");
        assertThat(UserColumnSpec.sql().orderBy("fullName", "ASC"))
                .contains("su.full_name ASC");
    }

    @Test
    void unknownColumnIsRefused() {
        assertThatThrownBy(() -> UserColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("secret", "eq", "x", null)), new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ListColumnMeta column(String field) {
        return UserColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
