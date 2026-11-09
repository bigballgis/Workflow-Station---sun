package com.workflow.component;

import com.platform.security.service.LostEntryTaskPolicy;
import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import com.platform.security.service.LostEntryTaskPolicy.TaskHolding;
import com.workflow.client.VirtualGroupMembersClient;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How a task's people were chosen, as the lost-entry rules need it (D-17, D-39). */
@ExtendWith(MockitoExtension.class)
class TaskHoldingResolverTest {

    @Mock
    private TaskService taskService;
    @Mock
    private BpmnActionParser bpmnActionParser;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private VirtualGroupMembersClient virtualGroupMembersClient;
    @Mock
    private LostEntryTaskPolicy lostEntryTaskPolicy;
    @Mock
    private EngineActorIdentities engineActorIdentities;

    /** Usernames the directory knows, by user id; every other value is already an id. */
    private final Map<String, String> idByUsername = new LinkedHashMap<>();
    private TaskHoldingResolver resolver;
    private Task task;

    @BeforeEach
    void setUp() {
        resolver = new TaskHoldingResolver(taskService, bpmnActionParser, extendedTaskInfoRepository,
                virtualGroupMembersClient, lostEntryTaskPolicy, engineActorIdentities);
        lenient().when(engineActorIdentities.userIds(anyCollection())).thenAnswer(call -> {
            Map<String, String> ids = new LinkedHashMap<>();
            for (String value : call.<Collection<String>>getArgument(0)) {
                ids.put(value, idByUsername.getOrDefault(value, value));
            }
            return ids;
        });
        task = mock(Task.class);
        lenient().when(task.getId()).thenReturn("t1");
        lenient().when(task.getProcessDefinitionId()).thenReturn("pd");
        lenient().when(task.getTaskDefinitionKey()).thenReturn("step");
        lenient().when(task.getAssignee()).thenReturn("holder");
    }

    @Test
    void aBuRoleNodeIsAMembershipPoolOfItsCandidates() {
        bpmnType("BU_ROLE");
        links(candidate("holder"), candidate("b"));

        TaskHolding holding = resolver.read(task, Optional.empty());

        assertThat(holding.kind()).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(holding.poolMemberIds()).containsExactlyInAnyOrder("holder", "b");
    }

    @Test
    void candidatesFromAFormFieldAreNamedPeople() {
        bpmnType("ASSIGNEE_FROM_VARIABLE");
        links(candidate("holder"), candidate("b"));

        assertThat(resolver.read(task, Optional.empty()).kind()).isEqualTo(PoolKind.PEOPLE);
    }

    @Test
    void namedPeopleComeInTheOrderTheVariableNamesThem() {
        bpmnType("ASSIGNEE_FROM_VARIABLE");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeVariable")).thenReturn("reviewers");
        when(taskService.getVariable("t1", "reviewers")).thenReturn(List.of("c", "a"));
        links(candidate("a"), candidate("b"), candidate("c"));

        assertThat(resolver.read(task, Optional.empty()).poolMemberIds()).containsExactly("c", "a", "b");
    }

    @Test
    void withoutAnAssignmentOrderCandidatesFollowTheirUserId() {
        bpmnType("PROCESS_INITIATOR");
        links(candidate("z"), candidate("a"), candidate("m"));

        assertThat(resolver.read(task, Optional.empty()).poolMemberIds()).containsExactly("a", "m", "z");
    }

    @Test
    void noCandidatesMeansAssignedToOnePerson() {
        bpmnType("PROCESS_INITIATOR");
        links();

        TaskHolding holding = resolver.read(task, Optional.empty());

        assertThat(holding.kind()).isEqualTo(PoolKind.NONE);
        assertThat(holding.assigneeId()).isEqualTo("holder");
    }

    @Test
    void aLegacyVirtualGroupRowPoolsTheGroupsMembers() {
        links();
        when(virtualGroupMembersClient.memberUserIds("vg-1")).thenReturn(List.of("holder", "c"));
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1")
                .assignmentType(AssignmentType.VIRTUAL_GROUP).assignmentTarget("vg-1").build();

        TaskHolding holding = resolver.read(task, Optional.of(row));

        assertThat(holding.kind()).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(holding.poolMemberIds()).containsExactlyInAnyOrder("holder", "c");
    }

    @Test
    void anMiRoleRowIsAMembershipPool() {
        links(candidate("holder"));
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1")
                .extendedProperties("{\"assigneeMode\":\"role\",\"businessUnitCode\":\"FIN\",\"roleCodes\":[\"MGR\"]}")
                .build();

        assertThat(resolver.read(task, Optional.of(row)).kind()).isEqualTo(PoolKind.MEMBERSHIP);
    }

    @Test
    void takeOverAsksTheSharedPolicyWithEveryonesEntry() {
        bpmnType("BU_ROLE");
        links(candidate("holder"), candidate("b"));
        when(lostEntryTaskPolicy.withPortalEntry(anyCollection())).thenReturn(Set.of("b"));

        assertThat(resolver.mayTakeOverFromLostHolder(task, "b")).isTrue();
        assertThat(resolver.mayTakeOverFromLostHolder(task, "outsider")).isFalse();
    }

    @Test
    void aTaskThatStoresUsernamesIsResolvedInUserIds() {
        idByUsername.put("holder", "u-holder");
        idByUsername.put("bob", "u-bob");
        bpmnType("BU_ROLE");
        links(candidate("holder"), candidate("bob"), candidate("u-carol"));

        TaskHolding holding = resolver.resolve(task);

        assertThat(holding.assigneeId()).isEqualTo("u-holder");
        assertThat(holding.poolMemberIds()).containsExactlyInAnyOrder("u-holder", "u-bob", "u-carol");
        assertThat(holding.kind()).isEqualTo(PoolKind.MEMBERSHIP);
    }

    @Test
    void aHolderStoredByUsernameWhoStillHasEntryIsNotTakenOverFrom() {
        idByUsername.put("holder", "u-holder");
        bpmnType("BU_ROLE");
        links(candidate("holder"), candidate("b"));
        when(lostEntryTaskPolicy.withPortalEntry(anyCollection())).thenReturn(Set.of("u-holder", "b"));

        assertThat(resolver.mayTakeOverFromLostHolder(task, "b")).isFalse();
    }

    @Test
    void readKeepsThePeopleAsTheTaskStoresThem() {
        idByUsername.put("holder", "u-holder");
        bpmnType("BU_ROLE");
        links(candidate("holder"));

        TaskHolding read = resolver.read(task, Optional.empty());

        assertThat(read.assigneeId()).isEqualTo("holder");
        assertThat(resolver.inUserIds(Map.of("t1", read)).get("t1").assigneeId()).isEqualTo("u-holder");
    }

    private void bpmnType(String type) {
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeType")).thenReturn(type);
    }

    private void links(IdentityLink... links) {
        when(taskService.getIdentityLinksForTask("t1")).thenReturn(new ArrayList<>(List.of(links)));
    }

    private static IdentityLink candidate(String userId) {
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        lenient().when(link.getUserId()).thenReturn(userId);
        lenient().when(link.getGroupId()).thenReturn(null);
        return link;
    }
}
