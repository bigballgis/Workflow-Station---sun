import { computed, type Ref } from 'vue'
import type {
  AttachmentFieldOption,
  EmailAttachmentRef,
} from '@/composables/taskProperties/useSendTaskEmailAttachments'

/**
 * Grouped el-select options for email attachment FILE fields, including saved refs that
 * are not yet present in a stale catalog reload.
 */
export function useEmailAttachmentOptionGroups(
  fieldOptions: Ref<AttachmentFieldOption[]>,
  emailAttachments: Ref<EmailAttachmentRef[]>,
  selectedOptionValue: (att: EmailAttachmentRef) => string,
  orphanGroupLabel: () => string,
) {
  return computed(() => {
    const byGroup = new Map<string, AttachmentFieldOption[]>()
    for (const opt of fieldOptions.value) {
      const list = byGroup.get(opt.group) || []
      list.push(opt)
      byGroup.set(opt.group, list)
    }
    const known = new Set(fieldOptions.value.map((o) => o.value))
    const orphanLabel = orphanGroupLabel()
    for (const att of emailAttachments.value) {
      const value = selectedOptionValue(att)
      if (!value || known.has(value)) {
        continue
      }
      const label = value.startsWith('lookup:')
        ? value.slice('lookup:'.length)
        : (att.fieldName || value)
      const list = byGroup.get(orphanLabel) || []
      list.push({ value, label, group: orphanLabel, ref: { ...att } })
      byGroup.set(orphanLabel, list)
      known.add(value)
    }
    return Array.from(byGroup.entries()).map(([label, options]) => ({ label, options }))
  })
}
