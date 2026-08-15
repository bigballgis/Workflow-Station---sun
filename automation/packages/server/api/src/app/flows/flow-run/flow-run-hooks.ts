import { isManualPieceTrigger, isNil } from '@activepieces/core-utils'
import { FlowRun, FlowTriggerType, isFlowRunStateTerminal, RunEnvironment, WebsocketClientEvent } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { websocketService } from '../../core/websockets.service'
import { flowVersionService } from '../flow-version/flow-version.service'

export const flowRunHooks = (log: FastifyBaseLogger) => ({
    async onFinish(flowRun: FlowRun): Promise<void> {
        if (!isFlowRunStateTerminal({
            status: flowRun.status,
            ignoreInternalError: true,
        })) {
            return
        }
        const flowVersion = await flowVersionService(log).getOne(flowRun.flowVersionId)
        const isPieceTrigger = !isNil(flowVersion) && flowVersion.trigger.type === FlowTriggerType.PIECE && !isNil(flowVersion.trigger.settings.triggerName)
        const isManualTrigger = isPieceTrigger && isManualPieceTrigger({ pieceName: flowVersion.trigger.settings.pieceName, triggerName: flowVersion.trigger.settings.triggerName })
        if (flowRun.environment === RunEnvironment.TESTING || isManualTrigger) {
            websocketService.to(flowRun.projectId).emit(WebsocketClientEvent.UPDATE_RUN_PROGRESS, {
                flowRun,
            })
        }
        // HERMES: EE alerts removed (AG-EE / G10). alertsService was an EE-only,
        // paid-edition feature with no CE consumer; CE never sent alerts.
        // HERMES: paid-edition AI-usage credit tracking (flowRunAiUsageTracker) and
        // production-run AP_CREDITS billing removed (AG-EE) — both were gated on
        // CLOUD/ENTERPRISE editions and are billing-only; CE has no credit ledger.
    },
})
