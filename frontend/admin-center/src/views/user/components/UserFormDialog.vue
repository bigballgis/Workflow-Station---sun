<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    :title="isEdit ? t('user.editUser') : t('user.createUser')" 
    width="560px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('user.username')" prop="username">
        <el-input v-model="form.username" :disabled="isEdit" :placeholder="t('user.usernamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('user.fullName')" prop="fullName">
        <el-input v-model="form.fullName" :placeholder="t('user.fullNamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('user.email')" prop="email">
        <el-input v-model="form.email" :placeholder="t('user.emailPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('user.employeeId')" prop="employeeId">
        <el-input v-model="form.employeeId" :placeholder="t('user.employeeIdPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('user.position')" prop="position">
        <el-input v-model="form.position" :placeholder="t('user.positionPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('user.entityManager')" prop="entityManagerId">
        <el-select 
          v-model="form.entityManagerId" 
          filterable 
          remote 
          :remote-method="searchUsers"
          clearable 
          :placeholder="t('user.entityManagerPlaceholder')"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option 
            v-for="user in userOptions" 
            :key="user.id" 
            :label="`${user.fullName} (${user.username})`" 
            :value="user.id" 
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('user.functionManager')" prop="functionManagerId">
        <el-select 
          v-model="form.functionManagerId" 
          filterable 
          remote 
          :remote-method="searchUsers"
          clearable 
          :placeholder="t('user.functionManagerPlaceholder')"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option 
            v-for="user in userOptions" 
            :key="user.id" 
            :label="`${user.fullName} (${user.username})`" 
            :value="user.id" 
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!isEdit" :label="t('user.initialPassword')" prop="initialPassword">
        <el-input v-model="form.initialPassword" type="password" show-password :placeholder="t('user.initialPasswordPlaceholder')" />
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi, type User } from '@/api/user'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const userSearchLoading = ref(false)
const userOptions = ref<{ id: string; fullName: string; username: string }[]>([])
const isEdit = computed(() => !!props.user)

const form = reactive({
  username: '',
  fullName: '',
  email: '',
  employeeId: '',
  position: '',
  entityManagerId: '',
  functionManagerId: '',
  initialPassword: ''
})

const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: t('user.usernamePlaceholder'), trigger: 'blur' },
    { min: 3, max: 50, message: t('user.usernamePlaceholder'), trigger: 'blur' }
  ],
  fullName: [{ required: true, message: t('user.fullNamePlaceholder'), trigger: 'blur' }],
  email: [
    { required: true, message: t('user.emailPlaceholder'), trigger: 'blur' },
    { type: 'email', message: t('user.emailPlaceholder'), trigger: 'blur' }
  ],
  initialPassword: [
    { required: true, message: t('user.initialPasswordPlaceholder'), trigger: 'blur' },
    { min: 8, message: t('user.initialPasswordPlaceholder'), trigger: 'blur' }
  ]
}))

watch(() => props.modelValue, (val) => {
  if (val) {
    loadDefaultUsers() // 加载默认用户列表
    if (props.user) {
      Object.assign(form, {
        username: props.user.username,
        fullName: props.user.fullName,
        email: props.user.email,
        employeeId: props.user.employeeId || '',
        position: props.user.position || '',
        entityManagerId: (props.user as any).entityManagerId || '',
        functionManagerId: (props.user as any).functionManagerId || '',
        initialPassword: ''
      })
      // 加载已选管理者信息
      loadSelectedManagers()
    } else {
      Object.assign(form, {
        username: '', fullName: '', email: '',
        employeeId: '', position: '',
        entityManagerId: '', functionManagerId: '',
        initialPassword: ''
      })
    }
  }
})

const loadSelectedManagers = async () => {
  const managerIds = [form.entityManagerId, form.functionManagerId].filter(Boolean)
  if (managerIds.length === 0) return
  
  try {
    const managers: { id: string; fullName: string; username: string }[] = []
    for (const id of managerIds) {
      const user = await userApi.getById(id)
      if (user) {
        managers.push({
          id: user.id,
          fullName: user.fullName,
          username: user.username
        })
      }
    }
    // 合并已选管理者到选项列表（去重）
    const existingIds = new Set(userOptions.value.map(u => u.id))
    for (const mgr of managers) {
      if (!existingIds.has(mgr.id)) {
        userOptions.value.push(mgr)
      }
    }
  } catch (error) {
    console.error('Failed to load managers:', error)
  }
}

// 加载默认用户列表（前3个）
const loadDefaultUsers = async () => {
  userSearchLoading.value = true
  try {
    const res = await userApi.list({ page: 0, size: 3 })
    userOptions.value = (res.content || []).map((u) => ({
      id: u.id,
      fullName: u.fullName,
      username: u.username
    }))
  } catch (error) {
    console.error('Failed to load default users:', error)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

const searchUsers = async (query: string) => {
  // 如果没有输入，显示默认的前3个用户
  if (!query) {
    await loadDefaultUsers()
    return
  }
  
  userSearchLoading.value = true
  try {
    const res = await userApi.list({ keyword: query, page: 0, size: 20 })
    userOptions.value = (res.content || []).map((u) => ({
      id: u.id,
      fullName: u.fullName,
      username: u.username
    }))
  } catch (error) {
    console.error('Failed to search users:', error)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await userApi.update(props.user!.id, {
        fullName: form.fullName,
        email: form.email,
        employeeId: form.employeeId || undefined,
        position: form.position || undefined,
        entityManagerId: form.entityManagerId || undefined,
        functionManagerId: form.functionManagerId || undefined
      })
    } else {
      await userApi.create({
        username: form.username,
        fullName: form.fullName,
        email: form.email,
        employeeId: form.employeeId || undefined,
        position: form.position || undefined,
        entityManagerId: form.entityManagerId || undefined,
        functionManagerId: form.functionManagerId || undefined,
        initialPassword: form.initialPassword
      })
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || t('common.failed'))
  } finally {
    loading.value = false
  }
}
</script>
