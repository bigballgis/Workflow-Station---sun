import { ProjectId, UserId } from '@activepieces/core-utils'
import { apVersionUtil } from '@activepieces/server-utils'
import { pickTelemetryPii, TelemetryEvent, User, UserIdentity } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { PostHog } from 'posthog-node'
import { platformService } from '../platform/platform.service'
import { projectService } from '../project/project-service'
import { system } from './system/system'
import { AppSystemProp } from './system/system-props'

const telemetryEnabled = system.getBoolean(AppSystemProp.TELEMETRY_ENABLED)

let posthogInstance: PostHog | null = null
function getPostHog(): PostHog {
    if (!posthogInstance) {
        posthogInstance = new PostHog('phc_7F92HoXJPeGnTKmYv0eOw62FurPMRW9Aqr0TPrDzvHh', {
            host: 'https://us.i.posthog.com',
            maxQueueSize: BILLING_EVENTS_MAX_QUEUE_SIZE,
        })
    }
    return posthogInstance
}

const BILLING_EVENTS_MAX_QUEUE_SIZE = 20_000

export const telemetry = (log: FastifyBaseLogger) => ({
    async identify(identity: UserIdentity, user?: User, projectId?: ProjectId): Promise<void> {
        if (!telemetryEnabled) {
            return
        }
        getPostHog().identify({
            distinctId: user?.id ?? identity.id,
            properties: {
                ...pickTelemetryPii({
                    edition: system.getEdition(),
                    email: identity.email,
                    firstName: identity.firstName,
                    lastName: identity.lastName,
                }),
                projectId,
                firstSeenAt: user?.created ?? identity.created,
                ...(await getMetadata()),
            },
        })
    },
    async trackPlatform(platformId: ProjectId, event: TelemetryEvent): Promise<void> {
        if (!telemetryEnabled) {
            return
        }
        const platform = await platformService(log).getOneOrThrow(platformId)
        await this.trackUser(platform.ownerId, event, { platform: platformId })
    },
    async trackProject(
        projectId: ProjectId,
        event: TelemetryEvent,
    ): Promise<void> {
        if (!telemetryEnabled) {
            return
        }
        const project = await projectService(log).getOne(projectId)
        return this.trackUser(project!.ownerId, event, { platform: project!.platformId })
    },
    isEnabled: () => telemetryEnabled,
    async trackUser(userId: UserId, event: TelemetryEvent, groups?: Record<string, string>): Promise<void> {
        if (!telemetryEnabled) {
            return
        }
        const payloadEvent = {
            distinctId: userId,
            event: event.name,
            properties: {
                ...event.payload,
                ...(await getMetadata()),
                datetime: new Date().toISOString(),
            },
            groups,
        }
        log.info(payloadEvent, '[Telemetry#trackUser] sending event')
        getPostHog().capture(payloadEvent)
    },
})

// HERMES: `captureBillingEvent` / `flushBillingEvents` are removed along with their only
// caller (platform/billing-and-telemetry.ts, which had zero importers). They called
// getPostHog().capture() WITHOUT consulting `telemetryEnabled`, so unlike every method on
// the `telemetry` object above they were an egress path to us.i.posthog.com that
// AP_TELEMETRY_ENABLED=false did not switch off. Nothing may reintroduce a PostHog call
// that skips that gate.
export async function shutdownTelemetry(): Promise<void> {
    if (posthogInstance) {
        await posthogInstance.shutdown()
    }
}

async function getMetadata() {
    const currentVersion = apVersionUtil.getCurrentRelease()
    const edition = system.getEdition()
    return {
        activepiecesVersion: currentVersion,
        activepiecesEnvironment: system.get(AppSystemProp.ENVIRONMENT),
        activepiecesEdition: edition,
        source_site: 'product',
    }
}
