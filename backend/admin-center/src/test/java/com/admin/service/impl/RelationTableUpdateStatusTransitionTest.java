package com.admin.service.impl;

import com.admin.component.RelationTableFieldMapper;
import com.admin.component.RelationTableFunctionUnitResolver;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableFunctionUnitRepository;
import com.admin.service.RelationComputedFieldValidator;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Deployed→Updated 状态门测试。
 *
 * <p>用户改了表结构（尤其是 LOOKUP 配置、主键/外键设置），状态却仍显示 Deployed —— 根因是
 * {@code RelationTableStructureDiff} 的比较键白名单漏掉了字段。这些测试锁定"真实改动必须翻状态、
 * 原样保存必须不翻状态"两侧行为。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Relation Table 编辑 → Deployed/Updated 状态转换")
class RelationTableUpdateStatusTransitionTest {

    @Mock private RelationTableDefinitionRepository tableDefinitionRepository;
    @Mock private RelationFieldDefinitionRepository fieldDefinitionRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private RelationTableStructureServiceImpl service;

    @BeforeEach
    void setUp() {
        RelationTableFunctionUnitResolver resolver = new RelationTableFunctionUnitResolver(
                relationTableFunctionUnitRepository, functionUnitRepository);
        // Real mapper: fromEntities() is a pure normalizer; a Mockito mock would return null and NPE
        // inside RelationTableStructureDiff.unchanged().
        RelationTableFieldMapper mapper = new RelationTableFieldMapper(tableDefinitionRepository);
        service = new RelationTableStructureServiceImpl(
                tableDefinitionRepository, fieldDefinitionRepository, functionUnitRepository,
                relationTableFunctionUnitRepository, resolver,
                new RelationComputedFieldValidator(), mapper, jdbcTemplate);
    }

    // ==================== Fixtures ====================

