import { KeyAlgorithm, Platform, SigningKey } from '@activepieces/shared'
import { EntitySchema } from 'typeorm'
import { ApIdSchema, BaseColumnSchemaPart } from '../database/database-common'

// CE reimplementation of the (removed) ee signing-key entity. The signing_key
// TABLE is created by MIT-region migrations (1698602417745-add-signing-key +
// 1698698190965-AddDisplayNameToSigningKey; `generatedBy` later moved to audit
// by 1709669091258), so only the entity MAPPING had to be re-provided here.
// Needed by L7 per-user provisioning: external-token-extractor verifies the
// DW-signed external token (RS256) against publicKey looked up by `kid`.
type SigningKeySchema = SigningKey & {
    platform: Platform
}

export const SigningKeyEntity = new EntitySchema<SigningKeySchema>({
    name: 'signing_key',
    columns: {
        ...BaseColumnSchemaPart,
        displayName: {
            type: String,
            nullable: false,
        },
        platformId: {
            ...ApIdSchema,
            nullable: false,
        },
        publicKey: {
            type: String,
            nullable: false,
        },
        algorithm: {
            type: String,
            enum: KeyAlgorithm,
            nullable: false,
        },
    },
    indices: [],
    relations: {
        platform: {
            type: 'many-to-one',
            target: 'platform',
            onDelete: 'RESTRICT',
            onUpdate: 'RESTRICT',
            joinColumn: {
                name: 'platformId',
                foreignKeyConstraintName: 'fk_signing_key_platform_id',
            },
        },
    },
})
