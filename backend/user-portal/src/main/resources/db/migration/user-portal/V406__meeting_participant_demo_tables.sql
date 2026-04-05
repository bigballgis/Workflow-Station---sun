-- Physical tables for Meeting Participant demo flow (MeetingParticipantVariablesPersistence JDBC).
-- dw_table_definitions in developer DB (init-scripts/16) alone do not create these relations.

CREATE TABLE IF NOT EXISTS meeting (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    meeting_time TIMESTAMP NOT NULL,
    location VARCHAR(200) NOT NULL DEFAULT '',
    organizer_name VARCHAR(100) NOT NULL DEFAULT '',
    description TEXT,
    status VARCHAR(30) NOT NULL,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS participants (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL REFERENCES meeting (id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL DEFAULT '',
    department VARCHAR(500),
    email VARCHAR(500) NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_participants_meeting_id ON participants (meeting_id);
