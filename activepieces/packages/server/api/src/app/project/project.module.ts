import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { projectController } from './project.controller'
import { projectWorkerController } from './project-worker-controller'

// HERMES: CE reimplementation of the project module that lived in app/ee
// (platformProjectModule; AG-EE / EE_REMOVAL_PLAN G6). Registers the CE /v1/projects
// controller and the existing CE worker-project controller. The EE /v1/platforms list
// controller was platform-admin-only and is not exposed in CE.
export const projectModule: FastifyPluginAsyncZod = async (app) => {
    await app.register(projectController, { prefix: '/v1/projects' })
    await app.register(projectWorkerController, { prefix: '/v1/worker/project' })
}
