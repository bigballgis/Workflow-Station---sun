<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? t('user.editUser') : t('user.createUser')" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('user.username')" prop="username">
        <el-input v-model="form.username" :disabled="isEdit" />
      </el-form-item>
      <el-form-item :label="t('user.realName')" prop="realName">
        <el-input v-model="form.realName" />
      </el-form-item>
      <el-form-item :label="t('user.email')" prop="email">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item :label="t('user.phone')" prop="phone">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item :label="t('user.department')" prop="departmentId">
        <el-tree-select v-model="form.departmentId" :data="departmentTree" :props="{ label: 'name', value: 'id' }" clearable check-strictly />
      </el-form-item>
      <el-form-item v-if="!isEdit" label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password />
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
import { useUserStore } from '@/stores/user'
import { useOrganizationStore } from '@/stores/organization'
import { User } from '@/api/user'

const props = defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const { t } = useI18n()
const userStore = useUserStore()
const orgStore = useOrganizationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.user)
const departmentTree = computed(() => orgStore.departmentTree)

const form = reactive({ username: '', realName: '', email: '', phone: '', departmentId: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur', validator: (_: any, v: string, cb: Function) => isEdit.value || v ? cb() : cb(new Error('请输入密码')) }]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    orgStore.fetchTree()
    if (props.user) {
      Object.assign(form, props.user, { password: '' })
    } else {
      Object.assign(form, { username: '', realName: '', email: '', phone: '', departmentId: '', password: '' })
    }
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await userStore.updateUser(props.user!.id, form)
    } else {
      await userStore.createUser({ ...form, activateImmediately: true })
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
