<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <div class="flex" style="align-items: center; gap: 16px;">
          <el-button @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
            {{ $t('common.back') }}
          </el-button>
          <h3>{{ store.current?.name }}</h3>
          <el-tag :type="statusTagType(store.current?.status)">
            {{ statusLabel(store.current?.status) }}
          </el-tag>
        </div>
        <div>
          <el-button @click="handleValidate">{{ $t('functionUnit.validate') }}</el-button>
          <el-button type="primary" @click="handleSave">{{ $t('common.save') }}</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane :label="$t('functionUnit.process')" name="process">
          <ProcessDesigner v-if="activeTab === 'process'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="$t('functionUnit.tables')" name="tables">
          <TableDesigner v-if="activeTab === 'tables'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="$t('functionUnit.forms')" name="forms">
          <FormDesigner v-if="activeTab === 'forms'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="$t('functionUnit.actionDesign')" name="actions">
          <ActionDesigner v-if="activeTab === 'actions'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="$t('version.title')" name="versions">
          <VersionManager v-if="activeTab === 'versions'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import ProcessDesigner from '@/components/designer/ProcessDesigner.vue'
import TableDesigner from '@/components/designer/TableDesigner.vue'
import FormDesigner from '@/components/designer/FormDesigner.vue'
import ActionDesigner from '@/components/designer/ActionDesigner.vue'
import VersionManager from '@/components/version/VersionManager.vue'

const route = useRoute()
const router = useRouter()
const store = useFunctionUnitStore()

const functionUnitId = computed(() => Number(route.params.id))
const activeTab = ref('process')

const statusTagType = (status?: string) => {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[status || ''] || 'info'
}

const statusLabel = (status?: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
  return map[status || ''] || status
}

async function handleValidate() {
  // TODO: Implement validation
  ElMessage.info('验证功能开发中')
}

async function handleSave() {
  // TODO: Implement save
  ElMessage.success('保存成功')
}

onMounted(() => {
  store.fetchById(functionUnitId.value)
})
</script>
