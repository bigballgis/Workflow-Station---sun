import type { ExtractionFieldRule } from '@/api/emailMonitor'
import type { SubTableFieldOption } from './useProcessFormSubBindings'

export const ATTRIBUTE_SOURCES = [
  'SUBJECT', 'FROM', 'TO', 'CC', 'REPLY_TO', 'DATE', 'MESSAGE_ID',
] as const

export const BODY_SOURCES = ['TEXT_AND_HTML', 'TEXT', 'HTML', 'HTML_RICH', 'HEADER', 'CONST'] as const

export const ATTACHMENT_SOURCES = ['ATTACHMENTS'] as const

export const RAW_EML_SOURCES = ['RAW_EML'] as const

export const LOCKED_ATTRIBUTE_SOURCES = ['FROM', 'TO', 'CC', 'REPLY_TO', 'DATE', 'MESSAGE_ID'] as const

/** Whole HTML body kept as sanitized rich text for an editor field (DIRECT only). */
export function isRichBodySource(source?: string): boolean {
  return source === 'HTML_RICH'
}

const BODY_TYPES = ['LABEL', 'BETWEEN', 'REGEX', 'CONST', 'HEADER'] as const
const SUBJECT_TYPES = ['DIRECT', 'LABEL', 'BETWEEN', 'REGEX'] as const

export function isAttachmentsSource(source?: string): boolean {
  return source === 'ATTACHMENTS'
}

export function isRawEmlSource(source?: string): boolean {
  return source === 'RAW_EML'
}

export function isFileStoreSource(source?: string): boolean {
  return isAttachmentsSource(source) || isRawEmlSource(source)
}

export function isFileField(option: SubTableFieldOption): boolean {
  return String(option.dataType || '').toUpperCase() === 'FILE'
}

export function isLockedAttributeSource(source?: string): boolean {
  return LOCKED_ATTRIBUTE_SOURCES.includes(source as typeof LOCKED_ATTRIBUTE_SOURCES[number])
    || isFileStoreSource(source)
    || isRichBodySource(source)
}

export function typesForSource(source?: string): string[] {
  if (isFileStoreSource(source) || isRichBodySource(source)) {
    return ['DIRECT']
  }
  if (source === 'SUBJECT') {
    return [...SUBJECT_TYPES]
  }
  return [...BODY_TYPES]
}

export function onSourceChange(row: ExtractionFieldRule): void {
  if (isLockedAttributeSource(row.source)) {
    row.type = 'DIRECT'
    return
  }
  if (row.source === 'SUBJECT' && !SUBJECT_TYPES.includes(row.type as typeof SUBJECT_TYPES[number])) {
    row.type = 'DIRECT'
    return
  }
  if (row.type === 'DIRECT') {
    row.type = 'LABEL'
  }
}

export function targetOptionsForRow(
  row: ExtractionFieldRule,
  mainFieldOptions: SubTableFieldOption[],
): SubTableFieldOption[] {
  const base = isFileStoreSource(row.source)
    ? mainFieldOptions.filter(isFileField)
    : mainFieldOptions
  const trimmed = row.target?.trim()
  if (trimmed && !base.some((f) => f.fieldName === trimmed)) {
    return [{ fieldName: trimmed, displayName: trimmed }, ...base]
  }
  return base
}

export function invalidAttachmentTargets(
  fields: ExtractionFieldRule[] | undefined,
  mainFieldOptions: SubTableFieldOption[],
): string[] {
  if (!fields?.length) {
    return []
  }
  const fileNames = new Set(
    mainFieldOptions.filter(isFileField).map((f) => f.fieldName),
  )
  const invalid: string[] = []
  for (const row of fields) {
    if (!isFileStoreSource(row.source)) {
      continue
    }
    const target = row.target?.trim()
    if (target && !fileNames.has(target)) {
      invalid.push(target)
    }
  }
  return invalid
}

export function previewAttachmentNames(sampleNames?: string): string {
  const names = (sampleNames ?? '')
    .split(/[,;\n]+/)
    .map((n) => n.trim())
    .filter(Boolean)
  return names.length ? names.join(', ') : '—'
}

export function previewRawEmlFilename(subject?: string): string {
  const base = (subject ?? '').trim() || 'message'
  const cleaned = base.replace(/[\\/:*?"<>|\x00-\x1F]/g, '_').slice(0, 80).trim()
  return `${cleaned || 'message'}.eml`
}

/** BETWEEN / REGEX need more than one short value — edit in a popover, not the table cell. */
export function usesRulePopover(type?: string): boolean {
  return type === 'BETWEEN' || type === 'REGEX'
}

export function summarizeMappingRule(
  row: Pick<ExtractionFieldRule, 'type' | 'before' | 'after' | 'pattern'>,
  emptyLabel: string,
): string {
  if (row.type === 'BETWEEN') {
    const before = row.before?.trim() ?? ''
    const after = row.after?.trim() ?? ''
    if (!before && !after) {
      return emptyLabel
    }
    return `${before || '…'} … ${after || '…'}`
  }
  if (row.type === 'REGEX') {
    const pattern = row.pattern?.trim() ?? ''
    return pattern || emptyLabel
  }
  return emptyLabel
}
