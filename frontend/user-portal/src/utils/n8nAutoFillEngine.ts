// ============ Configuration Type Definitions ============

/** 字段映射条目 */
export interface FieldMapping {
  sourceField?: string
  targetField: string
  valueMapping?: Record<string, string>
  defaultValue?: string
  formatTemplate?: string
  separator?: string
}

/** 子表映射条目 */
export interface SubTableMappingEntry {
  targetType: 'sub_table'
  targetBindingId: number
  sourceArrayKey: string
  fillMode?: 'append' | 'update'
  fieldMappings: FieldMapping[]
}

/** 主表单字段映射条目 */
export interface FieldMappingEntry {
  targetType: 'field'
  source: string
  targetField: string
}

export type OutputMappingEntry = SubTableMappingEntry | FieldMappingEntry

// ============ Sub-table binding data structure ============

export interface SubTableBinding {
  bindingId: number
  tableName: string
  columns: Array<{ field: string; label: string; type?: string }>
  data: any[]
  [key: string]: any
}

// ============ AutoFill result ============

export interface AutoFillResult {
  updatedBindings: SubTableBinding[]
  updatedFormData: Record<string, any>
  filledCount: number
}

// ============ Helper Functions ============

/**
 * Resolve a value from a nested object using dot notation path.
 * Returns null for missing intermediate keys or non-object nodes.
 */
export function getByPath(obj: any, path: string): any {
  if (obj == null || typeof path !== 'string' || path === '') return null
  const keys = path.split('.')
  let current: any = obj
  for (const key of keys) {
    if (current == null || typeof current !== 'object') return null
    current = current[key]
  }
  return current ?? null
}

/**
 * Apply a format template by replacing {placeholder} tokens with source data values.
 * Segments where the referenced field is null/undefined are omitted.
 * Non-empty segments are joined with the separator (default " | ").
 */
export function applyFormatTemplate(
  template: string,
  sourceItem: Record<string, any>,
  separator: string = ' | '
): string {
  // Match segments: each segment is text that may contain one {placeholder}
  // Strategy: split template by placeholders, rebuild with values
  const placeholderRegex = /\{([^}]+)\}/g

  // Split template by separator to get individual segments.
  // If a placeholder in a segment resolves to null/undefined, that segment is omitted.
  const rawSegments = template.split(separator)
  const resultSegments: string[] = []

  for (const seg of rawSegments) {
    let hasNull = false
    const resolved = seg.replace(placeholderRegex, (_full, fieldName) => {
      const value = sourceItem[fieldName]
      if (value == null) {
        hasNull = true
        return ''
      }
      return String(value)
    })
    if (!hasNull) {
      // Trim surrounding whitespace from template literal text (e.g., " {field} " → "{field}")
      // but preserve the resolved value content
      const trimmed = resolved.trim()
      if (trimmed !== '') {
        resultSegments.push(trimmed)
      }
    }
  }

  return resultSegments.join(separator)
}

/**
 * Apply a single FieldMapping to one N8N response item, returning the target field value.
 * - When formatTemplate is present, delegates to applyFormatTemplate
 * - Otherwise reads sourceField from sourceItem and applies valueMapping
 */
export function applyFieldMapping(
  mapping: FieldMapping,
  sourceItem: Record<string, any>
): any {
  if (mapping.formatTemplate) {
    return applyFormatTemplate(
      mapping.formatTemplate,
      sourceItem,
      mapping.separator ?? ' | '
    )
  }

  const rawValue = mapping.sourceField != null ? sourceItem[mapping.sourceField] : null

  if (mapping.valueMapping && rawValue != null) {
    const strValue = String(rawValue)
    if (strValue in mapping.valueMapping) {
      return mapping.valueMapping[strValue]
    }
    return mapping.defaultValue !== undefined ? mapping.defaultValue : rawValue
  }

  return rawValue
}

// ============ Aggregation Helpers (skeleton — full implementation in Task 3) ============

