import { QueryRunner } from 'typeorm'
import { Migration } from '../../migration'

/**
 * HERMES-PATCH-030 —— 建 hermes_piece_block（自研 piece 启停的存储）。
 *
 * 背景见 app/pieces/hermes-piece-block.entity.ts。一句话：上游
 * DropPlatformPieceFilters1809000000000 删掉了 platform 级 piece 过滤，
 * 替代品 piece_set 属 EE 已剥离，Admin Center 的启停开关因此失去落点。
 *
 * 非破坏性（只建表），故 breaking = false：老版本 admin-center 连着新库也不会因它出错。
 */
export class HermesCreatePieceBlock1826000000000 implements Migration {
    name = 'HermesCreatePieceBlock1826000000000'
    breaking = false
    release = '0.88.0'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            CREATE TABLE IF NOT EXISTS "hermes_piece_block" (
                "pieceName" character varying NOT NULL,
                "blockedAt" timestamp with time zone NOT NULL DEFAULT now(),
                "blockedBy" character varying,
                CONSTRAINT "pk_hermes_piece_block" PRIMARY KEY ("pieceName")
            )
        `)
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query('DROP TABLE IF EXISTS "hermes_piece_block"')
    }
}
