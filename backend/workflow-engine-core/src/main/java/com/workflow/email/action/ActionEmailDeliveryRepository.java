package com.workflow.email.action;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Ledger for post-action emails ({@code we_action_email_deliveries}).
 *
 * <p>The primary key is the Kafka event id, so a redelivered event is a no-op insert. Rows move
 * {@code PENDING → SENDING → SENT | FAILED}; a transient failure puts the row back to
 * {@code PENDING} with a later {@code next_attempt_at}. A row left in {@code SENDING} (engine died
 * mid-send) is never picked up again — delivery is at most once per event.</p>
 */
@Repository
@RequiredArgsConstructor
public class ActionEmailDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    /** @return {@code true} if a new row was written, {@code false} if the event was already recorded */
    public boolean insertPending(String eventId, String payloadJson, String processInstanceId, String actionId) {
        int rows = jdbcTemplate.update(
                "INSERT INTO we_action_email_deliveries "
                        + "(event_id, payload, status, attempts, next_attempt_at, process_instance_id, action_id, "
                        + "created_at, updated_at) "
                        + "VALUES (?, CAST(? AS jsonb), 'PENDING', 0, now(), ?, ?, now(), now()) "
                        + "ON CONFLICT (event_id) DO NOTHING",
                eventId, payloadJson, processInstanceId, actionId);
        return rows > 0;
    }

    public List<PendingDelivery> findDue(int limit) {
        return jdbcTemplate.query(
                "SELECT event_id, payload::text AS payload, attempts FROM we_action_email_deliveries "
                        + "WHERE status = 'PENDING' AND next_attempt_at <= now() "
                        + "ORDER BY created_at LIMIT ?",
                (rs, rowNum) -> new PendingDelivery(
                        rs.getString("event_id"), rs.getString("payload"), rs.getInt("attempts")),
                limit);
    }

    /** Move one row to SENDING; {@code false} means another node already took it. */
    public boolean claim(String eventId) {
        return jdbcTemplate.update(
                "UPDATE we_action_email_deliveries SET status = 'SENDING', attempts = attempts + 1, "
                        + "updated_at = now() WHERE event_id = ? AND status = 'PENDING'",
                eventId) > 0;
    }

    public void markSent(String eventId) {
        jdbcTemplate.update(
                "UPDATE we_action_email_deliveries SET status = 'SENT', sent_at = now(), last_error = NULL, "
                        + "updated_at = now() WHERE event_id = ?",
                eventId);
    }

    public void reschedule(String eventId, long delaySeconds, String error) {
        jdbcTemplate.update(
                "UPDATE we_action_email_deliveries SET status = 'PENDING', "
                        + "next_attempt_at = now() + (? * interval '1 second'), last_error = ?, updated_at = now() "
                        + "WHERE event_id = ?",
                delaySeconds, error, eventId);
    }

    public void markFailed(String eventId, String error) {
        jdbcTemplate.update(
                "UPDATE we_action_email_deliveries SET status = 'FAILED', last_error = ?, updated_at = now() "
                        + "WHERE event_id = ?",
                error, eventId);
    }

    /** A due row: {@code attempts} is the count before the attempt about to be made. */
    public record PendingDelivery(String eventId, String payloadJson, int attempts) {}
}
