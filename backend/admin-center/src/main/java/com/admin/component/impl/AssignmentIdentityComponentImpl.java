package com.admin.component.impl;

import com.admin.component.AssignmentIdentityComponent;

import com.admin.repository.UserRepository;
import com.platform.common.assignment.AssignmentAuthorityFence;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Qualification snapshot and version are read under the same shared transaction lock. */
@Component
public class AssignmentIdentityComponentImpl implements AssignmentIdentityComponent {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final com.admin.component.AssignmentManagementAuthority managementAuthority;
    private final com.admin.component.AssignmentAuthorityTimeWindow time;

    public AssignmentIdentityComponentImpl(UserRepository users, JdbcTemplate jdbc, com.admin.component.AssignmentManagementAuthority managementAuthority,
            com.admin.component.AssignmentAuthorityTimeWindow time) {
        this.users = users;
        this.jdbc = jdbc;
        this.managementAuthority = managementAuthority;
        this.time=time;
    }

    @Override @Transactional(readOnly = true)
    public com.platform.common.assignment.AssignmentManagementSnapshot management(String userId) {
        long revision = new AssignmentAuthorityFence(jdbc).readLocked();
        var user = users.findById(userId);
        boolean active = user.isPresent() && user.get().getStatus() == UserStatus.ACTIVE && !Boolean.TRUE.equals(user.get().getDeleted());
        boolean administrator = active && managementAuthority.systemAdministrator(userId);
        return new com.platform.common.assignment.AssignmentManagementSnapshot(revision, userId, active, administrator);
    }

    @Transactional(readOnly = true)
    public AssignmentIdentitySnapshot resolve(AssignmentIdentitySnapshot.Request request) {
        long revision = new AssignmentAuthorityFence(jdbc).readLocked();
        List<String> values = request.references().stream().map(AssignmentIdentitySnapshot.Reference::value).distinct().toList();
        Map<String,User> ids = users.findAllById(values).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String,User> names = users.findByUsernameIn(values).stream().collect(Collectors.toMap(User::getUsername, Function.identity()));
        Map<String,AssignmentIdentitySnapshot.User> result = new LinkedHashMap<>();
        var missing = new java.util.LinkedHashSet<String>();
        for (var reference : request.references()) {
            User idMatch = reference.kind() == AssignmentIdentitySnapshot.Kind.USERNAME ? null : ids.get(reference.value());
            User nameMatch = reference.kind() == AssignmentIdentitySnapshot.Kind.ID ? null : names.get(reference.value());
            if (idMatch != null && nameMatch != null && !idMatch.getId().equals(nameMatch.getId())) {
                throw new IllegalArgumentException("Ambiguous assignment identity reference");
            }
            User match = idMatch != null ? idMatch : nameMatch;
            if (match == null) { missing.add(reference.value()); continue; }
            boolean active = match.getStatus() == UserStatus.ACTIVE && !Boolean.TRUE.equals(match.getDeleted());
            var resolved = new AssignmentIdentitySnapshot.User(match.getId(), match.getUsername(), active);
            var previous = result.put(reference.value(), resolved);
            if (previous != null && !previous.equals(resolved)) throw new IllegalArgumentException("Conflicting identity reference kinds");
        }
        if (missing.stream().anyMatch(result::containsKey)) throw new IllegalArgumentException("Conflicting identity reference kinds");
        return new AssignmentIdentitySnapshot(revision, Map.copyOf(result), java.util.Set.copyOf(missing),time.read());
    }
}
