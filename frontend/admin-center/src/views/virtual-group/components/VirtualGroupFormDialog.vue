<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('virtualGroup.edit') : t('virtualGroup.create')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('common.name')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('common.code')" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('common.type')" prop="type">
        <el-select v-model="form.type">
          <el-option :label="t('role.functionRole')" value="PROJECT" />
          <el-option :label="t('role.tempRole')" value="TEMPORARY" />
          <el-option :label="t('role.businessRole')" value="CROSS_DEPT" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('common.validityPeriod')">
        <el-date-picker v-model="form.dateRange" type="daterange" :start-placeholder="t('virtualGroup.startDatePlaceholder')" :end-placeholder="t('virtualGroup.endDatePlaceholder')" />
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

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.group)

const form = reactive({ name: '', code: '', type: 'PROJECT', dateRange: null as any, description: '' })

const rules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  code: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  type: [{ required: true, message: t('common.selectPlaceholder'), trigger: 'change' }]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.group) {
    Object.assign(form, { name: props.group.name, code: props.group.code, type: props.group.type, dateRange: [props.group.validFrom, props.group.validTo], description: props.group.description || '' })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'PROJECT', dateRange: null, description: '' })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  }, 500)
}
</script>
