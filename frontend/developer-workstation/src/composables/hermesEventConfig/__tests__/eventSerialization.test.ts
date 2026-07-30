import { describe, expect, it } from 'vitest'
import {
  loadEventData,
  parseEventData,
  FNX_PREFIX,
} from '../eventSerialization'
import {
  prepareFormCreateRulesForPersist,
  ensureEmptyRuleComponentEvents,
} from '@/utils/formCreateDefaultEvents'

describe('hermesEventConfig eventSerialization', () => {
  it('loadEventData keeps change body and skips empty $FNX stubs', () => {
    const loaded = loadEventData(
      {
        change: '$FNX:\napi.hidden(true, "__subTable_1")',
        blur: '$FNX:',
      },
      null,
    )
    expect(loaded.change).toEqual(['api.hidden(true, "__subTable_1")'])
    expect(loaded.blur).toBeUndefined()
  })

  it('loadEventData falls back to rule.on when _on/modelValue is empty', () => {
    const loaded = loadEventData(
      {},
      {
        on: {
          change: '$FNX:\nvar hasUser = !!value\napi.hidden(hasUser, "__subTable_42")',
        },
      },
    )
    expect(loaded.change?.[0]).toContain('__subTable_42')
  })

  it('loadEventData prefers rule._on over rule.on like _hook', () => {
    const loaded = loadEventData(
      {},
      {
        _on: {
          change: '$FNX:\napi.hidden(true, "__from_on_shadow")',
        },
        on: {
          change: '$FNX:\napi.hidden(true, "__from_on")',
        },
      },
    )
    expect(loaded.change?.[0]).toContain('__from_on_shadow')
  })

  it('loadEventData reads __json from compiled preview handlers', () => {
    const fn = Object.assign(() => {}, {
      __json: '$FNX:\napi.hidden(true, "x")',
    })
    const loaded = loadEventData({ change: fn }, null)
    expect(loaded.change).toEqual(['api.hidden(true, "x")'])
  })

  it('parseEventData drops empty bodies and keeps real code', () => {
    const { on, hooks } = parseEventData({
      change: ['api.hidden(true, "__subTable_1")', ''],
      hook_load: ['  '],
      hook_value: ['api.hidden(false, "__subTable_1")'],
    })
    expect(on.change).toBe(`${FNX_PREFIX}api.hidden(true, "__subTable_1")`)
    expect(hooks.load).toBeUndefined()
    expect(hooks.value).toContain('__subTable_1')
  })

  it('prepareFormCreateRulesForPersist keeps handlers on `on` even when _fc_id is present', () => {
    const rules = [
      {
        type: 'lookup',
        field: 'user',
        _fc_id: 'id_live',
        _on: {
          change: '$FNX:\napi.hidden(true, "__subTable_9")',
        },
      },
    ] as Record<string, unknown>[]

    prepareFormCreateRulesForPersist(rules)
    // Simulate the old buggy save path: ensure after prepare must NOT be required
    // for handlers to survive. Persist shape should already be portal `on`.
    expect(rules[0]._on).toBeUndefined()
    expect(String((rules[0].on as Record<string, unknown>).change)).toContain('__subTable_9')

    // Re-seed as designer would after load — Event panel reads _on
    ensureEmptyRuleComponentEvents(rules[0])
    // With _fc_id, ensure writes _on; non-empty change must survive
    expect(String((rules[0]._on as Record<string, unknown>).change)).toContain('__subTable_9')
  })
})
