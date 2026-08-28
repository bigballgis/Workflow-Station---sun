package com.admin.dto.list;

import com.admin.dto.request.AdminAuditListQueryRequest;
import com.admin.dto.request.RoleListQueryRequest;
import com.admin.dto.request.UserListQueryRequest;
import com.admin.dto.request.VirtualGroupListQueryRequest;
import com.platform.common.list.ListColumnMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminListPageContractTest {

    @Test
    void pageHasNoGroupsField() {
        assertThat(AdminListPage.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("columns", "content", "page", "size", "totalElements");
    }

    @Test
    void representativeQueryRecordsHaveNoGroupBy() {
        for (Class<?> type : List.of(
                UserListQueryRequest.class,
                RoleListQueryRequest.class,
                AdminAuditListQueryRequest.class,
                VirtualGroupListQueryRequest.class)) {
            assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
                    .as(type.getSimpleName())
                    .doesNotContain("groupBy", "groups", "groupable");
        }
    }

    @Test
    void pageCtorDoesNotAcceptGroups() {
        AdminListPage<String> page = new AdminListPage<>(
                List.of(ListColumnMeta.of("username", "Username", ListColumnMeta.Kind.TEXT)),
                List.of("a"),
                0,
                20,
                1);
        assertThat(page.content()).containsExactly("a");
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
