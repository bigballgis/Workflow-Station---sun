<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.governanceRules') }}</span>
      <el-button type="primary" @click="showCreateDialog"><el-icon><Plus /></el-icon>{{ t('gateway.createRule') }}</el-button>
    </div>
    <el-card class="table-card">
      <el-table :data="rules" v-loading="loading" stripe>
        <el-table-column prop="ruleCode" :label="t('gateway.ruleCode')" width="180" />
        <el-table-column prop="name" :label="t('gateway.ruleName')" />
        <el-table-column prop="ruleType" :label="t('gateway.ruleType')" width="120" />
        <el-table-column :label="t('gateway.severity')" width="100">
          <template #default="{ row }"><el-tag :type="row.severity === 'BLOCK' ? 'danger' : 'warning'">{{ row.severity }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="t('gateway.environment')" width="100">
          <template #default="{ row }"><el-tag v-if="row.environmentCode">{{ row.environmentCode }}</el-tag><span v-else class="text-muted">{{ t('gateway.allEnvironments') }}</span></template>
        </el-table-column>
        <el-table-column :label="t('gateway.enabled')" width="80" align="center">
          <template #default="{ row }"><el-switch :model-value="row.enabled" disabled /></template>
        </el-table-column>
        <el-table-column :label="t('gateway.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">{{ t('gateway.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">{{ t('gateway.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" @current-change="fetchData" layout="prev,pager,next" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? t('gateway.editRule') : t('gateway.createRule')" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('gateway.ruleCode')" required><el-input v-model="form.ruleCode" :disabled="editing" /></el-form-item>
        <el-form-item :label="t('gateway.ruleName')" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('gateway.ruleType')" required>
          <el-select v-model="form.ruleType"><el-option v-for="rt in ruleTypes" :key="rt" :label="rt" :value="rt" /></el-select>
        </el-form-item>
        <el-form-item :label="t('gateway.severity')" required>
          <el-select v-model="form.severity"><el-option label="BLOCK" value="BLOCK" /><el-option label="WARN" value="WARN" /></el-select>
        </el-form-item>
        <el-form-item :label="t('gateway.environment')"><el-input v-model="form.environmentCode" :placeholder="t('gateway.envPlaceholder')" /></el-form-item>
        <el-form-item :label="t('gateway.expression')" required><el-input v-model="form.expression" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="t('gateway.enabled')"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item :label="t('gateway.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('gateway.cancel') }}</el-button><el-button type="primary" @click="handleSave">{{ t('gateway.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listRules, createRule, updateRule, deleteRule } from '@/api/gateway'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()
const loading = ref(false), rules = ref<any[]>([]), page = ref(0), size = ref(20), total = ref(0)
const dialogVisible = ref(false), editing = ref(false), form = ref<any>({}), editId = ref<number | null>(null)
const ruleTypes = ['NAMING', 'SECURITY', 'VERSIONING', 'TRAFFIC', 'ENVIRONMENT']

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await listRules({ page: page.value, size: size.value })
    rules.value = res.content || []
    total.value = res.totalElements || 0
  } catch (e: any) { ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error')) }
  finally { loading.value = false }
}

const showCreateDialog = () => {
  editing.value = false; editId.value = null
  form.value = { ruleCode: '', name: '', ruleType: 'NAMING', severity: 'WARN', environmentCode: '', expression: '', enabled: true, description: '' }
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  editing.value = true; editId.value = row.id
  form.value = { ruleCode: row.ruleCode, name: row.name, ruleType: row.ruleType, severity: row.severity, environmentCode: row.environmentCode || '', expression: row.expression, enabled: row.enabled, description: row.description || '' }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (editing.value && editId.value) {
      await updateRule(editId.value, form.value)
      ElMessage.success(t('gateway.ruleUpdated'))
    } else {
      await createRule(form.value)
      ElMessage.success(t('gateway.ruleCreated'))
    }
    dialogVisible.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error')) }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm(t('gateway.confirmDelete'), t('gateway.warning'), { type: 'warning' })
    await deleteRule(id)
    ElMessage.success(t('gateway.deleted'))
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(fetchData)
</script>
