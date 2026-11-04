import { describe, expect, it } from 'vitest'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { supportedBatchFieldTypes } from '@platform-shared/batch/fields'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { FORM_LEVEL_EVENT_DEFS, COMPONENT_HOOK_NAMES, COMMON_COMPONENT_ON_EVENTS, TYPE_ON_EVENTS } from '../../../../developer-workstation/src/utils/formCreateDefaultEvents/constants'

const runner = createBatchRowEventRunner(parseFormCreateEventHandler)
const basic = [{ type: 'input', field: 'source', title: 'Source' }, { type: 'input', field: 'target', title: 'Target', options: [{ label: 'Original', value: 'original' }] }]
const script = '$FNX:api.setValue("target", "event:" + field);'

describe('Batch supported event matrix', () => {
  it.each(FORM_LEVEL_EVENT_DEFS.map(def => def.name))('executes form %s in its own row', event => {
    const session = runner.createSession({ source: 'a', target: '' }, basic, () => false, { [event]: script })
    if (event === 'onChange') session.change('source', 'changed')
    else if (event === 'onReset') session.reset()
    else if (event === 'onReload') session.reload()
    else if (event === 'beforeSubmit' || event === 'onSubmit') session.submit()
    else if (event === 'beforeFetch') session.beforeFetch({}, {})
    else session.bootstrap()
    expect(session.data.target).toMatch(/^event:/)
  })
  it.each(COMPONENT_HOOK_NAMES)('executes hook %s with primary and legacy keys', event => {
    for (const kind of ['hook', '_hook']) {
      const rule = { ...basic[0], [kind]: { [event]: script } }
      const session = runner.createSession({ source: '', target: '' }, [rule, basic[1]!], () => false)
      if (event === 'load' || event === 'mounted') session.bootstrap()
      else if (event === 'value') session.change('source', 'changed')
      else session.ruleEvent(rule, event, ['native-argument'], 'hook')
      expect(session.data.target).toBe('event:source')
    }
  })
  const cases = [...supportedBatchFieldTypes].flatMap(type => [...new Set([...COMMON_COMPONENT_ON_EVENTS, ...(TYPE_ON_EVENTS[type] ?? [])])].map(event => ({ type, event })))
  it.each(cases)('dispatches $type.$event script with args', ({ type, event }) => {
    for (const kind of ['on', '_on']) {
      const rule = { type, field: 'source', [kind]: { [event]: '$FNX:api.setValue("target", args[0].marker)' } }
      const session = runner.createSession({ source: '', target: '' }, [rule, basic[1]!], () => false)
      session.event('source', event, [{ marker: type + '.' + event }])
      expect(session.data.target).toBe(type + '.' + event)
    }
  })
  it('runs initialization once and preserves bootstrap/load/mounted ordering', () => {
    const append = (value: string) => '$FNX:api.setValue("target", api.getValue("target") + "' + value + '")'
    const session = runner.createSession({ target: '' }, [{ ...basic[0], hook: { load: append('L'), mounted: append('M') } }, basic[1]!], () => false,
      { onCreated: append('C'), onMounted: append('F') })
    session.bootstrap(); session.bootstrap()
    expect(session.data.target).toBe('CLMF')
  })
  it('propagates chained changes once, with row-local original values', () => {
    const rules = [{ ...basic[0], on: { change: '$FNX:api.setValue("target", api.getValue("target") + 1)' } }, basic[1]!]
    const a = runner.createSession({ target: 1 }, rules, () => false)
    const b = runner.createSession({ target: 8 }, rules, () => false)
    a.change('source', 0); b.change('source', 0); a.change('source', 0)
    expect(a.data.target).toBe(2); expect(b.data.target).toBe(9)
  })
  it('stops cyclic backfills and rejects dangerous or invalid scripts', () => {
    const cyclic = runner.createSession({ source: 0 }, [{ ...basic[0], on: { change: '$FNX:api.setValue("source", value + 1)' } }], () => false)
    expect(() => cyclic.change('source', 1)).toThrow('batchEventInvalid')
    for (const body of ['$FNX:window.location = "x"', '$FNX:const =']) {
      const session = runner.createSession({}, basic, () => false, { onCreated: body })
      expect(() => session.bootstrap()).toThrow('batchEventInvalid')
    }
  })
  it('supports script arrays, stopping on false before later handlers', () => {
    const session = runner.createSession({ target: '' }, basic, () => false, { beforeSubmit: [script, '$FNX:return false', '$FNX:api.setValue("target", "bad")'] })
    expect(() => session.submit()).toThrow('batchEventSubmitRejected')
    expect(session.data.target).toBe('event:__submit__')
  })
  it('honors error and dynamic required guards before and after onSubmit', () => {
    const field = { field: 'target', label: 'Target', type: 'input', required: true, options: [] }
    const session = runner.createSession({ target: '' }, basic, () => false)
    expect(() => session.submit([field])).toThrow('batchEventValidation')
    session.api.required(false, 'target'); expect(() => session.submit([field])).not.toThrow()
    session.api.setFieldError('target', 'Check'); expect(() => session.submit()).toThrow('batchEventValidation')
    session.api.clearFieldError('target'); expect(() => session.submit()).not.toThrow()
    const badSubmit = runner.createSession({ target: 'ok' }, basic, () => false, { onSubmit: '$FNX:api.setFieldError("target", "Rejected")' })
    expect(() => badSubmit.submit([field])).toThrow('batchEventValidation')
  })
  it('provides the whole ordinary overlay API and restores an isolated snapshot', () => {
    const session = runner.createSession({ source: 'a', target: 'b', undeclared: 'keep' }, basic, () => false)
    const saved = session.snapshot(); const api = session.api
    api.setValue({ Source: 'changed', target: 0 }); expect(api.getValue('Source')).toBe('changed'); expect(api.form.target).toBe(0)
    api.hidden(true, 'Target'); expect(api.hiddenStatus('target')).toBe(true); api.hidden(false, 'target'); expect(api.hiddenStatus('target')).toBe(false)
    api.display(true, 'target'); expect(api.displayStatus('target')).toBe(false); api.display(false, 'target'); expect(api.displayStatus('target')).toBe(true)
    api.disabled(true, ['source', 'target']); expect(api.disabledStatus('source')).toBe(true); api.disabled(false, 'source'); expect(api.disabledStatus('source')).toBe(false)
    api.required(true, 'target'); expect(api.requiredStatus('target')).toBe(true)
    api.setLabel('target', 'Changed'); expect(api.getLabel('target')).toBe('Changed'); api.resetLabel('target'); expect(api.getLabel('target')).toBe('Target')
    api.setOptions('target', [{ label: 'One', value: 1 }]); api.addOption('target', { label: 'Two', value: 2 }); api.removeOption('target', 1)
    expect(session.choices.get('target')).toEqual([{ label: 'Two', value: 2 }]); api.clearOptions('target'); expect(session.choices.get('target')).toEqual([])
    api.resetOptions('target'); expect(session.choices.has('target')).toBe(false)
    api.setFormNotification('Test', 'WARNING', 'notice'); expect(session.notifications.size).toBe(1); api.clearFormNotification('notice'); expect(session.notifications.size).toBe(0)
    api.setLookupFilter('source', [{ fieldName: 'id', value: 'synthetic', matchType: 'eq' }]); expect(session.lookupFilters.size).toBe(1)
    api.clearLookupFilter('source'); expect(session.lookupFilters.size).toBe(0); api.refresh('source'); expect(session.refreshes.get('source')).toBe(1)
    api.setFocus('target'); expect(session.focusField).toBe('target')
    session.restore(saved); expect(session.data).toEqual({ source: 'a', target: 'b', undeclared: 'keep' }); expect(session.disabled.size).toBe(0); expect(session.focusField).toBe('')
    const snapshot = api.form; snapshot.target = 'outside'; expect(api.getValue('target')).toBe('b')
  })
})
