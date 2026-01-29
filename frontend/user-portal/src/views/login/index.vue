<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>{{ t('login.title') }}</h1>
        <p>{{ t('login.subtitle') }}</p>
      </div>
      
      <!-- 测试用户快速选择 (仅开发环境) -->
      <div v-if="isDev" class="test-user-section">
        <el-divider content-position="center">
          <span class="test-user-label">{{ t('login.testUserQuickLogin') }}</span>
        </el-divider>
        <el-select 
          v-model="selectedTestUser" 
          :placeholder="t('login.selectTestUser')" 
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
            :placeholder="t('login.username')"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('login.password')"
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
            {{ t('login.login') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login as authLogin, saveTokens, saveUser } from '@/api/auth'
import axios from 'axios'

const { t } = useI18n()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 开发环境检测
const isDev = import.meta.env.DEV

// 测试用户数据 (从数据库动态加载)
interface TestUser {
  username: string
  password: string
  name: string
  role: string
  tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

const testUsers = ref<TestUser[]>([])
const selectedTestUser = ref('')

// 从数据库加载用户列表
const loadTestUsers = async () => {
  if (!isDev) return
  
  try {
    const response = await axios.get('/api/admin-center/users', {
      params: { size: 50, page: 0 }
    })
    
    // 将数据库用户转换为测试用户格式
    const users = response.data?.content || []
    testUsers.value = users.map((user: any, index: number) => {
      // 根据用户名或角色分配标签颜色
      let tagType: TestUser['tagType'] = 'primary'
      const username = user.username.toLowerCase()
      
      if (username.includes('admin') || username.includes('director')) {
        tagType = 'danger'
      } else if (username.includes('manager') || username.includes('lead')) {
        tagType = 'warning'
      } else if (username.includes('reviewer') || username.includes('approver')) {
        tagType = 'success'
      } else if (username.includes('countersign')) {
        tagType = 'info'
      }
      
      return {
        username: user.username,
        password: 'admin123', // 默认测试密码
        name: user.fullName || user.displayName || user.username,
        role: user.email || 'User',
        tagType
      }
    })
  } catch (error) {
    console.error('Failed to load test users:', error)
    // 如果加载失败，使用默认的测试用户列表
    testUsers.value = [
      { username: 'purchase.requester', password: 'admin123', name: 'Tom Wilson', role: 'Initiator (IT-DEV)', tagType: 'primary' },
      { username: 'dept.reviewer', password: 'admin123', name: 'Alice Johnson', role: 'Dept Reviewer (IT-DEV)', tagType: 'success' },
      { username: 'parent.reviewer', password: 'admin123', name: 'Bob Smith', role: 'Senior Approver (IT)', tagType: 'warning' },
      { username: 'finance.reviewer', password: 'admin123', name: 'Carol Davis', role: 'Finance Reviewer', tagType: 'danger' },
      { username: 'countersign.approver1', password: 'admin123', name: 'Daniel Brown', role: 'Countersign Approver', tagType: 'info' },
      { username: 'countersign.approver2', password: 'admin123', name: 'Eva Martinez', role: 'Countersign Approver', tagType: 'info' },
      { username: 'core.lead', password: 'admin123', name: 'Kevin Huang', role: 'Entity Manager', tagType: 'warning' },
      { username: 'tech.director', password: 'admin123', name: 'Robert Sun', role: 'Function Manager', tagType: 'danger' },
    ]
  }
}

// 组件挂载时加载用户列表
onMounted(() => {
  loadTestUsers()
})

const form = reactive({
  username: '',
  password: ''
})

const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }]
}))

// 选择测试用户时自动填充
const onTestUserSelect = (username: string) => {
  const user = testUsers.value.find((u: TestUser) => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`${t('login.selected')}: ${user.name}`)
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
        
        ElMessage.success(t('login.loginSuccess'))
        router.push('/dashboard')
      } catch (error: any) {
        const message = error.response?.data?.message || error.message || t('login.loginFailed')
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
  position: relative;
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
