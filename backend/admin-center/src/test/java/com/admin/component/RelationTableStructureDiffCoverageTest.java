package com.admin.component;

import com.admin.entity.RelationFieldDefinition;
import com.platform.common.relationtable.RelationTableStructureDiff;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard for the {@link RelationTableStructureDiff} comparison whitelist.
 *
 * <p>The DEPLOYED→UPDATED gate compares a hand-maintained list of keys. Every designer-editable
 * column on {@code rt_field_definitions} that is missing from that list is silently invisible to
 * the gate: the user edits it, the save succeeds, and the table stays "Deployed" with no
 * re-deploy prompt. That is exactly how {@code lookupConfig} and {@code sortOrder} were lost.
 *
 * <p>This test fails when someone adds a persisted column to {@link RelationFieldDefinition}
 * without either comparing it or explicitly declaring it non-structural below, so the omission
 * is a build failure rather than a silent behavioural regression.
 */
@DisplayName("RelationTableStructureDiff 比较键必须覆盖实体上所有可设计字段")
class RelationTableStructureDiffCoverageTest {

    /**
     * Entity properties deliberately excluded from the structural diff, each with the reason it
     * cannot mean "the user changed the design".
     */
    private static final Set<String> INTENTIONALLY_NOT_COMPARED = Set.of(
            "id",              // surrogate key, not design data
            "tableDefinition", // back-reference to the owner
            "refTableId",      // compared as refTableName instead, so it survives cross-env id remap
            "computedFieldJson" // compared under the payload key "computedField"
    );

    /** Diff keys that have no identically-named entity property, mapped to the property they cover. */
    private static final Set<String> KEYS_WITHOUT_DIRECT_PROPERTY = Set.of(
            "refTableName",  // covers refTableId, by name
            "computedField"  // covers computedFieldJson
    );

    @Test
    @DisplayName("实体上每个持久化字段要么参与 diff，要么显式登记为非结构字段")
    void everyPersistedEntityFieldIsEitherComparedOrExplicitlyExcluded() {
        Set<String> compared = new TreeSet<>(RelationTableStructureDiff.FIELD_KEYS);
        Set<String> uncovered = new TreeSet<>();

        for (Field f : RelationFieldDefinition.class.getDeclaredFields()) {
            if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())
                    || f.isAnnotationPresent(Transient.class)) {
                continue;
            }
            String name = f.getName();
            if (compared.contains(name) || INTENTIONALLY_NOT_COMPARED.contains(name)) {
                continue;
            }
            uncovered.add(name);
        }

        assertThat(uncovered)
                .as("这些字段既没被 RelationTableStructureDiff.FIELD_KEYS 比较，也没登记为非结构字段。"
                        + "改了它们，表结构状态不会从 Deployed 变成 Updated。"
                        + "请把字段加进 FIELD_KEYS（并同步三个 normalizer），"
                        + "或在 INTENTIONALLY_NOT_COMPARED 里写明为什么它不算设计变更。")
                .isEmpty();
    }

    @Test
    @DisplayName("LOOKUP 配置与字段顺序必须在比较键内（回归守卫）")
    void lookupConfigAndSortOrderAreCompared() {
        assertThat(RelationTableStructureDiff.FIELD_KEYS)
                .as("改 LOOKUP 配置 / 调整字段顺序都是真实的设计变更，必须触发 Deployed→Updated")
                .contains("lookupConfig", "sortOrder");
    }

    @Test
    @DisplayName("主外键相关键必须在比较键内（回归守卫）")
    void primaryAndForeignKeyPropertiesAreCompared() {
        assertThat(RelationTableStructureDiff.FIELD_KEYS)
                .as("主键/外键设置变化必须触发 Deployed→Updated")
                .contains("isPrimaryKey", "pkGenerationJson",
                        "isForeignKey", "refTableName", "refPrimaryKeyFields", "fkDisplayMode");
    }

    @Test
    @DisplayName("每个比较键都能对应到实体属性，避免比较一个不存在的键")
    void everyComparedKeyMapsToAnEntityProperty() {
        Set<String> entityProperties = new TreeSet<>();
        for (Field f : RelationFieldDefinition.class.getDeclaredFields()) {
            entityProperties.add(f.getName());
        }

        Set<String> danglingKeys = new TreeSet<>();
        for (String key : RelationTableStructureDiff.FIELD_KEYS) {
            if (!entityProperties.contains(key) && !KEYS_WITHOUT_DIRECT_PROPERTY.contains(key)) {
                danglingKeys.add(key);
            }
        }

        assertThat(danglingKeys)
                .as("这些比较键在实体上没有对应属性——normalizer 很可能永远给它们填 null，"
                        + "使该键的比较形同虚设")
                .isEmpty();
    }
}
