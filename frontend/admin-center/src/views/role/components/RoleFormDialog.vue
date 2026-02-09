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
        <el-select v-model="form.type" :placeholder="t('role.selectRoleType')" style="width: 100%">
          <el-option :label="t('role.buBounded')" value="BU_BOUNDED">
            <span>{{ t('role.buBounded') }}</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">{{ t('role.buBoundedDesc') }}</span>
          </el-option>
          <el-option :label="t('role.buUnbounded')" value="BU_UNBOUNDED">
            <span>{{ t('role.buUnbounded') }}</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">{{ t('role.buUnboundedDesc') }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="isEdit" :label="t('common.status')" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio label="ACTIVE">{{ t('common.enabled') }}</el-radio>
          <el-radio label="INACTIVE">{{ t('common.disabled') }}</el-radio>
        </el-radio-group>
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

const form = reactive({ name: '', code: '', type: 'BU_BOUNDED', description: '', status: 'ACTIVE' })

const rules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  code: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  type: [{ required: true, message: t('role.selectRoleType'), trigger: 'change' }]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.role) {
    Object.assign(form, { 
      name: props.role.name, 
      code: props.role.code, 
      type: props.role.type || 'BU_BOUNDED',
      description: props.role.description || '',
      status: props.role.status || 'ACTIVE'
    })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'BU_BOUNDED', description: '', status: 'ACTIVE' })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await roleStore.updateRole(props.role!.id, { 
        name: form.name, 
        type: form.type, 
        description: form.description,
        status: form.status
      })
    } else {
      await roleStore.createRole({ ...form })
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
