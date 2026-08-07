import { apId, ApplicationEventName, PlatformRole, PrincipalType } from '@activepieces/shared'
import { faker } from '@faker-js/faker'
import { FastifyInstance } from 'fastify'
import { StatusCodes } from 'http-status-codes'
import { generateMockToken } from '../../../helpers/auth'
import { db } from '../../../helpers/db'
import {
    createMockProject,
    mockAndSaveBasicSetup,
    mockBasicUser,
} from '../../../helpers/mocks'
import { setupTestEnvironment, teardownTestEnvironment } from '../../../helpers/test-setup'

let app: FastifyInstance | null = null

beforeAll(async () => {
    app = await setupTestEnvironment()
})

afterAll(async () => {
    await teardownTestEnvironment()
})

const createMockAuditEvent = (overrides: {
    platformId: string
    projectId?: string
    userId?: string
    action?: string
}) => ({
    id: apId(),
    created: new Date().toISOString(),
    updated: new Date().toISOString(),
    action: overrides.action ?? ApplicationEventName.FLOW_CREATED,
    userEmail: faker.internet.email(),
    projectDisplayName: faker.lorem.word(),
    data: {},
    ip: faker.internet.ipv4(),
    ...overrides,
})

describe('Audit Events API', () => {
    describe('List audit events endpoint', () => {
        it('Lists only the current platform\'s events', async () => {
            // arrange
            const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup()
            const { mockPlatform: otherPlatform } = await mockAndSaveBasicSetup()

            const ownEvents = [
                createMockAuditEvent({ platformId: mockPlatform.id }),
                createMockAuditEvent({ platformId: mockPlatform.id, action: ApplicationEventName.FLOW_DELETED }),
            ]
            const foreignEvent = createMockAuditEvent({ platformId: otherPlatform.id })
            await db.save('audit_event', [...ownEvents, foreignEvent])

            const testToken = await generateMockToken({
                type: PrincipalType.USER,
                id: mockOwner.id,
                platform: { id: mockPlatform.id },
            })

            // act
            const response = await app?.inject({
                method: 'GET',
                url: '/api/v1/audit-events',
                headers: { authorization: `Bearer ${testToken}` },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(responseBody.data).toHaveLength(2)
            const returnedIds = responseBody.data.map((e: { id: string }) => e.id)
            expect(returnedIds).toEqual(
                expect.arrayContaining(ownEvents.map((e) => e.id)),
            )
            expect(returnedIds).not.toContain(foreignEvent.id)
        })

        it('Filters by action', async () => {
            // arrange
            const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup()

            const flowCreated = createMockAuditEvent({ platformId: mockPlatform.id, action: ApplicationEventName.FLOW_CREATED })
            const flowDeleted = createMockAuditEvent({ platformId: mockPlatform.id, action: ApplicationEventName.FLOW_DELETED })
            await db.save('audit_event', [flowCreated, flowDeleted])

            const testToken = await generateMockToken({
                type: PrincipalType.USER,
                id: mockOwner.id,
                platform: { id: mockPlatform.id },
            })

            // act
            const response = await app?.inject({
                method: 'GET',
                url: `/api/v1/audit-events?action=${ApplicationEventName.FLOW_DELETED}`,
                headers: { authorization: `Bearer ${testToken}` },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(responseBody.data).toHaveLength(1)
            expect(responseBody.data[0].id).toBe(flowDeleted.id)
        })

        it('Rejects non-admin platform members', async () => {
            // arrange
            const { mockPlatform } = await mockAndSaveBasicSetup()
            const { mockUser: memberUser } = await mockBasicUser({
                user: {
                    platformId: mockPlatform.id,
                    platformRole: PlatformRole.MEMBER,
                },
            })

            const testToken = await generateMockToken({
                type: PrincipalType.USER,
                id: memberUser.id,
                platform: { id: mockPlatform.id },
            })

            // act
            const response = await app?.inject({
                method: 'GET',
                url: '/api/v1/audit-events',
                headers: { authorization: `Bearer ${testToken}` },
            })

            // assert
            expect(response?.statusCode).toBe(StatusCodes.FORBIDDEN)
        })
    })

    describe('Event persistence', () => {
        it('Persists application events emitted by API actions', async () => {
            // arrange
            const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup()
            const mockProject = createMockProject({
                ownerId: mockOwner.id,
                platformId: mockPlatform.id,
            })
            await db.save('project', mockProject)

            const testToken = await generateMockToken({
                type: PrincipalType.USER,
                id: mockOwner.id,
                projectId: mockProject.id,
                platform: { id: mockPlatform.id },
            })

            // act — folder creation emits FOLDER_CREATED through applicationEvents
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/folders',
                body: { displayName: faker.lorem.word(), projectId: mockProject.id },
                headers: { authorization: `Bearer ${testToken}` },
            })
            expect(response?.statusCode).toBe(StatusCodes.OK)

            // assert — the audit write is fire-and-forget, so poll briefly
            let persisted = null
            for (let attempt = 0; attempt < 20 && !persisted; attempt++) {
                persisted = await db.findOneBy('audit_event', {
                    platformId: mockPlatform.id,
                    projectId: mockProject.id,
                    action: ApplicationEventName.FOLDER_CREATED,
                })
                if (!persisted) {
                    await new Promise((resolve) => setTimeout(resolve, 100))
                }
            }

            expect(persisted).not.toBeNull()
        })
    })
})
