<template>
  <div class="email-template-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ t('emailTemplate.create') }}</el-button>
      <el-button @click="loadTemplates" :loading="loading">
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="templates" v-loading="loading" stripe>
      <el-table-column prop="name" :label="t('emailTemplate.name')" min-width="180" />
      <el-table-column prop="subject" :label="t('emailTemplate.subject')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="enabled" :label="t('emailTemplate.enabled')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('common.yes') : t('common.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row as EmailTemplate)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="handleDelete(row as EmailTemplate)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="showFormDialog"
      :title="editingId ? t('emailTemplate.edit') : t('emailTemplate.create')"
      width="820px"
      destroy-on-close
      top="6vh"
    >
      <el-form :model="form" label-position="top" class="template-form">
        <el-form-item :label="t('emailTemplate.name')" required>
          <el-input v-model="form.name" :placeholder="t('emailTemplate.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('emailTemplate.subject')">
          <el-input v-model="form.subject" :placeholder="t('emailTemplate.subjectPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('emailTemplate.body')">
          <EmailRichBodyEditor
            v-model="form.bodyHtml"
            :function-unit-id="functionUnitId"
          />
        </el-form-item>
        <el-form-item :label="t('emailTemplate.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item>
          <el-button link type="primary" @click="showPreview = !showPreview">
            {{ showPreview ? t('emailTemplate.hidePreview') : t('emailTemplate.showPreview') }}
          </el-button>
        </el-form-item>
        <el-form-item v-if="showPreview" :label="t('emailTemplate.preview')">
          <div class="template-preview" v-html="sanitizedPreview"></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, defineAsyncComponent, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { emailTemplateApi, type EmailTemplate, type EmailTemplateRequest } from '@/api/emailTemplate'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

const EmailRichBodyEditor = defineAsyncComponent(
  () => import('@/components/designer/email/EmailRichBodyEditor.vue')
)

const props = defineProps<{ functionUnitId: number }>()
const { t } = useI18n()

const templates = ref<EmailTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const showFormDialog = ref(false)
const showPreview = ref(false)
const editingId = ref<number | null>(null)

const defaultForm = (): EmailTemplateRequest => ({
  name: '',
  subject: '',
  bodyHtml: '',
  enabled: true
})

const form = reactive<EmailTemplateRequest>(defaultForm())

const sanitizedPreview = computed(() => DOMPurify.sanitize(form.bodyHtml || ''))

async function loadTemplates() {
  loading.value = true
  try {
    const res = await emailTemplateApi.list(props.functionUnitId)
    templates.value = res.data || []
  } catch {
    ElMessage.error(t('emailTemplate.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingId.value = null
  Object.assign(form, defaultForm())
  showPreview.value = false
  showFormDialog.value = true
}

async function openEditDialog(row: EmailTemplate) {
  editingId.value = row.id
  showPreview.value = false
  try {
    const res = await emailTemplateApi.get(props.functionUnitId, row.id)
    const tpl = res.data
    Object.assign(form, {
      name: tpl.name,
      subject: tpl.subject || '',
      bodyHtml: tpl.bodyHtml || '',
      enabled: tpl.enabled
    })
  } catch {
    Object.assign(form, {
      name: row.name,
      subject: row.subject || '',
      bodyHtml: row.bodyHtml || '',
      enabled: row.enabled
    })
  }
  showFormDialog.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning(t('emailTemplate.nameRequired'))
    return
  }
  saving.value = true
  try {
    const payload: EmailTemplateRequest = {
      name: form.name.trim(),
      subject: form.subject?.trim() || undefined,
      bodyHtml: form.bodyHtml || undefined,
      enabled: form.enabled
    }
    if (editingId.value) {
      await emailTemplateApi.update(props.functionUnitId, editingId.value, payload)
    } else {
      await emailTemplateApi.create(props.functionUnitId, payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    showFormDialog.value = false
    await loadTemplates()
  } catch (e) {
    ElMessage.error(resolveUserFacingHttpMessage(e, t) || t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: EmailTemplate) {
  try {
    await ElMessageBox.confirm(t('emailTemplate.deleteConfirm', { name: row.name }), t('common.confirm'), { type: 'warning' })
    await emailTemplateApi.delete(props.functionUnitId, row.id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadTemplates()
  } catch {
    // cancelled or failed
  }
}

onMounted(loadTemplates)
</script>

<style scoped lang="scss">
.email-template-designer {
  padding: 8px 0;
}
.designer-toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.template-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}
.template-preview {
  width: 100%;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  background: #fafafa;
  max-height: 320px;
  overflow-y: auto;
}
</style>
