import { describe, it, expect } from 'vitest'

// Unit tests for SubTableField file upload filename auto-fill logic
// Validates: Requirements 11.2, 11.3, 11.4

// ===== Types (mirroring component) =====

interface Column {
  field: string
  label: string
  type?: 'text' | 'number' | 'date' | 'upload'
  minWidth?: number
  props?: Record<string, any>
}

// ===== Extracted logic (mirrors handleUploadSuccess auto-fill behavior) =====

/**
 * Determines the filename auto-fill target for a given upload column.
 * Returns the target field name if fileNameTargetField is configured and the
 * target column exists, otherwise returns null (no auto-fill).
 */
function resolveFileNameTarget(columns: Column[], uploadField: string): string | null {
  const uploadCol = columns.find(c => c.field === uploadField)
  const fileNameTarget = uploadCol?.props?.fileNameTargetField
  if (fileNameTarget && columns.some(c => c.field === fileNameTarget)) {
    return fileNameTarget
  }
  return null
}

/**
 * Simulates the auto-fill portion of handleUploadSuccess.
 * Given a row, columns config, upload field, and filename, applies the
 * filename auto-fill if configured.
 */
function applyUploadAutoFill(
  row: Record<string, any>,
  columns: Column[],
  uploadField: string,
  fileName: string
): Record<string, any> {
  const updated = { ...row }
  const target = resolveFileNameTarget(columns, uploadField)
  if (target) {
    updated[target] = fileName
  }
  return updated
}

// ===== Tests =====

describe('SubTableField fileNameTargetField', () => {
  const baseColumns: Column[] = [
    { field: 'file', label: 'File', type: 'upload', props: { action: '/api/v1/upload', accept: '.pdf', fileNameTargetField: 'file_name' } },
    { field: 'file_name', label: 'File Name', type: 'text' },
    { field: 'description', label: 'Description', type: 'text' },
  ]

  describe('resolveFileNameTarget', () => {
    it('returns target field when fileNameTargetField is configured and target column exists', () => {
      // Validates: Requirement 11.2, 11.3
      const target = resolveFileNameTarget(baseColumns, 'file')
      expect(target).toBe('file_name')
    })

    it('returns null when fileNameTargetField is not specified', () => {
      // Validates: Requirement 11.4
      const columns: Column[] = [
        { field: 'file', label: 'File', type: 'upload', props: { action: '/api/v1/upload' } },
        { field: 'file_name', label: 'File Name', type: 'text' },
      ]
      const target = resolveFileNameTarget(columns, 'file')
      expect(target).toBeNull()
    })

    it('returns null when upload column has no props at all', () => {
      // Validates: Requirement 11.4
      const columns: Column[] = [
        { field: 'file', label: 'File', type: 'upload' },
        { field: 'file_name', label: 'File Name', type: 'text' },
      ]
      const target = resolveFileNameTarget(columns, 'file')
      expect(target).toBeNull()
    })

    it('returns null when fileNameTargetField points to a non-existent column', () => {
      // Validates: Requirement 11.3 (target column must exist)
      const columns: Column[] = [
        { field: 'file', label: 'File', type: 'upload', props: { fileNameTargetField: 'nonexistent_col' } },
        { field: 'description', label: 'Description', type: 'text' },
      ]
      const target = resolveFileNameTarget(columns, 'file')
      expect(target).toBeNull()
    })

    it('returns null when upload field is not found in columns', () => {
      const target = resolveFileNameTarget(baseColumns, 'unknown_field')
      expect(target).toBeNull()
    })
  })

  describe('applyUploadAutoFill', () => {
    it('auto-fills filename to target column when fileNameTargetField is configured', () => {
      // Validates: Requirement 11.2, 11.3
      const row = { file: '', file_name: '', description: 'test' }
      const result = applyUploadAutoFill(row, baseColumns, 'file', 'invoice.pdf')
      expect(result.file_name).toBe('invoice.pdf')
      expect(result.description).toBe('test') // other fields unchanged
    })

    it('does not auto-fill when fileNameTargetField is not specified', () => {
      // Validates: Requirement 11.4
      const columns: Column[] = [
        { field: 'file', label: 'File', type: 'upload', props: { action: '/api/v1/upload' } },
        { field: 'file_name', label: 'File Name', type: 'text' },
      ]
      const row = { file: '', file_name: 'original.pdf', description: '' }
      const result = applyUploadAutoFill(row, columns, 'file', 'new_file.pdf')
      expect(result.file_name).toBe('original.pdf') // unchanged
    })

    it('does not auto-fill when target column does not exist', () => {
      // Validates: Requirement 11.3
      const columns: Column[] = [
        { field: 'file', label: 'File', type: 'upload', props: { fileNameTargetField: 'missing_col' } },
        { field: 'description', label: 'Description', type: 'text' },
      ]
      const row = { file: '', description: 'test' }
      const result = applyUploadAutoFill(row, columns, 'file', 'doc.pdf')
      expect(result).toEqual({ file: '', description: 'test' })
    })
  })
})
