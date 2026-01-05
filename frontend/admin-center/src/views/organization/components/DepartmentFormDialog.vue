<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('organization.editDepartment') : t('organization.createDepartment')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('organization.departmentName')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('organization.departmentCode')" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('organization.parentDepartment')">
        <el-tree-select v-model="form.parentId" :data="orgStore.departmentTree" :props="{ label: 'name', value: 'id' }" clearable check-strictly :disabled="!!parent" />
      </el-form-item>
      <el-form-item :label="t('organization.leader')">
        <el-select v-model="form.leaderId" clearable filterable>
          <el-option v-for="user in users" :key="user.id" :label="user.realName" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
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
import { Department } from '@/api/organization'

const props = defineProps<{ modelValue: boolean; department: Department | null; parent: Department | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const { t } = useI18n()
const orgStore = useOrganizationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const users = ref<any[]>([])
const isEdit = computed(() => !!props.department)

const form = reactive({ name: '', code: '', parentId: '', leaderId: '', sortOrder: 0 })

const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    if (props.department) {
      Object.assign(form, { name: props.department.name, code: props.department.code, parentId: props.department.parentId || '', leaderId: props.department.leaderId || '', sortOrder: props.department.sortOrder })
    } else {
      Object.assign(form, { name: '', code: '', parentId: props.parent?.id || '', leaderId: '', sortOrder: 0 })
    }
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await orgStore.updateDepartment(props.department!.id, { name: form.name, leaderId: form.leaderId || undefined, sortOrder: form.sortOrder })
    } else {
      await orgStore.createDepartment(form)
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
