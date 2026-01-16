<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('organization.editBusinessUnit') : t('organization.createBusinessUnit')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('organization.businessUnitName')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('organization.businessUnitCode')" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('organization.parentBusinessUnit')">
        <el-tree-select v-model="form.parentId" :data="orgStore.businessUnitTree" :props="{ label: 'name', children: 'children' }" node-key="id" clearable check-strictly :disabled="!!parent" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="t('common.sort')">
        <el-input-number v-model="form.sortOrder" :min="0" />
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
import { useOrganizationStore } from '@/stores/organization'
import { BusinessUnit } from '@/api/organization'

const props = defineProps<{ modelValue: boolean; businessUnit: BusinessUnit | null; parent: BusinessUnit | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const { t } = useI18n()
const orgStore = useOrganizationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.businessUnit)

const form = reactive({ name: '', code: '', parentId: '', sortOrder: 0 })

const rules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  code: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }]
}))

watch(() => props.modelValue, (val) => {
  if (val) {
    if (props.businessUnit) {
      Object.assign(form, { 
        name: props.businessUnit.name, 
        code: props.businessUnit.code, 
        parentId: props.businessUnit.parentId || '', 
        sortOrder: props.businessUnit.sortOrder 
      })
    } else {
      Object.assign(form, { name: '', code: '', parentId: props.parent?.id || '', sortOrder: 0 })
    }
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await orgStore.updateBusinessUnit(props.businessUnit!.id, { 
        name: form.name, 
        sortOrder: form.sortOrder 
      })
    } else {
      await orgStore.createBusinessUnit({
        name: form.name,
        code: form.code,
        parentId: form.parentId || undefined,
        sortOrder: form.sortOrder
      })
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
