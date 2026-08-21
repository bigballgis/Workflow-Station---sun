import { apId, assertNotNullOrUndefined, ProjectRole, RoleType } from '@activepieces/core-utils'
import { LATEST_CONTEXT_VERSION, PieceMetadata } from '@activepieces/pieces-framework'
import { AppConnection, AppConnectionScope, AppConnectionStatus, AppConnectionType, ApplicationEvent, ApplicationEventName, ColorName, File, FileCompression, FileLocation, FileType, Flow, FlowOperationStatus, FlowRun, FlowRunStatus, FlowStatus, FlowTriggerType, FlowVersion, FlowVersionState, Folder, KeyAlgorithm, LATEST_FLOW_SCHEMA_VERSION, PackageType, PiecesFilterType, PieceType, Platform, PlatformRole, Project, ProjectIcon, ProjectMember, ProjectPlan, ProjectRelease, ProjectReleaseType, ProjectType, RunEnvironment, SigningKey, User, UserIdentity, UserIdentityProvider, UserStatus } from '@activepieces/shared'
import { faker } from '@faker-js/faker'
import bcrypt from 'bcrypt'
import dayjs from 'dayjs'
import { FastifyBaseLogger } from 'fastify'
import { databaseConnection } from '../../../src/app/database/database-connection'
import { PieceMetadataSchema } from '../../../src/app/pieces/metadata/piece-metadata-entity'
import { pieceMetadataService } from '../../../src/app/pieces/metadata/piece-metadata-service'

export const CLOUD_PLATFORM_ID = 'cloud-id'

export const createMockUserIdentity = (userIdentity?: Partial<UserIdentity>): UserIdentity => {
    return {
        id: userIdentity?.id ?? apId(),
        created: userIdentity?.created ?? faker.date.recent().toISOString(),
        updated: userIdentity?.updated ?? faker.date.recent().toISOString(),
        email: (userIdentity?.email ?? faker.internet.email()).toLowerCase().trim(),
        firstName: userIdentity?.firstName ?? faker.person.firstName(),
        lastName: userIdentity?.lastName ?? faker.person.lastName(),
        tokenVersion: userIdentity?.tokenVersion ?? undefined,
        password: userIdentity?.password
            ? bcrypt.hashSync(userIdentity.password, 10)
            : faker.internet.password(),
        trackEvents: userIdentity?.trackEvents ?? faker.datatype.boolean(),
        newsLetter: userIdentity?.newsLetter ?? faker.datatype.boolean(),
        verified: userIdentity?.verified ?? faker.datatype.boolean(),
        provider: userIdentity?.provider ?? UserIdentityProvider.EMAIL,
    }
}

export const createMockUser = (user?: Partial<User>): User => {
    return {
        id: user?.id ?? apId(),
        created: user?.created ?? faker.date.recent().toISOString(),
        updated: user?.updated ?? faker.date.recent().toISOString(),
        status: user?.status ?? UserStatus.ACTIVE,
        platformRole: user?.platformRole ?? faker.helpers.enumValue(PlatformRole),
        externalId: user?.externalId,
        identityId: user?.identityId ?? apId(),
        platformId: user?.platformId ?? null,
    }
}

// HERMES: createMockOAuthApp removed — the ee oauth-apps entity went with the EE strip (G6).

// HERMES: createMockTemplate removed with the template domain (FR-D2).

export const createMockPlan = (plan?: Partial<ProjectPlan>): ProjectPlan => {
    return {
        id: plan?.id ?? apId(),
        created: plan?.created ?? faker.date.recent().toISOString(),
        updated: plan?.updated ?? faker.date.recent().toISOString(),
        projectId: plan?.projectId ?? apId(),
        name: plan?.name ?? faker.lorem.word(),
        locked: plan?.locked ?? false,
        pieces: plan?.pieces ?? [],
        piecesFilterType: plan?.piecesFilterType ?? PiecesFilterType.NONE,
        activeFlowsLimit: plan?.activeFlowsLimit ?? null,
    }
}

