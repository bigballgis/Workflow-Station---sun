<template>
  <div class="file-preview-text-wrap">
    <p
      v-if="truncated"
      class="file-preview-text-note"
    >
      {{ t('filePreview.truncatedText', { limit: TEXT_CHAR_LIMIT }) }}
    </p>
    <pre class="file-preview-text">{{ text }}</pre>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { decodeTextPreview, extractDocPreviewText, TEXT_CHAR_LIMIT } from '@/utils/filePreview'

const props = defineProps<{ blob: Blob; mode: 'text' | 'doc' }>()
const emit = defineEmits<{ error: [] }>()
const { t } = useI18n()
const text = ref('')
const truncated = ref(false)

async function loadText() {
  try {
    const buffer = await props.blob.arrayBuffer()
    const parsed = props.mode === 'doc'
      ? extractDocPreviewText(buffer)
      : decodeTextPreview(buffer)
    text.value = parsed.text
    truncated.value = parsed.truncated
  } catch {
    emit('error')
  }
}

watch(() => [props.blob, props.mode] as const, () => { void loadText() }, { immediate: true })
</script>

<style scoped>
.file-preview-text-wrap {
  width: 100%;
  height: var(--file-preview-pane-height, 72vh);
  display: flex;
  flex-direction: column;
  background: #fff;
}
.file-preview-text-note {
  margin: 0;
  padding: 8px 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.file-preview-text {
  flex: 1;
  margin: 0;
  padding: 16px;
  overflow: auto;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
