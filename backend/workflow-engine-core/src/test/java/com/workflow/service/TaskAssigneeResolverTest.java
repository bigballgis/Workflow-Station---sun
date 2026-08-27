package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssigneeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAssigneeResolver Tests")
class TaskAssigneeResolverTest {

    @Mock
    private AdminCenterClient adminCenterClient;

    @InjectMocks
    private TaskAssigneeResolver resolver;

    private static final String INITIATOR_ID = "initiator-001";
    private static final String ANCHOR_USER_ID = "anchor-user-001";
    private static final String ROLE_ID = "role-001";
    private static final String BU_ID = "bu-001";
    private static final String FUNCTION_MANAGER_ID = "manager-func-001";
    private static final String ENTITY_MANAGER_ID = "manager-entity-001";

    @Nested
    @DisplayName("Direct assignment")
    class DirectTests {

        @Test
        void functionalManagerUsesAnchorUser() {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("functionManagerId", FUNCTION_MANAGER_ID);
            when(adminCenterClient.getUserInfo(ANCHOR_USER_ID)).thenReturn(userInfo);

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.FUNCTIONAL_MANAGER, null, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getAssignee()).isEqualTo(FUNCTION_MANAGER_ID);
            assertThat(result.isRequiresClaim()).isFalse();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        void entityManagerUsesAnchorUser() {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("entityManagerId", ENTITY_MANAGER_ID);
            when(adminCenterClient.getUserInfo(ANCHOR_USER_ID)).thenReturn(userInfo);

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.ENTITY_MANAGER, null, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getAssignee()).isEqualTo(ENTITY_MANAGER_ID);
            assertThat(result.isRequiresClaim()).isFalse();
        }

