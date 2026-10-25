<script setup lang="ts">
import { ref, watch } from 'vue'
import { useDesignerDraft, confirmDesignerLeave } from '@/composables/useDesignerDraft'
import { useI18n } from 'vue-i18n'
import MainTableViewFilterGroup from './MainTableViewFilterGroup.vue'
import {
  type FilterConfig,
  type FilterFieldOption,
  type FilterGroupEditorNode,
  parseFilterConfigToEditorRoot,
  serializeFilterEditorRoot,
} from '@/utils/mainTableViewFilter'

const props = defineProps<{
  modelValue: boolean
  filterConfig?: FilterConfig | null
  fieldOptions: FilterFieldOption[]
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  save: [config: FilterConfig]
}>()

const { t } = useI18n()

const rootGroup = ref<FilterGroupEditorNode>(parseFilterConfigToEditorRoot(null))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    rootGroup.value = parseFilterConfigToEditorRoot(props.filterConfig)
    draft.markSaved()
  },
)

async function closeDialog() {
  if (await confirmDesignerLeave(draft, t)) emit('update:modelValue', false)
}

function confirmDialog() {
  emit('save', serializeFilterEditorRoot(rootGroup.value))
  emit('update:modelValue', false)
}

const draft = useDesignerDraft({
  snapshot: () => JSON.stringify(serializeFilterEditorRoot(rootGroup.value)), enabled: () => props.modelValue,
  save: async () => { confirmDialog(); return true },
  restore: (snapshot) => {
    rootGroup.value = parseFilterConfigToEditorRoot(JSON.parse(snapshot))
    emit('update:modelValue', false)
  },
})
defineExpose(draft)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :before-close="closeDialog"
    :title="title ?? t('mainTableView.editFiltersTitle')"
    width="760px"
    destroy-on-close
    class="mtv-filter-dialog"
    @update:model-value="(open: boolean) => { if (!open) closeDialog() }"
  >
    <div class="filter-editor-root">
      <MainTableViewFilterGroup
        :group="rootGroup"
        :depth="0"
        :field-options="fieldOptions"
      />
    </div>

    <template #footer>
      <el-button @click="closeDialog">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="confirmDialog"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.filter-editor-root {
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 0;
}
</style>
