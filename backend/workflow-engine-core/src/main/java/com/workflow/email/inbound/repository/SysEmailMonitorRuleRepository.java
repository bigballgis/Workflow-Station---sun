package com.workflow.email.inbound.repository;

import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface SysEmailMonitorRuleRepository extends JpaRepository<SysEmailMonitorRule, String> {

    List<SysEmailMonitorRule> findByEnabledTrue();

    /**
     * Writes back the poll position only while the row still targets the mailbox that was
     * polled. A deploy that rebinds the rule mid-poll has already reset these columns; saving
     * the whole stale snapshot would restore the old connection together with its cursor.
     *
     * @param folderLabel the polled folder, {@code ""} when the rule has none
     * @return {@code 0} when the rule was rebound or removed during the poll
     */
    @Modifying
    @Transactional
    @Query("UPDATE SysEmailMonitorRule r SET r.lastSyncCursor = :cursor, r.lastSyncedAt = :syncedAt "
            + "WHERE r.id = :id AND r.connectionUid = :connectionUid "
            + "AND COALESCE(r.folderLabel, '') = :folderLabel")
    int updatePollState(@Param("id") String id,
                        @Param("connectionUid") String connectionUid,
                        @Param("folderLabel") String folderLabel,
                        @Param("cursor") String cursor,
                        @Param("syncedAt") Instant syncedAt);
}
