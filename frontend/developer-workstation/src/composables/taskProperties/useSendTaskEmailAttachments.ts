import { ref } from 'vue'

/** BPMN emailAttachments item — upload field reference only (no free-form Base64). */
export interface EmailAttachmentRef {
  source: 'main' | 'lookup' | 'sub'
  /** MAIN / SUB FILE field name */
  fieldName?: string
  /** Form lookup widget field key when source=lookup */
  lookupField?: string
  /** FILE field on lookup target Relation Table when source=lookup */
  targetField?: string
  /** Optional snapshot of lookupConfig.tableId */
  tableId?: number
  /** PROCESS-preferred FormTableBinding id when source=sub */
  bindingId?: number
}

export interface AttachmentFieldOption {
  value: string
  label: string
  group: string
  ref: EmailAttachmentRef
}

export function attachmentOptionValue(ref: EmailAttachmentRef): string {
  if (ref.source === 'main' && ref.fieldName) {
    return `main:${ref.fieldName}`
  }
  if (ref.source === 'sub' && ref.bindingId != null && ref.fieldName) {
    return `sub:${ref.bindingId}:${ref.fieldName}`
  }
  if (ref.source === 'lookup' && ref.lookupField && ref.targetField) {
    return `lookup:${ref.lookupField}@${ref.targetField}`
  }
  return ''
}

export function parseAttachments(raw: unknown): EmailAttachmentRef[] {
  // getExtensionProperties/parsePropertyValue may already JSON.parse array values.
  let parsed: unknown = raw
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed) {
      return []
    }
    try {
      parsed = JSON.parse(trimmed)
    } catch {
      return []
    }
  }
  if (!Array.isArray(parsed)) {
    return []
  }
  return parsed
    .map(normalizeAttachmentItem)
    .filter((item): item is EmailAttachmentRef => item != null)
}

function normalizeAttachmentItem(item: unknown): EmailAttachmentRef | null {
  if (item == null || typeof item !== 'object') {
    return null
  }
  const obj = item as Record<string, unknown>
  const source = obj.source
  if (source === 'main') {
    const fieldName = typeof obj.fieldName === 'string' ? obj.fieldName.trim() : ''
    if (!fieldName) return null
    return { source: 'main', fieldName }
  }
  if (source === 'sub') {
    const fieldName = typeof obj.fieldName === 'string' ? obj.fieldName.trim() : ''
    const bindingId = Number(obj.bindingId)
    if (!fieldName || !Number.isFinite(bindingId)) return null
    return { source: 'sub', fieldName, bindingId }
  }
  if (source === 'lookup') {
    const lookupField = typeof obj.lookupField === 'string' ? obj.lookupField.trim() : ''
    const targetField = typeof obj.targetField === 'string' ? obj.targetField.trim() : ''
    if (!lookupField || !targetField) return null
    const tableId = Number(obj.tableId)
    return {
      source: 'lookup',
      lookupField,
      targetField,
      ...(Number.isFinite(tableId) ? { tableId } : {})
    }
  }
  // Legacy { name, content } free-form — dropped (feature replaced).
  return null
}

export function useSendTaskEmailAttachments(
  updateExtProp: (name: string, value: unknown) => void
) {
  const emailAttachments = ref<EmailAttachmentRef[]>([])

  function loadFromExtension(raw: unknown) {
    emailAttachments.value = parseAttachments(raw)
  }

  function persistAttachments() {
    const items = emailAttachments.value.filter(isCompleteRef)
    updateExtProp('emailAttachments', items.length > 0 ? JSON.stringify(items) : '')
  }

  function addAttachment() {
    emailAttachments.value.push({ source: 'main', fieldName: '' })
  }

  function removeAttachment(index: number) {
    emailAttachments.value.splice(index, 1)
    persistAttachments()
  }

  function setAttachmentFromOption(index: number, optionValue: string, options: AttachmentFieldOption[]) {
    const opt = options.find(o => o.value === optionValue)
    if (!opt) {
      emailAttachments.value[index] = { source: 'main', fieldName: '' }
    } else {
      emailAttachments.value[index] = { ...opt.ref }
    }
    persistAttachments()
  }

  function selectedOptionValue(att: EmailAttachmentRef): string {
    return attachmentOptionValue(att)
  }

  return {
    emailAttachments,
    loadFromExtension,
    addAttachment,
    removeAttachment,
    setAttachmentFromOption,
    selectedOptionValue,
    persistAttachments
  }
}

function isCompleteRef(ref: EmailAttachmentRef): boolean {
  if (ref.source === 'main') {
    return !!ref.fieldName?.trim()
  }
  if (ref.source === 'sub') {
    return ref.bindingId != null && !!ref.fieldName?.trim()
  }
  return !!ref.lookupField?.trim() && !!ref.targetField?.trim()
}