// HERMES: createMockUserInvitation removed with the user-invitations domain (FR-D2).

export const createMockProject = (project?: Partial<Project>): Project => {
    const icon: ProjectIcon = {
        color: faker.helpers.enumValue(ColorName),
    }
    return {
        id: project?.id ?? apId(),
        created: project?.created ?? faker.date.recent().toISOString(),
        updated: project?.updated ?? faker.date.recent().toISOString(),
        deleted: project?.deleted ?? null,
        ownerId: project?.ownerId ?? apId(),
        displayName: project?.displayName ?? faker.lorem.word(),
        platformId: project?.platformId ?? apId(),
        externalId: project?.externalId ?? apId(),
        releasesEnabled: project?.releasesEnabled ?? false,
        notifyFlowOwnerOnFailure: project?.notifyFlowOwnerOnFailure ?? false,
        metadata: project?.metadata ?? null,
        type: project?.type ?? ProjectType.TEAM,
        poolId: project?.poolId ?? null,
        workerGroupId: project?.workerGroupId ?? null,
        executionDataRetentionDays: project?.executionDataRetentionDays ?? null,
        icon,
    }
}

// HERMES: createMockGitRepo removed — project-release/git-sync is EE and went with app/ee.

// HERMES: createMockPlatformPlan removed — the ee platform_plan entity is gone (G6);
// CE always reports OPEN_SOURCE_PLAN.

export const createMockPlatform = (platform?: Partial<Platform>): Platform => {
    return {
        id: platform?.id ?? apId(),
        created: platform?.created ?? faker.date.recent().toISOString(),
        updated: platform?.updated ?? faker.date.recent().toISOString(),
        ownerId: platform?.ownerId ?? apId(),
        enforceAllowedAuthDomains: platform?.enforceAllowedAuthDomains ?? false,
        federatedAuthProviders: platform?.federatedAuthProviders ?? { saml: null },
        allowedAuthDomains: platform?.allowedAuthDomains ?? [],
        allowedEmbedOrigins: platform?.allowedEmbedOrigins ?? [],
        name: platform?.name ?? faker.lorem.word(),
        primaryColor: platform?.primaryColor ?? faker.color.rgb(),
        themeColors: platform?.themeColors ?? null,
        logoIconUrl: platform?.logoIconUrl ?? faker.image.urlPlaceholder(),
        fullLogoUrl: platform?.fullLogoUrl ?? faker.image.urlPlaceholder(),
        emailAuthEnabled: platform?.emailAuthEnabled ?? faker.datatype.boolean(),
        pinnedPieces: platform?.pinnedPieces ?? [],
        favIconUrl: platform?.favIconUrl ?? faker.image.urlPlaceholder(),
        cloudAuthEnabled: platform?.cloudAuthEnabled ?? faker.datatype.boolean(),
        googleAuthEnabled: platform?.googleAuthEnabled ?? true,
        ssoDomain: platform?.ssoDomain ?? null,
        ssoDomainVerification: platform?.ssoDomainVerification ?? null,
    }
}

export const createMockPlatformWithOwner = (
    params?: CreateMockPlatformWithOwnerParams,
): CreateMockPlatformWithOwnerReturn => {
    const mockOwnerId = params?.owner?.id ?? apId()
    const mockPlatformId = params?.platform?.id ?? apId()

    const mockUserIdentity = createMockUserIdentity({})

    const mockOwner = createMockUser({
        identityId: mockUserIdentity.id,
        ...params?.owner,
        id: mockOwnerId,
        platformId: mockPlatformId,
        platformRole: PlatformRole.ADMIN,
    })

    const mockPlatform = createMockPlatform({
        ...params?.platform,
        id: mockPlatformId,
        ownerId: mockOwnerId,
    })

    return {
        mockUserIdentity,
        mockPlatform,
        mockOwner,
    }
}

