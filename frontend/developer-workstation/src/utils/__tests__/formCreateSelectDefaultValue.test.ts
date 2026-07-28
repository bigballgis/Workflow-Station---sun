import { describe, expect, it } from 'vitest'
import {
  buildSelectDefaultValuePropRule,
  extractSelectOptionsFromRule,
  syncSelectDefaultOntoRule,
} from '../formCreateSelectDefaultValue'

describe('formCreateSelectDefaultValue', () => {
  it('extractSelectOptionsFromRule reads rule.options', () => {
    const opts = extractSelectOptionsFromRule({
      type: 'select',
      options: [
        { label: 'A', value: 'a' },
        { label: 'B', value: 'b' },
        { label: 'skip', value: '' },
      ],
    })
    expect(opts).toEqual([
      { label: 'A', value: 'a' },
      { label: 'B', value: 'b' },
    ])
  })

  it('extractSelectOptionsFromRule falls back to props.options', () => {
    const opts = extractSelectOptionsFromRule({
      type: 'select',
      props: { options: [{ label: 'One', value: 1 }] },
    })
    expect(opts).toEqual([{ label: 'One', value: 1 }])
  })

  it('syncSelectDefaultOntoRule writes rule.value and props.value', () => {
    const rule: Record<string, unknown> = { type: 'select', props: { multiple: false } }
    syncSelectDefaultOntoRule(rule, 'a')
    expect(rule.value).toBe('a')
    expect((rule.props as Record<string, unknown>).value).toBe('a')
  })

  it('syncSelectDefaultOntoRule clears empty default', () => {
    const rule: Record<string, unknown> = {
      type: 'select',
      value: 'a',
      props: { value: 'a' },
    }
    syncSelectDefaultOntoRule(rule, '')
    expect(rule.value).toBeUndefined()
    expect((rule.props as Record<string, unknown>).value).toBeUndefined()
  })

  it('buildSelectDefaultValuePropRule uses formCreateValue and multiple from props', () => {
    const active: Record<string, unknown> = {
      type: 'select',
      value: 'b',
      options: [
        { label: 'A', value: 'a' },
        { label: 'B', value: 'b' },
      ],
      props: { multiple: true },
    }
    const propRule = buildSelectDefaultValuePropRule(active, {
      title: 'Default Value',
      placeholder: 'Pick',
    })
    expect(propRule.field).toBe('formCreateValue')
    expect(propRule.title).toBe('Default Value')
    expect(propRule.value).toBe('b')
    expect(propRule.options).toHaveLength(2)
    expect((propRule.props as Record<string, unknown>).multiple).toBe(true)
    expect((propRule.props as Record<string, unknown>).disabled).toBe(false)

    const target: Record<string, unknown> = { type: 'select', props: {} }
    const on = propRule.on as { change: (inject: unknown, value: unknown) => void }
    on.change({ api: { activeRule: target } }, 'a')
    expect(target.value).toBe('a')
  })

  it('buildSelectDefaultValuePropRule disables when Options empty', () => {
    const propRule = buildSelectDefaultValuePropRule({ type: 'select' }, {
      title: 'Default Value',
      placeholder: 'Pick',
    })
    expect((propRule.props as Record<string, unknown>).disabled).toBe(true)
  })
})
