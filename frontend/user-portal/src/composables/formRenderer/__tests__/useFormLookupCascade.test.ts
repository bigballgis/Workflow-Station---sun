import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import type { FormField } from '@/components/formRendererHelpers'
import {
  createLookupCascadeHandlers,
  processLookupCascadeSelect,
} from '../useFormLookupCascade'

vi.mock('@/components/lookup/useLookupBehaviors', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/components/lookup/useLookupBehaviors')>()
  return {
    ...actual,
    resolveDerivedLookup: vi.fn(),
  }
})

import { resolveDerivedLookup } from '@/components/lookup/useLookupBehaviors'

const resolveMock = vi.mocked(resolveDerivedLookup)

function lookupField(key: string, parent?: string): FormField {
  return {
    key,
    label: key,
    type: 'lookup',
    _lookupTableId: 45,
    _lookupSearchFields: ['id'],
    _lookupDisplayFields: ['name'],
    _lookupSelectedDisplayField: 'name',
    _lookupFilterConditions: [],
    ...(parent
      ? {
          _lookupDerivedFrom: {
            parentField: parent,
            derivedMode: 'autofill' as const,
            joins: [{ fromColumn: 'id', toColumn: 'stage_id', matchType: 'eq' as const }],
          },
        }
      : {}),
  } as FormField
}

describe('processLookupCascadeSelect', () => {
  beforeEach(() => {
    resolveMock.mockReset()
  })

  it('autofills dependent LOOKUP with full row object so the tag can render', async () => {
    const stage = lookupField('stage')
    const testStatus = lookupField('Test_status', 'stage')
    const formData = ref<Record<string, unknown>>({ stage: null, Test_status: null })
    const lookupSelectedData = ref<Record<string, Record<string, unknown>>>({})
    const changes: Array<[string, unknown]> = []

    const filled = { id: 'st-1', name: 'In Progress', stage_id: 'sg-1' }
    resolveMock.mockResolvedValue({
      value: 'st-1',
      row: filled,
    })

    await processLookupCascadeSelect(
      'stage',
      { id: 'sg-1', name: 'Stage A' },
      [stage, testStatus],
      formData,
      lookupSelectedData,
      (key, value) => {
        changes.push([key, value])
        formData.value[key] = value
      },
    )

    expect(formData.value.Test_status).toEqual(filled)
    expect(lookupSelectedData.value.Test_status).toEqual(filled)
    expect(changes.some(([k, v]) => k === 'Test_status' && (v as { name?: string })?.name === 'In Progress')).toBe(true)
  })

  it('autofills multiple LOOKUP with all matched row objects for display tags', async () => {
    const stage = lookupField('stage')
    const testStatus = {
      ...lookupField('test_status', 'stage'),
      _lookupMultiple: true,
      _lookupSelectedDisplayField: 'status_name',
      _lookupDisplayFields: ['status_name'],
      _lookupSearchFields: ['status_id'],
    } as FormField
    const formData = ref<Record<string, unknown>>({ stage: null, test_status: null })
    const lookupSelectedData = ref<Record<string, Record<string, unknown>>>({})

    const rows = [
      { status_id: '4', status_code: 'A', status_name: 'A llll' },
      { status_id: '5', status_code: 'A', status_name: 'A d dddd' },
    ]
    resolveMock.mockResolvedValue({
      value: ['4', '5'],
      rows,
    })

    await processLookupCascadeSelect(
      'stage',
      { id: 'CAST-1', code: 'A' },
      [stage, testStatus],
      formData,
      lookupSelectedData,
      (key, value) => {
        formData.value[key] = value
      },
    )

    expect(formData.value.test_status).toEqual(rows)
    expect(lookupSelectedData.value.test_status).toEqual(rows[0])
  })
})

describe('createLookupCascadeHandlers.handleLookupClear', () => {
  it('clears multi LOOKUP to [] (not null) and clears autofill dependents', () => {
    const stage = lookupField('stage')
    const testStatus = {
      ...lookupField('test_status', 'stage'),
      _lookupMultiple: true,
    } as FormField
    const formData = ref<Record<string, unknown>>({
      stage: { id: 'sg-1' },
      test_status: [{ status_id: '4' }, { status_id: '5' }],
    })
    const lookupSelectedData = ref<Record<string, Record<string, unknown>>>({
      stage: { id: 'sg-1' },
      test_status: { status_id: '4' },
    })
    const handlers = createLookupCascadeHandlers({
      allFields: () => [stage, testStatus],
      formData,
      lookupSelectedData,
      onFieldChange: (key, value) => {
        formData.value[key] = value
      },
    })

    handlers.handleLookupClear('test_status')

    expect(formData.value.test_status).toEqual([])
    expect(lookupSelectedData.value.test_status).toBeUndefined()
  })

  it('clears single LOOKUP to null', () => {
    const stage = lookupField('stage')
    const formData = ref<Record<string, unknown>>({ stage: { id: 'sg-1' } })
    const lookupSelectedData = ref<Record<string, Record<string, unknown>>>({
      stage: { id: 'sg-1' },
    })
    const handlers = createLookupCascadeHandlers({
      allFields: () => [stage],
      formData,
      lookupSelectedData,
      onFieldChange: (key, value) => {
        formData.value[key] = value
      },
    })

    handlers.handleLookupClear('stage')

    expect(formData.value.stage).toBeNull()
  })
})