export const createMockProjectMember = (
    projectMember?: Omit<Partial<ProjectMember>, 'projectRoleId'> & {
        projectRoleId: string
    },
): ProjectMember => {
    assertNotNullOrUndefined(projectMember?.userId, 'userId')
    return {
        id: projectMember?.id ?? apId(),
        created: projectMember?.created ?? faker.date.recent().toISOString(),
        updated: projectMember?.updated ?? faker.date.recent().toISOString(),
        platformId: projectMember?.platformId ?? apId(),
        projectRoleId: projectMember.projectRoleId,
        userId: projectMember?.userId,
        projectId: projectMember?.projectId ?? apId(),
    }
}

const MOCK_SIGNING_KEY_PUBLIC_KEY = `-----BEGIN RSA PUBLIC KEY-----
MIICCgKCAgEAlnd5vGP/1bzcndN/yRD+ZTd6tuemxaJd+12bOZ2QCXcTM03AKSp3
NE5QMyIi13PXMg+z1uPowfivPJ4iVTMaW1U00O7JlUduGR0VrG0BCJlfEf852V71
TfE+2+EpMme9Yw6Gs/YAuOwgVwu3n/XF0il3FTIm1oY1a/MA79rv0RSscnIgCaYJ
e86LWm+H6753Si0MIId/ajIfYYIndN6qRIlPsgagdL+kljUSPEiIzmV0POxTltBo
tXL1t7Mu+meJrY85MXG5W8BS05+q6dJql7Cl0UbPK152ziakB+biMI/4hYlaOIBT
3KeOcz/Jg7Zv21Y0tbdrZ5osVrrNpFsCV7PGyQIUDVmmnCHrOEBS2XM5zOHzTxMl
JQh3Db318rB5415zuBTzrO+20++03kH4SwZEEBg1SDAInYwLOWldbTuZuD0Hx7P2
g4a3OqHHVOcAgtsHgmU7/zCgCIETg4KbRdpSsqOm/YJDWWoLDTwvKnH5QHSBacq1
kxbNAUSuLQESkfZq1Dw5+tdBDJr29bxjmiSggyittTYn1B3iHACNoe4zj9sMQQIf
j9mmntXsa/leIwBVspiEOHYZwJOe5+goSd8K1VIQJxC1DVBxB2eHxMvuo3eyJ0HE
DlebIeZy4zrE1LPgRic1kfdemyxvuN3iwZnPGiY79nL1ZNDM3M4ApSMCAwEAAQ==
-----END RSA PUBLIC KEY-----`

// HERMES: createMockApiKey removed — the EE api-key module (and its generateApiKey) went
// with the EE strip (G6); nothing can mint a SERVICE token in this build.

export const createMockSigningKey = (
    signingKey?: Partial<SigningKey>,
): SigningKey => {
    return {
        id: signingKey?.id ?? apId(),
        created: signingKey?.created ?? faker.date.recent().toISOString(),
        updated: signingKey?.updated ?? faker.date.recent().toISOString(),
        displayName: signingKey?.displayName ?? faker.lorem.word(),
        platformId: signingKey?.platformId ?? apId(),
        publicKey: signingKey?.publicKey ?? MOCK_SIGNING_KEY_PUBLIC_KEY,
        algorithm: signingKey?.algorithm ?? KeyAlgorithm.RSA,
    }
}


