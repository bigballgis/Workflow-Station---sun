<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('virtualGroup.edit') : t('virtualGroup.create')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('common.name')" prop="name">
        <el-input v-model="form.name" :disabled="isSystemGroup" />
      </el-form-item>
      <el-form-item :label="t('common.code')" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('common.type')" prop="type">
        <el-select v-model="form.type" :disabled="isSystemGroup">
          <el-option :label="t('virtualGroup.typeCustom')" value="CUSTOM" />
          <el-option v-if="isSystemGroup" :label="t('virtualGroup.typeSystem')" value="SYSTEM" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('virtualGroup.adGroup')">
        <el-input v-model="form.adGroup" :placeholder="t('virtualGroup.adGroupPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('common.description')">
        <el-input v-model="form.description" type="textarea" :rows="3" :disabled="isSystemGroup" />
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
import { virtualGroupApi } from '@/api/virtualGroup'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.group)
const isSystemGroup = computed(() => props.group?.type === 'SYSTEM')

const form = reactive({ name: '', code: '', type: 'CUSTOM', adGroup: '', description: '' })

const rules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  code: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  type: [{ required: true, message: t('common.selectPlaceholder'), trigger: 'change' }]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.group) {
    Object.assign(form, { name: props.group.name, code: props.group.code, type: props.group.type, adGroup: props.group.adGroup || '', description: props.group.description || '' })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'CUSTOM', adGroup: '', description: '' })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    const data = {
      name: form.name,
      type: form.type as any,
      description: form.description,
      adGroup: form.adGroup || undefined
    }
    if (isEdit.value) {
      await virtualGroupApi.update(props.group.id, data)
    } else {
      await virtualGroupApi.create({ ...data, code: form.code } as any)
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } catch (e) {
    console.error('Failed to save virtual group:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}
</script>
