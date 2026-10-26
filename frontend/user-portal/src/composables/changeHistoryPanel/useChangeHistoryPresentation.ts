import { computed, ref, type Ref } from 'vue'
import type { useI18n } from 'vue-i18n'
import type { ChangeHistoryRecord } from '@/api/processForm'
import type { ChangeBatch } from './useChangeHistoryBatches'
import { HISTORY_VALUE_MAX_LENGTH, type ChangeHistoryFormatting } from './useChangeHistoryFormatting'

/** Search already-authorized groups using exactly the labels and masked values displayed by the table. */
export function useChangeHistoryPresentation(
  batches: Ref<ChangeBatch[]>,
  formatting: ChangeHistoryFormatting,
  t: ReturnType<typeof useI18n>['t'],
) {
  const keyword = ref('')
  const timeOrder = ref<'asc' | 'desc'>('asc')
  function rowText(row: ChangeHistoryRecord): string {
    const values = row.changeType === 'PROCESS_INITIATION' ? ['—', t('changeHistory.processStarted')]
      : [row.oldValue, row.newValue].map(value =>
          formatting.formatDisplayValue(value, HISTORY_VALUE_MAX_LENGTH, row.fieldName))
    return [formatting.getChangeTypeLabel(row.changeType), formatting.fieldLocationLabel(row),
      formatting.formatTimestamp(row.timestamp), ...values].join(' ').toLowerCase()
  }
  const indexedBatches = computed(() => batches.value.map(batch => ({
    batch,
    header: [batch.displayStage, batch.displayOperator, formatting.formatTimestamp(batch.timestamp),
      formatting.formatTimestamp(batch.endTimestamp)].join(' ').toLowerCase(),
    rows: batch.rows.map(row => ({ row, text: rowText(row) })),
  })))
  const totalCount = computed(() => batches.value.reduce((count, batch) => count + batch.rows.length, 0))
  const displayBatches = computed(() => {
    const query = keyword.value.trim().toLowerCase()
    const matching = indexedBatches.value.flatMap(({ batch, header, rows }) => {
      const matches = !query || header.includes(query) ? batch.rows
        : rows.filter(item => item.text.includes(query)).map(item => item.row)
      if (!matches.length) return []
      const ordered = timeOrder.value === 'asc' ? matches
        : [...matches].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))
      return [{ ...batch, rows: ordered }]
    })
    return timeOrder.value === 'asc' ? matching : matching.reverse()
  })
  const matchedCount = computed(() => displayBatches.value.reduce((count, batch) => count + batch.rows.length, 0))
  return { keyword, timeOrder, displayBatches, matchedCount, totalCount }
}
