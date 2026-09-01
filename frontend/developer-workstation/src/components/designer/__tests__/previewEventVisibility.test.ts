import { describe, expect, it } from 'vitest'
import {
  applyPreviewOverlayToRules,
  applyPreviewVisibilityToRules,
  collectFieldKeysFromRules,
} from '../previewEventVisibility'

describe('applyPreviewVisibilityToRules', () => {
  it('keeps Designer Hide even when script says visible', () => {
    const out = applyPreviewVisibilityToRules(
      [{ field: 'a', type: 'input', hidden: true }],
      () => true,
    ) as Array<{ hidden: boolean }>
    expect(out[0].hidden).toBe(true)
  })

  it('hides when script hides and designer did not', () => {
    const out = applyPreviewVisibilityToRules(
      [{ field: 'a', type: 'input', hidden: false }],
      (key) => key !== 'a',
    ) as Array<{ hidden: boolean }>
    expect(out[0].hidden).toBe(true)
  })

  it('leaves visible when designer and script both show', () => {
    const out = applyPreviewVisibilityToRules(
      [{ field: 'a', type: 'input' }],
      () => true,
    ) as Array<{ hidden?: boolean }>
    expect(out[0].hidden).toBe(false)
  })

  it('walks props.children nesting (same as getRuleChildren)', () => {
    const out = applyPreviewVisibilityToRules(
      [
        {
          type: 'elCard',
          props: {
            children: [{ field: 'nested', type: 'input', hidden: true }],
          },
        },
      ],
      () => true,
    ) as Array<{ props: { children: Array<{ hidden: boolean }> } }>
    expect(out[0].props.children[0].hidden).toBe(true)
  })

  it('walks rule.rule nesting used by some Preview snapshots', () => {
    const out = applyPreviewVisibilityToRules(
      [
        {
          type: 'group',
          rule: [{ field: 'inner', type: 'input', hidden: true }],
        },
      ],
      () => true,
    ) as Array<{ rule: Array<{ hidden: boolean }> }>
    expect(out[0].rule[0].hidden).toBe(true)
  })
})

describe('collectFieldKeysFromRules', () => {
  it('collects nested keys from props.list and rule.rule', () => {
    const keys = collectFieldKeysFromRules([
      {
        type: 'elRow',
        props: { list: [{ field: 'rowField', type: 'input' }] },
      },
      {
        type: 'group',
        rule: [{ field: 'groupField', type: 'input' }],
      },
      { field: 'top', type: 'input' },
    ])
    expect(keys).toEqual(expect.arrayContaining(['rowField', 'groupField', 'top']))
  })
})

describe('applyPreviewOverlayToRules', () => {
  it('sets props.disabled, options, and title from script overlay', () => {
    const out = applyPreviewOverlayToRules(
      [{ field: 'scenario', type: 'select', title: 'Scenario', options: [{ label: 'A', value: 'A' }] }],
      {
        isDisabled: () => true,
        optionsFor: () => [{ label: 'A only', value: 'A' }],
        labelFor: () => 'Case type',
      },
    ) as Array<{ title: string; options: unknown; props: { disabled: boolean } }>
    expect(out[0].props.disabled).toBe(true)
    expect(out[0].options).toEqual([{ label: 'A only', value: 'A' }])
    expect(out[0].title).toBe('Case type')
  })
})
