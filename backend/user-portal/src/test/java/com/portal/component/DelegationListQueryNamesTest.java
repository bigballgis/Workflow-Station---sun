package com.portal.component;

import com.portal.dto.DelegationListQueryRequest;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The Delegations page names people, read with the page in the same query (no lookup per row). */
class DelegationListQueryNamesTest {

    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder().generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2).build();
    private DelegationListQueryComponent component;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(64), username VARCHAR(100), full_name VARCHAR(100),"
                + " display_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE up_delegation_rule(id BIGINT, delegator_id VARCHAR(64), delegate_id VARCHAR(64),"
                + " delegation_type VARCHAR(20), start_time TIMESTAMP, end_time TIMESTAMP, status VARCHAR(20),"
                + " reason VARCHAR(200), created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE up_delegation_audit(id BIGINT, delegator_id VARCHAR(64), delegate_id VARCHAR(64),"
                + " task_id VARCHAR(64), operation_type VARCHAR(50), operation_result VARCHAR(50),"
                + " operation_detail VARCHAR(200), ip_address VARCHAR(50), user_agent VARCHAR(200), created_at TIMESTAMP)");
        jdbc.update("INSERT INTO sys_users VALUES ('user-e2e-zhangwei', 'e2e_zhangwei', 'Zhang Wei', NULL)");
        jdbc.update("INSERT INTO sys_users VALUES ('user-e2e-wangfang', 'e2e_wangfang', ' ', 'Fang')");
        jdbc.update("INSERT INTO sys_users VALUES ('me', 'me_user', NULL, NULL)");
        jdbc.update("INSERT INTO up_delegation_rule VALUES (1, 'me', 'user-e2e-zhangwei', 'ALL', NULL, NULL, 'ACTIVE',"
                + " 'leave', TIMESTAMP '2026-11-01 09:00:00', NULL)");
        jdbc.update("INSERT INTO up_delegation_rule VALUES (2, 'me', 'gone-user', 'ALL', NULL, NULL, 'ACTIVE',"
                + " 'old', TIMESTAMP '2026-10-01 09:00:00', NULL)");
        jdbc.update("INSERT INTO up_delegation_audit VALUES (1, 'me', 'user-e2e-wangfang', 't1', 'DELEGATE_TASK',"
                + " 'SUCCESS', NULL, NULL, NULL, TIMESTAMP '2026-11-02 09:00:00')");
        jdbc.update("INSERT INTO up_delegation_audit VALUES (2, 'me', 'BU_ROLE:FIN/CLERK', 't2', 'DELEGATE_TASK',"
                + " 'SUCCESS', NULL, NULL, NULL, TIMESTAMP '2026-11-01 09:00:00')");
        component = new DelegationListQueryComponent(jdbc, new EffectiveDelegationRules(
                org.mockito.Mockito.mock(com.portal.repository.DelegationRuleRepository.class),
                com.portal.testsupport.PortalEntryStubs.everyone()));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void aRuleNamesItsDelegateAndAnUnknownOneStaysUnnamed() {
        List<DelegationRule> rules = component.queryRules("me", request()).content();

        assertThat(rules).extracting(DelegationRule::getDelegateId).containsExactly("user-e2e-zhangwei", "gone-user");
        assertThat(rules).extracting(DelegationRule::getDelegateName).containsExactly("Zhang Wei", null);
    }

    @Test
    void anAuditRowNamesBothPartiesFallingBackFromFullToDisplayName() {
        List<DelegationAudit> audit = component.queryAudit("me", request()).content();

        assertThat(audit.get(0).getDelegatorName()).isEqualTo("me_user");
        assertThat(audit.get(0).getDelegateName()).isEqualTo("Fang");
        // A delegation to a BU + Role is not a person: the stored target is shown as it is.
        assertThat(audit.get(1).getDelegateName()).isNull();
    }

    private static DelegationListQueryRequest request() {
        return new DelegationListQueryRequest(0, 20, List.of(), null, null);
    }
}
