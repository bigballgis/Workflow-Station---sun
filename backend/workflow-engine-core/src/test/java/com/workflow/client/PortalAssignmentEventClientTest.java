package com.workflow.client;

import com.workflow.client.PortalAssignmentEventClient.DepartedReassignment;
import com.workflow.client.PortalAssignmentEventClient.PoolMembershipRelease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortalAssignmentEventClientTest {

    private static final String URL = "http://portal:8080/api/portal/internal/task-assignment-events";
    private static final List<PoolMembershipRelease> RELEASES = List.of(new PoolMembershipRelease(
            "t1", "pi-1", "gone", "FIN", List.of("MANAGER"), Instant.parse("2026-11-01T00:00:00Z"), "deputy"));

    private MockRestServiceServer server;
    private PortalAssignmentEventClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PortalAssignmentEventClient(restTemplate, "http://portal:8080", "svc-token");
    }

    @Test
    void releasesGoToThePortalWithTheServiceToken() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "svc-token"))
                .andExpect(jsonPath("$.poolMembershipReleases[0].taskId").value("t1"))
                .andExpect(jsonPath("$.poolMembershipReleases[0].previousAssigneeId").value("gone"))
                .andExpect(jsonPath("$.poolMembershipReleases[0].endedDelegate").value("deputy"))
                .andRespond(withSuccess());

        client.reportPoolMembershipReleases(RELEASES);

        server.verify();
    }

    @Test
    void reassignmentsToManagersGoInTheirOwnList() {
        server.expect(requestTo(URL))
                .andExpect(header("X-Service-Token", "svc-token"))
                .andExpect(jsonPath("$.poolMembershipReleases").isEmpty())
                .andExpect(jsonPath("$.departedReassignments[0].taskId").value("t2"))
                .andExpect(jsonPath("$.departedReassignments[0].previousAssigneeId").value("gone"))
                .andExpect(jsonPath("$.departedReassignments[0].newAssigneeId").value("manager"))
                .andRespond(withSuccess());

        client.reportDepartedReassignments(List.of(new DepartedReassignment(
                "t2", "pi-2", "gone", "manager", Instant.parse("2026-11-01T00:00:00Z"), null)));

        server.verify();
    }

    @Test
    void moreThanOneBatchGoesInSeveralRequests() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.poolMembershipReleases.length()").value(PortalAssignmentEventClient.BATCH))
                .andRespond(withSuccess());
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.poolMembershipReleases.length()").value(1))
                .andRespond(withSuccess());
        List<PoolMembershipRelease> many = new java.util.ArrayList<>();
        for (int i = 0; i <= PortalAssignmentEventClient.BATCH; i++) {
            many.add(new PoolMembershipRelease("t" + i, "pi", "gone", "FIN", List.of("MANAGER"), Instant.EPOCH, null));
        }

        client.reportPoolMembershipReleases(many);

        server.verify();
    }

    @Test
    void aPortalFailureDoesNotUndoTheCommittedRelease() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> client.reportPoolMembershipReleases(RELEASES)).doesNotThrowAnyException();
    }

    @Test
    void nothingToReportSendsNothing() {
        client.reportPoolMembershipReleases(List.of());
        client.reportDepartedReassignments(List.of());

        server.verify();
    }
}
