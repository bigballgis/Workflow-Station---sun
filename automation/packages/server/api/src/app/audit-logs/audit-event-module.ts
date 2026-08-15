import { ListAuditEventsRequest, PrincipalType } from '@activepieces/shared'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { auditLogService } from './audit-event-service'

// CE reimplementation of the (removed) ee audit-event module. Two deliberate
// departures from the ee original:
//   * no platformMustHaveFeatureEnabled preHandler — ee-authorization left with
//     the EE strip, and OPEN_SOURCE_PLAN has auditLogEnabled hard-true anyway;
//   * PrincipalType.SERVICE dropped from the route security — api-key auth was
//     removed with EE, so USER is the only principal that can reach this.
export const auditEventModule: FastifyPluginAsyncZod = async (app) => {
    auditLogService(app.log).setup()
    await app.register(auditEventController, { prefix: '/v1/audit-events' })
}

const auditEventController: FastifyPluginAsyncZod = async (app) => {

    app.get('/', ListAuditEventsRequestEndpoint, async (request) => {
        return auditLogService(request.log).list({
            platformId: request.principal.platform.id,
            cursorRequest: request.query.cursor ?? null,
            limit: request.query.limit ?? 20,
            action: request.query.action ?? undefined,
            projectId: request.query.projectId ?? undefined,
            userId: request.query.userId ?? undefined,
            createdBefore: request.query.createdBefore ?? undefined,
            createdAfter: request.query.createdAfter ?? undefined,
        })
    })
}


const ListAuditEventsRequestEndpoint = {
    config: {
        security: securityAccess.platformAdminOnly([PrincipalType.USER]),
    },
    schema: {
        querystring: ListAuditEventsRequest,
    },
}
