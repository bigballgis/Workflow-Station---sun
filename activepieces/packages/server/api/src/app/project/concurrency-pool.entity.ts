import { ConcurrencyPool, Project } from '@activepieces/shared'
import { EntitySchema } from 'typeorm'
import { ApIdSchema, BaseColumnSchemaPart } from '../database/database-common'

// HERMES: CE reimplementation of the concurrency_pool TypeORM schema, moved out of app/ee
// (AG-EE / EE_REMOVAL_PLAN G15). Kept (not deleted) because the core `project` table has a
// FK `project.poolId -> concurrency_pool` (verified against the live DB, EE_REMOVAL_PLAN
// §4.4/R12). The table is created by the MIT migration 1775800000000-AddConcurrencyPoolTable.
// The concurrency-pool SERVICE is stubbed to no-op (G5); only the entity/table is retained
// so the project FK resolves.
export type ConcurrencyPoolEntitySchema = ConcurrencyPool & {
    projects: Project[]
}

export const ConcurrencyPoolEntity = new EntitySchema<ConcurrencyPoolEntitySchema>({
    name: 'concurrency_pool',
    columns: {
        ...BaseColumnSchemaPart,
        platformId: ApIdSchema,
        key: { type: String },
        maxConcurrentJobs: { type: Number },
    },
    indices: [
        { name: 'idx_concurrency_pool_platform_key', columns: ['platformId', 'key'], unique: true },
    ],
    relations: {
        projects: {
            type: 'one-to-many',
            target: 'project',
            inverseSide: 'pool',
        },
    },
})
