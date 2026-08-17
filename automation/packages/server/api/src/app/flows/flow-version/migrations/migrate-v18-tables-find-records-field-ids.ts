import { isNil } from '@activepieces/core-utils'
import {
    FlowActionType,
    flowStructureUtil,
    FlowVersion,
    Step,
} from '@activepieces/shared'
import { databaseConnection } from '../../../database/database-connection'
import { Migration } from '.'

// HERMES: tables domain removed (AG-EE / G16). The old FieldEntity repo lookup is inlined
// below as a raw query against the physical "field" table — nothing registers that entity
// with TypeORM anymore and the tables domain must not be re-created. Databases that never
// had the tables feature have no "field" table; that is equivalent to upstream's empty
// result set (unknown field ids pass through unchanged), so the lookup is skipped there
// and the flow-schema upgrade chain still advances.
async function fetchLegacyFieldExternalIds(fieldIds: string[]): Promise<Record<string, string>> {
    if (fieldIds.length === 0) {
        return {}
    }
    const connection = databaseConnection()
    const regRows: { reg: string | null }[] = await connection.query('SELECT to_regclass(\'field\') AS reg')
    if (isNil(regRows[0]?.reg)) {
        return {}
    }
    const rows: { id: string, externalId: string }[] = await connection.query(
        'SELECT "id", "externalId" FROM "field" WHERE "id" = ANY($1)',
        [fieldIds],
    )
    const fieldIdToExternalId: Record<string, string> = {}
    for (const row of rows) {
        fieldIdToExternalId[row.id] = row.externalId
    }
    return fieldIdToExternalId
}

const TABLES_PIECE_NAME = '@activepieces/piece-tables'
const TABLES_PIECE_VERSION = '0.3.0'
const FIND_RECORDS_ACTION = 'tables-find-records'

function collectFieldIdsFromFilters(flowVersion: FlowVersion): string[] {
    const fieldIds: string[] = []

    flowStructureUtil.getAllSteps(flowVersion.trigger).forEach((step) => {
        if (step.type !== FlowActionType.PIECE || step.settings.pieceName !== TABLES_PIECE_NAME) {
            return
        }
        if (step.settings.actionName !== FIND_RECORDS_ACTION) {
            return
        }

        const input = step.settings?.input as Record<string, unknown> | undefined
        const filters = input?.filters as Record<string, unknown> | undefined
        const filtersArray = filters?.filters as { field?: { id?: string } }[] | undefined
        if (!Array.isArray(filtersArray)) {
            return
        }

        for (const filter of filtersArray) {
            if (filter.field?.id) {
                fieldIds.push(filter.field.id)
            }
        }
    })

    return fieldIds
}

export const migrateV18TablesFieldIds: Migration = {
    targetSchemaVersion: '18',
    migrate: async (flowVersion: FlowVersion): Promise<FlowVersion> => {
        const fieldIds = collectFieldIdsFromFilters(flowVersion)
        if (fieldIds.length === 0) {
            return {
                ...flowVersion,
                schemaVersion: '19',
            }
        }

        // HERMES: inlined raw lookup replaces the deleted tables-domain fieldRepo (see above).
        const fieldIdToExternalId = await fetchLegacyFieldExternalIds([...new Set(fieldIds)])

        const newVersion = flowStructureUtil.transferFlow(flowVersion, (step: Step) => {
            if (step.type !== FlowActionType.PIECE || step.settings.pieceName !== TABLES_PIECE_NAME) {
                return step
            }

            if (step.settings.actionName !== FIND_RECORDS_ACTION) {
                return {
                    ...step,
                    settings: {
                        ...step.settings,
                        pieceVersion: TABLES_PIECE_VERSION,
                    },
                }
            }

            const input = step.settings?.input as Record<string, unknown> | undefined
            const filters = input?.filters as Record<string, unknown> | undefined
            const filtersArray = filters?.filters as { field?: { id?: string, type?: string, name?: string } }[] | undefined
            if (!Array.isArray(filtersArray)) {
                return {
                    ...step,
                    settings: {
                        ...step.settings,
                        pieceVersion: TABLES_PIECE_VERSION,
                    },
                }
            }

            const migratedFilters = filtersArray.map((filter) => {
                if (!filter.field?.id || !fieldIdToExternalId[filter.field.id]) {
                    return filter
                }
                return {
                    ...filter,
                    field: {
                        ...filter.field,
                        id: fieldIdToExternalId[filter.field.id],
                    },
                }
            })

            return {
                ...step,
                settings: {
                    ...step.settings,
                    pieceVersion: TABLES_PIECE_VERSION,
                    input: {
                        ...input,
                        filters: {
                            ...filters,
                            filters: migratedFilters,
                        },
                    },
                },
            }
        })

        return {
            ...newVersion,
            schemaVersion: '19',
        }
    },
}
