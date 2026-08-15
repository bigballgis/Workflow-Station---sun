import { FlowRunId, ProjectId } from '@activepieces/core-utils'
import { Flow } from '@activepieces/shared'
import { Job, JobsOptions } from 'bullmq'
import { Dayjs } from 'dayjs'

// HERMES: every value here must have a `systemJobHandlers.registerJobHandler` call —
// `getJobHandler` throws for anything else, and worse, membership in this enum is what
// `removeDeprecatedJobs` (system-job.ts) uses to decide a scheduler is still wanted. A
// value with no handler therefore both fails at execution AND shields a stale BullMQ
// scheduler from cleanup. The six upstream names whose domains this fork deleted
// (pieces-analytics, hard-delete-project, hard-delete-platform, billing-usage-report,
// tool-search-reindex, chat-stale-sweep) are removed and listed in `deprecatedJobs`
// instead, so any scheduler left in Redis by a pre-trim deployment gets purged.
export enum SystemJobName {
    PIECES_SYNC = 'pieces-sync',
    FILE_CLEANUP_TRIGGER = 'file-cleanup-trigger',
    RUN_TELEMETRY = 'run-telemetry',
    DELETE_FLOW = 'delete-flow',
    RESUME_DELAY_WAITPOINT = 'resume-delay-waitpoint',
    BUNDLE_PIECE = 'bundle-piece',
}

type BundlePieceSystemJobData = {
    name: string
    version: string
}

type DeleteFlowDurableSystemJobData =  {
    flow: Flow
    preDeleteDone: boolean
}

type ResumeDelayWaitpointSystemJobData = {
    flowRunId: FlowRunId
    projectId: ProjectId
    waitpointId: string
}

type SystemJobDataMap = {
    [SystemJobName.PIECES_SYNC]: Record<string, never>
    [SystemJobName.FILE_CLEANUP_TRIGGER]: Record<string, never>
    [SystemJobName.RUN_TELEMETRY]: Record<string, never>
    [SystemJobName.DELETE_FLOW]: DeleteFlowDurableSystemJobData
    [SystemJobName.RESUME_DELAY_WAITPOINT]: ResumeDelayWaitpointSystemJobData
    [SystemJobName.BUNDLE_PIECE]: BundlePieceSystemJobData
}

export type SystemJobData<T extends SystemJobName = SystemJobName> = T extends SystemJobName ? SystemJobDataMap[T] : never

export type SystemJobDefinition<T extends SystemJobName> = {
    name: T
    data: SystemJobData<T>
    jobId: string
}

export type SystemJobHandler<T extends SystemJobName = SystemJobName> = (data: SystemJobData<T>) => Promise<void>

type OneTimeJobSchedule = {
    type: 'one-time'
    date: Dayjs
}

type RepeatedJobSchedule = {
    type: 'repeated'
    cron: string
}

export type JobSchedule = OneTimeJobSchedule | RepeatedJobSchedule

type UpsertJobParams<T extends SystemJobName> = {
    job: SystemJobDefinition<T>
    schedule: JobSchedule
    customConfig?: JobsOptions
}

export type SystemJobSchedule = {
    init(): Promise<void>
    startWorker(): Promise<void>
    upsertJob<T extends SystemJobName>(params: UpsertJobParams<T>): Promise<void>
    getJob<T extends SystemJobName>(jobId: string): Promise<Job<SystemJobData<T>> | undefined>
    close(): Promise<void>
}
