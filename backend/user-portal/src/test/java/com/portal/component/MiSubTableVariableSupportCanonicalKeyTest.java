package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code canonicalizeSubTablesAliasKeys} 与规范 key（{@code dw:<name>} / {@code rt:<name>}）的交互。
 *
 * <p>回归：该方法原本的规则是「有数字 key 就只保留数字 key」。规范 key 不是数字，因此只要有任何一个
 * 遗留的 bindingId key 共存（旧实例 hydrate、TaskInfo 带回旧结构），{@code dw:subtable} 就会被
 * <b>静默删除</b> —— 子表数据整段丢失，不是回滚而是消失。
 */
class MiSubTableVariableSupportCanonicalKeyTest {

    private static Map<String, Object> rows(String pk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id_idwvvbz", pk);
        return m;
    }

    @Test
    void canonicalKeySurvivesWhenLegacyNumericKeyCoexists() {
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("dw:subtable", List.of(rows("Test-000005"), rows("Test-000006")));
        subTables.put("50627", List.of(rows("Test-000006")));   // 遗留 bindingId key

        Map<String, Object> out = MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(subTables);

        assertThat(out).containsOnlyKeys("dw:subtable");
        assertThat((List<?>) out.get("dw:subtable")).hasSize(2);
    }

    @Test
    void canonicalKeysWinOverEveryLegacyAlias() {
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("50539", List.of(rows("Test-000006")));
        subTables.put("subtable", List.of(rows("Test-000006")));
        subTables.put("Participants", List.of(rows("Test-000006")));
        subTables.put("dw:subtable", List.of(rows("Test-000006")));
        subTables.put("rt:test", List.of());

        Map<String, Object> out = MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(subTables);

        assertThat(out).containsOnlyKeys("dw:subtable", "rt:test");
    }

    @Test
    void legacyBehaviourUnchangedWhenNoCanonicalKeyPresent() {
        // 没有规范 key 时保持原规则：有数字 key 就只留数字 key。
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("50627", List.of(rows("Test-000006")));
        subTables.put("subtable", List.of(rows("Test-000006")));

        Map<String, Object> out = MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(subTables);

        assertThat(out).containsOnlyKeys("50627");
    }

    @Test
    void mapWithNeitherCanonicalNorNumericKeysIsLeftAsIs() {
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("subtable", List.of(rows("Test-000006")));

        Map<String, Object> out = MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(subTables);

        assertThat(out).containsOnlyKeys("subtable");
    }

    @Test
    void nestedRowSubTablesAreCanonicalizedRecursively() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("dw:people", List.of(rows("p-1")));
        nested.put("50547", List.of(rows("p-1")));

        Map<String, Object> parentRow = new LinkedHashMap<>(rows("Test-000006"));
        parentRow.put("__subTables__", nested);

        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("dw:subtable", List.of(parentRow));

        MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(subTables);

        @SuppressWarnings("unchecked")
        Map<String, Object> nestedOut = (Map<String, Object>) parentRow.get("__subTables__");
        assertThat(nestedOut).containsOnlyKeys("dw:people");
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        assertThat(MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(null)).isNull();
        Map<String, Object> empty = new LinkedHashMap<>();
        assertThat(MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(empty)).isEmpty();
    }
}
