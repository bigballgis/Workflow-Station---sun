import service from './request'

export interface RecordNoteTargetParams {
  targetType: 'TABLE' | 'RECORD'
  targetId: string
  tableKind?: 'DW' | 'RT'
  tableId: string
  functionUnitId?: number | string | null
}

export interface RecordNoteAttachment {
  id: string
  fileName: string
  mimeType?: string
  fileSize?: number
  isInlineImage?: boolean
}

export interface RecordNoteItem {
  id: string
  noteType: 'COMMENT' | 'ATTACHMENT'
  subject?: string
  bodyText?: string
  fileName?: string
  mimeType?: string
  fileSize?: number
  createdBy: string
  createdByName?: string
  createdAt: string
  updatedAt?: string
  editable?: boolean
  attachments?: RecordNoteAttachment[]
}

export interface RecordNoteDetail {
  id: string
  noteType: string
  subject?: string
  bodyHtml?: string
  createdBy: string
  createdByName?: string
  createdAt: string
  updatedAt?: string
  editable?: boolean
  attachments?: RecordNoteAttachment[]
}

export interface RecordNotePage {
  content: RecordNoteItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

interface ApiEnvelope<T> {
  success?: boolean
  code?: string
  message?: string
  data?: T
}

function targetQuery(target: RecordNoteTargetParams): Record<string, unknown> {
  return {
    targetType: target.targetType,
    targetId: target.targetId,
    tableKind: target.tableKind ?? 'DW',
    tableId: target.tableId,
    ...(target.functionUnitId != null && target.functionUnitId !== ''
      ? { functionUnitId: target.functionUnitId }
      : {}),
  }
}

export async function listRecordNotes(
  target: RecordNoteTargetParams,
  page: number,
  size: number,
): Promise<RecordNotePage | null> {
  const res = (await service.get('/record-notes', {
    params: { ...targetQuery(target), page, size },
  })) as ApiEnvelope<RecordNotePage>
  return res?.data ?? null
}

export async function getRecordNoteDetail(noteId: string): Promise<RecordNoteDetail | null> {
  const res = (await service.get(`/record-notes/${encodeURIComponent(noteId)}`)) as ApiEnvelope<RecordNoteDetail>
  return res?.data ?? null
}

export async function createRecordNote(
  target: RecordNoteTargetParams,
  payload: { subject?: string; bodyHtml?: string; inlineImageIds?: string[]; files?: File[] },
): Promise<RecordNoteItem | null> {
  const form = new FormData()
  Object.entries(targetQuery(target)).forEach(([k, v]) => form.append(k, String(v)))
  if (payload.subject) form.append('subject', payload.subject)
  if (payload.bodyHtml) form.append('bodyHtml', payload.bodyHtml)
  payload.inlineImageIds?.forEach((id) => form.append('inlineImageIds', id))
  payload.files?.forEach((f) => form.append('files', f))
  const res = (await service.post('/record-notes', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })) as ApiEnvelope<RecordNoteItem>
  return res?.data ?? null
}

export async function uploadInlineImage(
  target: RecordNoteTargetParams,
  file: File,
): Promise<RecordNoteItem | null> {
  const form = new FormData()
  Object.entries(targetQuery(target)).forEach(([k, v]) => form.append(k, String(v)))
  form.append('file', file)
  const res = (await service.post('/record-notes/inline-images', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })) as ApiEnvelope<RecordNoteItem>
  return res?.data ?? null
}

export async function updateRecordNote(
  noteId: string,
  payload: { subject?: string; bodyHtml: string },
): Promise<RecordNoteDetail | null> {
  const res = (await service.put(
    `/record-notes/${encodeURIComponent(noteId)}`,
    payload,
  )) as ApiEnvelope<RecordNoteDetail>
  return res?.data ?? null
}

export async function deleteRecordNote(noteId: string): Promise<void> {
  await service.delete(`/record-notes/${encodeURIComponent(noteId)}`)
}

/** Re-anchors New-Request draft notes onto the freshly started process instance. */
export async function adoptRecordNoteDrafts(draftTargetId: string, processInstanceId: string): Promise<number> {
  const form = new FormData()
  form.append('draftTargetId', draftTargetId)
  form.append('processInstanceId', processInstanceId)
  const res = (await service.post('/record-notes/adopt', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })) as ApiEnvelope<number>
  return res?.data ?? 0
}

export function recordNoteContentUrl(noteId: string): string {
  return `/api/portal/record-notes/${encodeURIComponent(noteId)}/content`
}

/** Fetch attachment content as an object URL (inline <img> cannot carry auth headers). */
export async function fetchRecordNoteBlobUrl(noteId: string): Promise<string> {
  const blob = (await service.get(`/record-notes/${encodeURIComponent(noteId)}/content`, {
    responseType: 'blob',
  })) as unknown as Blob
  return URL.createObjectURL(blob)
}

/** Download an attachment through axios (carries auth headers), then save locally. */
export async function downloadRecordNoteAttachment(noteId: string, fileName: string): Promise<void> {
  const blob = (await service.get(`/record-notes/${encodeURIComponent(noteId)}/content`, {
    responseType: 'blob',
  })) as unknown as Blob
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || 'attachment'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
