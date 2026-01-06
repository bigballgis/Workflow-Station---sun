<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>工作流平台</h1>
        <p>用户门户</p>
      </div>
      
      <!-- 测试用户快速选择 (仅开发环境) -->
      <div v-if="isDev" class="test-user-section">
        <el-divider content-position="center">
          <span class="test-user-label">测试用户快速登录</span>
        </el-divider>
        <el-select 
          v-model="selectedTestUser" 
          placeholder="选择测试用户" 
          @change="onTestUserSelect"
          size="large"
          class="test-user-select"
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

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login as authLogin, saveTokens, saveUser } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 开发环境检测
const isDev = import.meta.env.DEV

// 测试用户数据 (仅开发环境使用)
const testUsers = [
  { username: 'manager', password: 'user123', name: '部门经理', role: '经理', tagType: 'danger' as const },
  { username: 'team_lead', password: 'user123', name: '团队主管', role: '主管', tagType: 'warning' as const },
  { username: 'employee_a', password: 'user123', name: '员工张三', role: '员工', tagType: 'success' as const },
  { username: 'employee_b', password: 'user123', name: '员工李四', role: '员工', tagType: 'success' as const },
  { username: 'hr_staff', password: 'user123', name: 'HR专员', role: 'HR', tagType: 'info' as const },
  { username: 'finance', password: 'user123', name: '财务人员', role: '财务', tagType: 'primary' as const },
]

const selectedTestUser = ref('')

const form = reactive({
  username: '',
  password: ''
})

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
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // Call real login API
        const response = await authLogin({
          username: form.username,
          password: form.password
        })
        
        // Save tokens and user info
        saveTokens(response.accessToken, response.refreshToken)
        saveUser(response.user)
        localStorage.setItem('userId', response.user.userId)
        
        ElMessage.success('登录成功')
        router.push('/dashboard')
      } catch (error: any) {
        const message = error.response?.data?.message || error.message || '登录失败'
        ElMessage.error(message)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, var(--hsbc-red) 0%, #8B0000 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
  
  h1 {
    font-size: 28px;
    color: var(--hsbc-red);
    margin: 0 0 8px 0;
  }
  
  p {
    font-size: 14px;
    color: var(--text-secondary);
    margin: 0;
  }
}

.login-form {
  .login-btn {
    width: 100%;
  }
}

.test-user-section {
  margin-bottom: 10px;
}

.test-user-label {
  font-size: 12px;
  color: #909399;
}

.test-user-select {
  width: 100%;
  margin-bottom: 16px;
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