export const createMockPieceMetadata = (
    pieceMetadata?: Partial<Omit<PieceMetadataSchema, 'project'>>,
): Omit<PieceMetadataSchema, 'project'> => {
    return {
        id: pieceMetadata?.id ?? apId(),
        projectUsage: 0,
        created: pieceMetadata?.created ?? faker.date.recent().toISOString(),
        updated: pieceMetadata?.updated ?? faker.date.recent().toISOString(),
        name: pieceMetadata?.name ?? faker.lorem.word(),
        displayName: pieceMetadata?.displayName ?? faker.lorem.word(),
        logoUrl: pieceMetadata?.logoUrl ?? faker.image.urlPlaceholder(),
        description: pieceMetadata?.description ?? faker.lorem.sentence(),
        directoryPath: pieceMetadata?.directoryPath,
        auth: pieceMetadata?.auth,
        authors: pieceMetadata?.authors ?? [],
        platformId: pieceMetadata?.platformId,
        version: pieceMetadata?.version ?? faker.system.semver(),
        minimumSupportedRelease: pieceMetadata?.minimumSupportedRelease ?? '0.0.0',
        maximumSupportedRelease: pieceMetadata?.maximumSupportedRelease ?? '9.9.9',
        actions: pieceMetadata?.actions ?? {},
        triggers: pieceMetadata?.triggers ?? {},
        pieceType: pieceMetadata?.pieceType ?? faker.helpers.enumValue(PieceType),
        packageType:
            pieceMetadata?.packageType ?? faker.helpers.enumValue(PackageType),
        archiveId: pieceMetadata?.archiveId,
        categories: pieceMetadata?.categories ?? [],
        contextInfo: pieceMetadata?.contextInfo ?? { version: LATEST_CONTEXT_VERSION },
    }
}

export const createAuditEvent = (auditEvent: Partial<ApplicationEvent>) => {
    return {
        id: auditEvent.id ?? apId(),
        created: auditEvent.created ?? faker.date.recent().toISOString(),
        updated: auditEvent.updated ?? faker.date.recent().toISOString(),
        ip: auditEvent.ip ?? faker.internet.ip(),
        platformId: auditEvent.platformId,
        userId: auditEvent.userId,
        userEmail: auditEvent.userEmail ?? faker.internet.email(),
        action: auditEvent.action ?? faker.helpers.enumValue(ApplicationEventName),
        data: auditEvent.data ?? {},
    }
}

// HERMES: createMockOtp removed — the ee otp module went with the EE strip (G6).

export const createMockFlowRun = (flowRun?: Partial<FlowRun>): FlowRun => {
    return {
        id: flowRun?.id ?? apId(),
        created: flowRun?.created ?? faker.date.recent().toISOString(),
        updated: flowRun?.updated ?? faker.date.recent().toISOString(),
        projectId: flowRun?.projectId ?? apId(),
        flowId: flowRun?.flowId ?? apId(),
        tags: flowRun?.tags ?? [],
        steps: {},
        failParentOnFailure: flowRun?.failParentOnFailure ?? false,
        parentRunId: flowRun?.parentRunId ?? undefined,
        flowVersionId: flowRun?.flowVersionId ?? apId(),
        flowVersion: flowRun?.flowVersion,
        logsFileId: flowRun?.logsFileId ?? null,
        status: flowRun?.status ?? faker.helpers.enumValue(FlowRunStatus),
        startTime: flowRun?.startTime ?? faker.date.recent().toISOString(),
        finishTime: flowRun?.finishTime ?? faker.date.recent().toISOString(),
        environment:
            flowRun?.environment ?? faker.helpers.enumValue(RunEnvironment),
    }
}

export const createMockFlow = (flow?: Partial<Flow>): Flow => {
    return {
        id: flow?.id ?? apId(),
        created: flow?.created ?? faker.date.recent().toISOString(),
        updated: flow?.updated ?? faker.date.recent().toISOString(),
        projectId: flow?.projectId ?? apId(),
        status: flow?.status ?? faker.helpers.enumValue(FlowStatus),
        folderId: flow?.folderId ?? null,
        operationStatus: flow?.operationStatus ?? FlowOperationStatus.NONE,
        publishedVersionId: flow?.publishedVersionId ?? null,
        externalId: flow?.externalId ?? apId(),
    }
}