    private static Map<String, Object> lookupConfig(String refTableName, String... searchFields) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("refTableName", refTableName);
        cfg.put("searchFields", List.of(searchFields));
        return cfg;
    }

    /** A deployed table with one plain column and one configured LOOKUP column. */
    private RelationTableDefinition deployedTable() {
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(1L)
                .tableName("rt_demo")
                .displayName("Demo")
                .description("demo table")
                .status(RelationTableStatus.DEPLOYED)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(3)
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        RelationFieldDefinition code = RelationFieldDefinition.builder()
                .id(10L).tableDefinition(table)
                .fieldName("code").dataType(RelationDataType.VARCHAR).length(64)
                .nullable(false).isPrimaryKey(true).isForeignKey(false)
                .fkDisplayMode("readonly").isComputed(false).sortOrder(0)
                .build();

        RelationFieldDefinition owner = RelationFieldDefinition.builder()
                .id(11L).tableDefinition(table)
                .fieldName("owner").dataType(RelationDataType.LOOKUP)
                .nullable(true).isPrimaryKey(false).isForeignKey(false)
                .fkDisplayMode("readonly").isComputed(false).sortOrder(1)
                .lookupConfig(lookupConfig("rt_users", "user_id"))
                .build();

        table.getFieldDefinitions().add(code);
        table.getFieldDefinitions().add(owner);
        return table;
    }

    /** The request that re-submits the fixture exactly as stored (an unchanged save). */
    private UpdateRelationTableRequest unchangedRequest() {
        return UpdateRelationTableRequest.builder()
                .displayName("Demo")
                .description("demo table")
                .fieldDefinitions(new ArrayList<>(List.of(
                        fieldRequest(10L, "code", RelationDataType.VARCHAR, 0)
                                .length(64).nullable(false).isPrimaryKey(true).isForeignKey(false)
                                .fkDisplayMode("readonly").build(),
                        fieldRequest(11L, "owner", RelationDataType.LOOKUP, 1)
                                .nullable(true).isPrimaryKey(false).isForeignKey(false)
                                .fkDisplayMode("readonly")
                                .lookupConfig(lookupConfig("rt_users", "user_id")).build())))
                .build();
    }

    private static UpdateRelationTableRequest.FieldDefinitionRequest.FieldDefinitionRequestBuilder fieldRequest(
            Long id, String name, RelationDataType type, int sortOrder) {
        return UpdateRelationTableRequest.FieldDefinitionRequest.builder()
                .id(id).fieldName(name).dataType(type).sortOrder(sortOrder);
    }

    private RelationTableResponse update(RelationTableDefinition stored, UpdateRelationTableRequest request) {
        when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return service.updateTable(1L, request);
    }

    // ==================== The reported bug ====================

    @Nested
    @DisplayName("LOOKUP 字段配置改动")
    class LookupConfigChanges {

        @Test
        @DisplayName("改 LOOKUP 引用表 → 状态变 UPDATED")
        void changingLookupRefTableFlipsToUpdated() {
            UpdateRelationTableRequest request = unchangedRequest();
            request.getFieldDefinitions().get(1).setLookupConfig(lookupConfig("rt_departments", "user_id"));

            assertThat(update(deployedTable(), request).getStatus())
                    .as("LOOKUP 引用表变了，是真实的设计改动，必须提示重新部署")
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("改 LOOKUP 搜索列 → 状态变 UPDATED")
        void changingLookupSearchFieldsFlipsToUpdated() {
            UpdateRelationTableRequest request = unchangedRequest();
            request.getFieldDefinitions().get(1)
                    .setLookupConfig(lookupConfig("rt_users", "user_id", "user_name"));

            assertThat(update(deployedTable(), request).getStatus())
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("LOOKUP 配置原样提交 → 状态保持 DEPLOYED")
        void resubmittingIdenticalLookupConfigKeepsDeployed() {
            assertThat(update(deployedTable(), unchangedRequest()).getStatus())
                    .as("打开又原样保存不该把已部署表打上待重新部署标记")
                    .isEqualTo(RelationTableStatus.DEPLOYED);
        }
    }

    @Nested
    @DisplayName("主键 / 外键配置改动")
    class KeyChanges {

        @Test
        @DisplayName("取消主键 → 状态变 UPDATED")
        void clearingPrimaryKeyFlipsToUpdated() {
            UpdateRelationTableRequest request = unchangedRequest();
            request.getFieldDefinitions().get(0).setIsPrimaryKey(false);

            assertThat(update(deployedTable(), request).getStatus())
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("把字段设为外键 → 状态变 UPDATED")
        void settingForeignKeyFlipsToUpdated() {
            UpdateRelationTableRequest request = unchangedRequest();
            UpdateRelationTableRequest.FieldDefinitionRequest owner = request.getFieldDefinitions().get(1);
            owner.setIsForeignKey(true);
            owner.setRefPrimaryKeyFields(List.of("user_id"));
            owner.setFkDisplayMode("editable");

            assertThat(update(deployedTable(), request).getStatus())
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("改外键显示模式 → 状态变 UPDATED")
        void changingFkDisplayModeFlipsToUpdated() {
            RelationTableDefinition stored = deployedTable();
            stored.getFieldDefinitions().get(1).setIsForeignKey(true);
            stored.getFieldDefinitions().get(1).setFkDisplayMode("readonly");

            UpdateRelationTableRequest request = unchangedRequest();
            UpdateRelationTableRequest.FieldDefinitionRequest owner = request.getFieldDefinitions().get(1);
            owner.setIsForeignKey(true);
            owner.setFkDisplayMode("editable");

            assertThat(update(stored, request).getStatus())
                    .isEqualTo(RelationTableStatus.UPDATED);
        }
    }

    @Nested
    @DisplayName("其他结构改动")
    class OtherStructuralChanges {

        @Test
        @DisplayName("调整字段顺序 → 状态变 UPDATED")
        void reorderingFieldsFlipsToUpdated() {
            UpdateRelationTableRequest request = unchangedRequest();
            request.getFieldDefinitions().get(0).setSortOrder(1);
            request.getFieldDefinitions().get(1).setSortOrder(0);

            assertThat(update(deployedTable(), request).getStatus())
                    .as("字段顺序是设计数据，会进 deploy 快照，改了必须能看出来")
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("改表名 → 状态变 UPDATED")
        void renamingTableFlipsToUpdated() {
            when(tableDefinitionRepository.existsByTableNameIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
            when(jdbcTemplate.queryForObject(any(String.class), any(Class.class), any(Object[].class)))
                    .thenReturn(0);

            UpdateRelationTableRequest request = unchangedRequest();
            request.setTableName("rt_demo_renamed");

            assertThat(update(deployedTable(), request).getStatus())
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("新增字段 → 状态变 UPDATED")
        void addingFieldFlipsToUpdated() {
            UpdateRelationTableRequest request = unchangedRequest();
            request.getFieldDefinitions().add(
                    fieldRequest(null, "remark", RelationDataType.VARCHAR, 2).length(255).nullable(true).build());

            assertThat(update(deployedTable(), request).getStatus())
                    .isEqualTo(RelationTableStatus.UPDATED);
        }

    }

    @Nested
    @DisplayName("非结构改动不应翻状态")
    class NonStructuralChanges {

        @Test
        @DisplayName("INIT 表编辑后仍是 INIT")
        void editingInitTableStaysInit() {
            RelationTableDefinition stored = deployedTable();
            stored.setStatus(RelationTableStatus.INIT);

            UpdateRelationTableRequest request = unchangedRequest();
            request.setDisplayName("Renamed Demo");

            assertThat(update(stored, request).getStatus())
                    .as("从未部署过的表不该出现 Updated")
                    .isEqualTo(RelationTableStatus.INIT);
        }
    }
}
