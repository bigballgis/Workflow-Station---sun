import { isNil } from '@activepieces/core-utils'
import { FlowActionType, flowStructureUtil, FlowVersion, Step } from '@activepieces/shared'
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
const TARGET_ACTIONS = ['tables-create-records', 'tables-update-record']

function collectFieldIdsFromFlow(flowVersion: FlowVersion) {
    const fieldIds: string[] = []

    flowStructureUtil.getAllSteps(flowVersion.trigger).forEach((step) => {
        if (step.type !== FlowActionType.PIECE || step.settings.pieceName !== TABLES_PIECE_NAME || !step.settings.pieceVersion.includes('0.1.')) {
            return
        }
        const actionName = step.settings.actionName as string | undefined
        if (!actionName || !TARGET_ACTIONS.includes(actionName)) {
            return
        }

        const input = step.settings?.input as Record<string, unknown>
        const values = input?.values as Record<string, unknown> | undefined
        const fieldsValues = actionName === 'tables-create-records' ? values?.values as Record<string, unknown>[] | undefined : values
        if (!fieldsValues) {
            return
        }
        if (Array.isArray(fieldsValues)) {
            for (const fieldValue of fieldsValues) {
                for (const [fieldId, _value] of Object.entries(fieldValue)) {
                    fieldIds.push(fieldId)
                }
            }
        }
        else {
            for (const fieldId of Object.keys(fieldsValues)) {
                fieldIds.push(fieldId)
            }
        }
    })

    return fieldIds
}

export const migrateV11TablesToV2: Migration = {
    targetSchemaVersion: '11',
    migrate: async (flowVersion: FlowVersion): Promise<FlowVersion> => {
        const fieldIds = collectFieldIdsFromFlow(flowVersion)
        // HERMES: inlined raw lookup replaces the deleted tables-domain fieldRepo (see above).
        const fieldIdToExternalId = await fetchLegacyFieldExternalIds([...new Set(fieldIds)])

        const newVersion = flowStructureUtil.transferFlow(flowVersion, (step: Step) => {
            if (!isTablesStep(step)) {
                return step
            }
            const actionName = step.settings.actionName as string | undefined
            const justUpgradePiece = !isOldTablesStep(step) || isNil(actionName) || !TARGET_ACTIONS.includes(actionName as string)
            const input = step.settings?.input as Record<string, unknown>
            const values = input?.values as Record<string, unknown> | undefined
            const fieldsValue = actionName === 'tables-create-records' ? values?.values as Record<string, unknown> | undefined : values
            if (justUpgradePiece || !fieldsValue) {
                return {
                    ...step,
                    settings: {
                        ...step.settings,
                        pieceVersion: '0.2.10',
                    },
                }
            }
            let stepSettings = JSON.stringify({
                ...step.settings,
                pieceVersion: '0.2.10',
            })
            for (const [fieldId, externalId] of Object.entries(fieldIdToExternalId)) {
                stepSettings = stepSettings.replaceAll(`"${fieldId}"`, `"${externalId}"`)
            }

            return {
                ...step,
                settings: JSON.parse(stepSettings),
            }
        })

        return {
            ...newVersion,
            schemaVersion: '12',
        }
    },
}


function isTablesStep(step: Step): boolean {
    return step.type === FlowActionType.PIECE && step.settings.pieceName === TABLES_PIECE_NAME && (step.settings.pieceVersion.includes('0.1.') || step.settings.pieceVersion.includes('0.2.'))
}

function isOldTablesStep(step: Step): boolean {
    return isTablesStep(step) && step.settings.pieceVersion.includes('0.1.')
}