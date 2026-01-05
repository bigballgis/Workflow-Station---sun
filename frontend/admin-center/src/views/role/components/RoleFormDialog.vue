<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('role.editRole') : t('role.createRole')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('role.roleName')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('role.roleCode')" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('role.roleType')" prop="type">
        <el-select v-model="form.type" :disabled="isEdit">
          <el-option :label="t('role.systemRole')" value="SYSTEM" />
          <el-option :label="t('role.businessRole')" value="BUSINESS" />
          <el-option :label="t('role.functionRole')" value="FUNCTION" />
          <el-option :label="t('role.tempRole')" value="TEMPORARY" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('role.description')">
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
import { useRoleStore } from '@/stores/role'
import { Role } from '@/api/role'

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const { t } = useI18n()
const roleStore = useRoleStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.role)

const form = reactive({ name: '', code: '', type: 'BUSINESS', description: '' })

const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择角色类型', trigger: 'change' }]
}

watch(() => props.modelValue, (val) => {
  if (val && props.role) {
    Object.assign(form, { name: props.role.name, code: props.role.code, type: props.role.type, description: props.role.description || '' })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'BUSINESS', description: '' })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await roleStore.updateRole(props.role!.id, { name: form.name, description: form.description })
    } else {
      await roleStore.createRole(form)
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
