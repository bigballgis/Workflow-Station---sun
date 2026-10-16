package com.portal.recordnote;

import com.portal.dto.RecordNoteDestination;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RecordNoteNavigationRepository {
    private final JdbcTemplate jdbc;
    public List<RecordNoteDestination> destinations(String submissionId,String user) {
        return jdbc.query("""
                SELECT s.process_instance_id,p.title,s.target_type,s.target_id,s.table_id,s.table_kind,s.id
                FROM up_record_note_submissions s JOIN up_record_note_deliveries d ON d.submission_id=s.id
                JOIN up_process_instance p ON p.id=s.process_instance_id
                WHERE s.id=? AND d.user_id=? AND d.state='DELIVERED'
                """,(rs,n) -> new com.portal.dto.RecordNoteDestination(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getString(5),rs.getString(6),rs.getString(7)),submissionId,user);
    }
    public List<String> noteIds(String submissionId) {
        return jdbc.queryForList("SELECT id FROM up_record_note WHERE submission_id=? "
                + "AND parent_note_id IS NULL AND is_deleted=false ORDER BY created_at LIMIT 1",String.class,submissionId);
    }
}
