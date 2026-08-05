package com.workflow.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Flowable creates {@code act_hi_comment} at engine startup with {@code varchar(255)} on several
 * columns. Postgres init script {@code 31-widen-flowable-act-hi-comment-columns.sql} often runs
 * <em>before</em> those tables exist, so the ALTER never applies. Re-run the same idempotent widen
 * after the datasource is up so task completion (history comment insert) does not fail with
 * "value too long for type character varying(255)".
 *
 * <p>Fails startup if the widen does not take effect. The previous version caught everything and
 * logged a warning, so a failed ALTER left the application running against a schema that would
 * reject the first task completion — surfacing in production long after the warning scrolled past.
 * The only silently-tolerated case is {@code act_hi_comment} not existing yet, which is the normal
 * first boot against an empty schema.
 */
@Component
@Order(Integer.MAX_VALUE)
public class FlowableActHiCommentSchemaRepair implements ApplicationRunner {
    // No @ConditionalOnBean(DataSource.class) here. On a scanned @Component the condition is
    // evaluated during component scanning, before the auto-configured DataSource bean definition
    // exists, so it always evaluated false and this runner was never registered — the widening
    // silently never ran. The JdbcTemplate constructor parameter already guarantees a DataSource.

    private static final Logger log = LoggerFactory.getLogger(FlowableActHiCommentSchemaRepair.class);

    /**
     * Mirrors {@code deploy/init-scripts/00-schema/31-widen-flowable-act-hi-comment-columns.sql}.
     */
    private static final String WIDEN_FULL_MSG_BLOCK = """
            DO $widen_full_msg$
            DECLARE
                dt text;
            BEGIN
                SELECT c.data_type INTO dt
                FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = 'act_hi_comment'
                  AND c.column_name = 'full_msg_';
                IF dt IS NULL THEN
                    RETURN;
                END IF;
                IF dt = 'bytea' THEN
                    RETURN;
                END IF;
                EXECUTE $sql$
                    ALTER TABLE act_hi_comment
                    ALTER COLUMN full_msg_ TYPE bytea
                    USING CASE
                        WHEN full_msg_ IS NULL THEN NULL::bytea
                        ELSE convert_to(full_msg_::text, 'UTF8')
                    END
                $sql$;
            END
            $widen_full_msg$;
            """;

    /** Post-condition for {@link #verifyWidened()}: information_schema.columns.data_type per column. */
    private static final Map<String, String> EXPECTED_COLUMN_TYPES = Map.of(
            "message_", "text",
            "action_", "character varying",
            "type_", "character varying",
            "user_id_", "character varying",
            "full_msg_", "bytea");

    /**
     * Identity-link columns widened to varchar(4000) by
     * {@code 00-schema/30-widen-flowable-identitylink-columns.sql}. Long virtual group ids and
     * scope ids overflow Flowable's stock varchar(255) and fail task completion with
     * "value too long for type character varying(255)".
     */
    private static final List<String> IDENTITYLINK_TABLES =
            List.of("act_ru_identitylink", "act_hi_identitylink");

    private static final List<String> IDENTITYLINK_COLUMNS = List.of(
            "group_id_", "type_", "user_id_", "scope_id_",
            "sub_scope_id_", "scope_type_", "scope_definition_id_");

    private final JdbcTemplate jdbcTemplate;

    public FlowableActHiCommentSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgreSQL()) {
            return;
        }
        if (!actHiCommentExists()) {
            // Flowable has not created its history tables yet (first boot with an empty schema).
            // Nothing to widen; Flowable will create them with its own defaults and the next boot
            // applies this repair. This is the one genuinely benign case.
            log.debug("act_hi_comment not present yet; skipping widen until Flowable creates it");
            return;
        }
        // Beyond this point a failure means the widen did NOT take effect, which resurfaces as
        // "value too long for type character varying(255)" on the first task completion — in
        // production, hours after the only clue (a log line) scrolled past. Fail startup instead.
        jdbcTemplate.execute("ALTER TABLE act_hi_comment ALTER COLUMN message_ TYPE TEXT");
        jdbcTemplate.execute("ALTER TABLE act_hi_comment ALTER COLUMN action_ TYPE VARCHAR(4000)");
        jdbcTemplate.execute("ALTER TABLE act_hi_comment ALTER COLUMN type_ TYPE VARCHAR(4000)");
        jdbcTemplate.execute("ALTER TABLE act_hi_comment ALTER COLUMN user_id_ TYPE VARCHAR(4000)");
        jdbcTemplate.execute(WIDEN_FULL_MSG_BLOCK);
        widenIdentityLinks();
        verifyWidened();
        log.debug("Flowable act_hi_comment and identitylink columns widened and verified (PostgreSQL)");
    }

    /**
     * Same story as act_hi_comment, one table further along: init script {@code 30-} runs before
     * Flowable has created act_*, so on a freshly built database (dev wipes its volume routinely)
     * the identity-link widening never applies and the columns stay at Flowable's stock
     * varchar(255). Re-apply it here, after the datasource is up.
     *
     * <p>{@code ALTER TABLE IF EXISTS} because act_ru_identitylink and act_hi_identitylink are
     * created at slightly different points; a missing table on first boot is benign and the next
     * boot picks it up.
     */
    private void widenIdentityLinks() {
        for (String table : IDENTITYLINK_TABLES) {
            for (String column : IDENTITYLINK_COLUMNS) {
                jdbcTemplate.execute(
                        "ALTER TABLE IF EXISTS " + table + " ALTER COLUMN " + column + " TYPE VARCHAR(4000)");
            }
        }
    }

    /**
     * Assert the post-condition rather than assuming the ALTERs took effect. Without this the
     * class silently "succeeded" even when the columns were untouched.
     */
    private void verifyWidened() {
        EXPECTED_COLUMN_TYPES.forEach((column, expectedType) -> {
            String actual = jdbcTemplate.queryForObject("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'act_hi_comment'
                      AND column_name = ?
                    """, String.class, column);
            if (!expectedType.equals(actual)) {
                throw new IllegalStateException(
                        "act_hi_comment." + column + " is '" + actual + "' but must be '" + expectedType
                                + "'. Task completion would fail at runtime with "
                                + "'value too long for type character varying(255)'. "
                                + "Check deploy/init-scripts/00-schema/31-widen-flowable-act-hi-comment-columns.sql.");
            }
        });

        for (String table : IDENTITYLINK_TABLES) {
            for (String column : IDENTITYLINK_COLUMNS) {
                Integer length = jdbcTemplate.queryForObject("""
                        SELECT character_maximum_length FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """, Integer.class, table, column);
                // null = the table does not exist yet on this boot, which widenIdentityLinks()
                // deliberately tolerates. Only an existing-but-narrow column is a failure.
                if (length != null && length != 4000) {
                    throw new IllegalStateException(
                            table + "." + column + " is varchar(" + length + ") but must be varchar(4000). "
                                    + "Long virtual group ids would fail task completion with "
                                    + "'value too long for type character varying(255)'. "
                                    + "Check deploy/init-scripts/00-schema/30-widen-flowable-identitylink-columns.sql.");
                }
            }
        }
    }

    private boolean actHiCommentExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'act_hi_comment')",
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private boolean isPostgreSQL() {
        try (Connection c = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            return "PostgreSQL".equalsIgnoreCase(md.getDatabaseProductName());
        } catch (Exception e) {
            log.debug("Skipping act_hi_comment repair (could not read DB product): {}", e.getMessage());
            return false;
        }
    }
}
