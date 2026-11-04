import { describe, expect, it, vi } from 'vitest'
import { ref, computed } from 'vue'
import { useFormCreateEvents } from '@/composables/formRenderer/useFormCreateEvents'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
import { FormEventHost } from '@platform-shared/formEvents/host'
import { createPortalFormApi } from '@platform-shared/formEvents/api'

describe('ordinary form accepted-state isolation', () => {
  it('does not emit a partial model on blur while change is still awaiting its script', async () => {
    const data = ref<Record<string, unknown>>({ person: '', email: '' })
    const emit = vi.fn()
    let release!: () => void
    const reply = new Promise<void>(resolve => { release = resolve })
    const fields = [{ key: 'person', label: 'Person', type: 'input' }, { key: 'email', label: 'Email', type: 'input' }]
    const events = useFormCreateEvents({ formData: data, formRef: ref(), allFields: computed(() => fields),
      fieldComponentEvents: computed(() => new Map([['person', { rule: {}, on: { change: (_field: string, _value: unknown, api: ReturnType<typeof createPortalFormApi>) => reply.then(() => api.setValue('email', 'accepted@example.test')) }, hook: {} }]])),
      formCreateRulesResolved: computed(() => []), formOptionsOnChange: () => undefined, fields: () => fields,
      tabs: () => [], fieldsAfterTabs: () => [], readonly: () => false, engineVisibility: ref(new Map()), emitModelValue: emit,
    } as Parameters<typeof useFormCreateEvents>[0])
    const pending = events.runComponentEventsOnFieldChange('person', 'selected')
    events.handleFieldBlur('person')
    expect(emit).not.toHaveBeenCalled()
    release(); await pending; await events.waitForEvents()
    expect(emit).toHaveBeenLastCalledWith({ person: 'selected', email: 'accepted@example.test' })
  })
  it('restores the last accepted source and output when another input cancels an async event', async () => {
    let data: Record<string, unknown> = { person: 'old', email: 'old@example.test', note: '' }
    const host = new FormEventHost({ snapshot: () => structuredClone(data), restore: saved => { data = saved }, notify: () => {} })
    host.acceptCurrent()
    let reply!: () => void
    const deferred = new Promise<void>(resolve => { reply = resolve })
    data.person = 'new'
    const pending = host.run('person', operation => {
      const api = operation.bindApi(createPortalFormApi(() => data, patch => { data = { ...data, ...patch } }))
      api.setValue('email', '')
      return deferred.then(() => api.setValue('email', 'late@example.test'))
    })
    host.cancel()
    data.note = 'keep this input'
    host.run('note', () => undefined)
    reply(); await pending
    expect(data).toEqual({ person: 'old', email: 'old@example.test', note: 'keep this input' })
  })
  it('accepts an external model reset as the next rollback baseline', () => {
    let data = { value: 'first' }
    const host = new FormEventHost({ snapshot: () => ({ ...data }), restore: saved => { data = saved }, notify: () => {} })
    host.acceptCurrent(); host.reset(); data = { value: 'server refresh' }; host.acceptCurrent()
    host.run('value', () => { data.value = 'failed'; throw new Error('failure') })
    expect(data.value).toBe('server refresh')
  })
})
