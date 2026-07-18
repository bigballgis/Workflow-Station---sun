<template>
  <div class="email-template-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ t('emailTemplate.create') }}</el-button>
      <el-button @click="loadTemplates" :loading="loading">
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
    </div>

    <DesignerListTable
      :loading="loading"
      :storage-key="`${functionUnitId}:email-templates`"
      :columns="listColumns"
      :rows="templates"
      :actions-width="180"
    >
      <template #cell-enabled="{ row }">
        <el-tag
          :type="row.enabled ? 'success' : 'info'"
          size="small"
        >
          {{ row.enabled ? t('common.yes') : t('common.no') }}
        </el-tag>
      </template>
      <template #actions="{ row }">
        <el-button
          link
          type="primary"
          @click="openEditDialog(row as EmailTemplate)"
        >
          {{ t('common.edit') }}
        </el-button>
        <el-button
          link
          type="danger"
          @click="handleDelete(row as EmailTemplate)"
        >
          {{ t('common.delete') }}
        </el-button>
      </template>
    </DesignerListTable>

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
          <div class="subject-field-row">
            <el-input
              ref="subjectInputRef"
              v-model="form.subject"
              :placeholder="t('emailTemplate.subjectPlaceholder', { example: EMAIL_SUBJECT_VAR_EXAMPLE })"
            />
            <el-select
              :model-value="''"
              :placeholder="t('emailTemplate.insertVariable')"
              size="small"
              filterable
              :loading="variablesLoading"
              class="subject-insert-select"
              @change="insertSubjectVariable"
            >
              <template v-for="group in variableGroups" :key="group.label">
                <el-option-group :label="subjectGroupLabel(group.label)">
                  <el-option
                    v-for="opt in group.options"
                    :key="opt.token"
                    :label="opt.label"
                    :value="opt.token"
                  />
                </el-option-group>
              </template>
            </el-select>
          </div>
          <div class="form-tip">
            {{
              t('emailTemplate.subjectHint', {
                pattern: EMAIL_FIELD_VAR_PATTERN,
                example: EMAIL_SUBJECT_VAR_EXAMPLE,
              })
            }}
          </div>
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
import type { ElInput } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { emailTemplateApi, type EmailTemplate, type EmailTemplateRequest } from '@/api/emailTemplate'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import DesignerListTable from '@/components/designer-list/DesignerListTable.vue'
import type { DesignerListTableColumn } from '@/composables/useDesignerListGrid'
import {
  EMAIL_FIELD_VAR_PATTERN,
  EMAIL_SUBJECT_VAR_EXAMPLE,
  useEmailTemplateVariables,
  type EmailVariableGroup,
} from '@/composables/email/useEmailTemplateVariables'

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
const subjectInputRef = ref<InstanceType<typeof ElInput> | null>(null)
const variableGroups = ref<EmailVariableGroup[]>([])
const { groups, loading: variablesLoading, load: loadTemplateVariables } =
  useEmailTemplateVariables(props.functionUnitId)

const defaultForm = (): EmailTemplateRequest => ({
  name: '',
  subject: '',
  bodyHtml: '',
  enabled: true
})

const form = reactive<EmailTemplateRequest>(defaultForm())

const sanitizedPreview = computed(() => DOMPurify.sanitize(form.bodyHtml || ''))

const listColumns = computed<DesignerListTableColumn<EmailTemplate>[]>(() => [
  {
    key: 'name',
    prop: 'name',
    label: t('emailTemplate.name'),
    defaultWidth: 180,
    showOverflowTooltip: true,
  },
  {
    key: 'subject',
    prop: 'subject',
    label: t('emailTemplate.subject'),
    defaultWidth: 220,
    showOverflowTooltip: true,
  },
  {
    key: 'enabled',
    prop: 'enabled',
    label: t('emailTemplate.enabled'),
    defaultWidth: 100,
    getValue: (row) => (row.enabled ? t('common.yes') : t('common.no')),
  },
])

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

function subjectGroupLabel(label: string): string {
  return label === '__SUBTABLES__' ? t('emailTemplate.subTableGroup') : label
}

function insertSubjectVariable(token: string) {
  if (!token) return
  const current = form.subject ?? ''
  form.subject = current.trim() ? `${current.trimEnd()} ${token}` : token
  subjectInputRef.value?.focus()
}

function openCreateDialog() {
  editingId.value = null
  Object.assign(form, defaultForm())
  showPreview.value = false
  showFormDialog.value = true
  void loadTemplateVariables().then(() => {
    variableGroups.value = groups.value
  })
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
  } catch (e) {
    // FALLBACK(ux): detail API failed — still open dialog with list row so user can edit without blocking
    ElMessage.warning(resolveUserFacingHttpMessage(e, t) || t('emailTemplate.loadFailed'))
    Object.assign(form, {
      name: row.name,
      subject: row.subject || '',
      bodyHtml: row.bodyHtml || '',
      enabled: row.enabled
    })
  }
  showFormDialog.value = true
  void loadTemplateVariables().then(() => {
    variableGroups.value = groups.value
  })
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

onMounted(() => {
  void loadTemplates()
  void loadTemplateVariables().then(() => {
    variableGroups.value = groups.value
  })
})
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
  .form-tip {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.4;
    color: #909399;
  }
}
.subject-field-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  width: 100%;
  .el-input {
    flex: 1 1 240px;
    min-width: 0;
  }
}
.subject-insert-select {
  flex: 0 1 220px;
  min-width: 160px;
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
