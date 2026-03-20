<template>
  <div class="document-editor">
    <!-- AI new version banner -->
    <div v-if="hasNewAiVersion" class="document-editor__banner">
      <span>{{ t('ai.doc.aiNewVersion') }}</span>
      <el-button size="small" type="primary" @click="handleLoadNewVersion">
        {{ t('ai.doc.loadNewVersion') }}
      </el-button>
      <el-button size="small" @click="hasNewAiVersion = false">
        {{ t('ai.doc.keepEditing') }}
      </el-button>
    </div>

    <!-- Textarea editor -->
    <el-input
      v-model="editContent"
      type="textarea"
      :autosize="{ minRows: 10 }"
      resize="vertical"
    />

    <!-- Action buttons -->
    <div class="document-editor__actions">
      <el-button
        type="primary"
        :disabled="!isDirty || saving"
        :loading="saving"
        @click="handleSave"
      >
        {{ t('ai.doc.save') }}
      </el-button>
      <el-button @click="emit('cancel')">
        {{ t('ai.doc.cancel') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { aiGenerationApi } from '@/api/aiGeneration'
import type { AiDocument, AiDocumentType } from '@/types/aiGeneration'

const props = defineProps<{
  content: string
  functionUnitId: number
  documentType: AiDocumentType
}>()

const emit = defineEmits<{
  saved: [doc: AiDocument]
  cancel: []
}>()

const { t } = useI18n()

const editContent = ref(props.content)
const saving = ref(false)
const hasNewAiVersion = ref(false)

const isDirty = computed(() => editContent.value !== props.content)

// Sync editContent when content prop changes (e.g. version switch)
watch(() => props.content, (val) => {
  editContent.value = val
})

async function handleSave() {
  if (!isDirty.value || saving.value) return
  saving.value = true
  try {
    const res = await aiGenerationApi.saveDocument(
      props.functionUnitId, props.documentType, editContent.value
    )
    hasNewAiVersion.value = false
    emit('saved', res.data)
  } catch {
    ElMessage.error(t('ai.doc.saveFailed'))
  } finally {
    saving.value = false
  }
}

function handleLoadNewVersion() {
  // Reset to latest content from prop (parent will have updated it)
  editContent.value = props.content
  hasNewAiVersion.value = false
}

function notifyNewAiVersion() {
  hasNewAiVersion.value = true
}

defineExpose({
  notifyNewAiVersion,
  isDirty
})
</script>

<style lang="scss" scoped>
.document-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.document-editor__banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 4px;
  font-size: 13px;
  color: #e6a23c;
}

.document-editor__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
