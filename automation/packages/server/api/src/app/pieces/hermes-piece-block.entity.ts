import { EntitySchema } from 'typeorm'

/**
 * HERMES-PATCH-030 —— 自研的 piece 启停（替代上游删掉的 platform 级过滤）。
 *
 * 上游 0.88 用 DropPlatformPieceFilters1809000000000 删掉了
 * platform.filteredPieceNames / filteredPieceBehavior，替代机制 piece_set 属 EE、
 * 已随 EE 剥离。Admin Center 的「停用某个 piece」因此两头落空（2026-08 UAT 事故）。
 *
 * 这里不把那两列加回 platform，而是新建 HERMES 自有表，理由：
 *   1. 复活一个上游明确删掉的列，会让后来人读迁移链时无从判断谁是权威；
 *   2. `hermes_` 前缀的表天然不与上游冲突 —— 我们是纯自维护分叉（D13），
 *      但「不去动上游实体」仍是成本最低的边界；
 *   3. 顺带能记下**谁**在**什么时候**停用的，platform 上那个字符串数组做不到，
 *      而本仓库对管理动作一向要求可审计。
 *
 * 语义与 0.84 一致：**只影响设计器目录（list），不影响运行**。
 * piece-metadata-service 的 get() 刻意不过滤，所以已经引用了被停用 piece 的存量 flow
 * 照常加载、照常执行 —— 停用是「不让人再选它」，不是「把已有的打断」。
 */
export type HermesPieceBlockSchema = {
    pieceName: string
    blockedAt: Date
    blockedBy: string | null
}

export const HermesPieceBlockEntity = new EntitySchema<HermesPieceBlockSchema>({
    name: 'hermes_piece_block',
    columns: {
        pieceName: {
            type: String,
            primary: true,
            nullable: false,
        },
        blockedAt: {
            type: 'timestamp with time zone',
            nullable: false,
            default: () => 'now()',
        },
        // 平台侧操作人（admin-center 的 userId/username）。历史行可能为空。
        blockedBy: {
            type: String,
            nullable: true,
        },
    },
})
