<template>
  <!-- Backdrop on body: nested under Form Preview dialog; ancestor transform breaks fixed overlays -->
  <Teleport to="body">
    <div
      v-if="visible"
      class="sub-table-form-dialog-backdrop"
      role="presentation"
      aria-hidden="true"
      :style="{ zIndex: backdropZIndex }"
      @click="handleClose"
    />
  </Teleport>
  <el-dialog
    :model-value="visible"
    :title="title || (mode === 'edit' ? t('common.edit') : t('common.add'))"
    width="700px"
    :close-on-click-modal="false"
    :modal="false"
    append-to-body
    :z-index="dialogZIndex"
    @update:model-value="handleClose"
    @closed="handleClosed"
  >
    <!-- Sub-table form preview (form-create based on designed rule) -->
    <div
      v-if="formRule && formRule.length"
      class="sub-table-form-preview"
    >
      <form-create
        v-if="formCreateMounted"
        v-model="formData"
        locale="en"
        :rule="formRule"
        :option="formOption"
      />
      <div
        v-else
        class="form-loading"
      >
        <el-icon class="is-loading">
          <Loading />
        </el-icon>
        <span>{{ t('common.loading') }}...</span>
      </div>
    </div>

    <!-- Fallback: if no rule defined, show message -->
    <el-empty
      v-else
      :description="t('subTable.noFormDesign')"
      :image-size="60"
    />

    <template #footer>
      <el-button @click="handleClose">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="handleSave"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'

export interface SubTableFormDialogProps {
  visible: boolean
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  /** Form-create rule from the sub-table form designer */
  rule?: any[]
  /** Form-create option from the sub-table form designer */
  option?: any
}

const props = withDefaults(defineProps<SubTableFormDialogProps>(), {
  mode: 'add',
  rule: () => [],
  option: () => ({}),
})

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

const { t } = useI18n()

const formData = ref<Record<string, any>>({})
const formCreateMounted = ref(false)

/** Stack above Form Preview / other open el-dialog overlays (see user-portal SubTableAddDialog). */
const NESTED_DIALOG_Z = 3010
const dialogZIndex = ref(NESTED_DIALOG_Z)
const backdropZIndex = computed(() => dialogZIndex.value - 1)

function refreshDialogZIndex() {
  let maxZ = 2000
  document.querySelectorAll('.el-overlay').forEach((el) => {
    const z = Number.parseInt(window.getComputedStyle(el).zIndex || '0', 10)
    if (z > maxZ) maxZ = z
  })
  dialogZIndex.value = Math.max(NESTED_DIALOG_Z, maxZ + 10)
}

// Default form-create option for sub-table forms
const defaultFormOption = {
  resetBtn: false,
  submitBtn: false,
  showMsg: true,
  form: {
    labelPosition: 'left',
    labelWidth: '140px',
  },
  language: {
    en: {
      clickToUpload: t('form.clickToUpload'),
    },
  },
  onSubmit: () => {}, // We handle save manually
}

function buildDialogFormOption(option: Record<string, any> = {}) {
  const { title: _dropTitle, ...rest } = option || {}
  return {
    ...defaultFormOption,
    ...rest,
    resetBtn: false,
    submitBtn: false,
    onSubmit: () => {},
  }
}

const formOption = ref(buildDialogFormOption(props.option))

// Watch for dialog open/close and initialData changes
watch(
  () => [props.visible, props.initialData],
  ([open, data]) => {
    if (open) {
      refreshDialogZIndex()
      formCreateMounted.value = false
      // Reset form data
      if (props.mode === 'edit' && data) {
        formData.value = { ...data }
      } else {
        formData.value = {}
      }
      // Update option if provided
      formOption.value = buildDialogFormOption(props.option)
      // Mount form-create after dialog opens
      nextTick(() => {
        formCreateMounted.value = true
      })
    }
  },
  { immediate: true }
)

// Watch rule changes
watch(
  () => props.rule,
  (rule) => {
    // Rule is reactive, form-create should update automatically
  }
)

function handleClose() {
  emit('update:visible', false)
}

function handleClosed() {
  formCreateMounted.value = false
  formData.value = {}
}

function handleSave() {
  // Get the form-create instance and validate
  // Since we're using v-model and rule are reactive, we can directly validate
  // For simplicity, just emit the current form data
  emit('save', { ...formData.value })
  emit('update:visible', false)
}

// Computed form rule (reactive to prop changes)
const formRule = ref<any[]>([])
watch(
  () => props.rule,
  (rule) => {
    formRule.value = rule || []
  },
  { immediate: true, deep: true }
)
</script>

<style>
.sub-table-form-dialog-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
}
</style>

<style scoped>
.sub-table-form-preview {
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 200px;
  color: #909399;
}
</style>
