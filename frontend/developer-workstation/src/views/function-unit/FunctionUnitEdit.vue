<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <div class="flex" style="align-items: center; gap: 16px;">
          <el-button @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
            {{ $t('common.back') }}
          </el-button>
          <el-tooltip :content="store.current?.description || '暂无描述'" placement="bottom">
            <IconPreview 
              :icon-id="store.current?.icon?.id" 
              size="large" 
            />
          </el-tooltip>
          <h3>{{ store.current?.name }}</h3>
          <el-tag :type="statusTagType(store.current?.status)">
            {{ statusLabel(store.current?.status) }}
          </el-tag>
          <span v-if="store.current?.currentVersion" class="version-badge">
            v{{ store.current.currentVersion }}
          </span>
        </div>
        <div>
          <el-button @click="openEditDialog">
            <el-icon><Setting /></el-icon>
            设置
          </el-button>
          <el-button @click="handleValidate" :loading="validating">{{ $t('functionUnit.validate') }}</el-button>
          <el-button type="primary" @click="handlePublish">{{ $t('functionUnit.publish') }}</el-button>
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

    <!-- Edit Function Unit Dialog -->
    <el-dialog v-model="showEditDialog" title="功能单元设置" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="图标">
          <div class="icon-edit-row">
            <IconPreview :icon-id="editForm.iconId" size="large" />
            <el-button @click="showIconSelectorForEdit = true">选择图标</el-button>
            <el-button v-if="editForm.iconId" link type="danger" @click="editForm.iconId = null">清除</el-button>
          </div>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" placeholder="功能单元名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="功能单元描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- Validation Result Dialog -->
    <el-dialog v-model="showValidationDialog" title="验证结果" width="500px">
      <div v-if="validationResult">
        <el-result v-if="validationResult.valid" icon="success" title="验证通过" sub-title="功能单元配置完整，可以发布" />
        <div v-else>
          <el-alert v-if="validationResult.errors?.length" type="error" :closable="false" style="margin-bottom: 12px;">
            <template #title>错误 ({{ validationResult.errors.length }})</template>
            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
              <li v-for="(err, i) in validationResult.errors" :key="i">{{ err }}</li>
            </ul>
          </el-alert>
          <el-alert v-if="validationResult.warnings?.length" type="warning" :closable="false">
            <template #title>警告 ({{ validationResult.warnings.length }})</template>
            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
              <li v-for="(warn, i) in validationResult.warnings" :key="i">{{ warn }}</li>
            </ul>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="showValidationDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Icon Selector for edit dialog -->
    <IconSelector 
      :model-value="editForm.iconId" 
      :visible="showIconSelectorForEdit" 
      @update:visible="showIconSelectorForEdit = $event"
      @select="handleIconSelectForEdit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Setting } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { ValidationResult } from '@/api/functionUnit'
import ProcessDesigner from '@/components/designer/ProcessDesigner.vue'
import TableDesigner from '@/components/designer/TableDesigner.vue'
import FormDesigner from '@/components/designer/FormDesigner.vue'
import ActionDesigner from '@/components/designer/ActionDesigner.vue'
import VersionManager from '@/components/version/VersionManager.vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import IconSelector from '@/components/icon/IconSelector.vue'

const route = useRoute()
const router = useRouter()
const store = useFunctionUnitStore()

const functionUnitId = computed(() => Number(route.params.id))
const activeTab = ref('process')
const validating = ref(false)
const saving = ref(false)
const showValidationDialog = ref(false)
const showEditDialog = ref(false)
const validationResult = ref<ValidationResult | null>(null)
const showIconSelectorForEdit = ref(false)

const editForm = reactive({
  name: '',
  description: '',
  iconId: null as number | null
})

const statusTagType = (status?: string) => {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[status || ''] || 'info'
}

const statusLabel = (status?: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
  return map[status || ''] || status
}

function openEditDialog() {
  editForm.name = store.current?.name || ''
  editForm.description = store.current?.description || ''
  editForm.iconId = store.current?.icon?.id || null
  showEditDialog.value = true
}

async function handleSaveEdit() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    await store.update(functionUnitId.value, {
      name: editForm.name,
      description: editForm.description,
      iconId: editForm.iconId
    })
    ElMessage.success('保存成功')
    showEditDialog.value = false
    store.fetchById(functionUnitId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function handleIconSelectForEdit(icon: any) {
  editForm.iconId = icon?.id || null
}

async function handleValidate() {
  validating.value = true
  try {
    validationResult.value = await store.validate(functionUnitId.value)
    showValidationDialog.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '验证失败')
  } finally {
    validating.value = false
  }
}

async function handlePublish() {
  try {
    const { value } = await ElMessageBox.prompt('请输入变更日志', '发布功能单元', { 
      inputType: 'textarea',
      inputPlaceholder: '描述本次发布的变更内容...'
    })
    await store.publish(functionUnitId.value, value)
    ElMessage.success('发布成功')
    store.fetchById(functionUnitId.value)
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '发布失败')
    }
  }
}

onMounted(() => {
  store.fetchById(functionUnitId.value)
})
</script>

<style lang="scss" scoped>
.version-badge {
  background-color: #f0f0f0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #666;
}

.icon-edit-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
