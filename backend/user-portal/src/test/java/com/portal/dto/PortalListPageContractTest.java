package com.portal.dto;

import com.platform.common.list.ListColumnMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortalListPageContractTest {

    @Test
    void pageHasNoGroupsField() {
        assertThat(PortalListPage.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("columns", "content", "page", "size", "totalElements");
    }

    @Test
    void representativeQueryRecordsHaveNoGroupBy() {
        for (Class<?> type : List.of(
                TodoTaskQueryRequest.class,
                CompletedTaskQueryRequest.class,
                MyApplicationQueryRequest.class,
                MainTableViewQueryRequest.class,
                RelationTableQueryRequest.class,
                DelegationListQueryRequest.class,
                PermissionListQueryRequest.class,
                UserPortalAuditListQueryRequest.class)) {
            assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
                    .as(type.getSimpleName())
                    .doesNotContain("groupBy", "groups", "groupable");
        }
    }

    @Test
    void pageCtorDoesNotAcceptGroups() {
        PortalListPage<String> page = new PortalListPage<>(
                List.of(ListColumnMeta.of("title", "Title", ListColumnMeta.Kind.TEXT)),
                List.of("a"),
                0,
                20,
                1);
        assertThat(page.content()).containsExactly("a");
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