export const createMockFlowVersion = (
    flowVersion?: Partial<FlowVersion>,
): FlowVersion => {
    const emptyTrigger = {
        type: FlowTriggerType.EMPTY,
        name: 'trigger',
        settings: {},
        valid: false,
        displayName: 'Select Trigger',
        lastUpdatedDate: dayjs().toISOString(),
    } as const

    return {
        id: flowVersion?.id ?? apId(),
        created: flowVersion?.created ?? faker.date.recent().toISOString(),
        updated: flowVersion?.updated ?? faker.date.recent().toISOString(),
        displayName: flowVersion?.displayName ?? faker.word.words(),
        flowId: flowVersion?.flowId ?? apId(),
        agentIds: flowVersion?.agentIds ?? [],
        trigger: flowVersion?.trigger ?? emptyTrigger,
        connectionIds: flowVersion?.connectionIds ?? [],
        state: flowVersion?.state ?? faker.helpers.enumValue(FlowVersionState),
        updatedBy: flowVersion?.updatedBy,
        valid: flowVersion?.valid ?? faker.datatype.boolean(),
        notes: flowVersion?.notes ?? [],
        schemaVersion: flowVersion?.schemaVersion ?? LATEST_FLOW_SCHEMA_VERSION,
        backupFiles: flowVersion?.backupFiles ?? null,
    }
}

export const createMockConnection = (connection: Partial<AppConnection>, ownerId: string): AppConnection<AppConnectionType.SECRET_TEXT> => {
    return {
        id: connection?.id ?? apId(),
        created: connection?.created ?? faker.date.recent().toISOString(),
        updated: connection?.updated ?? faker.date.recent().toISOString(),
        platformId: connection?.platformId ?? apId(),
        projectIds: connection?.projectIds ?? [],
        pieceName: connection?.pieceName ?? faker.lorem.word(),
        displayName: connection?.displayName ?? faker.lorem.word(),
        type: AppConnectionType.SECRET_TEXT,
        scope: AppConnectionScope.PROJECT,
        status: AppConnectionStatus.ACTIVE,
        ownerId,
        value: {
            type: AppConnectionType.SECRET_TEXT,
            secret_text: faker.lorem.word(),
        },
        metadata: connection?.metadata ?? {},
        externalId: connection?.externalId ?? apId(),
        owner: null,
        pieceVersion: connection?.pieceVersion ?? '0.0.0',
        preSelectForNewProjects: connection?.preSelectForNewProjects ?? false,
    }
}

// HERMES: createMockTable / createMockField / createMockRecord / createMockCell removed
// with the tables domain (FR-D2).

// HERMES: createMockSolutionAndSave / checkIfSolutionExistsInDb removed — the "solution"
// fixture was table/field/record/cell shaped and had no consumer once the tables domain went.

export const mockBasicUser = async ({ userIdentity, user }: { userIdentity?: Partial<UserIdentity>, user?: Partial<User> }) => {
    const mockUserIdentity = createMockUserIdentity({
        verified: true,
        ...userIdentity,
    })
    await databaseConnection().getRepository('user_identity').save(mockUserIdentity)
    const mockUser = createMockUser({
        ...user,
        identityId: mockUserIdentity.id,
    })
    await databaseConnection().getRepository('user').save(mockUser)
    return {
        mockUserIdentity,
        mockUser,
    }
}
export const mockAndSaveBasicSetup = async (params?: MockBasicSetupParams): Promise<MockBasicSetup> => {
    const mockUserIdentity = createMockUserIdentity({
        verified: true,
        ...params?.userIdentity,
    })
    await databaseConnection().getRepository('user_identity').save(mockUserIdentity)

    const mockOwner = createMockUser({
        ...params?.user,
        identityId: mockUserIdentity.id,
        platformRole: PlatformRole.ADMIN,
    })
    await databaseConnection().getRepository('user').save(mockOwner)

    const mockPlatform = createMockPlatform({
        ...params?.platform,
        ownerId: mockOwner.id,
    })

    await databaseConnection().getRepository('platform').save(mockPlatform)
    // HERMES: no platform_plan seeding — the ee platform-plan entity/table is gone (G6) and
    // getPlan() always returns OPEN_SOURCE_PLAN.

    mockOwner.platformId = mockPlatform.id
    await databaseConnection().getRepository('user').save(mockOwner)

    const mockProject = createMockProject({
        ...params?.project,
        ownerId: mockOwner.id,
        platformId: mockPlatform.id,
    })
    await databaseConnection().getRepository('project').save(mockProject)

    return {
        mockUserIdentity,
        mockOwner,
        mockPlatform,
        mockProject,
    }
}

