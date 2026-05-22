<template>
  <el-dialog
    v-model="visible"
    :title="t('functionUnit.importPackage')"
    width="500px"
    destroy-on-close
  >
    <el-upload
      ref="importUploadRef"
      drag
      :auto-upload="false"
      accept=".zip"
      :limit="1"
      :on-change="emitFileChange"
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
        :loading="importLoading"
        :disabled="!importFile"
        @click="emit('startImport')"
      >
        {{ t('functionUnit.startImport') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadInstance } from 'element-plus'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  importLoading: boolean
  importFile: File | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'fileChange': [uploadFile: unknown]
  'startImport': []
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const importUploadRef = ref<UploadInstance>()
defineExpose({ importUploadRef })

const emitFileChange = (uploadFile: unknown) => {
  emit('fileChange', uploadFile)
}
</script>
