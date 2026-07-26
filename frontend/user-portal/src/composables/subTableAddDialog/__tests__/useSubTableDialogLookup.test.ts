import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useSubTableDialogLookup } from '../useSubTableDialogLookup'

describe('useSubTableDialogLookup', () => {
  it('hides backfill after clear even if select-cache was stale', () => {
    const formData = ref<Record<string, unknown>>({
      host: { id: 'u1', full_name: 'Alice' },
    })
    const {
      onLookupSelect,
      onLookupClear,
      effectiveLookupSelectedRow,
    } = useSubTableDialogLookup(formData, ref([]))

    onLookupSelect('host', { id: 'u1', full_name: 'Alice', email: 'a@x.com' })
    expect(effectiveLookupSelectedRow('host')).toMatchObject({ id: 'u1' })

    formData.value = { ...formData.value, host: null }
    onLookupClear('host')
    expect(effectiveLookupSelectedRow('host')).toBeNull()
  })

  it('hides backfill when model is cleared without calling onLookupClear', () => {
    const formData = ref<Record<string, unknown>>({
      host: 'user-001',
    })
    const { onLookupSelect, effectiveLookupSelectedRow } = useSubTableDialogLookup(formData, ref([]))

    onLookupSelect('host', { id: 'user-001', full_name: 'Bob' })
    expect(effectiveLookupSelectedRow('host')?.full_name).toBe('Bob')

    formData.value = { ...formData.value, host: '' }
    expect(effectiveLookupSelectedRow('host')).toBeNull()
  })

  it('keeps backfill for scalar PK when select-cache is hydrated', () => {
    const formData = ref<Record<string, unknown>>({ host: 'user-001' })
    const { onLookupSelect, effectiveLookupSelectedRow } = useSubTableDialogLookup(formData, ref([]))
    onLookupSelect('host', { id: 'user-001', full_name: 'Bob' })
    expect(effectiveLookupSelectedRow('host')).toEqual({ id: 'user-001', full_name: 'Bob' })
  })
})
