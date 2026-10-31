package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentAuthorityFence;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.workflow.assignment.*;
import com.workflow.client.AdminCenterClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Only current, exact canonical identities can participate in a protected assignment decision. */
@Component
public class AssignmentQualificationComponentImpl implements AssignmentQualificationComponent {
    private final JdbcTemplate jdbc;
    private final AssignmentIdentityClient identities;
    private final AssignmentSourceResolver sources;
    private final AssignmentDefinitionPolicy definitions;
    private final AdminCenterClient admin;

    public AssignmentQualificationComponentImpl(JdbcTemplate jdbc, AssignmentIdentityClient identities,
            AssignmentSourceResolver sources, AssignmentDefinitionPolicy definitions, AdminCenterClient admin) {
        this.jdbc = jdbc; this.identities = identities; this.sources = sources; this.definitions = definitions; this.admin = admin;
    }

    @Override
    public Prepared prepare(Request request) {
        var authority = new AssignmentAuthorityFence(jdbc);
        long version = authority.revision();
        Instant started=authority.currentTime();
        var resolution = sources.resolve(request.task(), request.source(),request.context());
        var result = resolution.assignment();
        if (result == null) throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        if (result.getErrorMessage() != null && !result.isEmptyPool()) throw failure(result.isInfraFailure()
                ? AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE : AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        List<String> rawPrimary = result.getAssignee() != null && !result.getAssignee().isBlank()
                ? List.of(result.getAssignee()) : result.getCandidateUsers() == null ? List.of() : result.getCandidateUsers();
        var references = new LinkedHashSet<>(request.additionalReferences());
        rawPrimary.forEach(id -> references.add(new AssignmentIdentitySnapshot.Reference(id,
                resolution.identityKinds().getOrDefault(id, AssignmentIdentitySnapshot.Kind.ID_OR_USERNAME))));
        if (references.isEmpty()) references.add(new AssignmentIdentitySnapshot.Reference(request.context().initiator(),AssignmentIdentitySnapshot.Kind.ID));
        var snapshot=resolve(references.stream().toList(),version,started);
        Map<String,AssignmentIdentitySnapshot.User> resolved = new LinkedHashMap<>(snapshot.users());
        Instant nextEvaluation=snapshot.nextEvaluation();
        List<String> primary = active(rawPrimary, resolved);
        List<String> primaryIdentities = rawPrimary.stream().map(resolved::get).filter(java.util.Objects::nonNull)
                .map(AssignmentIdentitySnapshot.User::id).distinct().toList();
        List<String> rawBackup = List.of();
        boolean primaryVetoed = !primary.isEmpty() && primary.stream().allMatch(request.excluded()::contains);
        if (request.policy().backup().enabled() && (primaryVetoed || request.retainBackupHolder())) {
            rawBackup = backup(request, resolution.singleBusinessUnitCode());
            var backupReferences = rawBackup.stream().distinct()
                    .map(id -> new AssignmentIdentitySnapshot.Reference(id, AssignmentIdentitySnapshot.Kind.ID)).toList();
            if (!backupReferences.isEmpty()) {
                var backupSnapshot=resolve(backupReferences,version,started);
                resolved.putAll(backupSnapshot.users());
                if (backupSnapshot.nextEvaluation().isBefore(nextEvaluation)) nextEvaluation=backupSnapshot.nextEvaluation();
            }
        }
        if (authority.revision() != version) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        Instant time = authority.currentTime();
        if (!time.isBefore(nextEvaluation)) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        Instant validUntil=time.plusSeconds(5).isBefore(nextEvaluation)?time.plusSeconds(5):nextEvaluation;
        return new Prepared(version, validUntil, primary, active(rawBackup, resolved),
                result.isRequiresClaim(), Map.copyOf(resolved), primaryIdentities,nextEvaluation);
    }

    private List<String> backup(Request request, String nodeBu) {
        var backup = request.policy().backup();
        if (!backup.enabled()) return List.of();
        if (backup.mode() == com.platform.common.assignment.AssignmentExclusionPolicy.Mode.USERS) {
            var bound = definitions.boundUsers(request.task().getProcessDefinitionId());
            return backup.userRefs().stream().map(reference -> {
                String id = bound.get(reference);
                if (id == null || id.isBlank()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
                return id;
            }).toList();
        }
        String bu = switch (backup.buSource()) {
            case CASE_START_BU -> request.context().startBu();
            case NODE_ASSIGNMENT_BU -> nodeBu;
            case FIXED_BU -> backup.businessUnitCode();
        };
        if (bu == null || bu.isBlank()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        var users = new LinkedHashSet<String>();
        for (String role : backup.roleCodes()) {
            if (!admin.isEligibleRole(bu, role)) throw failure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
            users.addAll(AssignmentIdentityReadScope.fresh(()->admin.getUsersByBusinessUnitAndRole(bu, role)));
        }
        return List.copyOf(users);
    }

    private Resolution resolve(List<AssignmentIdentitySnapshot.Reference> references,long version,Instant started) {
        Map<String,AssignmentIdentitySnapshot.User> result = new LinkedHashMap<>();
        Instant next=Instant.parse("9999-12-31T23:59:59Z");
        for (int offset = 0; offset < references.size(); offset += 1000) {
            var snapshot = identities.resolve(references.subList(offset, Math.min(offset + 1000, references.size())));
            if (snapshot.authorityRevision() != version) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
            var window=snapshot.timeWindow();
            if (window==null || window.observedAt()==null) throw failure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
            if (window.previousBoundary()!=null && window.previousBoundary().isAfter(started)) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
            if (window.nextBoundary()!=null && window.nextBoundary().isBefore(next)) next=window.nextBoundary();
            snapshot.users().forEach((reference, user) -> {
                var previous = result.put(reference, user);
                if (previous != null && !previous.equals(user)) throw failure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
            });
        }
        return new Resolution(result,next);
    }
    private record Resolution(Map<String,AssignmentIdentitySnapshot.User> users,Instant nextEvaluation) { }

    private List<String> active(List<String> references, Map<String,AssignmentIdentitySnapshot.User> resolved) {
        var ids = new ArrayList<String>();
        for (String reference : references) {
            var user = resolved.get(reference);
            if (user == null) continue; // Exact authority returned this reference as missing; never invent a replacement identity.
            if (user.active()) ids.add(user.id());
        }
        return ids.stream().distinct().toList();
    }

    private AssignmentFailure failure(AssignmentFailure.Reason reason) { return new AssignmentFailure(reason); }
}
