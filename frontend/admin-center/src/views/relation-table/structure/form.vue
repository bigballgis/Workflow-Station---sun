<template>
  <div class="page-container">
    <PageHeader :title="isEdit ? 'Edit Table Structure' : 'Create Table Structure'">
      <template #actions>
        <el-button @click="router.back()">Back</el-button>
      </template>
    </PageHeader>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" label-position="left" style="max-width: 800px;">
      <el-form-item label="Table Name" prop="tableName">
        <el-input v-model="form.tableName" placeholder="e.g. my_table" :disabled="isEdit" />
      </el-form-item>
      <el-form-item label="Display Name" prop="displayName">
        <el-input v-model="form.displayName" placeholder="Display name" />
      </el-form-item>
      <el-form-item label="Description" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="Table description" />
      </el-form-item>
    </el-form>

    <div style="margin-top: 24px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
        <span style="font-size: 16px; font-weight: 600;">Field Definitions</span>
        <el-button type="primary" size="small" @click="addField">
          <el-icon><Plus /></el-icon>Add Field
        </el-button>
      </div>

      <el-table :data="form.fieldDefinitions" border>
        <el-table-column label="#" width="50" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="Field Name" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.fieldName" placeholder="field_name" size="small" :disabled="isAuditField(row)" />
          </template>
        </el-table-column>
        <el-table-column label="Data Type" width="140">
          <template #default="{ row }">
            <el-select v-model="row.dataType" placeholder="Type" size="small" style="width: 100%;" :disabled="isAuditField(row)">
              <el-option v-for="dt in dataTypes" :key="dt" :label="dt" :value="dt" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Length" width="90">
          <template #default="{ row }">
            <el-input-number v-model="row.length" :min="0" size="small" controls-position="right" style="width: 100%;" :disabled="isAuditField(row)" />
          </template>
        </el-table-column>
        <el-table-column label="Nullable" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.nullable" size="small" :disabled="isAuditField(row)" />
          </template>
        </el-table-column>
        <el-table-column label="Primary Key" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isPrimaryKey" size="small" :disabled="isAuditField(row)" />
          </template>
        </el-table-column>
        <el-table-column label="Default Value" width="130">
          <template #default="{ row }">
            <el-input v-model="row.defaultValue" placeholder="" size="small" :disabled="isAuditField(row)" />
          </template>
        </el-table-column>
        <el-table-column label="Comment" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.comment" placeholder="" size="small" :disabled="isAuditField(row)" />
          </template>
        </el-table-column>
        <el-table-column label="" width="60" align="center">
          <template #default="{ row, $index }">
            <el-button v-if="!isAuditField(row)" link type="danger" size="small" @click="removeField($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div style="margin-top: 24px;">
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? 'Save Changes' : 'Create Table' }}
      </el-button>
      <el-button @click="router.back()">Cancel</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  relationTableStructureApi,
  type RelationDataType,
  type CreateFieldDefinitionRequest,
  type UpdateFieldDefinitionRequest
} from '@/api/relationTable'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const tableId = computed(() => Number(route.params.id))

const formRef = ref<FormInstance>()
const submitting = ref(false)

const dataTypes: RelationDataType[] = [
  'VARCHAR', 'INTEGER', 'BIGINT', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP', 'TEXT'
]

interface FieldRow {
  id?: number
  fieldName: string
  dataType: RelationDataType
  length?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  comment?: string
  sortOrder?: number
}

const form = reactive({
  tableName: '',
  displayName: '',
  description: '',
  fieldDefinitions: [] as FieldRow[]
})

const rules: FormRules = {
  tableName: [{ required: true, message: 'Table name is required', trigger: 'blur' }],
}

const AUDIT_FIELD_NAMES = new Set(['created_at', 'created_by', 'updated_at', 'updated_by'])

const isAuditField = (row: FieldRow): boolean => AUDIT_FIELD_NAMES.has(row.fieldName)

const sortFieldsAuditLast = () => {
  const normal = form.fieldDefinitions.filter(f => !AUDIT_FIELD_NAMES.has(f.fieldName))
  const audit = form.fieldDefinitions.filter(f => AUDIT_FIELD_NAMES.has(f.fieldName))
  form.fieldDefinitions = [...normal, ...audit]
}

