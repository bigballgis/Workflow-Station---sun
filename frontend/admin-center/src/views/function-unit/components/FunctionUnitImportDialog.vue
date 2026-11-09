<template>
  <el-dialog
    v-model="visible"
    :title="t('functionUnit.importPackage')"
    width="520px"
    destroy-on-close
  >
    <el-upload
      ref="importUploadRef"
      drag
      :auto-upload="false"
      accept=".zip"
      :limit="1"
      :on-change="emitFileChange"
      :on-remove="emitFileRemove"
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

    <div
      v-if="previewLoading"
      v-loading="true"
      class="import-preview-loading"
      :element-loading-text="t('functionUnit.readingPackage')"
    />

    <div
      v-else-if="preview"
      class="import-preview"
    >
      <el-descriptions
        :column="1"
        size="small"
        border
      >
        <el-descriptions-item :label="t('common.name')">
          {{ preview.name }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('common.code')">
          {{ preview.code }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('functionUnit.version')">
          {{ preview.version }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('functionUnit.packageTeam')">
          {{ packageTeamText }}
        </el-descriptions-item>
        <el-descriptions-item
          v-if="preview.currentTeam"
          :label="t('functionUnit.currentTeam')"
        >
          <el-tag
            v-if="preview.currentTeam.state === 'MISSING'"
            type="warning"
            size="small"
          >
            {{ t('functionUnit.teamDeleted', { code: preview.currentTeam.code }) }}
          </el-tag>
          <template v-else>
            {{ preview.currentTeam.name }} ({{ preview.currentTeam.code }})
            <el-tag
              v-if="preview.currentTeam.state === 'INACTIVE'"
              type="warning"
              size="small"
            >
              {{ t('functionUnit.teamInactive') }}
            </el-tag>
          </template>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="block"
        class="import-block"
        type="error"
        :closable="false"
        show-icon
        :title="t(block.key, block.params)"
      />
      <el-form
        v-else
        label-position="top"
        class="import-team-form"
        @submit.prevent
      >
        <el-form-item
          :label="t('functionUnit.team')"
          required
        >
          <el-select
            v-model="teamCode"
            filterable
            :placeholder="t('functionUnit.selectTeam')"
            class="import-team-select"
          >
            <el-option
              v-for="team in preview.selectableTeams"
              :key="team.code"
              :label="team.name"
              :value="team.code"
            >
              <span>{{ team.name }}</span>
              <span class="import-team-code">{{ team.code }}</span>
            </el-option>
          </el-select>
          <div class="import-team-hint">
            {{ t(hint.key, hint.params) }}
          </div>
          <div
            v-if="moveHint"
            class="import-team-move"
          >
            {{ moveHint }}
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="importLoading"
        :disabled="!canStartImport"
        @click="emit('startImport')"
      >
        {{ t('functionUnit.startImport') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadInstance } from 'element-plus'
import type { ImportPreview } from '@/api/functionUnit'
import { importTeamHint, type ImportMessage } from '@/composables/modules/useFunctionUnitImport'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  importLoading: boolean
  previewLoading: boolean
  preview: ImportPreview | null
  block: ImportMessage | null
  canStartImport: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'fileChange': [uploadFile: { raw?: File } | null]
  'startImport': []
}>()

const teamCode = defineModel<string>('teamCode', { required: true })

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const importUploadRef = ref<UploadInstance>()
defineExpose({ importUploadRef })

const packageTeamText = computed(() => {
  const team = props.preview?.packageTeam
  return team ? `${team.name} (${team.code})` : t('functionUnit.packageTeamNone')
})

// Importing into another team than the unit's current one moves the unit (§8.1); say so before it happens.
const moveHint = computed(() => {
  const current = props.preview?.currentTeam
  const chosen = props.preview?.selectableTeams.find(team => team.code === teamCode.value)
  if (!current || !chosen || chosen.code === current.code) return ''
  return t('functionUnit.teamWillMove', { from: current.name, to: chosen.name })
})

const hint = computed<ImportMessage>(() =>
  props.preview ? importTeamHint(props.preview, teamCode.value) : { key: 'functionUnit.teamRequired', params: {} })

const emitFileChange = (uploadFile: { raw?: File }) => {
  emit('fileChange', uploadFile)
}

const emitFileRemove = () => {
  emit('fileChange', null)
}
</script>

<style scoped>
.import-preview-loading {
  height: 80px;
  margin-top: 12px;
}
.import-preview {
  margin-top: 16px;
}
.import-block {
  margin-top: 12px;
}
.import-team-form {
  margin-top: 12px;
}
.import-team-select {
  width: 100%;
}
.import-team-code {
  float: right;
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.import-team-move {
  margin-top: 4px;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.5;
}
.import-team-hint {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
