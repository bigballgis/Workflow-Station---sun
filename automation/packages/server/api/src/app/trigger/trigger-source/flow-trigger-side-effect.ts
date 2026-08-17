import { ActivepiecesError, ErrorCode, FlowId, FlowVersionId, isNil, tryCatch } from '@activepieces/core-utils'
import {
    TriggerBase,
    TriggerStrategy,
    WebhookRenewStrategy,
} from '@activepieces/pieces-framework'
import { ApEnvironment, EngineResponse, EngineResponseStatus, ExecuteTriggerResponse, FlowTriggerType, LATEST_JOB_DATA_SCHEMA_VERSION, ScheduleOptions, TriggerHookType, TriggerSourceScheduleType, WorkerJobType } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { system } from '../../helper/system/system'
import { AppSystemProp } from '../../helper/system/system-props'
import { projectService } from '../../project/project-service'
import { jobQueue, JobType } from '../../workers/job-queue/job-queue'
import { userInteractionWatcher } from '../../workers/user-interaction-watcher'
import { appEventRoutingService } from '../app-event-routing/app-event-routing.service'

const environment = system.getOrThrow<ApEnvironment>(AppSystemProp.ENVIRONMENT)

export const flowTriggerSideEffect = (log: FastifyBaseLogger) => {
    return {
        async enable(params: EnableFlowTriggerParams): Promise<ActiveTriggerReturn> {
            if (environment === ApEnvironment.TESTING) {
                return {
                    scheduleOptions: undefined,
                }
            }
            const { flowId, flowVersionId, projectId, simulate, pieceTrigger, isRepublish } = params

            const platformId = await projectService(log).getPlatformId(projectId)
            const engineHelperResponse = await userInteractionWatcher.submitAndWaitForResponse<EngineResponse<ExecuteTriggerResponse<TriggerHookType.ON_ENABLE>>>({
                jobType: WorkerJobType.EXECUTE_TRIGGER_HOOK,
                hookType: TriggerHookType.ON_ENABLE,
                flowId,
                flowVersionId,
                platformId,
                projectId,
                test: simulate,
                isRepublish,
            }, log)

            assertEngineResponseIsOk(engineHelperResponse, flowId, flowVersionId)

            switch (pieceTrigger.type) {
                case TriggerStrategy.APP_WEBHOOK: {
                    return handleAppWebhookTrigger({
                        engineHelperResponse,
                        log,
                        ...params,
                    })
                }
                case TriggerStrategy.WEBHOOK: {
                    return handleWebhookTrigger({
                        engineHelperResponse,
                        log,
                        ...params,
                    })
                }
                case TriggerStrategy.POLLING: {
                    return handlePollingTrigger({
                        engineHelperResponse,
                        log,
                        ...params,
                    })
                }
                case TriggerStrategy.MANUAL: {
                    return {
                        scheduleOptions: undefined,
                    }
                }
            }
        },
        async disable(params: DisableFlowTriggerParams): Promise<void> {
            if (environment === ApEnvironment.TESTING) {
                return
            }
            const { flowId, flowVersionId, projectId, simulate, pieceTrigger } = params
            const platformId = await projectService(log).getPlatformId(projectId)
            const { error, data: engineHelperResponse } = await tryCatch(
                () => userInteractionWatcher.submitAndWaitForResponse<EngineResponse<ExecuteTriggerResponse<TriggerHookType.ON_DISABLE>>>({
                    jobType: WorkerJobType.EXECUTE_TRIGGER_HOOK,
                    hookType: TriggerHookType.ON_DISABLE,
                    flowId,
                    flowVersionId,
                    test: simulate,
                    projectId,
                    platformId,
                }, log),
            )
            if (!isNil(error)) {
                if (!params.ignoreError) {
                    throw error
                }
                log.warn({ flow: { id: flowId }, error: error.message }, '[flowTriggerSideEffect#disable] Ignored error during trigger disable')
            }
            else if (!params.ignoreError) {
                assertEngineResponseIsOk(engineHelperResponse!, flowId, flowVersionId)
            }
            switch (pieceTrigger.type) {
                case TriggerStrategy.APP_WEBHOOK:
                    await appEventRoutingService.deleteListeners({
                        projectId,
                        flowId,
                    })
                    break
                case TriggerStrategy.WEBHOOK: {
                    const renewConfiguration = pieceTrigger.renewConfiguration
                    if (renewConfiguration?.strategy === WebhookRenewStrategy.CRON) {
                        await jobQueue(log).removeRepeatingJob({
                            flowVersionId,
                        })
                    }
                    break
                }
                case TriggerStrategy.POLLING:
                    await jobQueue(log).removeRepeatingJob({
                        flowVersionId,
                    })
                    break
                case TriggerStrategy.MANUAL:
                    break
            }
        },

    }
}

