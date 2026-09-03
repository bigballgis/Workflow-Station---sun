package com.portal.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalMainTableViewRowKeyTest {

    @Test
    void subRowKeyMatchesInstanceAndIdentityValueNotTheKeyword() {
        SqlFragment sql = PortalMainTableViewRowKeys.exactMatch(
                "944b041c-a781-11f1-bb05-0e9e01a266af|row_id=ATM-DC-PW-TRANS-000030",
                true);

        assertThat(sql.sql()).isEqualTo(" AND pi.id = ? AND pi.row_identity = ?");
        assertThat(sql.params()).containsExactly(
                "944b041c-a781-11f1-bb05-0e9e01a266af",
                "ATM-DC-PW-TRANS-000030");
    }

    @Test
    void mainRowKeyMatchesTheProcessInstance() {
        SqlFragment sql = PortalMainTableViewRowKeys.exactMatch(
                "944b041c-a781-11f1-bb05-0e9e01a266af", false);

        assertThat(sql.sql()).isEqualTo(" AND pi.id = ?");
        assertThat(sql.params()).containsExactly("944b041c-a781-11f1-bb05-0e9e01a266af");
    }

    @Test
    void blankRowKeyDoesNotConstrainTheQuery() {
        assertThat(PortalMainTableViewRowKeys.exactMatch("  ", true).isEmpty()).isTrue();
    }

    @Test
    void aSubRowKeyWithoutIdentityIsRefusedRatherThanMatchingEveryRowOfTheInstance() {
        assertThatThrownBy(() -> PortalMainTableViewRowKeys.exactMatch(
                "944b041c-a781-11f1-bb05-0e9e01a266af", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row identity");
    }
}