const createEmptyField = (): FieldRow => ({
  fieldName: '',
  dataType: 'VARCHAR',
  length: 255,
  nullable: true,
  isPrimaryKey: false,
  defaultValue: '',
  comment: ''
})

const createAuditFields = (): FieldRow[] => [
  { fieldName: 'created_at', dataType: 'TIMESTAMP', nullable: true, isPrimaryKey: false, comment: 'Created At' },
  { fieldName: 'created_by', dataType: 'VARCHAR', length: 64, nullable: true, isPrimaryKey: false, comment: 'Created By' },
  { fieldName: 'updated_at', dataType: 'TIMESTAMP', nullable: true, isPrimaryKey: false, comment: 'Updated At' },
  { fieldName: 'updated_by', dataType: 'VARCHAR', length: 64, nullable: true, isPrimaryKey: false, comment: 'Updated By' },
]

const addField = () => {
  // Insert before audit fields
  const firstAuditIdx = form.fieldDefinitions.findIndex(f => AUDIT_FIELD_NAMES.has(f.fieldName))
  if (firstAuditIdx >= 0) {
    form.fieldDefinitions.splice(firstAuditIdx, 0, createEmptyField())
  } else {
    form.fieldDefinitions.push(createEmptyField())
  }
}

const removeField = (index: number) => {
  form.fieldDefinitions.splice(index, 1)
}

const loadTableData = async () => {
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
      comment: f.comment || '',
      sortOrder: f.sortOrder
    }))
    // 补齐缺失的审计字段
    const existingNames = new Set(form.fieldDefinitions.map(f => f.fieldName))
    for (const af of createAuditFields()) {
      if (!existingNames.has(af.fieldName)) {
        form.fieldDefinitions.push(af)
      }
    }
    sortFieldsAuditLast()
  } catch (e) {
    console.error('Failed to load table:', e)
    ElMessage.error('Failed to load table data')
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.fieldDefinitions.length === 0) {
    ElMessage.warning('Please add at least one field')
    return
  }

  const hasEmptyFieldName = form.fieldDefinitions.some(f => !f.fieldName.trim())
  if (hasEmptyFieldName) {
    ElMessage.warning('All fields must have a name')
    return
  }

  submitting.value = true
  try {
    const fields: CreateFieldDefinitionRequest[] = form.fieldDefinitions.map((f, i) => ({
      fieldName: f.fieldName,
      dataType: f.dataType,
      length: f.length,
      nullable: f.nullable,
      isPrimaryKey: f.isPrimaryKey,
      defaultValue: f.defaultValue || undefined,
      comment: f.comment || undefined,
      sortOrder: i
    }))

    if (isEdit.value) {
      const updateFields: UpdateFieldDefinitionRequest[] = form.fieldDefinitions.map((f, i) => ({
        id: f.id,
        fieldName: f.fieldName,
        dataType: f.dataType,
        length: f.length,
        nullable: f.nullable,
        isPrimaryKey: f.isPrimaryKey,
        defaultValue: f.defaultValue || undefined,
        comment: f.comment || undefined,
        sortOrder: i
      }))
      await relationTableStructureApi.update(tableId.value, {
        displayName: form.displayName || undefined,
        description: form.description || undefined,
        fieldDefinitions: updateFields
      })
      ElMessage.success('Table updated successfully')
    } else {
      await relationTableStructureApi.create({
        tableName: form.tableName,
        displayName: form.displayName || undefined,
        description: form.description || undefined,
        fieldDefinitions: fields
      })
      ElMessage.success('Table created successfully')
    }
    router.push('/relation-tables/structure')
  } catch (e: any) {
    const details = e?.response?.data?.details
    if (details && typeof details === 'object') {
      const msgs = Object.values(details).join('; ')
      ElMessage.error(msgs || 'Submit failed')
    }
    console.error('Submit failed:', e)
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.tableName = ''
  form.displayName = ''
  form.description = ''
  form.fieldDefinitions = []
}

onMounted(() => {
  if (isEdit.value) {
    loadTableData()
  } else {
    resetForm()
    addField()
    form.fieldDefinitions.push(...createAuditFields())
  }
})

onActivated(() => {
  if (isEdit.value) {
    loadTableData()
  } else {
    resetForm()
    addField()
    form.fieldDefinitions.push(...createAuditFields())
  }
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
</style>
