package com.portal.component;

import com.portal.entity.ChangeHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

/** Multi-row statements avoid per-record PostgreSQL execution without changing the caller's audit transaction. */
final class SubTableAuditBatchWriter {
    private static final int CHUNK_SIZE = 200;
    private static final int COLUMN_COUNT = 12;
    private static final String INSERT = """
            INSERT INTO up_change_history (process_instance_id, task_instance_id, stage_id, user_id,
                timestamp, field_name, old_value, new_value, change_type, sub_table_name, row_identifier, is_concurrent)
            VALUES
            """;
    private static final String VALUES = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private SubTableAuditBatchWriter() { }

    static void insert(JdbcTemplate jdbc, List<ChangeHistory> records) {
        for (int offset = 0; offset < records.size(); offset += CHUNK_SIZE) {
            List<ChangeHistory> chunk = records.subList(offset, Math.min(offset + CHUNK_SIZE, records.size()));
            String sql = INSERT + String.join(",", Collections.nCopies(chunk.size(), VALUES));
            jdbc.update(sql, statement -> {
                for (int row = 0; row < chunk.size(); row++) bind(statement, row * COLUMN_COUNT, chunk.get(row));
            });
        }
    }

    private static void bind(PreparedStatement statement, int offset, ChangeHistory record) throws SQLException {
        statement.setString(offset + 1, record.getProcessInstanceId());
        statement.setString(offset + 2, record.getTaskInstanceId());
        statement.setString(offset + 3, record.getStageId());
        statement.setString(offset + 4, record.getUserId());
        statement.setTimestamp(offset + 5, Timestamp.from(record.getTimestamp()));
        statement.setString(offset + 6, record.getFieldName());
        statement.setString(offset + 7, record.getOldValue());
        statement.setString(offset + 8, record.getNewValue());
        statement.setString(offset + 9, record.getChangeType().name());
        statement.setString(offset + 10, record.getSubTableName());
        statement.setString(offset + 11, record.getRowIdentifier());
        statement.setBoolean(offset + 12, record.getIsConcurrent());
    }
}
