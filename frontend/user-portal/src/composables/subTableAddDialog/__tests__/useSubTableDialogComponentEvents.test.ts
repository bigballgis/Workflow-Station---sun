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

  // ─── Designer "Hide" on the sub-form canvas (ATM Transaction regression) ──────
  // The two date fields were hidden in the designer but rendered on first open:
  // the flag never reached the dialog and nothing seeded visibility on open.

  it('bootstrap seeds visibility from the designer Hide flag', () => {
    const formData = ref<Record<string, unknown>>({})
    const columns = [
      { field: 'merchant_credit', label: 'Merchant Credit', type: 'date', hidden: true },
      { field: 'temporary_refund', label: 'Temporary Refund', type: 'date', hidden: true },
      { field: 'amount', label: 'Amount', type: 'number' },
    ]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    bootstrapDialogFormLifecycle(null)
    expect(isDialogFieldVisible('merchant_credit')).toBe(false)
    expect(isDialogFieldVisible('temporary_refund')).toBe(false)
    expect(isDialogFieldVisible('amount')).toBe(true)
  })

  it('an event script can still reveal a statically hidden field', async () => {
    const formData = ref<Record<string, unknown>>({})
    const columns = [{ field: 'merchant_credit', label: 'Merchant Credit', type: 'date', hidden: true }]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    bootstrapDialogFormLifecycle({ onMounted: '$FNX:\napi.hidden(false, "merchant_credit")' })
    expect(isDialogFieldVisible('merchant_credit')).toBe(false)
    await nextTick()
    expect(isDialogFieldVisible('merchant_credit')).toBe(true)
  })

  it('reset clears the static seed so the next open re-seeds it', () => {
    const formData = ref<Record<string, unknown>>({})
    const columns = [{ field: 'merchant_credit', label: 'Merchant Credit', type: 'date', hidden: true }]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible, resetDialogEventVisibility } =
      useSubTableDialogComponentEvents(formData, () => columns)
    bootstrapDialogFormLifecycle(null)
    expect(isDialogFieldVisible('merchant_credit')).toBe(false)
    resetDialogEventVisibility()
    expect(isDialogFieldVisible('merchant_credit')).toBe(true)
    bootstrapDialogFormLifecycle(null)
    expect(isDialogFieldVisible('merchant_credit')).toBe(false)
  })

  it('bootstrap runs Form-level onChange with field __bootstrap__ (FormRenderer parity)', () => {
    const formData = ref<Record<string, unknown>>({ flag: 'Y', note: null })
    const columns = [
      { field: 'flag', label: 'Flag', type: 'select' },
      { field: 'note', label: 'Note', type: 'text' },
    ]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    // Echo the dispatched field name back through the model — the script sandbox blocks
    // globalThis, so api.setValue is the only observable channel.
    bootstrapDialogFormLifecycle({
      onChange:
        '$FNX:\n$inject.api.setValue("note", String($inject.field))\n'
        + 'if ($inject.field === "__bootstrap__") { $inject.api.hidden(true, "note") }',
    })
    expect(formData.value.note).toBe('__bootstrap__')
    expect(isDialogFieldVisible('note')).toBe(false)
  })

  it('bootstrap runs component hook load then mounted', async () => {
    const formData = ref<Record<string, unknown>>({ a: '', b: '' })
    const columns = [
      {
        field: 'a',
        label: 'A',
        type: 'text',
        sourceRule: {
          type: 'input',
          field: 'a',
          _hook: {
            load: '$FNX:\napi.setValue("a", "loaded")',
            mounted: '$FNX:\napi.hidden(true, "b")',
          },
        },
      },
      { field: 'b', label: 'B', type: 'text' },
    ]
    const { bootstrapDialogFormLifecycle, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    bootstrapDialogFormLifecycle(null)
    expect(formData.value.a).toBe('loaded')
    expect(isDialogFieldVisible('b')).toBe(true)
    await nextTick()
    expect(isDialogFieldVisible('b')).toBe(false)
  })
})
