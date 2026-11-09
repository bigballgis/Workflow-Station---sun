package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Status filter of LDAP Sync Records offers every status a sync run is written with, PARTIAL included. */
class LdapSyncAuditColumnSpecTest {

    @Test
    void shouldOfferEveryRunStatus_includingPartlySucceeded() {
        ListColumnMeta status = LdapSyncAuditColumnSpec.columns().stream()
                .filter(column -> column.field().equals("status"))
                .findFirst()
                .orElseThrow();

        assertThat(status.options()).extracting(ListColumnMeta.Option::value)
                .containsExactly("RUNNING", "SUCCESS", "PARTIAL", "FAILED");
        assertThat(status.options()).extracting(ListColumnMeta.Option::label)
                .contains("ldapSync.statusPartial");
    }

    @Test
    void shouldShowFailedAndSkippedItemsAsSeparateColumns() {
        assertThat(LdapSyncAuditColumnSpec.columns()).extracting(ListColumnMeta::field)
                .containsSubsequence("failed", "skippedMissingKey");
        assertThat(LdapSyncAuditColumnSpec.sqlFor("skippedMissingKey")).isEqualTo("a.skipped_missing_key::text");
    }

    @Test
    void shouldRejectAnUnknownColumn() {
        assertThatThrownBy(() -> LdapSyncAuditColumnSpec.sqlFor("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
