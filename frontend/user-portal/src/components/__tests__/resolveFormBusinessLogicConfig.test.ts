import { describe, it, expect } from 'vitest'
import { resolveFormBusinessLogicConfig } from '../formRendererHelpers'

describe('resolveFormBusinessLogicConfig', () => {
  it('uses formConfig when config is omitted (Portal start/task pages)', () => {
    const formConfig = {
      rule: [],
      options: {},
      subForms: {},
      linkages: [
        {
          sourceField: 'scenario',
          targetField: 'start_date',
          linkageType: 'field-state-change' as const,
          stateConfig: {
            condition: { field: 'scenario', operator: 'equals' as const, value: 'A' },
            required: true,
          },
        },
      ],
    }
    const resolved = resolveFormBusinessLogicConfig(undefined, formConfig)
    expect(resolved?.linkages).toHaveLength(1)
    expect(resolved?.linkages?.[0].sourceField).toBe('scenario')
  })

  it('prefers explicit config over formConfig', () => {
    const config = {
      rule: [],
      options: {},
      subForms: {},
      linkages: [{ sourceField: 'fromConfig', targetField: 'x', linkageType: 'value-auto-fill' as const }],
    }
    const formConfig = {
      linkages: [{ sourceField: 'fromFormConfig', targetField: 'y', linkageType: 'value-auto-fill' as const }],
    }
    expect(resolveFormBusinessLogicConfig(config, formConfig)?.linkages?.[0].sourceField).toBe('fromConfig')
  })

  it('returns undefined for missing or non-object payloads', () => {
    expect(resolveFormBusinessLogicConfig(undefined, null)).toBeUndefined()
    expect(resolveFormBusinessLogicConfig(undefined, [])).toBeUndefined()
  })
})