// HERMES: mockAndSaveBasicSetupWithApiKey removed with the EE api-key module (G6).

export const createMockFile = (file?: Partial<File>): File => {
    const hasExplicitProjectId = file !== undefined && 'projectId' in file
    const hasExplicitPlatformId = file !== undefined && 'platformId' in file
    return {
        id: file?.id ?? apId(),
        created: file?.created ?? faker.date.recent().toISOString(),
        updated: file?.updated ?? faker.date.recent().toISOString(),
        platformId: hasExplicitPlatformId ? (file?.platformId ?? null) : apId(),
        projectId: hasExplicitProjectId ? (file?.projectId ?? null) : apId(),
        location: file?.location ?? FileLocation.DB,
        compression: file?.compression ?? faker.helpers.enumValue(FileCompression),
        data: file?.data ?? Buffer.from(faker.lorem.paragraphs()),
        type: file?.type ?? faker.helpers.enumValue(FileType),
        fileName: file?.fileName ?? null,
        metadata: file?.metadata ?? null,
        s3Key: file?.s3Key ?? null,
        size: file?.size ?? null,
    }
}

export const createMockProjectRole = (projectRole?: Partial<ProjectRole>): ProjectRole => {
    return {
        id: projectRole?.id ?? apId(),
        name: projectRole?.name ?? faker.lorem.word(),
        created: projectRole?.created ?? faker.date.recent().toISOString(),
        updated: projectRole?.updated ?? faker.date.recent().toISOString(),
        permissions: projectRole?.permissions ?? [],
        platformId: projectRole?.platformId ?? apId(),
        type: projectRole?.type ?? faker.helpers.enumValue(RoleType),
    }
}

export const createMockProjectRelease = (projectRelease?: Partial<ProjectRelease>): ProjectRelease => {
    return {
        id: projectRelease?.id ?? apId(),
        created: projectRelease?.created ?? faker.date.recent().toISOString(),
        updated: projectRelease?.updated ?? faker.date.recent().toISOString(),
        projectId: projectRelease?.projectId ?? apId(),
        importedBy: projectRelease?.importedBy ?? apId(),
        fileId: projectRelease?.fileId ?? apId(),
        name: projectRelease?.name ?? faker.lorem.word(),
        description: projectRelease?.description ?? faker.lorem.sentence(),
        type: projectRelease?.type ?? faker.helpers.enumValue(ProjectReleaseType),
    }
}

// HERMES: createMockAIProvider / mockAndSaveAIProvider removed — the ai domain (and its
// ai_provider entity) was deleted (FR-D2).

export const mockPieceMetadata = async (mockLog: FastifyBaseLogger): Promise<PieceMetadata> => {
    const { mockPlatform } = await mockAndSaveBasicSetup()
    const mockPieceMetadata = createMockPieceMetadata({
        platformId: mockPlatform.id,
        packageType: PackageType.REGISTRY,
    })
    await databaseConnection().getRepository('piece_metadata').save([mockPieceMetadata])
    pieceMetadataService(mockLog).getOrThrow = vi.fn().mockResolvedValue(mockPieceMetadata)
    return mockPieceMetadata
}

export const createMockFolder = (folder?: Partial<Folder>): Folder => {
    return {
        id: folder?.id ?? apId(),
        created: folder?.created ?? faker.date.recent().toISOString(),
        updated: folder?.updated ?? faker.date.recent().toISOString(),
        projectId: folder?.projectId ?? apId(),
        displayName: folder?.displayName ?? faker.lorem.word(),
        displayOrder: folder?.displayOrder ?? faker.number.int({ min: 0, max: 100 }),
    }
}

// HERMES: createMockEventDestination removed with the event-destinations domain (FR-D2).
