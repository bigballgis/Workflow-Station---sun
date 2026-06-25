<template>
  <div class="fk-editor">
    <el-checkbox
      v-model="fkEnabled"
      :disabled="disabled"
      @change="onFkToggle"
    />
    <el-tooltip
      v-if="fkEnabled"
      :content="summaryLabel"
      placement="top"
      :show-after="400"
    >
      <el-popover
        :width="320"
        trigger="click"
        placement="bottom-end"
        popper-class="fk-editor-popover"
      >
        <template #reference>
          <el-button
            size="small"
            circle
            class="fk-config-btn"
            :type="isConfigured ? 'primary' : 'warning'"
            plain
          >
            <el-icon><Link /></el-icon>
          </el-button>
        </template>
        <div class="fk-popover-body">
          <div class="fk-popover-title">
            {{ t('form.fkSettings') }}
          </div>
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('form.fkRefTable')">
              <el-select
                v-model="localRefTableId"
                clearable
                filterable
                style="width: 100%;"
                :placeholder="t('form.fkRefTable')"
                @change="onRefTableChange"
              >
                <el-option
                  v-for="tb in refTables"
                  :key="tb.id"
                  :label="tb.displayName || tb.tableName"
                  :value="tb.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('form.fkRefPkFields')">
              <el-select
                v-model="localRefPkFields"
                multiple
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%;"
                :disabled="!localRefTableId"
                :placeholder="t('form.fkRefPkFields')"
                @change="emitChange"
              >
                <el-option
                  v-for="f in refPkFieldOptions"
                  :key="f.fieldName"
                  :label="f.displayName || f.fieldName"
                  :value="f.fieldName"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </el-popover>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Link } from '@element-plus/icons-vue'
import type { FieldDefinitionResponse, RelationTableResponse } from '@/api/relationTable'

const props = defineProps<{
  isForeignKey?: boolean
  refTableId?: number | null
  refPrimaryKeyFields?: string[]
  refTables: RelationTableResponse[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:isForeignKey': [value: boolean]
  'update:refTableId': [value: number | undefined]
  'update:refPrimaryKeyFields': [value: string[]]
}>()

const { t } = useI18n()

const fkEnabled = ref(!!props.isForeignKey)
const localRefTableId = ref<number | undefined>(props.refTableId ?? undefined)
const localRefPkFields = ref<string[]>([...(props.refPrimaryKeyFields ?? [])])

watch(
  () => props.isForeignKey,
  (v) => { fkEnabled.value = !!v },
)

watch(
  () => props.refTableId,
  (v) => { localRefTableId.value = v ?? undefined },
)

watch(
  () => props.refPrimaryKeyFields,
  (v) => { localRefPkFields.value = [...(v ?? [])] },
)

const refPkFieldOptions = computed<FieldDefinitionResponse[]>(() => {
  const table = props.refTables.find(tb => tb.id === localRefTableId.value)
  return (table?.fieldDefinitions ?? []).filter(f => f.isPrimaryKey && f.fieldName?.trim())
})

const isConfigured = computed(
  () => !!localRefTableId.value && localRefPkFields.value.length > 0,
)

const summaryLabel = computed(() => {
  if (!localRefTableId.value) return t('form.fkConfigure')
  const table = props.refTables.find(tb => tb.id === localRefTableId.value)
  const name = table?.displayName || table?.tableName || '?'
  if (!localRefPkFields.value.length) return name
  return `${name} · ${localRefPkFields.value.join(', ')}`
})

function resolvePrimaryKeyFields(tableId: number | undefined): string[] {
  if (!tableId) return []
  const table = props.refTables.find(tb => tb.id === tableId)
  if (!table?.fieldDefinitions?.length) return []
  return table.fieldDefinitions
    .filter(f => f.isPrimaryKey && f.fieldName?.trim())
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map(f => f.fieldName)
}

function onFkToggle(val: boolean | string | number) {
  const on = val === true
  emit('update:isForeignKey', on)
  if (!on) {
    localRefTableId.value = undefined
    localRefPkFields.value = []
    emit('update:refTableId', undefined)
    emit('update:refPrimaryKeyFields', [])
  }
}

function onRefTableChange() {
  emit('update:refTableId', localRefTableId.value)
  const pkFields = resolvePrimaryKeyFields(localRefTableId.value)
  localRefPkFields.value = pkFields
  emit('update:refPrimaryKeyFields', [...pkFields])
}

function emitChange() {
  emit('update:refPrimaryKeyFields', [...localRefPkFields.value])
}
</script>

<style scoped>
.fk-editor {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: nowrap;
}
.fk-config-btn {
  width: 24px;
  height: 24px;
  padding: 0;
}
.fk-popover-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
</style>
