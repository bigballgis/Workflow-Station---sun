import esbuild from 'esbuild'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..')
const src = (rel) => path.join(root, rel)
const alias = {
  '@activepieces/core-utils': src('packages/core/utils/src/index.ts'),
  '@activepieces/core-execution': src('packages/core/execution/src/index.ts'),
  '@activepieces/core-piece-types': src('packages/core/piece-types/src/index.ts'),
  '@activepieces/core-formula': src('packages/core/formula/src/index.ts'),
  '@activepieces/shared': src('packages/core/shared/src/index.ts'),
  '@activepieces/pieces-framework': src('packages/pieces/framework/src/index.ts'),
  '@activepieces/pieces-common': src('packages/pieces/common/src/index.ts'),
}
await esbuild.build({
  absWorkingDir: root,
  entryPoints: [src('packages/pieces/community/function-unit-data/src/index.ts')],
  bundle: true,
  platform: 'node',
  format: 'cjs',
  outfile: src('packages/pieces/community/function-unit-data/dist/src/index.js'),
  alias,
  logLevel: 'warning',
})
console.log('bundled')
