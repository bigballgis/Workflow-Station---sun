package com.portal.util;

import com.portal.util.PortalMainTableViewSubStoreKeys.SliceKeys;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortalMainTableViewSubStoreKeyTest {

    @Test
    void atmTransactionMapsToCanonicalDwKeyNotBindingId() {
        SliceKeys keys = PortalMainTableViewSubStoreKeys.forSubView(
                "ATM_Transaction", List.of(1152L, 1167L, 2936L));

        assertThat(keys.canonicalStoreKey()).isEqualTo("dw:atm_transaction");
        assertThat(keys.legacyBindingKeys()).containsExactly("1152", "1167", "2936");
        assertThat(keys.canonicalStoreKey()).doesNotContain("1152");
    }

    @Test
    void creditCardTransactionUsesTheDesignerTableName() {
        SliceKeys keys = PortalMainTableViewSubStoreKeys.forSubView(
                "credit_card_transaction", List.of(1152L));

        assertThat(keys.canonicalStoreKey()).isEqualTo("dw:credit_card_transaction");
    }

    @Test
    void missingTableNameLeavesOnlyLegacyBindingIds() {
        SliceKeys keys = PortalMainTableViewSubStoreKeys.forSubView("  ", List.of(3843L));

        assertThat(keys.canonicalStoreKey()).isNull();
        assertThat(keys.legacyBindingKeys()).containsExactly("3843");
    }
}
