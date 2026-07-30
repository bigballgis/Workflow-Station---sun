import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { useSubTableDialogComponentEvents } from '../useSubTableDialogComponentEvents'

describe('useSubTableDialogComponentEvents', () => {
  it('runs select blur/change handlers and setValue sibling field', () => {
    const formData = ref<Record<string, unknown>>({ case_type: null, card_number: '' })
    const columns = [
      {
        field: 'case_type',
        label: 'Case Type',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'case_type',
          _on: {
            blur: '$FNX:\nvar v = $inject.value\nif (v === 1 || v === \'1\') { $inject.api.setValue(\'card_number\', \'111\') }',
          },
        },
      },
      { field: 'card_number', label: 'Card Number', type: 'text' },
    ]
    const { onDialogFieldChange } = useSubTableDialogComponentEvents(formData, () => columns)
    onDialogFieldChange('case_type', 1)
    expect(formData.value.case_type).toBe(1)
    expect(formData.value.card_number).toBe('111')
  })

  it('hides sibling field via api.hidden on select change (Add Record dialog)', () => {
    const formData = ref<Record<string, unknown>>({ select: 'Option02', lookup: null })
    const columns = [
      {
        field: 'select',
        label: 'select',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'select',
          _on: {
            change: '$FNX:\napi.hidden(true, "lookup")',
          },
        },
      },
      { field: 'lookup', label: 'lookup', type: 'lookup' },
    ]
    const { onDialogFieldChange, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    expect(isDialogFieldVisible('lookup')).toBe(true)
    onDialogFieldChange('select', 'Option02')
    expect(isDialogFieldVisible('lookup')).toBe(false)
  })

  it('resetDialogEventVisibility restores fields after close/reopen', () => {
    const formData = ref<Record<string, unknown>>({ select: '', lookup: null })
    const columns = [
      {
        field: 'select',
        label: 'select',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'select',
          _on: { change: '$FNX:\napi.hidden(true, "lookup")' },
        },
      },
      { field: 'lookup', label: 'lookup', type: 'lookup' },
    ]
    const { onDialogFieldChange, isDialogFieldVisible, resetDialogEventVisibility } =
      useSubTableDialogComponentEvents(formData, () => columns)
    onDialogFieldChange('select', 'x')
    expect(isDialogFieldVisible('lookup')).toBe(false)
    resetDialogEventVisibility()
    expect(isDialogFieldVisible('lookup')).toBe(true)
  })

  it('no-ops when column has no sourceRule (v-model owns the value)', () => {
    const formData = ref<Record<string, unknown>>({ a: '' })
    const { onDialogFieldChange } = useSubTableDialogComponentEvents(
      formData,
      () => [{ field: 'a', type: 'text' }],
    )
    onDialogFieldChange('a', 'x')
    expect(formData.value.a).toBe('')
  })

  it('bootstrapDialogFormLifecycle runs onCreated then onMounted (not select change)', async () => {
    const formData = ref<Record<string, unknown>>({ select: 'Option02', lookup: null })
    const columns = [
      {
        field: 'select',
        label: 'select',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'select',
          _on: {
            change: '$FNX:\napi.hidden(true, "lookup")',
          },
        },
      },
      { field: 'lookup', label: 'lookup', type: 'lookup' },
    ]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    bootstrapDialogFormLifecycle({
      onCreated: '$FNX:\napi.setValue("select", "from-created")',
      onMounted: '$FNX:\napi.hidden(true, "lookup")',
    })
    expect(formData.value.select).toBe('from-created')
    await nextTick()
    expect(isDialogFieldVisible('lookup')).toBe(false)
  })

  it('bootstrap does not replay component change when select already has a value', () => {
    const formData = ref<Record<string, unknown>>({ select: 'Option02', lookup: null })
    const columns = [
      {
        field: 'select',
        label: 'select',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'select',
          _on: { change: '$FNX:\napi.hidden(true, "lookup")' },
        },
      },
      { field: 'lookup', label: 'lookup', type: 'lookup' },
    ]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    bootstrapDialogFormLifecycle({})
    expect(isDialogFieldVisible('lookup')).toBe(true)
  })
})
