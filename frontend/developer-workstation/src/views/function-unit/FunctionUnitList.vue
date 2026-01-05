<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <el-form :inline="true" @submit.prevent="handleSearch">
          <el-form-item>
            <el-input v-model="searchForm.name" :placeholder="$t('functionUnit.name')" clearable />
          </el-form-item>
          <el-form-item>
            <el-select v-model="searchForm.status" :placeholder="$t('functionUnit.status')" clearable>
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="已归档" value="ARCHIVED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="showCreateDialog = true">
          {{ $t('functionUnit.create') }}
        </el-button>
      </div>

      <el-table :data="store.list" v-loading="store.loading" stripe>
        <el-table-column prop="name" :label="$t('functionUnit.name')" />
        <el-table-column prop="description" :label="$t('functionUnit.description')" show-overflow-tooltip />
        <el-table-column prop="status" :label="$t('functionUnit.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentVersion" :label="$t('functionUnit.version')" width="100" />
        <el-table-column :label="$t('functionUnit.actions')" width="280">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="success" @click="handlePublish(row)">{{ $t('functionUnit.publish') }}</el-button>
            <el-button link type="warning" @click="handleClone(row)">{{ $t('functionUnit.clone') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="store.total"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" :title="$t('functionUnit.create')" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="formRules" label-width="80px">
        <el-form-item :label="$t('functionUnit.name')" prop="name">
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item :label="$t('functionUnit.description')" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { FunctionUnit } from '@/api/functionUnit'

const router = useRouter()
const store = useFunctionUnitStore()

const searchForm = reactive({ name: '', status: '' })
const pagination = reactive({ page: 1, size: 10 })
const showCreateDialog = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ name: '', description: '' })

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const statusTagType = (status: string) => {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
  return map[status] || status
}

function loadData() {
  store.fetchList({ ...searchForm, page: pagination.page - 1, size: pagination.size })
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleEdit(row: FunctionUnit) {
  router.push(`/function-units/${row.id}`)
}

async function handleCreate() {
  await createFormRef.value?.validate()
  await store.create(createForm)
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  createForm.name = ''
  createForm.description = ''
  loadData()
}

async function handlePublish(row: FunctionUnit) {
  const { value } = await ElMessageBox.prompt('请输入变更日志', '发布功能单元', { inputType: 'textarea' })
  await store.publish(row.id, value)
  ElMessage.success('发布成功')
  loadData()
}

async function handleClone(row: FunctionUnit) {
  const { value } = await ElMessageBox.prompt('请输入新名称', '克隆功能单元')
  await store.clone(row.id, value)
  ElMessage.success('克隆成功')
  loadData()
}

async function handleDelete(row: FunctionUnit) {
  await ElMessageBox.confirm('确定要删除该功能单元吗？', '提示', { type: 'warning' })
  await store.remove(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
