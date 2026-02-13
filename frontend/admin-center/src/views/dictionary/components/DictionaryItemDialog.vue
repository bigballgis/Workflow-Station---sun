<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('dictionary.editItem') : t('dictionary.addItemTitle')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('dictionary.displayName')" prop="label">
        <el-input v-model="form.label" />
      </el-form-item>
      <el-form-item :label="t('dictionary.dictValue')" prop="value">
        <el-input v-model="form.value" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('dictionary.dictSort')">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item :label="t('dictionary.dictStatus')">
        <el-switch v-model="form.status" active-value="ACTIVE" inactive-value="INACTIVE" />
      </el-form-item>
      <el-form-item :label="t('dictionary.translations')">
        <el-tabs type="border-card">
          <el-tab-pane :label="t('dictionary.zhCN')">
            <el-input v-model="form.translations['zh-CN']" />
          </el-tab-pane>
          <el-tab-pane :label="t('dictionary.zhTW')">
            <el-input v-model="form.translations['zh-TW']" />
          </el-tab-pane>
          <el-tab-pane label="English">
            <el-input v-model="form.translations['en']" />
          </el-tab-pane>
        </el-tabs>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; item: any; parent: any; dictionaryId: string | undefined }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.item)

const form = reactive({ label: '', value: '', sortOrder: 0, status: 'ACTIVE', translations: { 'zh-CN': '', 'zh-TW': '', 'en': '' } })

const rules = computed(() => ({
  label: [{ required: true, message: t('dictionary.inputDisplayName'), trigger: 'blur' }],
  value: [{ required: true, message: t('dictionary.inputValue'), trigger: 'blur' }]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.item) {
    Object.assign(form, { ...props.item, translations: props.item.translations || { 'zh-CN': '', 'zh-TW': '', 'en': '' } })
  } else if (val) {
    Object.assign(form, { label: '', value: '', sortOrder: 0, status: 'ACTIVE', translations: { 'zh-CN': '', 'zh-TW': '', 'en': '' } })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success(t('dictionary.operationSuccess'))
    emit('update:modelValue', false)
    emit('success')
  }, 500)
}
</script>
