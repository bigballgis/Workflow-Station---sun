package com.admin.repository;

import com.admin.entity.ActionDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 首个跑在真实 PostgreSQL 上的持久层测试。
 *
 * 背景：三个模块的 pom 早就声明了 testcontainers-postgresql，但在此之前
 * **没有任何一个测试文件引用过 @Testcontainers / @Container** —— 581 个后端测试
 * 文件里 168 个是 Mockito 类，主要在验证"代码调用了它被写成要调用的协作者"。
 * 结果是 JPA 映射、FK 级联、唯一约束、JSONB 读写、原生 SQL 全部零覆盖：
 * 改坏一个列映射，整套测试照样全绿。
 *
 * 这个类专门覆盖 mock 测不出来的东西：
 *   1. 实体列映射与真实 DDL 是否一致（写进去再读出来）
 *   2. JSONB 往返（@JdbcTypeCode(SqlTypes.JSON) + Map，mock 永远测不到序列化）
 *   3. uk_sys_action_name_fu 唯一约束是否真的生效
 *   4. FK ON DELETE CASCADE 是否真的级联
 *
 * 选 sys_action_definitions 作为切入点是有意的：它是架构审计里
 * **重复 @Entity 的五张表之一**（admin-center 与 user-portal 各定义了一份），
 * 正是最需要真实数据库来锁住行为的地方。
 *
 * 命名用 *IT 而非 *Test：surefire 的 includes 只收 *Test/*Tests/*Properties，
 * 所以默认的 `mvn test` **不会**跑这个类，需要 docker 的测试不会拖累无 docker 的环境。
 * 显式运行：mvn test -Dtest=ActionDefinitionRepositoryContainerIT -DfailIfNoTests=false
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ActionDefinition 持久层 — 真实 PostgreSQL")
class ActionDefinitionRepositoryContainerIT {

    /**
     * 本地最小配置类。两层收窄都是必要的：
     *
     * 1. 不让 @DataJpaTest 回退去扫 AdminCenterApplication —— 那会把 AdminAuditAspect
     *    等整个应用 bean 图拉进来，而 JPA 切片里没有 ObjectMapper，直接
     *    UnsatisfiedDependencyException。
     * 2. repository 扫描用 includeFilters 精确到本类，而不是整包 —— 同包下的
     *    UserBusinessUnitRoleRepository 等引用的是 platform-security 的实体，
     *    本切片只注册了 com.admin.entity，会报 "Not a managed type"。
     *
     * 这层"只装被测这一条链路"的克制，正是切片测试该有的样子。
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ActionDefinition.class)
    @EnableJpaRepositories(
            basePackageClasses = ActionDefinitionRepository.class,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = ActionDefinitionRepository.class))
    static class PersistenceOnlyConfig {
    }

    @Container
    @SuppressWarnings("resource") // 容器生命周期由 Testcontainers 的 JUnit 扩展管理
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("workflow_platform_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // 建表交给下面的 @Sql 式初始化，不让 Hibernate 按实体反推 DDL ——
        // 否则测的就是"实体和它自己一致"，恰好绕开了本类要发现的漂移。
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/action-definitions-schema.sql");
    }

    @Autowired
    private ActionDefinitionRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private static final String FU_ID = "fu-container-it";

    @BeforeEach
    void seedFunctionUnit() {
        repository.deleteAll();
        entityManager.flush();
        // FK 父行：sys_action_definitions.function_unit_id 指向 sys_function_units(id)
        entityManager.createNativeQuery(
                        "INSERT INTO sys_function_units (id, code, name, enabled, status) "
                                + "VALUES (:id, :code, :name, true, 'ACTIVE') "
                                + "ON CONFLICT (id) DO NOTHING")
                .setParameter("id", FU_ID)
                .setParameter("code", "FU_CONTAINER_IT")
                .setParameter("name", "Container IT fixture")
                .executeUpdate();
        entityManager.flush();
    }

    private ActionDefinition newAction(String name, Map<String, Object> config) {
        return ActionDefinition.builder()
                .functionUnitId(FU_ID)
                .actionName(name)
                .actionType("BUTTON")
                .description("container it")
                .configJson(config)
                .icon("Edit")
                .buttonColor("#1F5C4A")
                .isDefault(false)
                .build();
    }

    @Test
    @DisplayName("实体列映射与真实 DDL 一致：写入后可原样读回")
    void persistsAndReadsBackEveryMappedColumn() {
        ActionDefinition saved = repository.saveAndFlush(newAction("approve", Map.of("k", "v")));
        entityManager.clear(); // 清一级缓存，强制真正从库里读

        ActionDefinition loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getFunctionUnitId()).isEqualTo(FU_ID);
        assertThat(loaded.getActionName()).isEqualTo("approve");
        assertThat(loaded.getActionType()).isEqualTo("BUTTON");
        assertThat(loaded.getDescription()).isEqualTo("container it");
        assertThat(loaded.getIcon()).isEqualTo("Edit");
        assertThat(loaded.getButtonColor()).isEqualTo("#1F5C4A");
        assertThat(loaded.getIsDefault()).isFalse();
        // @PrePersist 应当真的落库，而不是只在内存里赋值
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("JSONB 往返：嵌套结构不丢失（mock 测不到序列化）")
    void roundTripsNestedJsonbConfig() {
        Map<String, Object> config = Map.of(
                "label", "提交",
                "enabled", true,
                "order", 3,
                "nested", Map.of("target", "task", "confirm", false));

        ActionDefinition saved = repository.saveAndFlush(newAction("submit", config));
        entityManager.clear();

        Map<String, Object> loaded = repository.findById(saved.getId()).orElseThrow().getConfigJson();

        assertThat(loaded).containsEntry("label", "提交");
        assertThat(loaded).containsEntry("enabled", true);
        assertThat(loaded).containsEntry("order", 3);
        assertThat(loaded.get("nested"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("target", "task")
                .containsEntry("confirm", false);
    }

    @Test
    @DisplayName("uk_sys_action_name_fu：同一 FU 下动作名唯一")
    void enforcesUniqueActionNamePerFunctionUnit() {
        repository.saveAndFlush(newAction("duplicate", Map.of()));

        assertThatThrownBy(() -> repository.saveAndFlush(newAction("duplicate", Map.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByFunctionUnitId 只返回本 FU 的动作")
    void findsOnlyOwnFunctionUnitActions() {
        repository.saveAndFlush(newAction("a", Map.of()));
        repository.saveAndFlush(newAction("b", Map.of()));
        entityManager.clear();

        List<ActionDefinition> found = repository.findByFunctionUnitId(FU_ID);
        assertThat(found).hasSize(2).extracting(ActionDefinition::getActionName)
                .containsExactlyInAnyOrder("a", "b");

        assertThat(repository.findByFunctionUnitId("fu-does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("FK ON DELETE CASCADE：删除 FU 一并删掉其动作")
    void cascadesDeleteFromFunctionUnit() {
        repository.saveAndFlush(newAction("cascade-me", Map.of()));
        assertThat(repository.findByFunctionUnitId(FU_ID)).hasSize(1);

        entityManager.createNativeQuery("DELETE FROM sys_function_units WHERE id = :id")
                .setParameter("id", FU_ID)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByFunctionUnitId(FU_ID)).isEmpty();
    }

    @Test
    @DisplayName("FK 约束真的生效：不存在的 FU 无法插入")
    void rejectsUnknownFunctionUnit() {
        ActionDefinition orphan = ActionDefinition.builder()
                .functionUnitId("fu-never-created")
                .actionName("orphan")
                .actionType("BUTTON")
                .build();

        assertThatThrownBy(() -> repository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
