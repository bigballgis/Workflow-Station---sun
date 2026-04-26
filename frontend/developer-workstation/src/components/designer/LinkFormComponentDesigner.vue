<template>
  <div class="link-form-component-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> {{ t('linkForm.createComponent') }}
      </el-button>
      <el-button @click="loadComponents" :loading="loading">
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
    </div>

    <div class="component-list" v-if="!selectedComponent">
      <el-table :data="components" v-loading="loading" stripe @row-click="handleSelectComponent">
        <el-table-column prop="componentName" :label="t('linkForm.componentName')" width="150" />
        <el-table-column prop="linkedFormName" :label="t('linkForm.linkedForm')" width="150">
          <template #default="{ row }">
            <span>{{ row.linkedFormName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="columnLabel" :label="t('linkForm.columnLabel')" width="120">
          <template #default="{ row }">
            <span>{{ row.columnLabel || row.linkText || t('linkForm.defaultLinkText') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="displayField" :label="t('linkForm.displayField')" width="120">
          <template #default="{ row }">
            <span>{{ row.displayField || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('linkForm.linkText')" width="100">
          <template #default="{ row }">
            <span>{{ row.linkText || t('linkForm.defaultLinkText') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" :label="t('linkForm.sort')" width="80" />
        <el-table-column :label="t('common.operations')" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="handleSelectComponent(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="danger" size="small" @click.stop="handleDeleteComponent(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && components.length === 0" :description="t('linkForm.noComponents')" />
    </div>

    <div class="component-editor" v-else>
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> {{ t('common.back') }}
        </el-button>
        <span class="component-name">{{ selectedComponent.componentName }}</span>
        <el-button type="primary" @click="handleSaveComponent" :loading="saving">
          {{ t('common.save') }}
        </el-button>
      </div>

      <el-form :model="selectedComponent" label-width="140px" label-position="left" style="max-width: 600px;">
        <el-form-item :label="t('linkForm.componentName')" required>
          <el-input v-model="selectedComponent.componentName" />
        </el-form-item>

        <el-form-item :label="t('linkForm.linkedForm')" required>
          <el-select
            v-model="selectedComponent.linkedFormId"
            :placeholder="t('linkForm.selectLinkedForm')"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="form in availableForms"
              :key="form.id"
              :label="form.formName"
              :value="form.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('linkForm.columnLabel')">
          <el-input v-model="selectedComponent.columnLabel" :placeholder="t('linkForm.columnLabelPlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('linkForm.linkText')">
          <el-input v-model="selectedComponent.linkText" :placeholder="t('linkForm.linkTextPlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('linkForm.displayField')">
          <el-input v-model="selectedComponent.displayField" :placeholder="t('linkForm.displayFieldPlaceholder')" />
          <div class="form-item-tip">{{ t('linkForm.displayFieldTip') }}</div>
        </el-form-item>

        <el-form-item :label="t('common.sort')">
          <el-input-number v-model="selectedComponent.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
    </div>

    <!-- Create Component Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      :title="t('linkForm.createComponent')"
      width="500px"
      @close="resetCreateForm"
    >
      <el-form :model="createForm" label-width="120px" label-position="left">
        <el-form-item :label="t('linkForm.componentName')" required>
          <el-input v-model="createForm.componentName" />
        </el-form-item>

        <el-form-item :label="t('linkForm.linkedForm')" required>
          <el-select
            v-model="createForm.linkedFormId"
            :placeholder="t('linkForm.selectLinkedForm')"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="form in availableForms"
              :key="form.id"
              :label="form.formName"
              :value="form.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('linkForm.columnLabel')">
          <el-input v-model="createForm.columnLabel" />
        </el-form-item>

        <el-form-item :label="t('linkForm.linkText')">
          <el-input v-model="createForm.linkText" placeholder="详情" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreateComponent" :loading="creating">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus, Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { linkFormComponentApi, type LinkFormComponentResponse } from '@/api/linkFormComponent'
import { useFunctionUnitStore } from '@/stores/functionUnit'

const { t } = useI18n()
const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const components = ref<LinkFormComponentResponse[]>([])
const selectedComponent = ref<LinkFormComponentResponse | null>(null)
const showCreateDialog = ref(false)
const creating = ref(false)
const saving = ref(false)

const createForm = ref({
  componentName: '',
  linkedFormId: null as number | null,
  columnLabel: '',
  linkText: '详情',
})

// Available forms: STANDALONE and ACTION type forms
const availableForms = computed(() => {
  return store.forms.filter(f =>
    f.formType === 'STANDALONE' || f.formType === 'ACTION'
  )
})

async function loadComponents() {
  loading.value = true
  try {
    const res = await linkFormComponentApi.getComponents(props.functionUnitId)
    components.value = res.data || []
  } catch (e) {
    console.error('[LinkFormComponentDesigner] failed to load components:', e)
    components.value = []
  } finally {
    loading.value = false
  }
}

function handleSelectComponent(row: LinkFormComponentResponse) {
  selectedComponent.value = { ...row }
}

function handleBackToList() {
  selectedComponent.value = null
}

async function handleCreateComponent() {
  if (!createForm.value.componentName || !createForm.value.linkedFormId) {
    ElMessage.warning(t('linkForm.fillRequiredFields'))
    return
  }

  creating.value = true
  try {
    await linkFormComponentApi.create(props.functionUnitId, {
      componentName: createForm.value.componentName,
      linkedFormId: createForm.value.linkedFormId,
      columnLabel: createForm.value.columnLabel,
      linkText: createForm.value.linkText || '详情',
    })
    ElMessage.success(t('common.saveSuccess'))
    showCreateDialog.value = false
    resetCreateForm()
    loadComponents()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.saveFailed'))
  } finally {
    creating.value = false
  }
}

async function handleSaveComponent() {
  if (!selectedComponent.value) return
  if (!selectedComponent.value.componentName || !selectedComponent.value.linkedFormId) {
    ElMessage.warning(t('linkForm.fillRequiredFields'))
    return
  }

  saving.value = true
  try {
    await linkFormComponentApi.update(props.functionUnitId, selectedComponent.value.id, {
      componentName: selectedComponent.value.componentName,
      linkedFormId: selectedComponent.value.linkedFormId,
      displayField: selectedComponent.value.displayField,
      linkText: selectedComponent.value.linkText,
      columnLabel: selectedComponent.value.columnLabel,
      sortOrder: selectedComponent.value.sortOrder,
    })
    ElMessage.success(t('common.saveSuccess'))
    loadComponents()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDeleteComponent(row: LinkFormComponentResponse) {
  await ElMessageBox.confirm(
    t('linkForm.deleteConfirm'),
    t('common.confirmTitle'),
    { type: 'warning' }
  )

  try {
    await linkFormComponentApi.delete(props.functionUnitId, row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadComponents()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.deleteFailed'))
  }
}

function resetCreateForm() {
  createForm.value = {
    componentName: '',
    linkedFormId: null,
    columnLabel: '',
    linkText: '详情',
  }
}

onMounted(async () => {
  await store.fetchForms(props.functionUnitId)
  loadComponents()
})
</script>

<style scoped lang="scss">
.link-form-component-designer {
  min-height: 400px;

  .designer-toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }

  .editor-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;

    .component-name {
      flex: 1;
      font-size: 18px;
      font-weight: bold;
    }
  }

  .form-item-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
