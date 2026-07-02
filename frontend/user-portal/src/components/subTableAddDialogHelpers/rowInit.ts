import type { FormRules } from 'element-plus'
import type { DialogColumn } from './types'
import { getUser, type UserInfo } from '@/api/auth'

/** Format the current instant as {@code YYYY-MM-DD HH:mm:ss} in UTC+8 (Asia/Shanghai). */
function formatTimestampLocal(): string {
  const d = new Date()
  const utc8 = new Date(d.getTime() + (d.getTimezoneOffset() + 480) * 60000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${utc8.getFullYear()}-${pad(utc8.getMonth() + 1)}-${pad(utc8.getDate())} ${pad(utc8.getHours())}:${pad(utc8.getMinutes())}:${pad(utc8.getSeconds())}`
}

/**
 * Audit field name patterns that should be auto-filled when a new sub-table row
 * is created. Case-insensitive; matches snake_case, camelCase, and flat variants.
 *
 * This is intentionally generic — any sub-table with these field names gets the
 * behaviour for free, no per-table wiring required.
 */
const AUDIT_FIELD_PATTERNS: ReadonlyArray<{
  /** Normalised (lowercase + underscores stripped) field-name check. */
  matches: (normalised: string) => boolean
  /** Produce the auto-filled value. Receives the stored user (may be null). */
  fill: (user: UserInfo | null) => string
}> = [
  {
    matches: (n) => n === 'created_at' || n === 'createdat' || n === 'create_time' || n === 'createtime',
    fill: () => formatTimestampLocal(),
  },
  {
    matches: (n) => n === 'updated_at' || n === 'updatedat' || n === 'update_time' || n === 'updatetime',
    fill: () => formatTimestampLocal(),
  },
  {
    matches: (n) => n === 'created_by' || n === 'createdby' || n === 'create_user' || n === 'createuser',
    fill: (user) => user?.displayName || user?.username || '',
  },
  {
    matches: (n) => n === 'updated_by' || n === 'updatedby' || n === 'update_user' || n === 'updateuser',
    fill: (user) => user?.displayName || user?.username || '',
  },
]

/** Normalise a field name for audit-field comparison. */
function normaliseFieldName(name: string): string {
  return name.trim().toLowerCase().replace(/[\s_-]+/g, '')
}

/**
 * Single source of truth for "is this a system audit field?".
 * Use everywhere — column enrichment, readonly marking, dialog guards.
 */
export function isAuditField(fieldName: string): boolean {
  const n = normaliseFieldName(fieldName)
  return AUDIT_FIELD_PATTERNS.some(p => p.matches(n))
}

/**
 * Apply audit-field auto-fill to an initial row before it opens in the Add dialog.
 * Callers (e.g. {@link buildInitialRow}) invoke this after type-default seeding
 * so audit values always win over generic type defaults.
 */
function applyAuditFieldDefaults(row: Record<string, unknown>, columns: DialogColumn[]): void {
  // Resolve user once — all audit columns share the same value.
  let cachedUser: UserInfo | null | undefined
  const resolveUser = (): UserInfo | null => {
    if (cachedUser === undefined) {
      try {
        cachedUser = getUser()
      } catch {
        cachedUser = null
      }
    }
    return cachedUser
  }

  for (const col of columns) {
    const normalised = normaliseFieldName(col.field)
    for (const pattern of AUDIT_FIELD_PATTERNS) {
      if (pattern.matches(normalised)) {
        row[col.field] = pattern.fill(resolveUser())
        break
      }
    }
  }
}

/**
 * Refresh {@code updated_at} / {@code updated_by} fields on an existing row during edit.
 * Only touches the "updated" family — created_at / created_by are left unchanged.
 */
export function applyEditAuditDefaults(row: Record<string, unknown>, columns: DialogColumn[]): void {
  let cachedUser: UserInfo | null | undefined
  const resolveUser = (): UserInfo | null => {
    if (cachedUser === undefined) {
      try { cachedUser = getUser() } catch { cachedUser = null }
    }
    return cachedUser
  }

  const UPDATED_PATTERNS = AUDIT_FIELD_PATTERNS.filter(p => p.matches('updated_at') || p.matches('updated_by'))

  for (const col of columns) {
    const normalised = normaliseFieldName(col.field)
    for (const pattern of UPDATED_PATTERNS) {
      if (pattern.matches(normalised)) {
        row[col.field] = pattern.fill(resolveUser())
        break
      }
    }
  }
}

export function buildInitialRow(columns: DialogColumn[]): Record<string, unknown> {
  const row: Record<string, unknown> = {}
  for (const col of columns) {
    switch (col.type) {
      case 'number':
        row[col.field] = undefined
        break
      case 'switch':
        row[col.field] = false
        break
      case 'checkbox':
        row[col.field] = []
        break
      case 'date':
      case 'datetime':
      case 'timerange':
        row[col.field] = null
        break
      case 'treeselect':
        row[col.field] = col.props?.multiple ? [] : ''
        break
      case 'rate':
      case 'slider':
        row[col.field] = 0
        break
      case 'colorPicker':
        row[col.field] = ''
        break
      case 'tree':
        row[col.field] = []
        break
      case 'transfer':
        row[col.field] = []
        break
      case 'cascader':
        row[col.field] = []
        break
      case 'lookup':
        row[col.field] = null
        break
      case 'editor':
        row[col.field] = ''
        break
      case 'signature':
        row[col.field] = ''
        break
      default:
        // text, textarea, password, radio, select, user, department
        row[col.field] = ''
    }
  }

  // After type-based defaults are set, apply audit field auto-fill so that
  // created_at / created_by / updated_at / updated_by always carry the correct
  // values — regardless of the field's declared type (VARCHAR, TIMESTAMP, etc.).
  applyAuditFieldDefaults(row, columns)

  return row
}

function isEmptyFormValue(value: unknown): boolean {
  if (value == null) return true
  if (typeof value === 'string' && value.trim() === '') return true
  return false
}

/** Keep seeded PK/FK/runtime values when form-create or empty inputs omit them on save. */
export function mergeFormRowWithSeed(
  seed: Record<string, unknown> | null | undefined,
  form: Record<string, unknown>,
): Record<string, unknown> {
  const row = { ...form }
  if (!seed) return row
  for (const [key, seedVal] of Object.entries(seed)) {
    if (isEmptyFormValue(row[key]) && !isEmptyFormValue(seedVal)) {
      row[key] = seedVal
    }
  }
  return row
}

export function buildRules(columns: DialogColumn[]): FormRules {
  const rules: FormRules = {}
  for (const col of columns) {
    // Auto-PK / readonly FK are system-filled; form-create disabled fields often fail required checks.
    if (col.required && !col.readonly) {
      const trigger =
        col.type === 'select' || col.type === 'date' || col.type === 'datetime' || col.type === 'checkbox'
        || col.type === 'cascader' || col.type === 'transfer' || col.type === 'lookup' || col.type === 'switch'
          ? 'change'
          : 'blur'
      if (col.type === 'switch') {
        rules[col.field] = [{
          type: 'boolean',
          required: true,
          message: `${col.label} is required`,
          trigger,
        }]
      } else {
        rules[col.field] = [{ required: true, message: `${col.label} is required`, trigger }]
      }
    }
  }
  return rules
}
