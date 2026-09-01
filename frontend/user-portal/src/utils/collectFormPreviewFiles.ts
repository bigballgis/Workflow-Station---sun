/**
 * Walk a parsed form tree (main + nested sub-tables) and collect stored upload URLs
 * for in-dialog previous/next and auto-open preview.
 */

import {
  flattenAllFormFieldSegments,
  collectSubTableFieldsFromLayout,
  type FormField,
  type FormTab,
} from '@/components/formRendererHelpers'
import { isUploadColumn } from '@/components/subTableAddDialogHelpers/fileColumns'
import { pullNestedRowsForBindingFromParentRows } from '@/composables/tasks/subTableNestedRows'
import { fileExtension, isBlockedPreviewExtension } from '@/utils/filePreviewKinds'
import { isCannotDownload, uploadPropsBlockDownload } from '@/utils/filePreviewFlags'
import type { FilePreviewItem } from '@/composables/filePreview/useFilePreview'

export interface PreviewBindingSlice {
  bindingId: number
  tableName?: string
  physicalTableName?: string
  tableId?: number | null
  columns?: Array<{ field: string; type?: string; props?: Record<string, unknown> }>
  dialogColumns?: Array<{ field: string; type?: string; props?: Record<string, unknown> }>
  data?: unknown[]
  formFields?: FormField[]
}

export interface CollectFormPreviewInput {
  fields?: FormField[]
  tabs?: FormTab[]
  fieldsAfterTabs?: FormField[]
  formData: Record<string, unknown>
  bindings?: PreviewBindingSlice[]
}

export function isPreviewableFileName(name: string): boolean {
  return !isBlockedPreviewExtension(fileExtension(name))
}

export function previewFileNameFromUrl(url: string, savedName?: string): string {
  if (savedName && savedName.trim()) return savedName.trim()
  if (!url) return 'file'
  try {
    const parsed = new URL(url, 'https://preview.local')
    const fromQuery = parsed.searchParams.get('originalName')
      || parsed.searchParams.get('fileName')
      || parsed.searchParams.get('filename')
      || parsed.searchParams.get('name')
    if (fromQuery) return decodeURIComponent(fromQuery)
    const pathPart = parsed.pathname.split('/').pop() || url
    return decodeURIComponent(pathPart)
  } catch {
    const [pathPart] = String(url).split('?')
    return decodeURIComponent(pathPart.split('/').pop() || url)
  }
}

function uniqueBindings(bindings: PreviewBindingSlice[]): PreviewBindingSlice[] {
  const seen = new Set<number>()
  const out: PreviewBindingSlice[] = []
  for (const b of bindings) {
    const id = Number(b.bindingId)
    if (!Number.isFinite(id) || seen.has(id)) continue
    seen.add(id)
    out.push(b)
  }
  return out
}

function findBinding(bindings: PreviewBindingSlice[], id?: number): PreviewBindingSlice | undefined {
  if (id == null || !Number.isFinite(Number(id))) return undefined
  const n = Number(id)
  return bindings.find((b) => Number(b.bindingId) === n)
}

function storedUrl(value: unknown): string {
  if (typeof value !== 'string') return ''
  return value.trim()
}

function pushItem(
  out: FilePreviewItem[],
  seen: Set<string>,
  url: string,
  name: string,
  cannotDownload: boolean,
) {
  if (!url || seen.has(url) || !isPreviewableFileName(name)) return
  seen.add(url)
  out.push({ url, name, cannotDownload })
}

function uploadColumnsOf(binding: PreviewBindingSlice) {
  const cols = binding.dialogColumns?.length ? binding.dialogColumns : (binding.columns || [])
  return cols.filter((col) => isUploadColumn(col))
}

function fileNameForColumn(
  row: Record<string, unknown>,
  col: { field: string; props?: Record<string, unknown> },
  url: string,
): string {
  const target = col.props?.fileNameTargetField
  const saved = typeof target === 'string' ? row[target] : undefined
  return previewFileNameFromUrl(url, typeof saved === 'string' ? saved : undefined)
}

function rowsForBinding(binding: PreviewBindingSlice, formData: Record<string, unknown>): unknown[] {
  if (Array.isArray(binding.data) && binding.data.length > 0) return binding.data
  const st = formData.__subTables__
  if (st && typeof st === 'object' && !Array.isArray(st)) {
    const map = st as Record<string, unknown>
    const hit = map[binding.bindingId] ?? map[String(binding.bindingId)]
    if (Array.isArray(hit)) return hit
  }
  return Array.isArray(binding.data) ? binding.data : []
}

function pushUploadFields(
  fields: FormField[],
  formData: Record<string, unknown>,
  out: FilePreviewItem[],
  seen: Set<string>,
) {
  for (const field of fields) {
    if (field.hidden === true || field.type !== 'upload') continue
    const url = storedUrl(formData[field.key])
    const target = (field as { fileNameTargetField?: string }).fileNameTargetField
    const saved = target ? formData[target] : undefined
    const name = previewFileNameFromUrl(url, typeof saved === 'string' ? saved : undefined)
    pushItem(out, seen, url, name, isCannotDownload(field.cannotDownload))
  }
}

function pushRowUploads(
  binding: PreviewBindingSlice,
  rows: unknown[],
  out: FilePreviewItem[],
  seen: Set<string>,
) {
  const cols = uploadColumnsOf(binding)
  for (const raw of rows) {
    if (!raw || typeof raw !== 'object') continue
    const row = raw as Record<string, unknown>
    for (const col of cols) {
      const url = storedUrl(row[col.field])
      pushItem(out, seen, url, fileNameForColumn(row, col, url), uploadPropsBlockDownload(col.props))
    }
  }
}

function walkBinding(
  binding: PreviewBindingSlice,
  rows: unknown[],
  bindings: PreviewBindingSlice[],
  path: Set<number>,
  out: FilePreviewItem[],
  seen: Set<string>,
) {
  const id = Number(binding.bindingId)
  if (!Number.isFinite(id) || path.has(id)) return
  path.add(id)
  pushRowUploads(binding, rows, out, seen)
  const placed = collectSubTableFieldsFromLayout(binding.formFields || [])
  for (const field of placed) {
    const child = findBinding(bindings, field._bindingId)
    if (!child) continue
    for (const raw of rows) {
      if (!raw || typeof raw !== 'object') continue
      const nested = pullNestedRowsForBindingFromParentRows(
        {
          bindingId: child.bindingId,
          tableName: child.tableName || '',
          physicalTableName: child.physicalTableName,
          tableId: child.tableId,
        },
        [raw],
      )
      if (nested.length) walkBinding(child, nested, bindings, path, out, seen)
    }
  }
  path.delete(id)
}

export function collectFormPreviewFiles(input: CollectFormPreviewInput): FilePreviewItem[] {
  const out: FilePreviewItem[] = []
  const seen = new Set<string>()
  const leaves = flattenAllFormFieldSegments(input.fields, input.tabs, input.fieldsAfterTabs)
  pushUploadFields(leaves, input.formData, out, seen)
  const bindings = uniqueBindings(input.bindings || [])
  const placed = collectSubTableFieldsFromLayout(input.fields || [], input.tabs, input.fieldsAfterTabs)
  const visited = new Set<number>()
  for (const field of placed) {
    const binding = findBinding(bindings, field._bindingId)
    if (!binding) continue
    const id = Number(binding.bindingId)
    if (visited.has(id)) continue
    visited.add(id)
    walkBinding(binding, rowsForBinding(binding, input.formData), bindings, new Set(), out, seen)
  }
  return out
}
