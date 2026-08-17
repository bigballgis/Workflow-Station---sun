import { QueryRunner } from 'typeorm'
import { Migration } from '../../migration'

/**
 * HERMES patch (HERMES-PATCH-027) — drop the project → piece_set foreign key.
 *
 * The piece_set domain is EE and was removed with the trim (FR-A03): the shared
 * contract (ee/piece-set), the entity and the service are all gone. What survived is
 * the MIT-area migration CreatePieceSetTable1807000000000, which still creates the
 * piece_set table, the project."pieceSetId" column and the FK constraint
 * "fk_project_piece_set_id".
 *
 * project-entity.ts keeps "pieceSetId" mapped as a plain column with no relation to
 * resolve, so TypeORM sees a FK in the database that no entity declares and
 * `migration:generate --check` (npm run test-api → check-migrations) reports drift:
 *     ALTER TABLE "project" DROP CONSTRAINT "fk_project_piece_set_id"
 *
 * We align the database to the entity rather than the other way round, and we do it
 * with the smallest possible change: only the constraint goes. The column,
 * "idx_project_piece_set_id" and the piece_set table itself are all left in place —
 * they are inert (nothing reads or writes them) and dropping them would be an
 * irreversible, data-destroying operation whose only payoff is one fewer NULL column.
 * Re-adding the FK later is a single ALTER; recovering dropped rows is not.
 *
 * No isPGlite() branch is needed: this migration issues no CONCURRENTLY DDL, so it is
 * transaction-safe and runs identically on POSTGRES and PGLITE.
 */
export class HermesDropProjectPieceSetFk1825000000000 implements Migration {
    name = 'HermesDropProjectPieceSetFk1825000000000'
    breaking = false
    release = '0.88.0'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "project"
            DROP CONSTRAINT IF EXISTS "fk_project_piece_set_id"
        `)
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        // Mirrors the definition in CreatePieceSetTable1807000000000. Guarded so a
        // re-run (or a rollback against a database that never lost the constraint) is a
        // no-op, and skipped entirely if piece_set has since been dropped, which would
        // otherwise turn the rollback into a hard failure.
        // to_regclass resolves against the *current* search_path, so this cannot be fooled by a
        // same-named table sitting in another schema (which a bare information_schema lookup on
        // table_name would have matched).
        const [pieceSetTable] = await queryRunner.query(
            'SELECT to_regclass($1) AS oid',
            ['piece_set'],
        ).then((rows: { oid: string | null }[]) => rows.filter(row => row.oid !== null))
        if (!pieceSetTable) {
            return
        }

        // Scoped to *our* project table for the same reason: conname is only unique per relation,
        // so an unqualified lookup could match a constraint on some other schema's table.
        const [existingFk] = await queryRunner.query(
            `SELECT 1 FROM pg_constraint
             WHERE conname = $1 AND conrelid = to_regclass('project')`,
            ['fk_project_piece_set_id'],
        )
        if (existingFk) {
            return
        }

        await queryRunner.query(`
            ALTER TABLE "project"
            ADD CONSTRAINT "fk_project_piece_set_id"
            FOREIGN KEY ("pieceSetId") REFERENCES "piece_set"("id") ON DELETE SET NULL
        `)
    }
}
