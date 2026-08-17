import { QueryRunner } from 'typeorm'
import { Migration } from '../../migration'

const CDN_PREFIX = 'https://cdn.activepieces.com/'
const LOCAL_PREFIX = '/ap-cdn/'

/**
 * HERMES patch — repoint branding and piece icons at assets we serve ourselves.
 *
 * Production is air-gapped (DECISIONS X-2/X-3), so anything still pointing at
 * cdn.activepieces.com renders broken there: the sidebar logo, the favicon, and the
 * icon of every step on every flow canvas. Fresh installs already get local paths
 * (defaultTheme + the piece seed), but any environment provisioned before this patch
 * has the CDN URLs baked into its rows — platform.create() copies the theme defaults
 * into the row, and piece_metadata stores logoUrl verbatim.
 *
 * Only rows still holding an untouched CDN URL are rewritten, so a genuinely
 * customised logo is never clobbered. Assets are mirrored path-for-path under
 * /ap-cdn by deploy/pieces/mirror-ap-cdn.mjs, which makes this a pure prefix swap.
 *
 * The two in-house pieces are special: their upstream URLs 404 even with internet
 * access, so they get hand-authored icons instead of a mirrored one.
 */
export class HermesLocalizeCdnAssets1824000000000 implements Migration {
    name = 'HermesLocalizeCdnAssets1824000000000'
    breaking = false
    release = '0.88.0'

    public async up(queryRunner: QueryRunner): Promise<void> {
        const platform = await queryRunner.query(`
            UPDATE "platform"
            SET "logoIconUrl" = '/hermes-mark.svg',
                "favIconUrl"  = '/hermes-mark.svg',
                "fullLogoUrl" = '/hermes-full-logo.svg'
            WHERE "logoIconUrl" LIKE '${CDN_PREFIX}%'
               OR "favIconUrl"  LIKE '${CDN_PREFIX}%'
               OR "fullLogoUrl" LIKE '${CDN_PREFIX}%'
        `)

        const pieces = await queryRunner.query(`
            UPDATE "piece_metadata"
            SET "logoUrl" = '${LOCAL_PREFIX}' || substring("logoUrl" from ${CDN_PREFIX.length + 1})
            WHERE "logoUrl" LIKE '${CDN_PREFIX}%'
        `)

        // Authored in-house (packages/web/public/ap-cdn/pieces/hermes/) because the
        // upstream CDN has no icon for either piece.
        await queryRunner.query(`
            UPDATE "piece_metadata"
            SET "logoUrl" = '${LOCAL_PREFIX}pieces/hermes/biz-calendar.svg'
            WHERE "name" = '@activepieces/piece-biz-calendar'
        `)
        await queryRunner.query(`
            UPDATE "piece_metadata"
            SET "logoUrl" = '${LOCAL_PREFIX}pieces/hermes/hash-helper.svg'
            WHERE "name" = '@activepieces/piece-hash-helper'
        `)

        const platformRows = platform?.[1] ?? 0
        const pieceRows = pieces?.[1] ?? 0
        // eslint-disable-next-line no-console
        console.log(`[HermesLocalizeCdnAssets] platform rows=${platformRows}, piece_metadata rows=${pieceRows}`)
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            UPDATE "piece_metadata"
            SET "logoUrl" = '${CDN_PREFIX}' || substring("logoUrl" from ${LOCAL_PREFIX.length + 1})
            WHERE "logoUrl" LIKE '${LOCAL_PREFIX}%'
        `)
        await queryRunner.query(`
            UPDATE "platform"
            SET "logoIconUrl" = '${CDN_PREFIX}brand/logo.svg',
                "favIconUrl"  = '${CDN_PREFIX}brand/logo.svg',
                "fullLogoUrl" = '${CDN_PREFIX}brand/full-logo.png'
            WHERE "logoIconUrl" = '/hermes-mark.svg'
        `)
    }
}
