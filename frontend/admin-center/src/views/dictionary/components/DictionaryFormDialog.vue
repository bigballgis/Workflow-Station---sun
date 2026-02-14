<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('dictionary.editDictTitle') : t('dictionary.createDictTitle')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('dictionary.dictName')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('dictionary.dictCode')" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('dictionary.dictType')" prop="type">
        <el-select v-model="form.type" :disabled="isEdit">
          <el-option :label="t('dictionary.typeSystemDict')" value="SYSTEM" />
          <el-option :label="t('dictionary.typeBusinessDict')" value="BUSINESS" />
          <el-option :label="t('dictionary.typeCustomDict')" value="CUSTOM" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('common.description')">
        <el-input v-model="form.description" type="textarea" :rows="3" />
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

const props = defineProps<{ modelValue: boolean; dictionary: any }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.dictionary)

const form = reactive({ name: '', code: '', type: 'CUSTOM', description: '' })

const rules = computed(() => ({
  name: [{ required: true, message: t('dictionary.inputName'), trigger: 'blur' }],
  code: [{ required: true, message: t('dictionary.inputCode'), trigger: 'blur' }],
  type: [{ required: true, message: t('dictionary.selectType'), trigger: 'change' }]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.dictionary) {
    Object.assign(form, props.dictionary)
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'CUSTOM', description: '' })
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
