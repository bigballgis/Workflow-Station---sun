import { isNil } from '@activepieces/core-utils'
import {
    DataSource,
    EntitySchema,
} from 'typeorm'
import { AppConnectionEntity } from '../app-connection/app-connection.entity'
import { AuditEventEntity } from '../audit-logs/audit-event-entity'
import { UserIdentityEntity } from '../authentication/user-identity/user-identity-entity'
import { FileEntity } from '../file/file.entity'
import { FlagEntity } from '../flags/flag.entity'
import { FlowEntity } from '../flows/flow/flow.entity'
import { FlowRunEntity } from '../flows/flow-run/flow-run-entity'
import { WaitpointEntity } from '../flows/flow-run/waitpoint/waitpoint-entity'
import { FlowVersionEntity } from '../flows/flow-version/flow-version-entity'
import { FolderEntity } from '../flows/folder/folder.entity'
import { system } from '../helper/system/system'
import { AppSystemProp } from '../helper/system/system-props'
import { PieceMetadataEntity } from '../pieces/metadata/piece-metadata-entity'
import { PlatformEntity } from '../platform/platform.entity'
import { ConcurrencyPoolEntity } from '../project/concurrency-pool.entity'
import { ProjectEntity } from '../project/project-entity'
import { ProjectMemberEntity } from '../project/project-member.entity'
import { ProjectRoleEntity } from '../project/project-role.entity'
import { SigningKeyEntity } from '../signing-key/signing-key-entity'
import { StoreEntryEntity } from '../store-entry/store-entry-entity'
import { AppEventRoutingEntity } from '../trigger/app-event-routing/app-event-routing.entity'
import { TriggerEventEntity } from '../trigger/trigger-events/trigger-event.entity'
import { TriggerSourceEntity } from '../trigger/trigger-source/trigger-source-entity'
import { UserEntity } from '../user/user-entity'
import { VariableEntity } from '../variable/variable.entity'
import { DatabaseType } from './database-type'
import { createPGliteDataSource } from './pglite-connection'
import { createPostgresDataSource } from './postgres-connection'

const databaseType = system.get(AppSystemProp.DB_TYPE)

// HERMES: EE strip for 0.88 (FR-A03/FR-D2). All ee/* entities removed with the
// app/ee directory; feature-domain entities (ai/mcp/tables/knowledge-base/
// tool-search/template/teams-bot/agents/analytics/event-destinations/
// user-invitations) removed with their domains. Their tables may still exist in
// old databases from historical migrations — un-managing them causes no schema
// change. CE rewrites keep SigningKey/AuditEvent/ProjectRole/ProjectMember/
// ConcurrencyPool entities alive at their new CE paths (0.84 parity).
function getEntities(): EntitySchema<unknown>[] {
    return [
        TriggerEventEntity,
        AppEventRoutingEntity,
        FileEntity,
        FlagEntity,
        FlowEntity,
        FlowVersionEntity,
        FlowRunEntity,
        ProjectEntity,
        StoreEntryEntity,
        UserEntity,
        AppConnectionEntity,
        VariableEntity,
        FolderEntity,
        PieceMetadataEntity,
        PlatformEntity,
        ProjectRoleEntity,
        UserIdentityEntity,
        TriggerSourceEntity,
        WaitpointEntity,
        // HERMES: CE rewrites (formerly ee) — see EE_REMOVAL_PLAN G6/G13 + AG-06
        SigningKeyEntity,
        ConcurrencyPoolEntity,
        ProjectMemberEntity,
        AuditEventEntity,
    ]
}

export const commonProperties = {
    subscribers: [],
    entities: getEntities(),
}

const DB_GLOBAL_KEY = '__AP_DB_CONNECTION__'

function getPersistedConnection(): DataSource | null {
    return ((globalThis as Record<string, unknown>)[DB_GLOBAL_KEY] as DataSource) ?? null
}

function setPersistedConnection(ds: DataSource | null): void {
    (globalThis as Record<string, unknown>)[DB_GLOBAL_KEY] = ds
}

const createDataSource = (): DataSource => {
    switch (databaseType) {
        case DatabaseType.PGLITE:
            return createPGliteDataSource()
        case DatabaseType.POSTGRES:
        default:
            return createPostgresDataSource()
    }
}

export const databaseConnection = (): DataSource => {
    const existing = getPersistedConnection()
    if (!isNil(existing)) {
        return existing
    }
    const ds = createDataSource()
    setPersistedConnection(ds)
    return ds
}

export function resetDatabaseConnection(): void {
    setPersistedConnection(null)
}
