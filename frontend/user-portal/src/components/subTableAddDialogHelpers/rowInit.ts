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
 * Platform-managed audit fields — EXACT four names only (case-insensitive, trimmed):
 * created_at / created_by / updated_at / updated_by.
 *
 * 判定语义与后端 platform-common `SystemAuditFields` 和 DW 前端 `useFormSave.ts`
 * 的 ALWAYS_VALID_FIELDS 保持一致；改动匹配规则时三处必须同步。
 * 不做模糊匹配（create_time / createUser 等变体一律不算）：审计字段名由平台
 * Table Design 自动生成、恒为精确四名；模糊匹配会把用户自建的同名业务字段
 * 误判为系统字段（设计端可编辑、运行端却被锁死/覆盖）。
 */
const AUDIT_FIELD_PATTERNS: ReadonlyArray<{
  /** Normalised (trimmed lowercase) field-name check — exact names only. */
  matches: (normalised: string) => boolean
  /** Produce the auto-filled value. Receives the stored user (may be null). */
  fill: (user: UserInfo | null) => string
}> = [
  {
    matches: (n) => n === 'created_at',
    fill: () => formatTimestampLocal(),
  },
  {
    matches: (n) => n === 'updated_at',
    fill: () => formatTimestampLocal(),
  },
  {
    matches: (n) => n === 'created_by',
    fill: (user) => user?.displayName || user?.username || '',
  },
  {
    matches: (n) => n === 'updated_by',
    fill: (user) => user?.displayName || user?.username || '',
  },
]

/** Normalise a field name for audit-field comparison (trim + lowercase; underscores kept). */
function normaliseFieldName(name: string): string {
  return name.trim().toLowerCase()
}

/**
 * Single source of truth for "is this a system audit field?" (portal frontend).
 * Use everywhere — column enrichment, readonly marking, dialog guards.
 */
export function isAuditField(fieldName: string): boolean {
  const n = normaliseFieldName(fieldName)
  return AUDIT_FIELD_PATTERNS.some(p => p.matches(n))
}

/**
 * Fill created_* / updated_* audit values on a row when it is SAVED from the Add dialog.
 * Must never run when the dialog opens — audit values are generated at real insert time
 * (dialog save), so an abandoned dialog leaves no timestamps behind.
 */
export function applyAuditFieldDefaults(row: Record<string, unknown>, columns: DialogColumn[]): void {
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

  // Audit fields (created_at / created_by / updated_at / updated_by) stay empty here:
  // they are filled by applyAuditFieldDefaults at dialog SAVE time, never on open.

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
    // Auto-PK / readonly FK / audit fields are system-filled (audit values only appear at save);
    // form-create disabled fields often fail required checks.
    if (col.required && !col.readonly && !isAuditField(col.field)) {
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
