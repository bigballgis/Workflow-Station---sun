<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="router.push('/common-tables')" text>
        <el-icon><ArrowLeft /></el-icon> {{ t('common.back') }}
      </el-button>
      <span class="page-title">{{ table?.name || t('commonTable.edit') }}</span>
      <el-tag :type="statusTagType(table?.status)" v-if="table">{{ statusLabel(table.status) }}</el-tag>
    </div>

    <div class="card" v-loading="loading">
      <!-- Basic Info -->
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" label-position="left"
        style="max-width: 640px; margin-bottom: 24px;">
        <el-form-item :label="t('commonTable.code')" prop="code">
          <el-input v-model="form.code" :placeholder="t('commonTable.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('commonTable.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('commonTable.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('commonTable.description')">
          <el-input v-model="form.description" type="textarea" :rows="2"
            :placeholder="t('commonTable.descriptionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('commonTable.status')">
          <el-select v-model="form.status">
            <el-option :label="t('commonTable.draft')" value="DRAFT" />
            <el-option :label="t('commonTable.published')" value="PUBLISHED" />
            <el-option :label="t('commonTable.archived')" value="ARCHIVED" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- Fields -->
      <div class="section-header">
        <h3>{{ t('commonTable.fields') }}</h3>
        <el-button size="small" type="primary" @click="addField">
          <el-icon><Plus /></el-icon> {{ t('commonTable.addField') }}
        </el-button>
      </div>

      <el-table :data="form.fields" border size="small" style="margin-bottom: 16px;">
        <el-table-column type="index" width="50" align="center" />
        <el-table-column :label="t('commonTable.fieldName')" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.fieldName" size="small" placeholder="field_name" />
          </template>
        </el-table-column>
        <el-table-column :label="t('commonTable.displayName')" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.displayName" size="small" :placeholder="t('commonTable.displayName')" />
          </template>
        </el-table-column>
        <el-table-column :label="t('commonTable.dataType')" min-width="110">
          <template #default="{ row }">
            <el-select v-model="row.dataType" size="small">
              <el-option label="VARCHAR" value="VARCHAR" />
              <el-option label="INTEGER" value="INTEGER" />
              <el-option label="BIGINT" value="BIGINT" />
              <el-option label="DECIMAL" value="DECIMAL" />
              <el-option label="BOOLEAN" value="BOOLEAN" />
              <el-option label="DATE" value="DATE" />
              <el-option label="TIMESTAMP" value="TIMESTAMP" />
              <el-option label="TEXT" value="TEXT" />
              <el-option label="FILE" value="FILE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="长度" width="90" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.length" size="small" :min="0" controls-position="right"
              style="width: 70px;" />
          </template>
        </el-table-column>
        <el-table-column :label="t('commonTable.isPrimaryKey')" width="70" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isPrimaryKey" />
          </template>
        </el-table-column>
        <el-table-column :label="t('commonTable.nullable')" width="70" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.nullable" />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="80" align="center">
          <template #default="{ row, $index }">
            <el-button link type="danger" size="small" @click="removeField($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="action-bar">
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
        <el-button @click="router.push('/common-tables')">{{ t('common.cancel') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Plus, Delete } from '@element-plus/icons-vue'
import { commonTableApi, type CommonTableDefinition, type CommonFieldDefinition } from '@/api/commonTable'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const tableId = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const table = ref<CommonTableDefinition | null>(null)
const formRef = ref()

const form = reactive<{
  code: string
  name: string
  description: string
  status: string
  fields: CommonFieldDefinition[]
}>({
  code: '',
  name: '',
  description: '',
  status: 'DRAFT',
  fields: []
})

const rules = {
  code: [
    { required: true, message: t('commonTable.codePlaceholder'), trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '只能包含字母、数字和下划线，且必须以字母开头', trigger: 'blur' }
  ],
  name: [{ required: true, message: t('commonTable.namePlaceholder'), trigger: 'blur' }]
}

function statusTagType(status?: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function statusLabel(status?: string) {
  if (status === 'PUBLISHED') return t('commonTable.published')
  if (status === 'ARCHIVED') return t('commonTable.archived')
  return t('commonTable.draft')
}

function addField() {
  form.fields.push({
    fieldName: '',
    displayName: '',
    dataType: 'VARCHAR',
    length: 255,
    isPrimaryKey: false,
    nullable: true,
    sortOrder: form.fields.length
  })
}

function removeField(index: number) {
  form.fields.splice(index, 1)
}

async function loadTable() {
  loading.value = true
  try {
    const res = await commonTableApi.getById(tableId)
    const data: CommonTableDefinition = (res as any).data || res
    table.value = data
    form.code = data.code
    form.name = data.name
    form.description = data.description || ''
    form.status = data.status || 'DRAFT'
    form.fields = (data.fieldDefinitions || []).map(f => ({ ...f }))
  } catch (e) {
    ElMessage.error(t('common.error'))
    router.push('/common-tables')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await commonTableApi.update(tableId, {
      code: form.code,
      name: form.name,
      description: form.description,
      status: form.status,
      fields: form.fields.map((f, i) => ({ ...f, sortOrder: i }))
    })
    ElMessage.success(t('commonTable.updateSuccess'))
    loadTable()
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

onMounted(loadTable)
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  flex: 1;
}
.card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 15px;
  }
}
.action-bar {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
</style>
