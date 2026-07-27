import {
    EngineGenericError,
    EngineResponse,
    EngineResponseStatus,
    ExecuteFlowOperation,
    ExecuteTriggerResponse,
    ExecutionState,
    ExecutionType,
    FlowActionType,
    FlowRunStatus,
    flowStructureUtil,
    GenericStepOutput,
    isFlowRunStateTerminal,
    isNil,
    JobPayload,
    LoopStepOutput,
    ResumePayload,
    ResumeReason,
    StepOutput,
    StepOutputStatus,
    TriggerHookType,
    TriggerPayload,
    tryCatch,
} from '@activepieces/shared'
import { engineFileApi } from '../engine-file-api'
import { EngineConstants, ResolvedBeginExecuteFlowOperation, ResolvedExecuteFlowOperation } from '../handler/context/engine-constants'
import { FlowExecutorContext } from '../handler/context/flow-execution-context'
import { testExecutionContext } from '../handler/context/test-execution-context'
import { flowExecutor } from '../handler/flow-executor'
import { flowRunProgressReporter } from '../helper/flow-run-progress-reporter'
import { triggerHelper } from '../helper/trigger-helper'
import { workerSocket } from '../worker-socket'

export const flowOperation = {
    execute: async (operation: ExecuteFlowOperation): Promise<EngineResponse<undefined>> => {
        const input = await resolveExecuteFlowOperation(operation)
        const constants = EngineConstants.fromExecuteFlowInput(input)
        const output: FlowExecutorContext = (await executieSingleStepOrFlowOperation(input, constants)).finishExecution()
        await flowRunProgressReporter.sendUpdate({
            engineConstants: constants,
            flowExecutorContext: output,
        })
        await flowRunProgressReporter.backup()
        await releaseSyncWebhookOnTerminalState({ constants, status: output.verdict.status })
        const status = output.verdict.status === FlowRunStatus.LOG_SIZE_EXCEEDED
            ? EngineResponseStatus.LOG_SIZE_EXCEEDED
            : EngineResponseStatus.OK
        return {
            status,
            response: undefined,
        }
    },
}

/**
 * HERMES-PATCH: unblock a sync webhook as soon as the run is over.
 *
 * Only the "Return Response" piece publishes a sync response (see piece-executor). A run that
 * fails, times out, or simply has no such step publishes nothing, so the caller blocked in
 * `handleSync` waits out the whole `WEBHOOK_TIMEOUT_MS` before getting the 204 fallback — 300s
 * here. Hermes' BPMN service task reads that 204 correctly (the flow produced no response) but
 * only learns it five minutes after the flow actually died, and the process instance stays wedged
 * the whole time.
 *
 * The run is terminal at this point and every response it was ever going to publish already went
 * out, so releasing the listener now changes only the latency. Publishing twice is harmless:
 * `oneTimeListener` deletes itself on the first response, and a publish with no listener is a
 * no-op. Non-terminal verdicts (PAUSED at a waitpoint/approval, QUEUED) must keep waiting — the
 * response is still to come after the resume.
 */
async function releaseSyncWebhookOnTerminalState({ constants, status }: ReleaseSyncWebhookParams): Promise<void> {
    const { workerHandlerId, httpRequestId } = constants
    if (isNil(workerHandlerId) || isNil(httpRequestId)) {
        return
    }
    if (!isFlowRunStateTerminal({ status, ignoreInternalError: false })) {
        return
    }
    // Best-effort on purpose: the run is already finished and reported. Throwing here would
    // turn a completed run into an INTERNAL_ERROR over a latency optimisation, and the 300s
    // timeout still delivers the same 204 if this never lands.
    const { error } = await tryCatch(() => workerSocket.getWorkerClient().sendFlowResponse({
        workerHandlerId,
        httpRequestId,
        runResponse: {
            status: NO_CONTENT_STATUS,
            body: {},
            headers: {},
        },
    }))
    if (error) {
        console.error('[HERMES-PATCH] Failed to release sync webhook on terminal run', error)
    }
}

const executieSingleStepOrFlowOperation = async (input: ResolvedExecuteFlowOperation, constants: EngineConstants): Promise<FlowExecutorContext> => {
    const testSingleStepMode = !isNil(constants.stepNameToTest)
    if (testSingleStepMode) {
        const testContext = await testExecutionContext.stateFromFlowVersion({
            apiUrl: input.internalApiUrl,
            flowVersion: input.flowVersion,
            excludedStepName: input.stepNameToTest!,
            projectId: input.projectId,
            engineToken: input.engineToken,
            sampleData: input.sampleData,
            engineConstants: constants,
        })
        const step = flowStructureUtil.getActionOrThrow(input.stepNameToTest!, input.flowVersion.trigger)
        return flowExecutor.execute({
            action: step,
            executionState: await getFlowExecutionState(input, constants, testContext),
            constants,
        })
    }
    return flowExecutor.executeFromTrigger({
        executionState: await getFlowExecutionState(input, constants, FlowExecutorContext.empty({
            engineApi: {
                engineToken: constants.engineToken,
                internalApiUrl: constants.internalApiUrl,
            },
        })),
        constants,
        input,
    })
}

