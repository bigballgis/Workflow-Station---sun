package com.portal.recordnote;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Set;

/** Read-access proof only: existing Request policy admits actual current/historic task assignees.
 * Recipient selection NEVER uses this history; it uses the separate effective-round resolver.
 */
@Repository
@RequiredArgsConstructor
public class RecordNoteParticipantRepository {
    private final JdbcTemplate jdbc;
    public Set<String> assignedReaders(String process,Set<String> users) {
        if(users.isEmpty()) return Set.of();
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        return new java.util.HashSet<>(named.queryForList("SELECT DISTINCT ASSIGNEE_ FROM ACT_HI_TASKINST "
                + "WHERE PROC_INST_ID_=:process AND ASSIGNEE_ IN (:users)",Map.of("process",process,"users",users),String.class));
    }
    public boolean isAssignedReader(String user,String process) {
        return assignedReaders(process,Set.of(user)).contains(user);
    }
}
