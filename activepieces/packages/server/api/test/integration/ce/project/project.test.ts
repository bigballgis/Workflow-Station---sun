import {
    apId,
    PrincipalType,
    ProjectType,
} from '@activepieces/shared'
import { faker } from '@faker-js/faker'
import { FastifyInstance } from 'fastify'
import { StatusCodes } from 'http-status-codes'
import { databaseConnection } from '../../../../src/app/database/database-connection'
import { generateMockToken } from '../../../helpers/auth'
import { createMockProject, mockAndSaveBasicSetup } from '../../../helpers/mocks'
import { setupTestEnvironment, teardownTestEnvironment } from '../../../helpers/test-setup'

let app: FastifyInstance | null = null

beforeAll(async () => {
    app = await setupTestEnvironment()
})

afterAll(async () => {
    await teardownTestEnvironment()
})

async function ownerToken(): Promise<{ token: string, platformId: string, ownerId: string }> {
    const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup({
        project: { type: ProjectType.PERSONAL },
    })
    const token = await generateMockToken({
        type: PrincipalType.USER,
        id: mockOwner.id,
        platform: { id: mockPlatform.id },
    })
    return { token, platformId: mockPlatform.id, ownerId: mockOwner.id }
}

describe('Project API', () => {
    describe('Create Project', () => {
        it('should create a team project owned by the caller', async () => {
            const { token, platformId, ownerId } = await ownerToken()
            const displayName = faker.animal.bird()

            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/projects',
                body: { displayName },
                headers: { authorization: `Bearer ${token}` },
            })

            expect(response?.statusCode).toBe(StatusCodes.CREATED)
            const body = response?.json()
            expect(body.displayName).toBe(displayName)
            expect(body.type).toBe(ProjectType.TEAM)
            expect(body.ownerId).toBe(ownerId)
            expect(body.platformId).toBe(platformId)
        })

        // Upstream gated this on plan.teamProjectsLimit and expected 402 on the second one.
        // OPEN_SOURCE_PLAN hardcodes UNLIMITED here (2db9b6ca6) and there is no per-platform
        // plan row to override, so team projects are unlimited by construction.
        it('should allow more than one team project', async () => {
            const { token } = await ownerToken()

            for (const displayName of [faker.animal.bird(), faker.animal.cat()]) {
                const response = await app?.inject({
                    method: 'POST',
                    url: '/api/v1/projects',
                    body: { displayName },
                    headers: { authorization: `Bearer ${token}` },
                })
                expect(response?.statusCode).toBe(StatusCodes.CREATED)
            }
        })

        it('should make the creator a project member so the project is reachable', async () => {
            const { token, ownerId } = await ownerToken()

            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/projects',
                body: { displayName: faker.animal.bird() },
                headers: { authorization: `Bearer ${token}` },
            })

            const projectId = response?.json().id
            const member = await databaseConnection()
                .getRepository('project_member')
                .findOneBy({ projectId, userId: ownerId })
            expect(member).not.toBeNull()
        })

        // The fields behind alerts / global connections / concurrency pools went with the EE
        // modules (D13). zod strips unknown keys by default, so without .strict() on the
        // request schema this would silently 201 and quietly honour none of them.
        it.each(['alertReceiverEmail', 'globalConnectionExternalIds', 'maxConcurrentJobs'])(
            'should reject the removed field %s instead of ignoring it',
            async (field) => {
                const { token } = await ownerToken()

                const response = await app?.inject({
                    method: 'POST',
                    url: '/api/v1/projects',
                    body: { displayName: faker.animal.bird(), [field]: 'x' },
                    headers: { authorization: `Bearer ${token}` },
                })

                expect(response?.statusCode).toBe(StatusCodes.BAD_REQUEST)
                expect(response?.json().message).toContain(field)
            },
        )
    })

    describe('Update Project', () => {
        it('should rename a team project', async () => {
            const { token } = await ownerToken()
            const created = await app?.inject({
                method: 'POST',
                url: '/api/v1/projects',
                body: { displayName: faker.animal.bird() },
                headers: { authorization: `Bearer ${token}` },
            })
            const projectId = created?.json().id
            const renamed = faker.animal.dog()

            const response = await app?.inject({
                method: 'POST',
                url: `/api/v1/projects/${projectId}`,
                body: { displayName: renamed },
                headers: { authorization: `Bearer ${token}` },
            })

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(response?.json().displayName).toBe(renamed)
        })
    })

    describe('Delete Project', () => {
        it('should soft delete an empty team project', async () => {
            const { token } = await ownerToken()
            const created = await app?.inject({
                method: 'POST',
                url: '/api/v1/projects',
                body: { displayName: faker.animal.bird() },
                headers: { authorization: `Bearer ${token}` },
            })
            const projectId = created?.json().id

            const response = await app?.inject({
                method: 'DELETE',
                url: `/api/v1/projects/${projectId}`,
                headers: { authorization: `Bearer ${token}` },
            })

            expect(response?.statusCode).toBe(StatusCodes.NO_CONTENT)
            const row = await databaseConnection()
                .getRepository('project')
                .findOne({ where: { id: projectId }, withDeleted: true })
            expect(row?.deleted).not.toBeNull()
        })

        it('should refuse to delete a personal project', async () => {
            const { mockOwner, mockPlatform, mockProject } = await mockAndSaveBasicSetup({
                project: { type: ProjectType.PERSONAL },
            })
            const token = await generateMockToken({
                type: PrincipalType.USER,
                id: mockOwner.id,
                platform: { id: mockPlatform.id },
            })

            const response = await app?.inject({
                method: 'DELETE',
                url: `/api/v1/projects/${mockProject.id}`,
                headers: { authorization: `Bearer ${token}` },
            })

            expect(response?.statusCode).not.toBe(StatusCodes.NO_CONTENT)
            expect(response?.json().params?.message).toContain('Personal projects cannot be deleted')
        })

        // externalId means HERMES provisioned it via managed-authn and the whole integration
        // resolves the shared project by that id — deleting it would strand every embedded
        // builder and every BPMN service task.
        it('should refuse to delete an externally provisioned project', async () => {
            const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup()
            const externalProject = createMockProject({
                ownerId: mockOwner.id,
                platformId: mockPlatform.id,
                type: ProjectType.TEAM,
                externalId: `hermes-${apId()}`,
            })
            await databaseConnection().getRepository('project').save(externalProject)
            const token = await generateMockToken({
                type: PrincipalType.USER,
                id: mockOwner.id,
                platform: { id: mockPlatform.id },
            })

            const response = await app?.inject({
                method: 'DELETE',
                url: `/api/v1/projects/${externalProject.id}`,
                headers: { authorization: `Bearer ${token}` },
            })

            expect(response?.statusCode).not.toBe(StatusCodes.NO_CONTENT)
            expect(response?.json().params?.message).toContain('provisioned externally')
        })
    })

    describe('List Projects', () => {
        it('should return the projects the caller can reach', async () => {
            const { token } = await ownerToken()
            const displayName = faker.animal.bird()
            await app?.inject({
                method: 'POST',
                url: '/api/v1/projects',
                body: { displayName },
                headers: { authorization: `Bearer ${token}` },
            })

            const response = await app?.inject({
                method: 'GET',
                url: '/api/v1/projects',
                headers: { authorization: `Bearer ${token}` },
            })

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(response?.json().data.map((p: { displayName: string }) => p.displayName)).toContain(displayName)
        })
    })
})
