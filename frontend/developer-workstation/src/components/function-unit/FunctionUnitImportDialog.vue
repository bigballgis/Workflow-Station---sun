<template>
  <el-dialog
    v-model="visible"
    :title="t('functionUnit.importPackage')"
    width="520px"
    destroy-on-close
    @closed="resetForm"
  >
    <el-alert
      :title="t('functionUnit.importVersionHint')"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px;"
    />
    <el-form
      label-width="auto"
      label-position="left"
    >
      <el-form-item :label="t('functionUnit.changeLog')">
        <el-input
          v-model="changeLog"
          type="textarea"
          :rows="2"
          :placeholder="t('functionUnit.changeLogPlaceholder')"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <!-- Only for an old package that does not say which team it belongs to (platform access model §6.3) -->
      <el-form-item
        v-if="needsWorkspace"
        :label="t('functionUnit.team')"
      >
        <el-select
          v-model="targetGroupId"
          :placeholder="t('functionUnit.selectTeam')"
          style="width: 100%;"
        >
          <el-option
            v-for="target in workspaceOptions"
            :key="target.id"
            :label="target.name"
            :value="target.id"
          />
        </el-select>
        <div class="field-tip">
          {{ t('functionUnit.importWorkspaceRequired') }}
        </div>
      </el-form-item>
    </el-form>
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      accept=".zip"
      :limit="1"
      :on-change="handleFileChange"
      :on-exceed="handleExceed"
    >
      <el-icon class="el-icon--upload">
        <UploadFilled />
      </el-icon>
      <div class="el-upload__text">
        {{ t('functionUnit.dragPackageHere') }}<em>{{ t('functionUnit.clickToUpload') }}</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          {{ t('functionUnit.zipFormatTip') }}
        </div>
      </template>
    </el-upload>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="!importFile || (needsWorkspace && !targetGroupId)"
        @click="handleStartImport"
      >
        {{ t('functionUnit.startImport') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadInstance, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { functionUnitApi } from '@/api/functionUnit'
import { canCreateInWorkspace } from '@/utils/permission'
import { getActiveGroupRaw, getMyTeams, PUBLIC_GROUP_ID } from '@/utils/devGroupContext'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  imported: []
}>()

const { t } = useI18n()

const uploadRef = ref<UploadInstance>()
const importFile = ref<File | null>(null)
const loading = ref(false)
const changeLog = ref('')
const needsWorkspace = ref(false)
const targetGroupId = ref('')

/** Workspaces a new unit may land in: teams the user may create in, and Public for a System Administrator. */
const workspaceOptions = computed(() => {
  const options = getMyTeams()
    .filter(team => canCreateInWorkspace(team.id))
    .map(team => ({ id: team.id, name: team.name }))
  if (canCreateInWorkspace(PUBLIC_GROUP_ID)) options.push({ id: PUBLIC_GROUP_ID, name: t('devGroup.publicGroup') })
  return options
})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

function resetForm() {
  importFile.value = null
  changeLog.value = ''
  needsWorkspace.value = false
  targetGroupId.value = ''
  uploadRef.value?.clearFiles()
}

function handleFileChange(file: UploadFile) {
  importFile.value = file.raw ?? null
}

function handleExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  importFile.value = files[0] ?? null
}

async function handleStartImport() {
  if (!importFile.value) return
  loading.value = true
  try {
    const response = await functionUnitApi.importFunctionUnit(
      importFile.value, changeLog.value, needsWorkspace.value ? targetGroupId.value : undefined)
    const result = response.data
    ElMessage.success(
      result.versioned ? t('functionUnit.importVersioned') : t('functionUnit.importSuccess')
    )
    // 包里的 flow 已建但未发布（本环境缺 connection 凭据）：必须让导入人看见，否则
    // service task 到运行时才失败
    const unpublished = (result.automationFlows ?? []).filter((f) => f.status === 'PUBLISH_FAILED')
    if (unpublished.length > 0) {
      ElMessage.warning({
        message: t('functionUnit.importAutomationFlowUnpublished', {
          flows: unpublished.map((f) => f.displayName).join(', '),
        }),
        duration: 0,
        showClose: true,
      })
    }
    visible.value = false
    emit('imported')
  } catch (e: unknown) {
    const data = (e as { response?: { data?: { error?: { code?: string; message?: string }; message?: string } } })
      ?.response?.data
    if (data?.error?.code === 'IMPORT_WORKSPACE_REQUIRED') {
      // An old package: let the user choose where the new unit goes, then import again.
      needsWorkspace.value = true
      const active = getActiveGroupRaw()
      targetGroupId.value = workspaceOptions.value.some(o => o.id === active) ? active! : ''
      return
    }
    ElMessage.error(data?.error?.message || data?.message || t('functionUnit.importFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.field-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
