import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Regression: api.hidden('__subTable_N') must hide placed SubTable widgets.
 * SubTable branch previously rendered without isFieldVisible — scripts appeared to "do nothing".
 */
describe('FormRendererFields subTable visibility gate', () => {
  it('gates subTable rendering with isFieldVisible(field.key)', () => {
    const src = readFileSync(
      resolve(__dirname, '../FormRendererFields.vue'),
      'utf8',
    )
    expect(src).toMatch(
      /field\.type === 'subTable'[\s\S]*?v-if="!inColumn && ctx\.isFieldVisible\(field\.key\)"/,
    )
    expect(src).toMatch(
      /v-else-if="inColumn && ctx\.isFieldVisible\(field\.key\)"[\s\S]*?form-col-subtable/,
    )
  })
})
