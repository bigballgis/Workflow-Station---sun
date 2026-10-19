package com.portal.component;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SubTableBatchInitialMiOutcomeTest {
    private Element target() throws Exception {
        return BpmnMiXmlSupport.parseBpmnSecurely("<subProcess xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL'>"
                + "<userTask id='configured-node'/></subProcess>").getDocumentElement();
    }
    private Map<String, Object> control(String node, boolean enabled) {
        return Map.of("capabilities", Map.of("allowBatchAssign", enabled),
                "assignmentConfig", Map.of("taskDefinitionKey", node, "assigneeField", "configured_person", "roleField", "configured_role", "buField", "configured_bu"),
                "rowDesign", Map.of("binding", Map.of("tableName", "configured_lines")));
    }
    private Map<String, Object> row(String key, String person) {
        return new HashMap<>(Map.of("rowKey", new HashMap<>(Map.of("configured_pk", key)), "configured_person", person));
    }
    private List<SubTableBatchInitialMiOutcome.Expected> plan(List<?> items) throws Exception {
        var key = new SubTableBatchRoleEligibility.Target("synthetic-bu", "synthetic-role");
        return SubTableBatchInitialMiOutcome.plan(target(), Map.of("stable-control", control("configured-node", true)), items,
                Map.of(key, new SubTableBatchRoleEligibility.Pool(key, java.util.Set.of("synthetic-member"))));
    }
    @Test void capturesDifferentPeopleWithFullConfiguredKeys() throws Exception {
        var expected = plan(List.of(row("first", "synthetic-first"), row("second", "synthetic-second"))).get(0);
        assertThat(expected.personalOnly()).isTrue();
        assertThat(expected.table()).isEqualTo("configured_lines");
        assertThat(expected.people()).containsExactly(
                new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "first"), "synthetic-first"),
                new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "second"), "synthetic-second"));
    }
    @Test void doesNotGuessFromAssigneeLikeColumnNames() throws Exception {
        var item = row("first", "synthetic-person"); item.remove("configured_person"); item.put("assignee", "synthetic-person");
        var expected = plan(List.of(item)).get(0);
        assertThat(expected.personalOnly()).isFalse();
        assertThat(expected.people()).isEmpty();
    }
    @Test void mixedRowsCannotBeDeclaredAllPersonal() throws Exception {
        var role = row("second", "unused"); role.remove("configured_person"); role.put("configured_role", "synthetic-role"); role.put("configured_bu", "synthetic-bu");
        var expected = plan(List.of(row("first", "synthetic-first"), role)).get(0);
        assertThat(expected.personalOnly()).isFalse();
        assertThat(expected.rowKeys()).hasSize(2);
        assertThat(expected.people()).hasSize(1);
        assertThat(expected.roles()).containsExactly(new SubTableBatchInitialMiOutcome.RoleTarget(Map.of("configured_pk", "second"),
                "synthetic-bu", "synthetic-role", java.util.Set.of("synthetic-member")));
    }
    @Test void disabledControlCreatesNoPlan() throws Exception {
        assertThat(SubTableBatchInitialMiOutcome.plan(target(), Map.of("stable", control("configured-node", false)),
                List.of(row("first", "synthetic-person")), Map.of())).isEmpty();
    }
    @Test void anotherNodeCreatesNoPlan() throws Exception {
        assertThat(SubTableBatchInitialMiOutcome.plan(target(), Map.of("stable", control("other-node", true)),
                List.of(row("first", "synthetic-person")), Map.of())).isEmpty();
    }
    @Test void emptyRowsCreateNoPlan() throws Exception {
        assertThat(plan(List.of())).isEmpty();
    }
    @Test void planCopiesRowsAndKeysInsteadOfRetainingMutableDrafts() throws Exception {
        var item = row("first", "synthetic-first");
        var expected = plan(List.of(item)).get(0);
        item.put("configured_person", "synthetic-later"); item.put("rowKey", Map.of("configured_pk", "later"));
        assertThat(expected.people()).containsExactly(new SubTableBatchInitialMiOutcome.PersonalTarget(
                Map.of("configured_pk", "first"), "synthetic-first"));
        assertThatThrownBy(() -> expected.rowKeys().get(0).put("configured_pk", "later")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> expected.people().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void equivalentControlsDoNotDuplicateTheExpectedRowSet() throws Exception {
        var controls = Map.<String, Object>of("first", control("configured-node", true), "second", control("configured-node", true));
        assertThat(SubTableBatchInitialMiOutcome.plan(target(), controls, List.of(row("first", "synthetic-person")), Map.of())).hasSize(1);
    }
}
