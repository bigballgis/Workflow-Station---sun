import { isNil, tryCatch } from '@activepieces/core-utils'
import { apDayjsDuration } from '@activepieces/server-utils'
import { ExecuteFlowJobData, JOB_PRIORITY, JobData, RATE_LIMIT_PRIORITY, RunEnvironment, WorkerJobType } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { getConcurrencyPoolSetKey } from '../../../database/redis/keys'
import { redisConnections } from '../../../database/redis-connections'
import { system } from '../../../helper/system/system'
import { AppSystemProp } from '../../../helper/system/system-props'
import { concurrencyPoolService } from '../concurrency-pool-stub'
import { InterceptorResult, InterceptorVerdict, JobInterceptor } from '../job-interceptor'

const RATE_LIMIT_WORKER_JOB_TYPES = [WorkerJobType.EXECUTE_FLOW]

function shouldContinue(jobData: JobData): jobData is ExecuteFlowJobData {
    if (!system.getBoolean(AppSystemProp.PROJECT_RATE_LIMITER_ENABLED)) {
        return false
    }
    if (!RATE_LIMIT_WORKER_JOB_TYPES.includes(jobData.jobType)) {
        return false
    }
    const castedJob = jobData as ExecuteFlowJobData
    if (castedJob.environment === RunEnvironment.TESTING) {
        return false
    }
    return true
}


// HERMES: EE removed (AG-EE / G5) — the CLOUD plan-tier limits, worker-group capacity
// clamp, and projectWorkerGroup routing are gone. CE has a single configured limit
// (DEFAULT_CONCURRENT_JOBS_LIMIT); an explicit pool limit would override it, but the
// stubbed concurrencyPoolService always returns null in CE ("no pool" = default path).
async function getMaxConcurrentJobs({ poolId, log }: { poolId: string | null, log: FastifyBaseLogger }): Promise<number> {
    if (!isNil(poolId)) {
        const { data: value, error } = await tryCatch(() => concurrencyPoolService(log).getPoolLimit(poolId))
        if (error === null && !isNil(value)) {
            return value
        }
    }
    return system.getNumberOrThrow(AppSystemProp.DEFAULT_CONCURRENT_JOBS_LIMIT)
}

async function tryAcquireSlot({ jobId, jobData, log }: { jobId: string, jobData: ExecuteFlowJobData, log: FastifyBaseLogger }): Promise<boolean> {
    const flowTimeoutInMilliseconds = apDayjsDuration(system.getNumberOrThrow(AppSystemProp.FLOW_TIMEOUT_SECONDS), 'seconds').add(1, 'minute').asMilliseconds()
    const { data: poolId } = await tryCatch(() => concurrencyPoolService(log).getProjectPoolId(jobData.projectId))
    const effectivePoolId = poolId ?? jobData.projectId
    const maxConcurrentJobs = await getMaxConcurrentJobs({
        poolId,
        log,
    })
    const setKey = getConcurrencyPoolSetKey(effectivePoolId)
    const currentTime = Date.now()
    const member = `${jobData.projectId}:${jobId}`
    const redisConnection = await redisConnections.useExisting()

    const result = await redisConnection.eval(
        `
local setKey = KEYS[1]
local currentTime = tonumber(ARGV[1])
local timeoutMs = tonumber(ARGV[2])
local maxJobs = tonumber(ARGV[3])
local member = ARGV[4]

redis.call('ZREMRANGEBYSCORE', setKey, '-inf', currentTime - timeoutMs)

local existingScore = redis.call('ZSCORE', setKey, member)
if existingScore then
    return 0
end

local currentSize = redis.call('ZCARD', setKey)
if currentSize >= maxJobs then
    return 1
end

redis.call('ZADD', setKey, currentTime, member)
redis.call('EXPIRE', setKey, math.ceil(timeoutMs / 1000))

return 0
`,
        1,
        setKey,
        currentTime.toString(),
        flowTimeoutInMilliseconds.toString(),
        maxConcurrentJobs.toString(),
        member,
    ) as number

    return result === 0
}

async function releaseSlot({ jobId, jobData, log }: { jobId: string, jobData: ExecuteFlowJobData, log: FastifyBaseLogger }): Promise<void> {
    const { data: poolId } = await tryCatch(() => concurrencyPoolService(log).getProjectPoolId(jobData.projectId))
    const effectivePoolId = poolId ?? jobData.projectId
    const setKey = getConcurrencyPoolSetKey(effectivePoolId)
    const member = `${jobData.projectId}:${jobId}`
    const redisConnection = await redisConnections.useExisting()
    await redisConnection.eval(
        `
local setKey = KEYS[1]
local member = ARGV[1]
redis.call('ZREM', setKey, member)
return 1
`,
        1,
        setKey,
        member,
    )
}

export const rateLimiterInterceptor: JobInterceptor = {
    async preDispatch({ jobId, jobData, job, log }): Promise<InterceptorResult> {
        if (!shouldContinue(jobData)) {
            return { verdict: InterceptorVerdict.ALLOW }
        }

        const allowed = await tryAcquireSlot({ jobId, jobData, log })
        if (allowed) {
            log.debug({ job: { id: jobId }, project: { id: jobData.projectId } }, '[rateLimiterInterceptor] Job allowed')
            return { verdict: InterceptorVerdict.ALLOW }
        }

        const delayInMs = Math.min(600_000, 20_000 * Math.pow(2, job.attemptsMade))
        log.info({ job: { id: jobId }, project: { id: jobData.projectId }, delayInMs }, '[rateLimiterInterceptor] Job rate limited')
        return {
            verdict: InterceptorVerdict.REJECT,
            delayInMs,
            priority: JOB_PRIORITY[RATE_LIMIT_PRIORITY],
        }
    },

    async onJobFinished({ jobId, jobData, log }): Promise<void> {
        if (!shouldContinue(jobData)) {
            return
        }
        await releaseSlot({ jobId, jobData, log })
        log.debug({ job: { id: jobId }, project: { id: jobData.projectId } }, '[rateLimiterInterceptor] Slot released')
    },
}
