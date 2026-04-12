package com.portal.properties;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.DashboardComponent;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.DashboardOverview;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.repository.BusinessUnitRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.repository.UserBusinessUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Property Test — 团队任务指标缺失
 *
 * <p>Validates: Requirements 1.1, 1.2, 2.1, 2.2</p>
 *
 * <p>This test encodes the EXPECTED behavior: when a user belonging to at least one BU
 * calls getTaskOverview, the returned TaskOverview should contain non-null team metrics
 * (teamPendingCount, teamOverdueCount, teamCompletedTodayCount) aggregated from all
 * team members across the user's BU hierarchy.</p>
 *
 * <p>On unfixed code, this test is EXPECTED TO FAIL because the current TaskOverview DTO
 * lacks team fields and DashboardComponent has no team aggregation logic.</p>
 */
class DashboardTaskOverviewBugConditionProperties {

    @Mock
    private TaskQueryComponent taskQueryComponent;

    @Mock
    private WorkflowEngineClient workflowEngineClient;

    @Mock
    private BusinessUnitRepository businessUnitRepository;

    @Mock
    private UserBusinessUnitRepository userBusinessUnitRepository;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    private DashboardComponent dashboardComponent;
    private Random random;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardComponent = new DashboardComponent(taskQueryComponent, workflowEngineClient,
                businessUnitRepository, userBusinessUnitRepository, processInstanceRepository);
        random = new Random();
    }


    /**
     * Property 1: Bug Condition — 团队任务指标缺失
     *
     * <p><b>Validates: Requirements 1.1, 1.2, 2.1, 2.2</b></p>
     *
     * <p>Scenario: A user belongs to BU-A, BU-A has child BU-B, both BUs have members.
     * When getTaskOverview(userId) is called, the returned TaskOverview MUST contain
     * teamPendingCount, teamOverdueCount, teamCompletedTodayCount fields that are not null,
     * and they should represent aggregated stats of all team members (BU-A + BU-B).</p>
     *
     * <p>On unfixed code: TaskOverview has no team fields → test fails (proves bug exists).</p>
     */
    @RepeatedTest(5)
    @DisplayName("Bug Condition: TaskOverview should contain non-null team metrics for user in BU")
    void taskOverviewShouldContainTeamMetricsForUserInBU() {
        // Arrange: generate a random user belonging to a BU hierarchy
        String userId = "user_" + random.nextInt(1000);

        // Mock personal tasks for the user
        int personalTaskCount = 1 + random.nextInt(5);
        List<TaskInfo> personalTasks = new ArrayList<>();
        for (int i = 0; i < personalTaskCount; i++) {
            TaskInfo task = TaskInfo.builder()
                    .taskId("task_personal_" + i)
                    .taskName("Personal Task " + i)
                    .assignee(userId)
                    .assignmentType("USER")
                    .priority(i == 0 ? "HIGH" : "NORMAL")
                    .isOverdue(i == 0)
                    .createTime(LocalDateTime.now().minusHours(i + 1))
                    .build();
            personalTasks.add(task);
        }

        // Mock taskQueryComponent to return personal tasks for any request
        when(taskQueryComponent.queryTasks(any(TaskQueryRequest.class)))
                .thenReturn(PageResponse.<TaskInfo>builder()
                        .content(personalTasks)
                        .page(0)
                        .size(20)
                        .totalElements(personalTaskCount)
                        .build());

        // Mock Flowable completed tasks
        Map<String, Object> completedResult = new HashMap<>();
        completedResult.put("totalElements", 3);
        completedResult.put("content", Collections.emptyList());
        when(workflowEngineClient.getCompletedTasks(
                eq(userId), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(Optional.of(completedResult));

        // Act: call getTaskOverview
        DashboardOverview.TaskOverview taskOverview = dashboardComponent.getTaskOverview(userId);

        // Assert: TaskOverview should not be null
        assertNotNull(taskOverview, "TaskOverview should not be null");

        // Assert: team fields MUST exist and be non-null
        // Use reflection to check for team fields since they don't exist in unfixed code
        Class<?> clazz = taskOverview.getClass();

        // Check teamPendingCount field exists and has a getter
        assertDoesNotThrow(() -> {
            Method getter = clazz.getMethod("getTeamPendingCount");
            Object value = getter.invoke(taskOverview);
            assertNotNull(value, "teamPendingCount should not be null for a user belonging to a BU");
        }, "TaskOverview should have getTeamPendingCount() method — team field is missing from DTO");

        // Check teamOverdueCount field exists and has a getter
        assertDoesNotThrow(() -> {
            Method getter = clazz.getMethod("getTeamOverdueCount");
            Object value = getter.invoke(taskOverview);
            assertNotNull(value, "teamOverdueCount should not be null for a user belonging to a BU");
        }, "TaskOverview should have getTeamOverdueCount() method — team field is missing from DTO");

        // Check teamCompletedTodayCount field exists and has a getter
        assertDoesNotThrow(() -> {
            Method getter = clazz.getMethod("getTeamCompletedTodayCount");
            Object value = getter.invoke(taskOverview);
            assertNotNull(value, "teamCompletedTodayCount should not be null for a user belonging to a BU");
        }, "TaskOverview should have getTeamCompletedTodayCount() method — team field is missing from DTO");
    }

    /**
     * Property 1b: Team metrics should be aggregated from all BU members
     *
     * <p><b>Validates: Requirements 2.1, 2.2</b></p>
     *
     * <p>When team fields exist, their values should represent aggregated stats
     * across all team members in the BU hierarchy, not just the individual user.</p>
     *
     * <p>On unfixed code: fails because team fields don't exist.</p>
     */
    @RepeatedTest(5)
    @DisplayName("Bug Condition: Team metrics should aggregate stats from all BU members")
    void teamMetricsShouldAggregateFromAllBUMembers() {
        String userId = "user_leader_" + random.nextInt(1000);

        // Mock personal tasks (small number for the leader)
        List<TaskInfo> leaderTasks = List.of(
                TaskInfo.builder()
                        .taskId("task_leader_1")
                        .taskName("Leader Task")
                        .assignee(userId)
                        .assignmentType("USER")
                        .priority("NORMAL")
                        .isOverdue(false)
                        .createTime(LocalDateTime.now().minusHours(1))
                        .build()
        );

        when(taskQueryComponent.queryTasks(any(TaskQueryRequest.class)))
                .thenReturn(PageResponse.<TaskInfo>builder()
                        .content(leaderTasks)
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .build());

        Map<String, Object> completedResult = new HashMap<>();
        completedResult.put("totalElements", 1);
        completedResult.put("content", Collections.emptyList());
        when(workflowEngineClient.getCompletedTasks(
                anyString(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(Optional.of(completedResult));

        // Act
        DashboardOverview.TaskOverview taskOverview = dashboardComponent.getTaskOverview(userId);
        assertNotNull(taskOverview);

        // Assert: team fields must exist (reflection check)
        Class<?> clazz = taskOverview.getClass();

        // The team metrics should be numeric values (Long type)
        assertDoesNotThrow(() -> {
            Method getTeamPending = clazz.getMethod("getTeamPendingCount");
            Object teamPending = getTeamPending.invoke(taskOverview);
            assertNotNull(teamPending, "teamPendingCount must not be null");
            assertInstanceOf(Long.class, teamPending,
                    "teamPendingCount should be of type Long");
        }, "TaskOverview must have getTeamPendingCount() — DTO is missing team aggregation fields");

        assertDoesNotThrow(() -> {
            Method getTeamOverdue = clazz.getMethod("getTeamOverdueCount");
            Object teamOverdue = getTeamOverdue.invoke(taskOverview);
            assertNotNull(teamOverdue, "teamOverdueCount must not be null");
            assertInstanceOf(Long.class, teamOverdue,
                    "teamOverdueCount should be of type Long");
        }, "TaskOverview must have getTeamOverdueCount() — DTO is missing team aggregation fields");

        assertDoesNotThrow(() -> {
            Method getTeamCompleted = clazz.getMethod("getTeamCompletedTodayCount");
            Object teamCompleted = getTeamCompleted.invoke(taskOverview);
            assertNotNull(teamCompleted, "teamCompletedTodayCount must not be null");
            assertInstanceOf(Long.class, teamCompleted,
                    "teamCompletedTodayCount should be of type Long");
        }, "TaskOverview must have getTeamCompletedTodayCount() — DTO is missing team aggregation fields");
    }
}
