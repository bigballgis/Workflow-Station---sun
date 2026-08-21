import {
    AP_MAXIMUM_PROFILE_PICTURE_SIZE,
    ApId,
    ApMultipartFile,
    FileType,
    isNil,
    PrincipalType,
    PROFILE_PICTURE_ALLOWED_TYPES,
    SERVICE_KEY_SECURITY_OPENAPI,
    UpdateMeResponse,
    UserWithMetaInformation,
} from '@activepieces/shared'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { StatusCodes } from 'http-status-codes'
import { z } from 'zod'
import { userIdentityService } from '../authentication/user-identity/user-identity-service'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { fileService } from '../file/file.service'
import { userService } from './user-service'

// CE reimplementation of the (removed) ee users controller — the "shadow /v1/users"
// left open by EE_REMOVAL_PLAN G8/R10. The builder fetches the current user through
// GET /v1/users/:id to render its header, so without this it 404s (harmless but noisy);
// the two /me routes back the profile-picture UI. All three are faithful to upstream and
// depend only on CE services (fileService.uploadPublicAsset and userIdentityService are
// core). HERMES: 0.88 dropped the badges domain, so this returns UserWithMetaInformation
// (what 0.88's userService.getOneByIdAndPlatformIdOrThrow yields) instead of 0.84's
// UserWithBadges.
const usersController: FastifyPluginAsyncZod = async (app) => {
    app.get('/:id', GetUserByIdRequest, async (req): Promise<UserWithMetaInformation> => {
        const userId = req.params.id
        const platformId = req.principal.platform.id
        return userService(req.log).getOneByIdAndPlatformIdOrThrow({ id: userId, platformId })
    })

    app.post('/me', UpdateMeRequest, async (req) => {
        const userId = req.principal.id
        const user = await userService(req.log).getOrThrow({ id: userId })
        const identityId = user.identityId
        const platformId = req.principal.platform.id

        const imageUrl = await fileService(app.log).uploadPublicAsset({
            file: req.body.profilePicture,
            type: FileType.USER_PROFILE_PICTURE,
            platformId,
            allowedMimeTypes: PROFILE_PICTURE_ALLOWED_TYPES,
            maxFileSizeInBytes: AP_MAXIMUM_PROFILE_PICTURE_SIZE,
            metadata: { identityId },
        })

        if (!isNil(imageUrl)) {
            await userIdentityService(app.log).update(identityId, { imageUrl })
        }

        return userIdentityService(app.log).getBasicInformation(identityId)
    })

    app.delete('/me/profile-picture', DeleteProfilePictureRequest, async (req) => {
        const userId = req.principal.id
        const user = await userService(req.log).getOrThrow({ id: userId })
        const identityId = user.identityId

        await userIdentityService(app.log).update(identityId, { imageUrl: null })

        return { success: true }
    })
}

export const userModule: FastifyPluginAsyncZod = async (app) => {
    await app.register(usersController, { prefix: '/v1/users' })
}

const GetUserByIdRequest = {
    schema: {
        tags: ['users'],
        description: 'Get a user by id',
        security: [SERVICE_KEY_SECURITY_OPENAPI],
        params: z.object({
            id: ApId,
        }),
        response: {
            [StatusCodes.OK]: UserWithMetaInformation,
        },
    },
    config: {
        security: securityAccess.publicPlatform([PrincipalType.USER, PrincipalType.SERVICE]),
    },
}

const UpdateMeRequest = {
    config: {
        security: securityAccess.publicPlatform([PrincipalType.USER]),
    },
    schema: {
        consumes: ['multipart/form-data'],
        body: z.object({
            profilePicture: z.optional(ApMultipartFile),
        }),
        response: {
            [StatusCodes.OK]: UpdateMeResponse,
        },
    },
}

const DeleteProfilePictureRequest = {
    schema: {
        response: {
            [StatusCodes.OK]: z.object({
                success: z.boolean(),
            }),
        },
    },
    config: {
        security: securityAccess.publicPlatform([PrincipalType.USER]),
    },
}
