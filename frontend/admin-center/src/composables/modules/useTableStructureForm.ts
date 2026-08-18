/**
 * Table Structure Form 业务逻辑 composable
 */
import { ref, reactive, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyWarning } from '@/utils/notify'
import { relationTableStructureApi, type RelationDataType, type CreateFieldDefinitionRequest, type UpdateFieldDefinitionRequest, type RelationTableResponse, type LookupConfig } from '@/api/relationTable'
import { suggestFieldName, suggestTableName } from '@/utils/fieldNameSlug'
import { serializePkGeneration } from '@/utils/pkGenerationConfig'
import { parseComputedFieldFromApi, type ComputedFieldDefinition } from '@/utils/computedFieldConfig'

interface FieldRow {
  id?: number
  fieldName: string
  dataType: RelationDataType
  length?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  displayName?: string
  sortOrder?: number
  fieldNameTouched?: boolean
  pkGeneration?: Record<string, unknown>
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  fkDisplayMode?: string
  lookupConfig?: LookupConfig
  isComputed?: boolean
  computedField?: ComputedFieldDefinition
}

export function useTableStructureForm(options: { tableId: Ref<number>; isEdit: Ref<boolean> }) {
  const { tableId, isEdit } = options
  const { t } = useI18n()

  const submitting = ref(false)
  const dataTypes: RelationDataType[] = ['VARCHAR', 'INTEGER', 'BIGINT', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP', 'TEXT', 'LOOKUP']
  const AUDIT_FIELD_NAMES = new Set(['created_at', 'created_by', 'updated_at', 'updated_by'])

  // Foreign Key 候选表：仅可引用其他 *已部署* 的 Relation Table（排除当前正在编辑的表）
  const allTables = ref<RelationTableResponse[]>([])
  const fkRefTables = computed<RelationTableResponse[]>(() =>
    allTables.value.filter(tb => tb.status === 'DEPLOYED' && tb.id !== tableId.value),
  )

  const loadFkRefTables = async () => {
    try {
      allTables.value = await relationTableStructureApi.list()
    } catch {
      allTables.value = []
    }
  }

  const form = reactive({ tableName: '', displayName: '', description: '', fieldDefinitions: [] as FieldRow[] })
  const rules = {
    displayName: [{ required: true, message: () => t('form.validationDisplayNameRequired'), trigger: 'blur' }],
    tableName: [{ required: true, message: () => t('form.validationTableNameRequired'), trigger: 'blur' }],
  }

  const onTableDisplayNameInput = () => {
    if (isEdit.value) return
    form.tableName = suggestTableName(form.displayName)
  }

  const isAuditField = (row: FieldRow): boolean => AUDIT_FIELD_NAMES.has(row.fieldName)

  // Mirrors the backend eligibility check in ComputedFieldDesignValidator: a derived column cannot
  // also be a key, a reference, or carry a default the user could edit.
  const canBeComputed = (row: FieldRow): boolean =>
    !isAuditField(row) && !row.isPrimaryKey && !row.isForeignKey && row.dataType !== 'LOOKUP'

  const serializeComputedField = (row: FieldRow): Record<string, unknown> | undefined => {
    if (!row.isComputed || !canBeComputed(row) || !row.computedField) return undefined
    return { ...row.computedField } as unknown as Record<string, unknown>
  }

  const sortFieldsAuditLast = () => {
    form.fieldDefinitions = [
      ...form.fieldDefinitions.filter(f => !AUDIT_FIELD_NAMES.has(f.fieldName)),
      ...form.fieldDefinitions.filter(f => AUDIT_FIELD_NAMES.has(f.fieldName)),
    ]
  }

  const existingFieldNames = (excludeIndex?: number): string[] =>
    form.fieldDefinitions
      .map((f, i) => (excludeIndex === i ? '' : f.fieldName))
      .filter(Boolean)

  const onFieldDisplayNameInput = (row: FieldRow, index: number) => {
    if (row.id || row.fieldNameTouched || isAuditField(row)) return
    row.fieldName = suggestFieldName(row.displayName || '', existingFieldNames(index))
  }

  const onFieldNameManualInput = (row: FieldRow) => {
    if (!isAuditField(row)) row.fieldNameTouched = true
  }

  const onPrimaryKeyChange = (row: FieldRow, checked: boolean) => {
    if (!checked) {
      row.pkGeneration = undefined
      return
    }
    row.isComputed = false
    row.computedField = undefined
    if (!row.pkGeneration) {
      row.pkGeneration = { strategy: 'uuid' }
    }
  }

  const createEmptyField = (): FieldRow => ({
    fieldName: '',
    dataType: 'VARCHAR',
    length: 255,
    nullable: true,
    isPrimaryKey: false,
    defaultValue: '',
    displayName: '',
    isForeignKey: false,
    refPrimaryKeyFields: [],
    fkDisplayMode: 'readonly',
  })

  const createAuditFields = (): FieldRow[] => [
    { fieldName: 'created_at', dataType: 'TIMESTAMP', nullable: true, isPrimaryKey: false, displayName: 'Created At', fieldNameTouched: true },
    { fieldName: 'created_by', dataType: 'VARCHAR', length: 64, nullable: true, isPrimaryKey: false, displayName: 'Created By', fieldNameTouched: true },
    { fieldName: 'updated_at', dataType: 'TIMESTAMP', nullable: true, isPrimaryKey: false, displayName: 'Updated At', fieldNameTouched: true },
    { fieldName: 'updated_by', dataType: 'VARCHAR', length: 64, nullable: true, isPrimaryKey: false, displayName: 'Updated By', fieldNameTouched: true },
  ]

  const addField = () => {
    const idx = form.fieldDefinitions.findIndex(f => AUDIT_FIELD_NAMES.has(f.fieldName))
    form.fieldDefinitions.splice(idx >= 0 ? idx : form.fieldDefinitions.length, 0, createEmptyField())
  }

  const removeField = (index: number) => { form.fieldDefinitions.splice(index, 1) }

  const assertTableNameAvailable = async (tableName: string, excludeTableId?: number): Promise<boolean> => {
    const trimmed = tableName?.trim()
    if (!trimmed) return false
    try {
      const res = await relationTableStructureApi.checkTableNameAvailable(trimmed, excludeTableId)
      const available = (res as { available?: boolean })?.available
        ?? (res as { data?: { available?: boolean } })?.data?.available
      if (!available) {
        notifyWarning(t('form.nameAlreadyExists', { name: trimmed }))
        return false
      }
      return true
    } catch {
      notifyError(t('common.error'))
      return false
    }
  }

  const loadTableData = async () => {
    await loadFkRefTables()
    if (!isEdit.value) return
    try {
      const data = await relationTableStructureApi.getById(tableId.value)
      form.tableName = data.tableName
      form.displayName = data.displayName || ''
      form.description = data.description || ''
      form.fieldDefinitions = (data.fieldDefinitions || []).map(f => ({
        id: f.id,
        fieldName: f.fieldName,
        dataType: f.dataType,
        length: f.length,
        nullable: f.nullable,
        isPrimaryKey: f.isPrimaryKey,
        defaultValue: f.defaultValue || '',
        displayName: f.displayName || '',
        sortOrder: f.sortOrder,
        fieldNameTouched: true,
        pkGeneration: f.isPrimaryKey
          ? (f.pkGeneration ?? { strategy: 'uuid' })
          : undefined,
        isForeignKey: f.isForeignKey || false,
        refTableId: f.refTableId,
        refPrimaryKeyFields: f.refPrimaryKeyFields || [],
        fkDisplayMode: f.fkDisplayMode || 'readonly',
        lookupConfig: f.lookupConfig || undefined,
        isComputed: f.isComputed || false,
        computedField: parseComputedFieldFromApi(f.computedField) ?? undefined,
      }))
      const names = new Set(form.fieldDefinitions.map(f => f.fieldName))
      for (const af of createAuditFields()) {
        if (!names.has(af.fieldName)) form.fieldDefinitions.push(af)
      }
      sortFieldsAuditLast()
    } catch {
      notifyError('Failed to load table data')
    }
  }

  const submit = async () => {
    if (!form.fieldDefinitions.length) { notifyWarning('Please add at least one field'); return false }
    if (form.fieldDefinitions.some(f => !f.fieldName.trim())) { notifyWarning('All fields must have a name'); return false }

    if (!isEdit.value) {
      if (!await assertTableNameAvailable(form.tableName)) return false
    } else if (form.tableName?.trim()) {
      if (!await assertTableNameAvailable(form.tableName, tableId.value)) return false
    }

    submitting.value = true
    try {
      if (isEdit.value) {
        const fields: UpdateFieldDefinitionRequest[] = form.fieldDefinitions.map((f, i) => ({
          id: f.id,
          fieldName: f.fieldName,
          dataType: f.dataType,
          length: f.length,
          nullable: f.nullable,
          isPrimaryKey: f.isPrimaryKey,
          defaultValue: f.defaultValue || undefined,
          displayName: f.displayName || undefined,
          sortOrder: i,
          pkGeneration: serializePkGeneration(f.pkGeneration, f.isPrimaryKey),
          isForeignKey: f.isForeignKey || false,
          refTableId: f.isForeignKey ? f.refTableId : undefined,
          refPrimaryKeyFields: f.isForeignKey ? f.refPrimaryKeyFields : undefined,
          fkDisplayMode: f.isForeignKey ? (f.fkDisplayMode || 'readonly') : undefined,
          lookupConfig: f.dataType === 'LOOKUP' ? f.lookupConfig : undefined,
          isComputed: !!serializeComputedField(f),
          computedField: serializeComputedField(f),
        }))
        await relationTableStructureApi.update(tableId.value, {
          displayName: form.displayName || undefined,
          description: form.description || undefined,
          fieldDefinitions: fields,
        })
        notifySuccess('Table updated successfully')
      } else {
        const fields: CreateFieldDefinitionRequest[] = form.fieldDefinitions.map((f, i) => ({
          fieldName: f.fieldName,
          dataType: f.dataType,
          length: f.length,
          nullable: f.nullable,
          isPrimaryKey: f.isPrimaryKey,
          defaultValue: f.defaultValue || undefined,
          displayName: f.displayName || undefined,
          sortOrder: i,
          pkGeneration: serializePkGeneration(f.pkGeneration, f.isPrimaryKey),
          isForeignKey: f.isForeignKey || false,
          refTableId: f.isForeignKey ? f.refTableId : undefined,
          refPrimaryKeyFields: f.isForeignKey ? f.refPrimaryKeyFields : undefined,
          fkDisplayMode: f.isForeignKey ? (f.fkDisplayMode || 'readonly') : undefined,
          lookupConfig: f.dataType === 'LOOKUP' ? f.lookupConfig : undefined,
          isComputed: !!serializeComputedField(f),
          computedField: serializeComputedField(f),
        }))
        await relationTableStructureApi.create({
          tableName: form.tableName,
          displayName: form.displayName || undefined,
          description: form.description || undefined,
          fieldDefinitions: fields,
        })
        notifySuccess('Table created successfully')
      }
      return true
    } catch (e: unknown) {
      const err = e as { response?: { data?: { details?: Record<string, unknown> } } }
      if (err?.response?.data?.details && typeof err.response.data.details === 'object') {
        notifyError(Object.values(err.response.data.details).join('; ') || 'Submit failed')
      }
      else notifyError('Submit failed')
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    form, rules, submitting, dataTypes, fkRefTables, isAuditField, canBeComputed, addField, removeField, loadTableData, submit,
    onFieldDisplayNameInput, onFieldNameManualInput, onTableDisplayNameInput, onPrimaryKeyChange,
  }
}
