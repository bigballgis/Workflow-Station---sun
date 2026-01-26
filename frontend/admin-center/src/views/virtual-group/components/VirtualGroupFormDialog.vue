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
          <el-option :label="t('virtualGroup.typeSystem')" value="SYSTEM" />
        </el-select>
      </el-form-item>
      <el-form-item 
        :label="t('virtualGroup.boundRole')" 
        prop="roleId"
        :required="form.type === 'SYSTEM'"
      >
        <el-select 
          v-model="form.roleId" 
          :placeholder="t('virtualGroup.selectRolePlaceholder')" 
          filterable 
          style="width: 100%"
          :disabled="isSystemGroup"
        >
          <el-option
            v-for="role in availableRoles"
            :key="role.id"
            :label="`${role.name} (${getRoleTypeLabel(role.type)})`"
            :value="role.id"
          />
        </el-select>
        <div v-if="form.type === 'SYSTEM'" style="color: #909399; font-size: 12px; margin-top: 4px;">
          {{ t('virtualGroup.systemGroupRoleHint') }}
        </div>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'
import { virtualGroupApi } from '@/api/virtualGroup'
import { roleApi, type Role } from '@/api/role'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.group)
const isSystemGroup = computed(() => props.group?.type === 'SYSTEM')
const allRoles = ref<Role[]>([])

const form = reactive({ name: '', code: '', type: 'CUSTOM', roleId: '', adGroup: '', description: '' })

// 只显示业务角色（BU_BOUNDED 或 BU_UNBOUNDED）
const availableRoles = computed(() => {
  return allRoles.value.filter(r => {
    const roleType = r.type as string
    return roleType === 'BU_BOUNDED' || roleType === 'BU_UNBOUNDED' || roleType === 'BUSINESS'
  })
})

const getRoleTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    BU_BOUNDED: t('role.buBounded'),
    BU_UNBOUNDED: t('role.buUnbounded'),
    BUSINESS: t('role.businessRole'),
    ADMIN: t('role.adminRole'),
    DEVELOPER: t('role.developerRole')
  }
  return map[type || ''] || type
}

const fetchRoles = async () => {
  try {
    allRoles.value = await roleApi.list()
  } catch (e) {
    console.error('Failed to fetch roles:', e)
  }
}

onMounted(() => {
  fetchRoles()
})

const rules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  code: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  type: [{ required: true, message: t('common.selectPlaceholder'), trigger: 'change' }],
  roleId: [
    { 
      required: form.type === 'SYSTEM', 
      message: t('virtualGroup.roleRequiredForSystem'), 
      trigger: 'change' 
    }
  ]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.group) {
    Object.assign(form, { 
      name: props.group.name, 
      code: props.group.code, 
      type: props.group.type, 
      roleId: props.group.boundRoleId || '', 
      adGroup: props.group.adGroup || '', 
      description: props.group.description || '' 
    })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'CUSTOM', roleId: '', adGroup: '', description: '' })
    fetchRoles()
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
      adGroup: form.adGroup || undefined,
      roleId: form.roleId || undefined
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
