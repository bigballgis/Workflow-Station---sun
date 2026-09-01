<template>
  <div class="file-preview-table">
    <div
      v-if="sheetNames.length > 1"
      class="file-preview-table-tabs"
    >
      <el-radio-group
        v-model="active"
        size="small"
      >
        <el-radio-button
          v-for="(name, i) in sheetNames"
          :key="`${name}-${i}`"
          :label="i"
        >
          {{ name }}
        </el-radio-button>
      </el-radio-group>
    </div>
    <p
      v-if="truncated"
      class="file-preview-table-note"
    >
      {{ t('filePreview.truncatedTable', { rows: TABLE_MAX_ROWS, cols: TABLE_MAX_COLS }) }}
    </p>
    <div class="file-preview-table-scroll">
      <table>
        <tbody>
          <tr
            v-for="(row, ri) in currentRows"
            :key="ri"
          >
            <td
              v-for="(cell, ci) in row"
              :key="ci"
            >
              {{ cell }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  parseSpreadsheetPreview,
  TABLE_MAX_COLS,
  TABLE_MAX_ROWS,
} from '@/utils/filePreview'

const props = defineProps<{ blob: Blob }>()
const emit = defineEmits<{ error: [] }>()
const { t } = useI18n()
const sheets = ref<Array<{ name: string; rows: string[][] }>>([])
const truncated = ref(false)
const active = ref(0)

const sheetNames = computed(() => sheets.value.map((s) => s.name))
const currentRows = computed(() => sheets.value[active.value]?.rows ?? [])

async function loadTable() {
  try {
    const parsed = await parseSpreadsheetPreview(await props.blob.arrayBuffer())
    sheets.value = parsed.sheets
    truncated.value = parsed.truncated
    active.value = 0
  } catch {
    emit('error')
  }
}

watch(() => props.blob, () => { void loadTable() }, { immediate: true })
</script>

<style scoped>
.file-preview-table {
  width: 100%;
  height: var(--file-preview-pane-height, 72vh);
  display: flex;
  flex-direction: column;
  background: #fff;
}
.file-preview-table-tabs,
.file-preview-table-note {
  padding: 8px 12px;
}
.file-preview-table-note {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.file-preview-table-scroll {
  flex: 1;
  overflow: auto;
}
table {
  border-collapse: collapse;
  min-width: 100%;
  font-size: 12px;
}
td {
  border: 1px solid var(--el-border-color-lighter);
  padding: 4px 8px;
  white-space: nowrap;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