/**
 * Parse an aggregation expression like "sum:InvoiceRecognitionResults.totalAmount"
 * Returns { func, arrayKey, field } or null for non-aggregation sources.
 */
export function parseAggregation(source: string): { func: string; arrayKey: string; field: string } | null {
  const colonIdx = source.indexOf(':')
  if (colonIdx <= 0) return null
  const func = source.substring(0, colonIdx)
  const rest = source.substring(colonIdx + 1)
  const lastDot = rest.lastIndexOf('.')
  if (lastDot <= 0) return null
  return { func, arrayKey: rest.substring(0, lastDot), field: rest.substring(lastDot + 1) }
}

/**
 * Compute aggregation over array items. Currently only supports 'sum'.
 */
export function computeAggregation(func: string, items: any[], field: string): number | null {
  if (func === 'sum') {
    return items.reduce((acc: number, item: any) => {
      const val = Number(item?.[field])
      return acc + (isNaN(val) ? 0 : val)
    }, 0)
  }
  return null
}

// ============ Main AutoFill Engine ============

/**
 * Generic N8N output auto-fill engine.
 * Pure function — does not mutate the original inputs.
 * Skeleton: full sub-table and field logic will be added in Tasks 2 and 3.
 */
export function applyAutoFill(
  n8nOutput: Record<string, any>,
  outputMapping: OutputMappingEntry[],
  subTableBindings: SubTableBinding[],
  formData: Record<string, any>
): AutoFillResult {
  // Deep clone inputs to ensure immutability
  const updatedBindings: SubTableBinding[] = JSON.parse(JSON.stringify(subTableBindings))
  const updatedFormData: Record<string, any> = JSON.parse(JSON.stringify(formData))
  let filledCount = 0

  for (const entry of outputMapping) {
    if (entry.targetType === 'sub_table') {
      const binding = updatedBindings.find(b => b.bindingId === entry.targetBindingId)
      if (!binding) {
        console.warn(`[AutoFill] targetBindingId ${entry.targetBindingId} not found in subTableBindings, skipping`)
        continue
      }
      if (!entry.fieldMappings || entry.fieldMappings.length === 0) {
        continue
      }

      const sourceData = getByPath(n8nOutput, entry.sourceArrayKey)
      if (sourceData == null || !Array.isArray(sourceData)) {
        console.warn(`[AutoFill] sourceArrayKey "${entry.sourceArrayKey}" not found or not an array, skipping`)
        continue
      }

      if (entry.fillMode === 'update') {
        // Update mode: match i-th source item to i-th existing row
        const updateCount = Math.min(sourceData.length, binding.data.length)
        for (let i = 0; i < updateCount; i++) {
          for (const fm of entry.fieldMappings) {
            const value = applyFieldMapping(fm, sourceData[i])
            if (value != null) {
              binding.data[i][fm.targetField] = value
            }
          }
        }
        filledCount += updateCount
      } else {
        // Append mode (default): create new rows and append
        for (const sourceItem of sourceData) {
          const row: Record<string, any> = {}
          let hasNonNull = false
          for (const fm of entry.fieldMappings) {
            const value = applyFieldMapping(fm, sourceItem)
            row[fm.targetField] = value
            if (value != null) {
              hasNonNull = true
            }
          }
          if (hasNonNull) {
            binding.data.push(row)
            filledCount++
          }
        }
      }
    }
    if (entry.targetType === 'field') {
      const agg = parseAggregation(entry.source)
      if (agg) {
        const items = getByPath(n8nOutput, agg.arrayKey)
        if (Array.isArray(items)) {
          const result = computeAggregation(agg.func, items, agg.field)
          if (result != null) {
            updatedFormData[entry.targetField] = result
            filledCount++
          }
        }
      } else {
        const value = getByPath(n8nOutput, entry.source)
        if (value != null) {
          updatedFormData[entry.targetField] = value
          filledCount++
        }
      }
    }
  }

  return { updatedBindings, updatedFormData, filledCount }
}
