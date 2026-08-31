<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('form.createFormTitle')"
    width="500px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      :model="createForm"
      label-width="auto"
      label-position="left"
    >
      <el-form-item
        :label="$t('form.formNameLabel')"
        required
      >
        <el-input
          v-model="createForm.formName"
          :placeholder="$t('form.enterFormName')"
        />
      </el-form-item>
      <el-form-item :label="$t('form.formTypeLabel')">
        <div
          v-if="processFormTakenForScene"
          class="form-item-tip"
          style="margin-bottom: 8px;"
        >
          {{ $t('form.processFormLimitHint') }}
        </div>
        <el-select
          v-model="createForm.formType"
          style="width: 100%"
          @change="handleCreateFormTypeChange"
        >
          <el-option
            :label="$t('form.processForm')"
            value="PROCESS"
            :disabled="processFormTakenForScene"
          />
          <el-option
            :label="$t('form.taskForm')"
            value="TASK"
          />
          <el-option
            :label="$t('form.actionForm')"
            value="ACTION"
          />
          <el-option
            :label="$t('form.detailForm')"
            value="DETAIL"
          />
        </el-select>
      </el-form-item>
      <!-- PROCESS / TASK forms render a workflow step, and every step needs both a To Do and a
           My Requests design, so both are created together and the scene picker is redundant. -->
      <el-form-item
        v-if="createsBothScenes"
        :label="$t('form.sceneLabel')"
      >
        <div class="form-item-tip">
          {{ $t('form.pairCreateHint', { suffix: REQUEST_SCENE_SUFFIX }) }}
        </div>
      </el-form-item>
      <!-- DETAIL forms open from a view row rather than a workflow step, so the
           To Do / My Requests split does not apply to them. -->
      <el-form-item
        v-else-if="createForm.formType !== 'DETAIL'"
        :label="$t('form.sceneLabel')"
      >
        <el-radio-group v-model="createForm.scene">
          <el-radio-button value="TASK">
            {{ $t('form.sceneTask') }}
          </el-radio-button>
          <!-- Action forms are opened by a To Do action button; My Requests has no action
               buttons at all, so a My Requests action form could never be opened. Disabled
               with the reason spelled out rather than hidden, so the limit is visible. -->
          <el-radio-button
            value="REQUEST"
            :disabled="createForm.formType === 'ACTION'"
          >
            {{ $t('form.sceneRequest') }}
          </el-radio-button>
        </el-radio-group>
        <div class="form-item-tip">
          {{ createForm.formType === 'ACTION' ? $t('form.actionFormTodoOnlyHint') : $t('form.sceneHint') }}
        </div>
      </el-form-item>
      <el-form-item
        v-if="createForm.formType === 'TASK'"
        :label="$t('form.stageBinding')"
        required
      >
        <el-select
          :model-value="stageIds"
          multiple
          :placeholder="$t('form.stageBindingPlaceholder')"
          style="width: 100%"
          @update:model-value="$emit('update:stageIds', $event)"
        >
          <el-option
            v-for="node in createDialogProcessNodes"
            :key="node.id"
            :label="node.name"
            :value="node.id"
          />
        </el-select>
        <div class="form-item-tip">
          {{ $t('form.stageBindingHint') }}
        </div>
      </el-form-item>
      <el-form-item :label="$t('form.bindTableLabel')">
        <el-select
          v-model="createForm.boundTableId"
          :placeholder="$t('form.selectTableToBind')"
          style="width: 100%"
          clearable
        >
          <el-option
            v-for="table in bindableTables"
            :key="table.id"
            :label="`${table.tableDisplayName || table.tableName} (${tableTypeLabel(table.tableType)})`"
            :value="table.id"
          />
        </el-select>
        <div class="form-item-tip">
          {{ $t('form.bindTableHint') }}
        </div>
      </el-form-item>
      <el-form-item :label="$t('form.descriptionLabel')">
        <el-input
          v-model="createForm.description"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="$emit('confirm')"
      >
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FormType } from '@/api/functionUnit'

const props = defineProps<{
  modelValue: boolean
  createForm: any
  forms: any[]
  tables: any[]
  createDialogProcessNodes: any[]
  stageIds: any[]
  tableTypeLabel: (type: string) => string
  /** Receives the newly selected form type — it pins the scene for types that have only one. */
  handleCreateFormTypeChange: (type: FormType) => void
}>()

/** Mirrors the backend suffix in FormDesignComponentImpl.REQUEST_SCENE_NAME_SUFFIX. */
const REQUEST_SCENE_SUFFIX = ' (My Request)'

/**
 * Step forms are created as a scene pair, so the developer names the step once and designs each
 * scene afterwards. ACTION and DETAIL forms have only one scene each and keep the picker.
 */
const createsBothScenes = computed(
  () => props.createForm.formType === 'PROCESS' || props.createForm.formType === 'TASK',
)

/**
 * DETAIL forms are opened from a view row, and MAIN-table rows open the request detail page
 * instead — so a DETAIL form bound to the MAIN table could never be reached. Offering the
 * table here would create exactly that dead form.
 */
const bindableTables = computed(() => {
  if (props.createForm.formType !== 'DETAIL') return props.tables
  return props.tables.filter(
    (table: any) => String(table?.tableType ?? '').toUpperCase() !== 'MAIN',
  )
})

/**
 * One start form per scene, not per function unit: the New Request form and the
 * read-only one shown in My Requests are separate designs of the same step.
 *
 * <p>A paired create fills both scenes at once, so it needs both slots free — checking only the
 * currently selected scene would let the create through and fail on the second row.
 */
const processFormTakenForScene = computed(() => {
  if (props.createForm.formType !== 'PROCESS') return false
  const taken = (scene: string) =>
    props.forms.some((f: any) => f.formType === 'PROCESS' && (f.scene ?? 'TASK') === scene)
  return createsBothScenes.value
    ? taken('TASK') || taken('REQUEST')
    : taken(props.createForm.scene)
})

defineEmits<{
  'update:modelValue': [value: boolean]
  'update:stageIds': [value: any[]]
  confirm: []
}>()
</script>
