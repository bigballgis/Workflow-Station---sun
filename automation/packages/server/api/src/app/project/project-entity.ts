// HERMES: tables domain (table/field/record/cell/table_webhook) deleted per FR-D2 —
// its entities are no longer registered, so relations pointing at them break TypeORM's
// metadata build at startup. Relations and their imports removed with the domain.
import {
    AppConnection,
    ConcurrencyPool,
    File,
    Flow,
    Folder,
    Platform,
    Project,
    TriggerEvent,
    User,
} from '@activepieces/shared'
import { EntitySchema } from 'typeorm'
import {
    ApIdSchema,
    BaseColumnSchemaPart,
} from '../database/database-common'

type ProjectSchema = Project & {
    owner: User
    flows: Flow[]
    files: File[]
    folders: Folder[]
    events: TriggerEvent[]
    appConnections: AppConnection[]
    platform: Platform
    pool?: ConcurrencyPool | null
}

export const ProjectEntity = new EntitySchema<ProjectSchema>({
    name: 'project',
    columns: {
        ...BaseColumnSchemaPart,
        deleted: {
            type: 'timestamp with time zone',
            deleteDate: true,
            nullable: true,
        },
        ownerId: ApIdSchema,
        displayName: {
            type: String,
        },
        type: {
            type: String,
            nullable: false,
        },
        platformId: {
            ...ApIdSchema,
        },
        externalId: {
            type: String,
            nullable: true,
        },
        maxConcurrentJobs: {
            type: Number,
            nullable: true,
        },
        icon: {
            type: 'jsonb',
            nullable: false,
        },
        releasesEnabled: {
            type: Boolean,
            nullable: false,
            default: false,
        },
        notifyFlowOwnerOnFailure: {
            type: Boolean,
            nullable: false,
            default: false,
        },
        metadata: {
            type: 'jsonb',
            nullable: true,
        },
        poolId: {
            ...ApIdSchema,
            nullable: true,
        },
        // HERMES: the piece_set entity/domain is EE and was removed (FR-A03). The column
        // itself stays mapped — the MIT migration still creates it — but there is no
        // relation to resolve, so TypeORM never looks for a piece_set entity.
        // The matching FK that 1807 created is dropped by
        // HermesDropProjectPieceSetFk1825000000000, otherwise check-migrations reports
        // it as drift (a constraint in the database that no entity declares).
        pieceSetId: {
            ...ApIdSchema,
            nullable: true,
        },
        workerGroupId: {
            type: String,
            nullable: true,
        },
        executionDataRetentionDays: {
            type: Number,
            nullable: true,
        },
    },
    indices: [
        {
            name: 'idx_project_owner_id',
            columns: ['ownerId'],
            unique: false,
        },
        {
            name: 'idx_project_platform_id_external_id',
            columns: ['platformId', 'externalId'],
            where: 'deleted IS NULL',
            unique: true,
        },
        {
            name: 'idx_project_platform_id',
            columns: ['platformId'],
            unique: false,
        },
        {
            name: 'idx_project_pool_id',
            columns: ['poolId'],
            unique: false,
        },
        {
            name: 'idx_project_piece_set_id',
            columns: ['pieceSetId'],
            unique: false,
        },
        {
            name: 'idx_project_worker_group',
            columns: ['workerGroupId'],
            unique: false,
        },
        {
            name: 'idx_project_execution_data_retention_days',
            columns: ['executionDataRetentionDays'],
            where: '"executionDataRetentionDays" IS NOT NULL',
            unique: false,
        },
    ],
    relations: {
        owner: {
            type: 'many-to-one',
            target: 'user',
            joinColumn: {
                name: 'ownerId',
                foreignKeyConstraintName: 'fk_project_owner_id',
            },
        },
        platform: {
            type: 'many-to-one',
            target: 'platform',
            cascade: true,
            onDelete: 'RESTRICT',
            onUpdate: 'RESTRICT',
            joinColumn: {
                name: 'platformId',
                foreignKeyConstraintName: 'fk_project_platform_id',
            },
        },
        folders: {
            type: 'one-to-many',
            target: 'folder',
            inverseSide: 'project',
        },
        appConnections: {
            type: 'one-to-many',
            target: 'app_connection',
            inverseSide: 'project',
        },
        events: {
            type: 'one-to-many',
            target: 'trigger_event',
            inverseSide: 'project',
        },
        files: {
            type: 'one-to-many',
            target: 'file',
            inverseSide: 'project',
        },
        flows: {
            type: 'one-to-many',
            target: 'flow',
            inverseSide: 'project',
        },
        pool: {
            type: 'many-to-one',
            target: 'concurrency_pool',
            onDelete: 'SET NULL',
            nullable: true,
            joinColumn: {
                name: 'poolId',
                foreignKeyConstraintName: 'fk_project_pool_id',
            },
        },
    },
})