        @Test
        void processInitiator() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.PROCESS_INITIATOR, null, null, INITIATOR_ID, null);

            assertThat(result.getAssignee()).isEqualTo(INITIATOR_ID);
            assertThat(result.isRequiresClaim()).isFalse();
            verifyNoInteractions(adminCenterClient);
        }

        @ParameterizedTest
        @EnumSource(value = AssigneeType.class, names = {"FUNCTIONAL_MANAGER", "ENTITY_MANAGER"})
        void managersNeedAnchor(AssigneeType type) {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(type, null, null, INITIATOR_ID, null);
            assertThat(result.getErrorMessage()).contains("anchor user ID");
        }
    }

    @Nested
    @DisplayName("Hierarchy role (BU chain union)")
    class HierarchyTests {

        @Test
        void unionMultipleCandidatesRequiresClaim() {
            when(adminCenterClient.getUserBusinessUnitId(eq(ANCHOR_USER_ID), isNull())).thenReturn(BU_ID);
            when(adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("u1", "u2"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.HIERARCHY_ROLE, ROLE_ID, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getCandidateUsers()).containsExactly("u1", "u2");
            assertThat(result.isRequiresClaim()).isTrue();
            assertThat(result.getAssignee()).isNull();
        }

        @Test
        void singleCandidateAutoAssigns() {
            when(adminCenterClient.getUserBusinessUnitId(eq(ANCHOR_USER_ID), isNull())).thenReturn(BU_ID);
            when(adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(BU_ID, ROLE_ID))
                    .thenReturn(List.of("only-one"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.HIERARCHY_ROLE, ROLE_ID, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getAssignee()).isEqualTo("only-one");
            assertThat(result.isRequiresClaim()).isFalse();
        }

        @Test
        void zeroCandidatesError() {
            when(adminCenterClient.getUserBusinessUnitId(eq(ANCHOR_USER_ID), isNull())).thenReturn(BU_ID);
            when(adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(BU_ID, ROLE_ID))
                    .thenReturn(Collections.emptyList());

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.HIERARCHY_ROLE, ROLE_ID, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getErrorMessage()).contains("No users");
        }

        @Test
        void multiRoleUnionCandidates() {
            String role2 = "role-002";
            when(adminCenterClient.getUserBusinessUnitId(eq(ANCHOR_USER_ID), isNull())).thenReturn(BU_ID);
            when(adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(BU_ID, ROLE_ID))
                    .thenReturn(List.of("u1"));
            when(adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(BU_ID, role2))
                    .thenReturn(List.of("u2"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolveWithRoleIds(
                    "INITIATOR_BU_ROLE", List.of(ROLE_ID, role2), null, INITIATOR_ID, ANCHOR_USER_ID, null);

            assertThat(result.getCandidateUsers()).containsExactlyInAnyOrder("u1", "u2");
            assertThat(result.isRequiresClaim()).isTrue();
        }
    }

    @Nested
    @DisplayName("BU role")
    class BuRoleTests {

        @Test
        void twoCandidatesPool() {
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(true);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("a", "b"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.BU_ROLE, ROLE_ID, BU_ID, INITIATOR_ID, null);

            assertThat(result.getCandidateUsers()).containsExactly("a", "b");
            assertThat(result.isRequiresClaim()).isTrue();
        }

        @Test
        void multiRoleUnionCandidates() {
            String role2 = "role-002";
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(true);
            when(adminCenterClient.isEligibleRole(BU_ID, role2)).thenReturn(true);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(List.of("a", "b"));
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, role2))
                    .thenReturn(List.of("b", "c"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolveWithRoleIds(
                    "BU_ROLE", List.of(ROLE_ID, role2), BU_ID, INITIATOR_ID, null, null);

            assertThat(result.getCandidateUsers()).containsExactly("a", "b", "c");
            assertThat(result.isRequiresClaim()).isTrue();
        }

        /**
         * Unlike HIERARCHY_ROLE, a one-person BU role is still a claim pool: the portal shows it under
         * "Tasks to Claim" and the member must Hold it, so a second member added to the role later cannot
         * walk into a form someone is already editing.
         */
        @Test
        void singleCandidateStillRequiresClaim() {
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(true);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(List.of("only-one"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.BU_ROLE, ROLE_ID, BU_ID, INITIATOR_ID, null);

            assertThat(result.getAssignee()).isNull();
            assertThat(result.getCandidateUsers()).containsExactly("only-one");
            assertThat(result.isRequiresClaim()).isTrue();
        }

        @Test
        void notEligible() {
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(false);

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.BU_ROLE, ROLE_ID, BU_ID, INITIATOR_ID, null);

            assertThat(result.getErrorMessage()).contains("not eligible");
        }
    }

    @Nested
    @DisplayName("String fromCode resolve")
    class StringResolveTests {

        @Test
        void legacyFixedBuRoleCode() {
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(true);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(List.of("x"));

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    "FIXED_BU_ROLE", ROLE_ID, BU_ID, INITIATOR_ID, null);

            assertThat(result.getCandidateUsers()).containsExactly("x");
            assertThat(result.isRequiresClaim()).isTrue();
        }

        @Test
        void deprecatedUnboundedReturnsUnknown() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    "BU_UNBOUNDED_ROLE", ROLE_ID, null, INITIATOR_ID, null);

            assertThat(result.getErrorMessage()).contains("Unknown or deprecated");
        }
    }

    @Nested
    @DisplayName("ASSIGNEE_FROM_VARIABLE list")
    class VariableListTests {

        @Test
        void emptyListError() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolveFromUserIdList(
                    AssigneeType.ASSIGNEE_FROM_VARIABLE, List.of());
            assertThat(result.getErrorMessage()).contains("no user IDs");
        }

        @Test
        void oneUserDirect() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolveFromUserIdList(
                    AssigneeType.ASSIGNEE_FROM_VARIABLE, List.of("u1"));
            assertThat(result.getAssignee()).isEqualTo("u1");
            assertThat(result.isRequiresClaim()).isFalse();
        }

        @Test
        void twoUsersPool() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolveFromUserIdList(
                    AssigneeType.ASSIGNEE_FROM_VARIABLE, Arrays.asList("u1", "u2"));
            assertThat(result.getCandidateUsers()).containsExactly("u1", "u2");
            assertThat(result.isRequiresClaim()).isTrue();
        }
    }

    @Nested
    @DisplayName("Infra failure vs no-data distinction")
    class InfraFailureTests {

        @Test
        @DisplayName("admin-center transport failure marks result infraFailure=true")
        void infraFailureWhenAdminCenterUnavailable() {
            when(adminCenterClient.getUserBusinessUnitId(eq(ANCHOR_USER_ID), isNull()))
                    .thenThrow(new com.workflow.exception.AdminCenterUnavailableException(
                            "Connection refused", null));

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.HIERARCHY_ROLE, ROLE_ID, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getErrorMessage()).contains("admin-center unavailable");
            assertThat(result.isInfraFailure()).isTrue();
        }

        @Test
        @DisplayName("Genuine no-data (empty candidate pool) keeps infraFailure=false")
        void noDataIsNotInfraFailure() {
            when(adminCenterClient.getUserBusinessUnitId(eq(ANCHOR_USER_ID), isNull())).thenReturn(BU_ID);
            when(adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(BU_ID, ROLE_ID))
                    .thenReturn(Collections.emptyList());

            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.HIERARCHY_ROLE, ROLE_ID, null, INITIATOR_ID, ANCHOR_USER_ID);

            assertThat(result.getErrorMessage()).isNotNull();
            assertThat(result.isInfraFailure()).isFalse();
        }
    }
}
