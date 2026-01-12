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
        <el-tree-select v-model="form.parentId" :data="orgStore.departmentTree" :props="{ label: 'name', children: 'children' }" node-key="id" clearable check-strictly :disabled="!!parent" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="t('organization.leader')">
        <el-select 
          v-model="form.managerId" 
          clearable 
          filterable 
          remote 
          :remote-method="searchUsers"
          :loading="userSearchLoading"
          placeholder="搜索并选择部门主经理"
          style="width: 100%"
        >
          <el-option v-for="user in userOptions" :key="user.id" :label="`${user.fullName} (${user.username})`" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="副经理">
        <el-select 
          v-model="form.secondaryManagerId" 
          clearable 
          filterable 
          remote 
          :remote-method="searchUsers"
          :loading="userSearchLoading"
          placeholder="搜索并选择部门副经理"
          style="width: 100%"
        >
          <el-option v-for="user in userOptions" :key="user.id" :label="`${user.fullName} (${user.username})`" :value="user.id" />
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
import { userApi } from '@/api/user'

const props = defineProps<{ modelValue: boolean; department: Department | null; parent: Department | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const { t } = useI18n()
const orgStore = useOrganizationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const userSearchLoading = ref(false)
const userOptions = ref<{ id: string; fullName: string; username: string }[]>([])
const isEdit = computed(() => !!props.department)

const form = reactive({ name: '', code: '', parentId: '', managerId: '', secondaryManagerId: '', sortOrder: 0 })

const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    loadDefaultUsers() // 加载默认用户列表
    if (props.department) {
      Object.assign(form, { 
        name: props.department.name, 
        code: props.department.code, 
        parentId: props.department.parentId || '', 
        managerId: props.department.managerId || '', 
        secondaryManagerId: props.department.secondaryManagerId || '',
        sortOrder: props.department.sortOrder 
      })
      loadSelectedManagers()
    } else {
      Object.assign(form, { name: '', code: '', parentId: props.parent?.id || '', managerId: '', secondaryManagerId: '', sortOrder: 0 })
    }
  }
})

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

const loadSelectedManagers = async () => {
  const managerIds = [form.managerId, form.secondaryManagerId].filter(Boolean)
  if (managerIds.length === 0) return
  
  try {
    const managers: { id: string; fullName: string; username: string }[] = []
    for (const id of managerIds) {
      const user = await userApi.getById(id)
      if (user) {
        managers.push({ id: user.id, fullName: user.fullName, username: user.username })
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
      await orgStore.updateDepartment(props.department!.id, { 
        name: form.name, 
        managerId: form.managerId || undefined, 
        secondaryManagerId: form.secondaryManagerId || undefined,
        sortOrder: form.sortOrder 
      })
    } else {
      await orgStore.createDepartment({
        name: form.name,
        code: form.code,
        parentId: form.parentId || undefined,
        managerId: form.managerId || undefined,
        secondaryManagerId: form.secondaryManagerId || undefined,
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
