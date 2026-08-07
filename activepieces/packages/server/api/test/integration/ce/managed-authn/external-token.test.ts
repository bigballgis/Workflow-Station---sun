import { apId, DefaultProjectRole, ProjectRole } from '@activepieces/shared'
import { FastifyInstance } from 'fastify'
import { StatusCodes } from 'http-status-codes'
import { Redis } from 'ioredis'
import { redisConnections } from '../../../../src/app/database/redis-connections'
import { generateMockExternalToken } from '../../../helpers/auth'
import { db } from '../../../helpers/db'
import {
    createMockProject,
    createMockSigningKey,
    mockAndSaveBasicSetup,
    mockBasicUser,
} from '../../../helpers/mocks'
import { setupTestEnvironment, teardownTestEnvironment } from '../../../helpers/test-setup'

async function deleteKeysByPattern(redis: Redis, pattern: string): Promise<void> {
    const stream = redis.scanStream({ match: pattern, count: 100 })
    for await (const keys of stream) {
        if (keys.length > 0) await redis.del(...keys)
    }
}

let app: FastifyInstance | null = null

beforeAll(async () => {
    app = await setupTestEnvironment()
})

afterAll(async () => {
    await teardownTestEnvironment()
})

beforeEach(async () => {
    const redis = await redisConnections.useExisting()
    await deleteKeysByPattern(redis, 'concurrency-pool:limit:*')
    await deleteKeysByPattern(redis, 'project:concurrency-pool:*')
})
describe('Managed Authentication API', () => {
    describe('External token endpoint', () => {
        it('Signs up new users', async () => {
            // arrange
            const { mockPlatform } = await mockAndSaveBasicSetup()

            const mockSigningKey = createMockSigningKey({
                platformId: mockPlatform.id,
            })
            await db.save('signing_key', mockSigningKey)

            const { mockExternalToken, mockExternalTokenPayload } = generateMockExternalToken({
                platformId: mockPlatform.id,
                signingKeyId: mockSigningKey.id,
            })

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(responseBody?.id).toHaveLength(21)
            expect(responseBody?.firstName).toBe(mockExternalTokenPayload.firstName)
            expect(responseBody?.lastName).toBe(mockExternalTokenPayload.lastName)
            expect(responseBody?.trackEvents).toBe(true)
            expect(responseBody?.newsLetter).toBe(false)
            expect(responseBody?.password).toBeUndefined()
            expect(responseBody?.status).toBe('ACTIVE')
            expect(responseBody?.verified).toBe(true)
            expect(responseBody?.externalId).toBe(
                mockExternalTokenPayload.externalUserId,
            )
            expect(responseBody?.platformId).toBe(mockPlatform.id)
            expect(responseBody?.projectId).toHaveLength(21)
            expect(responseBody?.token).toBeDefined()
        })

        it('Creates new project', async () => {
            // arrange
            const { mockPlatform } = await mockAndSaveBasicSetup()

            const mockSigningKey = createMockSigningKey({
                platformId: mockPlatform.id,
            })
            await db.save('signing_key', mockSigningKey)

            const { mockExternalToken, mockExternalTokenPayload } =
                generateMockExternalToken({
                    platformId: mockPlatform.id,
                    signingKeyId: mockSigningKey.id,
                })

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)

            const generatedProject = await db.findOneBy('project', {
                id: responseBody?.projectId,
            })

            expect(generatedProject?.displayName).toBe(
                mockExternalTokenPayload.externalProjectId,
            )
            expect(generatedProject?.ownerId).toBe(mockPlatform.ownerId)
            expect(generatedProject?.platformId).toBe(mockPlatform.id)
            expect(generatedProject?.externalId).toBe(
                mockExternalTokenPayload.externalProjectId,
            )
        })

        it('Provisions a personal project for the shadow user, once', async () => {
            // arrange
            const { mockPlatform } = await mockAndSaveBasicSetup()

            const mockSigningKey = createMockSigningKey({
                platformId: mockPlatform.id,
            })
            await db.save('signing_key', mockSigningKey)

            const { mockExternalToken } = generateMockExternalToken({
                platformId: mockPlatform.id,
                signingKeyId: mockSigningKey.id,
            })

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)

            const personalProject = await db.findOneBy('project', {
                ownerId: responseBody?.id,
                type: 'PERSONAL',
            })

            expect(personalProject).not.toBeNull()
            expect(personalProject?.platformId).toBe(mockPlatform.id)
            // the shared HERMES project stays a separate TEAM project
            expect(personalProject?.id).not.toBe(responseBody?.projectId)

            // act again — same token, existing user: must not create a second one
            const secondResponse = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            expect(secondResponse?.statusCode).toBe(StatusCodes.OK)

            const personalProjects = await db.findBy('project', {
                ownerId: responseBody?.id,
                type: 'PERSONAL',
            })
            expect(personalProjects).toHaveLength(1)
        })

        it('Adds new user as a member in new project', async () => {
            // arrange
            const { mockPlatform } = await mockAndSaveBasicSetup()

            const mockSigningKey = createMockSigningKey({
                platformId: mockPlatform.id,
            })
            await db.save('signing_key', mockSigningKey)


            const projectRole = await db.findOneByOrFail<ProjectRole>('project_role', { name: DefaultProjectRole.VIEWER })

            const { mockExternalToken } = generateMockExternalToken({
                platformId: mockPlatform.id,
                signingKeyId: mockSigningKey.id,
                projectRole: projectRole.name,
            })

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)

            const generatedProjectMember = await db.findOneBy('project_member', {
                projectId: responseBody?.projectId,
                userId: responseBody?.id,
            })

            expect(generatedProjectMember?.projectId).toBe(responseBody?.projectId)
            expect(generatedProjectMember?.userId).toBe(responseBody?.id)
            expect(generatedProjectMember?.platformId).toBe(mockPlatform.id)
            expect(generatedProjectMember?.projectRoleId).toBe(projectRole.id)
        })

        it('Adds new user to existing project', async () => {
            // arrange
            const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup()

            const mockSigningKey = createMockSigningKey({
                platformId: mockPlatform.id,
            })
            await db.save('signing_key', mockSigningKey)

            const mockExternalProjectId = apId()

            const mockProject = createMockProject({
                ownerId: mockOwner.id,
                platformId: mockPlatform.id,
                externalId: mockExternalProjectId,
            })
            await db.save('project', mockProject)

            const { mockExternalToken } = generateMockExternalToken({
                platformId: mockPlatform.id,
                signingKeyId: mockSigningKey.id,
                externalProjectId: mockExternalProjectId,
            })

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(responseBody?.projectId).toBe(mockProject.id)
        })

        it('Signs in existing users', async () => {
            // arrange
            const { mockOwner, mockPlatform } = await mockAndSaveBasicSetup()

            const mockSigningKey = createMockSigningKey({
                platformId: mockPlatform.id,
            })
            await db.save('signing_key', mockSigningKey)

            const { mockExternalToken, mockExternalTokenPayload } = generateMockExternalToken({
                platformId: mockPlatform.id,
                signingKeyId: mockSigningKey.id,
            })

            const { mockUser } = await mockBasicUser({
                user: {
                    externalId: mockExternalTokenPayload.externalUserId,
                    platformId: mockPlatform.id,
                },
            })

            const mockProject = createMockProject({
                ownerId: mockOwner.id,
                platformId: mockPlatform.id,
                externalId: mockExternalTokenPayload.externalProjectId,
            })
            await db.save('project', mockProject)

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.OK)
            expect(responseBody?.projectId).toBe(mockProject.id)
            expect(responseBody?.id).toBe(mockUser.id)
        })

        it('Fails if signing key is not found', async () => {
            // arrange
            await mockAndSaveBasicSetup()

            const nonExistentSigningKeyId = apId()

            const { mockExternalToken } = generateMockExternalToken({
                signingKeyId: nonExistentSigningKeyId,
            })

            // act
            const response = await app?.inject({
                method: 'POST',
                url: '/api/v1/managed-authn/external-token',
                body: {
                    externalAccessToken: mockExternalToken,
                },
            })

            // assert
            const responseBody = response?.json()

            expect(response?.statusCode).toBe(StatusCodes.UNAUTHORIZED)
            expect(responseBody?.params?.message).toBe(
                `signing key not found signingKeyId=${nonExistentSigningKeyId}`,
            )
        })
    })
})
