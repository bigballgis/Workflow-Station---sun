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

/**
 * HSWORKFLOW-928: Inline Form widget must pass the bound sub-form's Event config.
 * SubTableInlineForm already runs JS; FormRendererFields used to omit formOptions.
 */
describe('FormRendererFields inlineSubForm Event wiring', () => {
  it('passes formOptions and dialogColumns on both in-column and top-level arms', () => {
    const src = readFileSync(
      resolve(__dirname, '../FormRendererFields.vue'),
      'utf8',
    )
    const inlineBlock = src.split("field.type === 'inlineSubForm'")[1]
    expect(inlineBlock).toBeTruthy()
    expect(
      inlineBlock.match(/:form-options="ctx\.resolveBinding\(field\._bindingId\)\?\.formOptions"/g)?.length,
    ).toBe(2)
    expect(
      inlineBlock.match(/:dialog-columns="\(ctx\.resolveBinding\(field\._bindingId\)\?\.dialogColumns as any\[\]\) \|\| undefined"/g)?.length,
    ).toBe(2)
  })
})