// HERMES-PATCH-012 摘掉了 /v1/app-events/:pieceUrl —— APP_WEBHOOK 策略唯一的入口。
// 这条链路的其余部分（app_event_routing 表与 service、worker 的 getAppWebhookUrl、上面 disable
// 分支里的 deleteListeners）都保留着，因为它们是与具体 piece 无关的通用逻辑，删了编译不过。
// 于是留下一个陷阱：一旦有人把 APP_WEBHOOK 策略的件加进 hermes/pieces.json 白名单，用户会拿到
// 一个**必然 404 且毫无线索**的 webhook URL —— 启用全绿、监听器行也写进去了，只是事件永远不来。
//
// 与其在运行时无声失败，不如在启用时当场炸掉。这里是唯一同时拿得到 flowId 和 pieceName 的地方，
// 报错能直接指向真正的决策点（012 与白名单），而不是丢一个 404 让人从头猜。
// 原先写 app_event_routing 行的逻辑一并删除：既然到不了这一步，留着只会让人误以为它还有效。
function handleAppWebhookTrigger({ flowId, pieceName }: ActiveTriggerParams): Promise<ActiveTriggerReturn> {
    throw new ActivepiecesError({
        code: ErrorCode.FEATURE_DISABLED,
        params: {
            message: `App-webhook triggers are not available in this deployment: the /v1/app-events endpoint was removed by HERMES-PATCH-012. Piece "${pieceName}" (flow ${flowId}) uses TriggerStrategy.APP_WEBHOOK, so its events could never arrive. Either drop that piece from hermes/pieces.json, or revert HERMES-PATCH-012 and re-expose the endpoint deliberately.`,
        },
    })
}

async function handleWebhookTrigger({ flowId, flowVersionId, projectId, pieceTrigger, log }: ActiveTriggerParams): Promise<ActiveTriggerReturn> {
    const renewConfiguration = pieceTrigger.renewConfiguration
    switch (renewConfiguration?.strategy) {
        case WebhookRenewStrategy.CRON: {
            const platformId = await projectService(log).getPlatformId(projectId)
            await jobQueue(log).add({
                id: flowVersionId,
                type: JobType.REPEATING,
                data: {
                    schemaVersion: LATEST_JOB_DATA_SCHEMA_VERSION,
                    projectId,
                    flowVersionId,
                    flowId,
                    jobType: WorkerJobType.RENEW_WEBHOOK,
                    platformId,
                },
                scheduleOptions: {
                    type: TriggerSourceScheduleType.CRON_EXPRESSION,
                    cronExpression: renewConfiguration.cronExpression,
                    timezone: 'UTC',
                },
            })
            break
        }
        default:
            break
    }
    return {
        scheduleOptions: undefined,
    }
}

async function handlePollingTrigger({ engineHelperResponse, flowId, flowVersionId, projectId, log }: ActiveTriggerParams): Promise<ActiveTriggerReturn> {
    const pollIntervalMinutes = system.getNumberOrThrow(AppSystemProp.TRIGGER_DEFAULT_POLL_INTERVAL)
    const defaultScheduleOptions: ScheduleOptions = {
        type: TriggerSourceScheduleType.INTERVAL,
        intervalMs: pollIntervalMinutes * 60_000,
    }
    const scheduleOptions = engineHelperResponse.response?.scheduleOptions ?? defaultScheduleOptions
    const platformId = await projectService(log).getPlatformId(projectId)
    await jobQueue(log).add({
        id: flowVersionId,
        type: JobType.REPEATING,
        data: {
            schemaVersion: LATEST_JOB_DATA_SCHEMA_VERSION,
            projectId,
            flowVersionId,
            flowId,
            triggerType: FlowTriggerType.PIECE,
            jobType: WorkerJobType.EXECUTE_POLLING,
            platformId,
        },
        scheduleOptions,
    })
    return {
        scheduleOptions,
    }
}

function assertEngineResponseIsOk(engineHelperResponse: EngineResponse<ExecuteTriggerResponse<TriggerHookType.ON_ENABLE | TriggerHookType.ON_DISABLE>>, flowId: FlowId, flowVersionId: FlowVersionId) {
    if (isNil(engineHelperResponse) || engineHelperResponse.status !== EngineResponseStatus.OK) {
        throw new ActivepiecesError({
            code: ErrorCode.TRIGGER_UPDATE_STATUS,
            params: {
                flowId,
                flowVersionId,
                standardOutput: '',
                standardError: engineHelperResponse?.error ?? 'Engine response is undefined',
            },
        }, `flowId=${flowId} standardError=${engineHelperResponse?.error ?? 'Engine response is undefined'}`)
    }
}



type EnableFlowTriggerParams = {
    flowId: FlowId
    flowVersionId: FlowVersionId
    pieceName: string
    projectId: string
    pieceTrigger: TriggerBase
    simulate: boolean
    isRepublish?: boolean
}

type DisableFlowTriggerParams = EnableFlowTriggerParams & {
    ignoreError: boolean
}

type ActiveTriggerParams = EnableFlowTriggerParams & {
    log: FastifyBaseLogger
    engineHelperResponse: EngineResponse<ExecuteTriggerResponse<TriggerHookType.ON_ENABLE>>
}

type ActiveTriggerReturn = {
    scheduleOptions?: ScheduleOptions
}