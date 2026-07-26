import { ref } from 'vue'
import { functionUnitApi } from '@/api/functionUnit'
import { relationTableBindingApi } from '@/api/relationTable'
import type { AttachmentFieldOption } from './useSendTaskEmailAttachments'
import { buildSendTaskAttachmentFieldOptions } from './sendTaskAttachmentFieldOptions'

/**
 * Loads selectable upload fields for Send Email attachments:
 * MAIN FILE columns, MAIN fields with form Upload widgets, and Lookup-target FILE columns.
 */
export function useSendTaskAttachmentFieldOptions() {
  const fieldOptions = ref<AttachmentFieldOption[]>([])
  const loadingFieldOptions = ref(false)

  async function loadFieldOptions(functionUnitId: number): Promise<void> {
    if (!functionUnitId) {
      fieldOptions.value = []
      return
    }
    loadingFieldOptions.value = true
    try {
      const [tablesRes, formsRes, rtRes] = await Promise.all([
        functionUnitApi.getTables(functionUnitId),
        functionUnitApi.getForms(functionUnitId),
        // FALLBACK(ux): RT catalog unavailable — still offer MAIN upload fields.
        relationTableBindingApi.getAvailableTables().catch(() => ({ data: [] as never[] })),
      ])
      const relationTables = (rtRes as { data?: unknown })?.data
        || (Array.isArray(rtRes) ? rtRes : [])
      fieldOptions.value = buildSendTaskAttachmentFieldOptions(
        tablesRes.data || [],
        formsRes.data || [],
        relationTables as never[],
      )
    } catch {
      // FALLBACK(ux): attachment picker empty when catalog APIs fail; send still works without attachments.
      fieldOptions.value = []
    } finally {
      loadingFieldOptions.value = false
    }
  }

  return {
    fieldOptions,
    loadingFieldOptions,
    loadFieldOptions,
  }
}
