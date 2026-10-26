import { ref } from 'vue'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import type { ChangeHistoryRecord } from '@/api/processForm'
import { useChangeHistoryBatches } from '../useChangeHistoryBatches'
import { useChangeHistoryFormatting } from '../useChangeHistoryFormatting'
import { useChangeHistoryPresentation } from '../useChangeHistoryPresentation'

const t = ((key: string) => key) as never
function row(id: number, seconds: number, extra: Partial<ChangeHistoryRecord> = {}): ChangeHistoryRecord {
  return { id, processInstanceId: 'process', taskInstanceId: 'task', stageId: 'stage', stageName: 'Review',
    userId: 'editor', userName: 'Editor', timestamp: dayjs('2026-10-23T10:00:00Z').add(seconds, 'second').toISOString(),
    fieldName: 'caption', fieldLabel: `Field ${id}`, oldValue: 'Before', newValue: 'After',
    changeType: 'FIELD_UPDATE', subTableName: null, rowIdentifier: null, concurrent: false, ...extra }
}
function setup(rows: ChangeHistoryRecord[], formatting = useChangeHistoryFormatting(t, dayjs)) {
  const records = ref(rows)
  const { groupedBatches } = useChangeHistoryBatches(records, formatting, dayjs)
  return { records, groupedBatches, ...useChangeHistoryPresentation(groupedBatches, formatting, t) }
}

describe('history search and time order', () => {
  it('defaults to ascending, reverses groups and detail times, and preserves equal-time field order', () => {
    const state = setup([row(2, 0), row(1, 0), row(3, 10), row(4, 400)])
    const original = state.groupedBatches.value.map(batch => [...batch.rows])
    expect(state.timeOrder.value).toBe('asc')
    expect(state.displayBatches.value.map(batch => batch.rows.map(item => item.id))).toEqual([[2, 1, 3], [4]])
    state.timeOrder.value = 'desc'
    expect(state.displayBatches.value.map(batch => batch.rows.map(item => item.id))).toEqual([[4], [3, 2, 1]])
    expect(state.groupedBatches.value.map(batch => batch.rows)).toEqual(original)
  })
  it('finds stage and operator headers, or only the matching field/value rows, ignoring case and whitespace', () => {
    const state = setup([row(1, 0, { newValue: 'EUR' }), row(2, 1, { newValue: 'USD' })])
    state.keyword.value = '  eur  '
    expect(state.displayBatches.value[0]!.rows.map(item => item.id)).toEqual([1])
    expect(state.matchedCount.value).toBe(1)
    state.keyword.value = 'field 2'
    expect(state.displayBatches.value[0]!.rows.map(item => item.id)).toEqual([2])
    for (const keyword of ['REVIEW', 'editor', '2026-10-23', 'changeHistory.fieldUpdate']) {
      state.keyword.value = keyword
      expect(state.matchedCount.value).toBe(2)
    }
    state.keyword.value = 'no such history'
    expect(state.displayBatches.value).toEqual([])
    state.keyword.value = ''
    expect(state.matchedCount.value).toBe(2)
  })
  it('never re-groups across an independent event hidden by the search', () => {
    const state = setup([row(1, 0), row(2, 1, { changeType: 'CLAIM' }), row(3, 2)])
    const keys = state.groupedBatches.value.map(batch => batch.key)
    state.keyword.value = 'changeHistory.fieldUpdate'
    expect(state.displayBatches.value.map(batch => batch.key)).toEqual([keys[0], keys[2]])
  })
  it('searches masked display values and lookup labels without exposing hidden raw values', () => {
    const mask = new Map([['card', { enabled: true, preset: 'last4' as const, keepPrefix: 0,
      keepSuffix: 4, maskChar: '*', revealPlainOnFocus: false }]])
    const formatting = useChangeHistoryFormatting(t, dayjs, () => mask)
    const state = setup([row(1, 0, { fieldName: 'card', newValue: '6222021234567890' }),
      row(2, 1, { newValue: JSON.stringify({ dropdown_name: 'Currency label', internalSecret: 'hidden-lookup-payload' }) })], formatting)
    for (const hidden of ['622202', 'hidden-lookup-payload']) {
      state.keyword.value = hidden
      expect(state.matchedCount.value).toBe(0)
    }
    state.keyword.value = '7890'
    expect(state.matchedCount.value).toBe(1)
    state.keyword.value = 'currency label'
    expect(state.displayBatches.value[0]!.rows.map(item => item.id)).toEqual([2])
  })
  it('searches process initiation as displayed and hides its unused raw values', () => {
    const state = setup([row(1, 0, { changeType: 'PROCESS_INITIATION', newValue: 'hidden-init-payload' })])
    state.keyword.value = 'hidden-init-payload'
    expect(state.matchedCount.value).toBe(0)
    state.keyword.value = 'changeHistory.processStarted'
    expect(state.matchedCount.value).toBe(1)
  })
  it('reacts to history refresh without changing the current search or order', () => {
    const state = setup([row(1, 0, { newValue: 'EUR' })])
    state.keyword.value = 'eur'
    state.timeOrder.value = 'desc'
    state.records.value = [...state.records.value, row(2, 400, { newValue: 'EUR' })]
    expect(state.displayBatches.value.map(batch => batch.rows[0]!.id)).toEqual([2, 1])
  })
})
