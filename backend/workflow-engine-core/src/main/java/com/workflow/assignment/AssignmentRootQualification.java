package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.dto.UserPrincipal;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.request.StartProcessRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Root identity comes from the authenticated principal or an explicitly authenticated Portal service call. */
@Component
public class AssignmentRootQualification {
    private final AssignmentIdentityClient identities;
    private final AdminCenterClient admin;
    private final JdbcTemplate jdbc;
    @Value("${service.internal-token:}") private String serviceToken;

    public AssignmentRootQualification(AssignmentIdentityClient identities, AdminCenterClient admin, JdbcTemplate jdbc) {
        this.identities = identities; this.admin = admin; this.jdbc = jdbc;
    }

    public Prepared prepare(StartProcessRequest request) {
        var authority = new AssignmentAuthorityFence(jdbc);
        long version = authority.revision();
        Instant started=authority.currentTime();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor, bu;
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserPrincipal principal) {
            actor = principal.getUserId(); bu = principal.getActiveBusinessUnitId();
            if (request.getStartUserId() != null && !request.getStartUserId().equals(actor)) throw excluded();
        } else {
            var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            String token = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                    ? servlet.getRequest().getHeader("X-Internal-Token") : null;
            AssignmentServiceAuthentication.require(serviceToken, token);
            actor = request.getStartUserId();
            Object active = request.getVariables() == null ? null : request.getVariables().get("activeBusinessUnitId");
            if (active != null && !(active instanceof String)) throw excluded();
            bu = (String) active;
        }
        if (actor == null || actor.isBlank()) throw excluded();
        var snapshot = identities.resolve(List.of(new AssignmentIdentitySnapshot.Reference(actor, AssignmentIdentitySnapshot.Kind.ID)));
        var user = snapshot.users().get(actor);
        if (user == null || !user.active() || !actor.equals(user.id())) throw excluded();
        String code = bu == null || bu.isBlank() ? null : admin.getBusinessUnitCodeById(bu);
        if (bu != null && !bu.isBlank() && (code == null || code.isBlank())) throw excluded();
        if (snapshot.authorityRevision() != version || authority.revision() != version) throw changed();
        var window=snapshot.timeWindow();
        if (window==null || window.observedAt()==null) throw changed();
        if (window.previousBoundary()!=null && window.previousBoundary().isAfter(started)) throw changed();
        Instant until=authority.currentTime().plusSeconds(5);
        if (window.nextBoundary()!=null && window.nextBoundary().isBefore(until)) until=window.nextBoundary();
        return new Prepared(actor, bu, code, version, until);
    }

    public void check(Prepared prepared) {
        var authority = new AssignmentAuthorityFence(jdbc);
        if (authority.revision() != prepared.authorityRevision() || !authority.currentTime().isBefore(prepared.validUntil())) throw changed();
    }

    private AssignmentFailure excluded() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED); }
    private AssignmentFailure changed() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED); }
    public record Prepared(String actor, String activeBu, String startBuCode, long authorityRevision, Instant validUntil) { }
}
