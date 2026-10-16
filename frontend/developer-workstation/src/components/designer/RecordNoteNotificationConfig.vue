<template>
  <div class="note-notification-config" v-loading="loading">
    <div class="note-notification-heading">
      <el-switch :model-value="value.enabled" @update:model-value="setEnabled(Boolean($event))" />
      <DesignerHelpLink path="/form-ctl-record-note#attachment-notifications"
        :ariaLabel="t('recordNoteNotification.guide')" />
    </div>
    <template v-if="value.enabled">
      <el-select :model-value="value.targetNodeKeys" multiple filterable style="width:100%"
        :placeholder="t('recordNoteNotification.targets')" @update:model-value="setTargets">
        <el-option v-for="node in nodes" :key="node.id" :label="node.name" :value="node.id" />
      </el-select>
      <p class="note-notification-hint">{{ t('recordNoteNotification.assignmentHint') }}</p>
      <el-alert v-if="failed" :title="t('recordNoteNotification.loadFailed')" type="error" :closable="false" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { functionUnitApi } from '@/api/functionUnit'
import DesignerHelpLink from './DesignerHelpLink.vue'
import { recordNoteNotificationNodes } from '@/utils/recordNoteNotificationNodes'

interface Policy { enabled: boolean; targetNodeKeys: string[] }
const props = defineProps<{ modelValue?: Policy }>()
const emit = defineEmits<{ 'update:modelValue': [value: Policy] }>()
const { t } = useI18n()
const value = computed<Policy>(() => props.modelValue ?? { enabled: false, targetNodeKeys: [] })
const nodes = ref<Array<{ id: string; name: string }>>([])
const loading = ref(false)
const failed = ref(false)
function setEnabled(enabled: boolean): void { emit('update:modelValue', { ...value.value, enabled }) }
function setTargets(targetNodeKeys: string[]): void { emit('update:modelValue', { ...value.value, targetNodeKeys }) }

onMounted(async () => {
  const element = getCurrentInstance()?.proxy?.$el as HTMLElement | undefined
  const host = element?.closest('[data-function-unit-id]')
  const id = Number(host?.getAttribute('data-function-unit-id'))
  if (!id) { failed.value = true; return }
  loading.value = true
  try {
    const result = await functionUnitApi.getProcess(id)
    const raw = result.data.bpmnXml
    if (!raw) throw new Error('Missing process design')
    const xml = raw.trimStart().startsWith('<') ? raw : new TextDecoder().decode(
      Uint8Array.from(atob(raw), char => char.charCodeAt(0)))
    const document = new DOMParser().parseFromString(xml, 'application/xml')
    if (document.querySelector('parsererror')) throw new Error('Invalid process design')
    nodes.value = recordNoteNotificationNodes(document)
  } catch { failed.value = true } finally { loading.value = false }
})
</script>

<style scoped>
.note-notification-config { min-width: 200px; }
.note-notification-heading { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.note-notification-hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; }
</style>
