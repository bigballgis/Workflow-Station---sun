import { PieceMetadataModel } from '@activepieces/pieces-framework'
import { AddPieceRequestBody, PrincipalType } from '@activepieces/shared'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { StatusCodes } from 'http-status-codes'
import { z } from 'zod'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { pieceMetadataService } from './metadata/piece-metadata-service'
import { pieceInstallService } from './piece-install-service'

export const communityPiecesModule: FastifyPluginAsyncZod = async (app) => {
    await app.register(communityPiecesController, { prefix: '/v1/pieces' })
}

const communityPiecesController: FastifyPluginAsyncZod = async (app) => {
    app.post(
        '/',
        {
            config: {
                security: securityAccess.platformAdminOnly([PrincipalType.USER, PrincipalType.SERVICE]),
            },
            schema: {
                body: AddPieceRequestBody,
            },
        },
        async (req, res): Promise<PieceMetadataModel> => {
            const platformId = req.principal.platform.id
            const pieceMetadata = await pieceInstallService(req.log).installPiece(
                platformId,
                req.body,
            )
            return res.code(StatusCodes.CREATED).send(pieceMetadata)
        },
    )

    // HERMES-PATCH(piece-admin P2): expose piece deletion for the Admin Center
    // management surface. Upstream CE has bulkDelete() in the service layer but no
    // HTTP route (only the EE platform module ever surfaced pieces management).
    // Scope note: deletes by (name, version) without a platformId filter so the
    // single-platform HERMES deployment can also clean up OFFICIAL seed residue;
    // the route is platformAdminOnly, matching the install route above. A deleted
    // ARCHIVE piece leaves its archive row in the file table (FK is on
    // piece_metadata.archiveId → file.id, so the orphan is inert and tiny).
    app.delete(
        '/',
        {
            config: {
                security: securityAccess.platformAdminOnly([PrincipalType.USER, PrincipalType.SERVICE]),
            },
            schema: {
                body: DeletePieceRequestBody,
            },
        },
        async (req, res): Promise<void> => {
            await pieceMetadataService(req.log).bulkDelete([
                { name: req.body.pieceName, version: req.body.pieceVersion },
            ])
            return res.code(StatusCodes.NO_CONTENT).send()
        },
    )
}

const DeletePieceRequestBody = z.object({
    pieceName: z.string().min(1),
    pieceVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
})
