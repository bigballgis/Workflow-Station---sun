<template>
  <div class="page-container">
    <div class="card">
      <div class="filter-panel">
        <div class="filter-left">
          <el-input
            v-model="searchText"
            :placeholder="t('commonTable.name')"
            clearable
            style="width: 220px;"
            @clear="loadTables"
            @keyup.enter="loadTables"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="loadTables">{{ t('common.search') }}</el-button>
        </div>
        <el-button v-if="canCreate" type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          {{ t('commonTable.create') }}
        </el-button>
      </div>

      <el-table :data="filteredTables" v-loading="loading" stripe>
        <el-table-column prop="code" :label="t('commonTable.code')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" :label="t('commonTable.name')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" :label="t('commonTable.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('commonTable.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('commonTable.fields')" width="90" align="center">
          <template #default="{ row }">{{ row.fieldDefinitions?.length || 0 }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)" :disabled="!canDelete">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="filteredTables.length === 0 && !loading" :description="t('commonTable.noTables')" />
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" :title="t('commonTable.create')" width="500px" @close="resetForm">
      <el-form :model="createForm" :rules="formRules" ref="formRef" label-width="100px" label-position="left">
        <el-form-item :label="t('commonTable.code')" prop="code">
          <el-input v-model="createForm.code" :placeholder="t('commonTable.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('commonTable.name')" prop="name">
          <el-input v-model="createForm.name" :placeholder="t('commonTable.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('commonTable.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="3"
            :placeholder="t('commonTable.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { commonTableApi, type CommonTableDefinition } from '@/api/commonTable'
import { getUser } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const creating = ref(false)
const tables = ref<CommonTableDefinition[]>([])
const searchText = ref('')
const showCreateDialog = ref(false)
const formRef = ref()

const createForm = ref({ code: '', name: '', description: '' })

const formRules = {
  code: [
    { required: true, message: t('commonTable.codePlaceholder'), trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '只能包含字母、数字和下划线，且必须以字母开头', trigger: 'blur' }
  ],
  name: [{ required: true, message: t('commonTable.namePlaceholder'), trigger: 'blur' }]
}

const user = computed(() => getUser())
const userRole = computed(() => user.value?.roles?.[0] || '')
const canCreate = computed(() => ['TECH_LEAD', 'TEAM_LEAD'].includes(userRole.value))
const canDelete = computed(() => userRole.value === 'TECH_LEAD')

const filteredTables = computed(() => {
  if (!searchText.value) return tables.value
  const q = searchText.value.toLowerCase()
  return tables.value.filter(t =>
    t.name.toLowerCase().includes(q) || t.code.toLowerCase().includes(q)
  )
})

function statusTagType(status: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function statusLabel(status: string) {
  if (status === 'PUBLISHED') return t('commonTable.published')
  if (status === 'ARCHIVED') return t('commonTable.archived')
  return t('commonTable.draft')
}

async function loadTables() {
  loading.value = true
  try {
    const res = await commonTableApi.list()
    tables.value = (res as any).data || res || []
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    loading.value = false
  }
}

function handleEdit(row: CommonTableDefinition) {
  router.push(`/common-tables/${row.id}`)
}

async function handleDelete(row: CommonTableDefinition) {
  try {
    await ElMessageBox.confirm(t('commonTable.deleteConfirm'), t('common.confirmTitle'), { type: 'warning' })
    await commonTableApi.delete(row.id)
    ElMessage.success(t('commonTable.deleteSuccess'))
    loadTables()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(t('common.error'))
  }
}

async function handleCreate() {
  await formRef.value?.validate()
  creating.value = true
  try {
    const res = await commonTableApi.create(createForm.value)
    const created = (res as any).data || res
    ElMessage.success(t('commonTable.createSuccess'))
    showCreateDialog.value = false
    router.push(`/common-tables/${created.id}`)
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    creating.value = false
  }
}

function resetForm() {
  createForm.value = { code: '', name: '', description: '' }
  formRef.value?.resetFields()
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
}
.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.filter-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.filter-left {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
