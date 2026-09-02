package com.platform.common.subtable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * key 规则以 FU 50005「Multi-Instance Subtask Demo」的实测绑定为样本，
 * 并与前端 {@code subTableStore.test.ts} 保持同一组断言。
 */
class SubTableStoreKeysTest {

    @Test
    void dwKeyUsesDesignerTableNameNotDisplayName() {
        // 实测 API：binding 50627 的 tableName=subtable、tableDisplayName=Participants。
        // 显示名可能跨 FU 重复，且历史上正是 "Participants"/"participants" 这类别名
        // 造成同一行的多份副本，绝不能进 key。
        assertThat(SubTableStoreKeys.dwKey("subtable")).isEqualTo("dw:subtable");
    }

    @Test
    void rtKeyGoesToItsOwnNamespace() {
        assertThat(SubTableStoreKeys.rtKey("test")).isEqualTo("rt:test");
    }

    @Test
    void platformVirtualTableNeedsNoSpecialCase() {
        // sys_users(id -1000000001) 不在 rt_table_definitions 里，但 binding 上带 tableName。
        assertThat(SubTableStoreKeys.storeKey(null, "sys_users", true)).isEqualTo("rt:sys_users");
    }

    @Test
    void dwAndRtWithSameNameAreIsolatedByPrefix() {
        // 两张定义表各自唯一，跨表无联合约束（实测 id 已撞过 2 个），故前缀是必需的。
        assertThat(SubTableStoreKeys.storeKey("test", null, false)).isEqualTo("dw:test");
        assertThat(SubTableStoreKeys.storeKey(null, "test", true)).isEqualTo("rt:test");
    }

    @Test
    void nameIsLowercasedAndTrimmedToMatchDbUniqueIndex() {
        assertThat(SubTableStoreKeys.dwKey("  SubTable ")).isEqualTo("dw:subtable");
        assertThat(SubTableStoreKeys.normalizeTableName("  Participants ")).isEqualTo("participants");
    }

    @Test
    void returnsNullWhenNameUnresolvableInsteadOfGuessing() {
        assertThat(SubTableStoreKeys.dwKey(null)).isNull();
        assertThat(SubTableStoreKeys.dwKey("   ")).isNull();
        assertThat(SubTableStoreKeys.rtKey(null)).isNull();
    }

    @Test
    void keyNeverContainsBindingId() {
        // binding 不是数据身份 —— key 里不允许出现任何 id。
        assertThat(SubTableStoreKeys.dwKey("subtable")).doesNotMatch(".*\\d.*");
    }

    @Test
    void everyBindingOfOneTableResolvesToTheSameKey() {
        // 实测：subtable 被 6 个 binding 绑定（50539/50544/50612/50617/50625/50627）。
        // 历史结构下它们各存一份、可以分叉；新规则下必须收敛到同一个 key。
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            keys.add(SubTableStoreKeys.storeKey("subtable", null, false));
        }
        assertThat(keys).containsOnly("dw:subtable");
    }

    @Test
    void isCanonicalDistinguishesNewFromLegacyKeys() {
        assertThat(SubTableStoreKeys.isCanonical("dw:subtable")).isTrue();
        assertThat(SubTableStoreKeys.isCanonical("rt:test")).isTrue();
        // 旧结构的三类 key
        assertThat(SubTableStoreKeys.isCanonical("50539")).isFalse();        // binding id
        assertThat(SubTableStoreKeys.isCanonical("subtable")).isFalse();     // 裸表名
        assertThat(SubTableStoreKeys.isCanonical("Participants")).isFalse(); // 显示名别名
    }

    @Test
    void readRowsOnlyAcceptsCanonicalKeyWithNoNameFallback() {
        List<Object> rows = List.of(Map.of("id_idwvvbz", "Test-000006"));
        Map<String, Object> store = new HashMap<>();
        store.put("dw:subtable", rows);

        assertThat(SubTableStoreKeys.readRows(store, "dw:subtable")).isSameAs(rows);
        // 旧 key 一律不认 —— 名字兜底正是旧结构分叉的来源之一
        assertThat(SubTableStoreKeys.readRows(store, "subtable")).isNull();
        assertThat(SubTableStoreKeys.readRows(store, "50627")).isNull();
        assertThat(SubTableStoreKeys.readRows(null, "dw:subtable")).isNull();
        assertThat(SubTableStoreKeys.readRows(store, null)).isNull();
    }

    @Test
    void readRowsReturnsNullWhenValueIsNotAList() {
        Map<String, Object> store = new HashMap<>();
        store.put("dw:subtable", "not-an-array");
        assertThat(SubTableStoreKeys.readRows(store, "dw:subtable")).isNull();
    }

    @Test
    void writeRowsWritesExactlyOneKeyWithNoAliasFanOut() {
        Map<String, Object> store = new HashMap<>();
        List<Object> rows = List.of(Map.of("id_idwvvbz", "Test-000005"));

        assertThat(SubTableStoreKeys.writeRows(store, "dw:subtable", rows)).isTrue();

        assertThat(store).containsOnlyKeys("dw:subtable");
        // 历史实现会同时写这些 key，正是分叉的来源
        assertThat(store).doesNotContainKey("50627");
        assertThat(store).doesNotContainKey("subtable");
        assertThat(store).doesNotContainKey("Participants");
    }

    @Test
    void writeRowsFromDifferentBindingsOverwritesTheSameKey() {
        Map<String, Object> store = new HashMap<>();
        SubTableStoreKeys.writeRows(store, SubTableStoreKeys.dwKey("subtable"), List.of(Map.of("id_idwvvbz", "A")));
        SubTableStoreKeys.writeRows(store, SubTableStoreKeys.dwKey("subtable"),
                List.of(Map.of("id_idwvvbz", "A"), Map.of("id_idwvvbz", "B")));

        assertThat(store).hasSize(1);
        assertThat(SubTableStoreKeys.readRows(store, "dw:subtable")).hasSize(2);
    }

    @Test
    void writeRowsRefusesWhenKeyUnresolvable() {
        Map<String, Object> store = new HashMap<>();
        assertThat(SubTableStoreKeys.writeRows(store, null, List.of())).isFalse();
        assertThat(store).isEmpty();
    }

    @Test
    void tableNameOfExtractsNameFromCanonicalKey() {
        assertThat(SubTableStoreKeys.tableNameOf("dw:subtable")).isEqualTo("subtable");
        assertThat(SubTableStoreKeys.tableNameOf("rt:sys_users")).isEqualTo("sys_users");
    }

    @Test
    void tableNameOfReturnsNullForLegacyKeys() {
        // 旧 key 不带表名信息，调用方需另行查表 —— 明确返回 null 而不是猜。
        assertThat(SubTableStoreKeys.tableNameOf("50627")).isNull();
        assertThat(SubTableStoreKeys.tableNameOf("Participants")).isNull();
        assertThat(SubTableStoreKeys.tableNameOf(null)).isNull();
    }
}