async function getFlowExecutionState(input: ResolvedExecuteFlowOperation, constants: EngineConstants, flowContext: FlowExecutorContext): Promise<FlowExecutorContext> {
    if (input.executionType === ExecutionType.BEGIN) {
        const newPayload = await runOrReturnPayload(input, constants)
        return flowContext.upsertStep(input.flowVersion.trigger.name,
            GenericStepOutput.create({
                type: input.flowVersion.trigger.type,
                status: StepOutputStatus.SUCCEEDED,
                input: {},
            }).setOutput(newPayload))
    }
    flowContext = flowContext.addTags(input.executionState.tags)
    const isWaitpointResume = input.resumeReason === ResumeReason.WAITPOINT
    for (const [step, output] of Object.entries(input.executionState.steps)) {
        if (isStepRestorable({ status: output.status, isWaitpointResume })) {
            const newOutput = await insertSuccessStepsOrPausedRecursively({ stepOutput: output, isWaitpointResume })
            if (!isNil(newOutput)) {
                flowContext = await flowContext.upsertStep(step, newOutput)
            }
        }
    }
    return flowContext
}

async function runOrReturnPayload(input: ResolvedBeginExecuteFlowOperation, constants: EngineConstants): Promise<TriggerPayload> {
    if (!input.executeTrigger) {
        return input.triggerPayload as TriggerPayload
    }
    const newPayload = await triggerHelper.executeTrigger({
        params: {
            ...input,
            hookType: TriggerHookType.RUN,
            test: false,
            webhookUrl: '',
            triggerPayload: input.triggerPayload as TriggerPayload,
        },
        constants,
    }) as ExecuteTriggerResponse<TriggerHookType.RUN>
    return newPayload.output[0] as TriggerPayload
}


async function insertSuccessStepsOrPausedRecursively({ stepOutput, isWaitpointResume }: InsertStepsParams): Promise<StepOutput | null> {
    if (!isStepRestorable({ status: stepOutput.status, isWaitpointResume })) {
        return null
    }
    if (stepOutput.type === FlowActionType.LOOP_ON_ITEMS) {
        const loopOutput = new LoopStepOutput(stepOutput)
        const iterations = loopOutput.output?.iterations ?? []
        const newIterations: Record<string, StepOutput>[] = []
        for (const iteration of iterations) {
            const newSteps: Record<string, StepOutput> = {}
            for (const [step, output] of Object.entries(iteration)) {
                const newOutput = await insertSuccessStepsOrPausedRecursively({ stepOutput: output, isWaitpointResume })
                if (!isNil(newOutput)) {
                    newSteps[step] = newOutput
                }
            }
            newIterations.push(newSteps)
        }
        return loopOutput.setIterations(newIterations)
    }
    return stepOutput
}

async function resolveExecuteFlowOperation(operation: ExecuteFlowOperation): Promise<ResolvedExecuteFlowOperation> {
    if (operation.executionType === ExecutionType.BEGIN) {
        return {
            ...operation,
            triggerPayload: await resolveJobPayload(operation.triggerPayload, operation),
        }
    }
    const executionState = await fetchExecutionStateFromLogs(operation.logsFileId, operation)
    if (Object.keys(executionState.steps).length === 0) {
        throw new EngineGenericError('EmptyResumeStateError', 'RESUME operation received with empty execution state')
    }
    return {
        ...operation,
        resumePayload: await resolveJobPayload(operation.resumePayload, operation) as ResumePayload,
        executionState,
    }
}

async function resolveJobPayload(payload: JobPayload, operation: ExecuteFlowOperation): Promise<unknown> {
    if (payload.type === 'inline') {
        return payload.value
    }
    const bytes = await engineFileApi.download({
        fileId: payload.fileId,
        apiUrl: operation.internalApiUrl,
        engineToken: operation.engineToken,
    })
    return JSON.parse(new TextDecoder('utf-8').decode(bytes))
}

async function fetchExecutionStateFromLogs(logsFileId: string | undefined, operation: ExecuteFlowOperation): Promise<ExecutionState> {
    if (isNil(logsFileId)) {
        throw new EngineGenericError('ResumeLogsFileMissing', 'logsFileId is missing for RESUME operation')
    }
    const bytes = await engineFileApi.download({
        fileId: logsFileId,
        apiUrl: operation.internalApiUrl,
        engineToken: operation.engineToken,
    })
    const parsed = JSON.parse(new TextDecoder('utf-8').decode(bytes))
    if (isNil(parsed?.executionState)) {
        throw new EngineGenericError('ExecutionStateMissing', 'executionState is missing in logs file')
    }
    return parsed.executionState as ExecutionState
}

// Waitpoint resumes preserve FAILED so a `continueOnFailure` step isn't replayed,
// which would re-fire its waitpoint and let the global `constants.resumePayload`
// pollute the new output. Retry resumes (FlowRetryStrategy.FROM_FAILED_STEP) drop
// FAILED so the engine re-executes the failed step. The discriminator is the
// explicit `resumeReason` set when the run is enqueued.
function isStepRestorable({ status, isWaitpointResume }: IsStepRestorableParams): boolean {
    if (status === StepOutputStatus.SUCCEEDED || status === StepOutputStatus.PAUSED) {
        return true
    }
    return isWaitpointResume && status === StepOutputStatus.FAILED
}

type IsStepRestorableParams = {
    status: StepOutputStatus
    isWaitpointResume: boolean
}

type InsertStepsParams = {
    stepOutput: StepOutput
    isWaitpointResume: boolean
}

/** Matches the fallback `handleSync` returns on timeout, so the caller sees one outcome either way. */
const NO_CONTENT_STATUS = 204

type ReleaseSyncWebhookParams = {
    constants: EngineConstants
    status: FlowRunStatus
}
