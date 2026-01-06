<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">管理员中心</h2>
      
      <!-- 测试用户快速选择 (仅开发环境) -->
      <div v-if="isDev" class="test-user-section">
        <el-divider content-position="center">
          <span class="test-user-label">测试用户快速登录</span>
        </el-divider>
        <el-select 
          v-model="selectedTestUser" 
          placeholder="选择测试用户" 
          @change="onTestUserSelect"
          style="width: 100%; margin-bottom: 16px;"
        >
          <el-option
            v-for="user in testUsers"
            :key="user.username"
            :label="`${user.name} (${user.role})`"
            :value="user.username"
          >
            <div class="test-user-option">
              <span class="user-name">{{ user.name }}</span>
              <el-tag size="small" :type="user.tagType">{{ user.role }}</el-tag>
            </div>
          </el-option>
        </el-select>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 开发环境检测
const isDev = import.meta.env.DEV

// 测试用户数据 (仅开发环境使用)
const testUsers = [
  { username: 'super_admin', password: 'admin123', name: '超级管理员', role: '超级管理员', tagType: 'danger' as const },
  { username: 'system_admin', password: 'admin123', name: '系统管理员', role: '系统管理员', tagType: 'warning' as const },
  { username: 'tenant_admin', password: 'admin123', name: '租户管理员', role: '租户管理员', tagType: 'success' as const },
  { username: 'auditor', password: 'admin123', name: '审计员', role: '审计员', tagType: 'info' as const },
]

const selectedTestUser = ref('')

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 选择测试用户时自动填充
const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`已选择: ${user.name}`)
  }
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    // Mock login
    localStorage.setItem('token', 'mock-token')
    localStorage.setItem('userId', form.username)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 20px;
}

.login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #303133;
}

.test-user-section {
  margin-bottom: 10px;
}

.test-user-label {
  font-size: 12px;
  color: #909399;
}

.test-user-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.user-name {
  font-size: 14px;
}
</style>
