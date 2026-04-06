package com.workflow.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Flowable creates {@code act_hi_comment} at engine startup with {@code varchar(255)} on several
 * columns. Postgres init script {@code 31-widen-flowable-act-hi-comment-columns.sql} often runs
 * <em>before</em> those tables exist, so the ALTER never applies. Re-run the same idempotent widen
 * after the datasource is up so task completion (history comment insert) does not fail with
 * "value too long for type character varying(255)".
 */
@Component
@Order(Integer.MAX_VALUE)
@ConditionalOnBean(DataSource.class)
public class FlowableActHiCommentSchemaRepair implements ApplicationRunner {

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

    private final JdbcTemplate jdbcTemplate;

    public FlowableActHiCommentSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgreSQL()) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN message_ TYPE TEXT");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN action_ TYPE VARCHAR(4000)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN type_ TYPE VARCHAR(4000)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN user_id_ TYPE VARCHAR(4000)");
            jdbcTemplate.execute(WIDEN_FULL_MSG_BLOCK);
            log.debug("Flowable act_hi_comment columns widened (PostgreSQL)");
        } catch (Exception e) {
            log.warn("Flowable act_hi_comment widen skipped or failed (non-fatal): {}", e.getMessage());
        }
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
