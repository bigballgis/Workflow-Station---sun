import { ApplicationEvent, Platform } from '@activepieces/shared'
import { EntitySchema } from 'typeorm'
import { BaseColumnSchemaPart } from '../database/database-common'

// CE reimplementation of the (removed) ee audit-event entity, byte-identical in
// schema: the audit_event table plus its indices are created by MIT-area
// migrations (1707614902283-AddAuditEvents and successors) that survived the EE
// strip — same situation as signing_key (G15 pattern) — so re-mapping the entity
// needs no new migration.
//
// HERMES-PATCH-027: because this entity IS registered in CE (unlike upstream,
// where audit-logs is EE-only and the table is therefore never mapped), TypeORM's
// schema builder now diffs audit_event — and it wanted to DROP the DESC index
// added by AddAuditEventPlatformIdCreatedIdIndex1820000000000. See the note on
// that index below.
type AuditEventSchema = ApplicationEvent & {
    platform: Platform
}

export const AuditEventEntity = new EntitySchema<AuditEventSchema>({
    name: 'audit_event',
    columns: {
        ...BaseColumnSchemaPart,
        platformId: {
            type: String,
        },
        projectId: {
            type: String,
            nullable: true,
        },
        action: {
            type: String,
        },
        userEmail: {
            type: String,
            nullable: true,
        },
        projectDisplayName: {
            type: String,
            nullable: true,
        },
        data: {
            type: 'jsonb',
        },
        ip: {
            type: String,
            nullable: true,
        },
        userId: {
            type: String,
            nullable: true,
        },
    },
    indices: [
        {
            name: 'audit_event_platform_id_project_id_user_id_action_idx',
            columns: ['platformId', 'projectId', 'userId', 'action'],
        },
        {
            name: 'audit_event_platform_id_user_id_action_idx',
            columns: ['platformId', 'userId', 'action'],
        },
        {
            name: 'audit_event_platform_id_action_idx',
            columns: ['platformId', 'action'],
        },
        {
            // Owned by AddAuditEventPlatformIdCreatedIdIndex1820000000000, which
            // creates it as ("platformId", "created" DESC, "id" DESC) — the
            // covering index for the audit-log list query (platformId filter +
            // buildPaginator's created/id DESC ordering). EntitySchema has no way
            // to express per-column sort order, so this declaration can only name
            // the index; the migration remains the single source of truth for its
            // definition.
            //
            // synchronize: false is exactly that statement to TypeORM's schema
            // builder: dropOldIndices() skips it (no spurious DROP INDEX in
            // `migration:generate --check`) and createNewIndices()/Table.create()
            // never emit it (both filter on synchronize === true). Scoping the
            // opt-out to this one index — rather than putting synchronize: false
            // on the whole EntitySchema — keeps every column, relation and other
            // index of audit_event under drift detection.
            name: 'audit_event_platform_id_created_id_desc_idx',
            columns: ['platformId', 'created', 'id'],
            synchronize: false,
        },
    ],
    relations: {
        platform: {
            type: 'many-to-one',
            target: 'platform',
            cascade: true,
            onDelete: 'CASCADE',
            joinColumn: {
                name: 'platformId',
                referencedColumnName: 'id',
            },
        },
    },
})
