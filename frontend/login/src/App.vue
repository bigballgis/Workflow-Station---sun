<template>
  <div class="wrap">
    <div class="card">
      <h1>Workflow Platform</h1>
      <p class="sub">统一登录</p>
      <form @submit.prevent="onSubmit">
        <label>用户名</label>
        <input v-model="username" type="text" autocomplete="username" required />
        <label>密码</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
        <p v-if="error" class="err">{{ error }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

let clientId = ''
let redirectUri = ''
let state = ''

onMounted(() => {
  const q = new URLSearchParams(window.location.search)
  clientId = q.get('client_id') || ''
  redirectUri = q.get('redirect_uri') || ''
  state = q.get('state') || ''
  if (!clientId || !redirectUri) {
    error.value = '缺少 client_id 或 redirect_uri 参数'
  }
})

async function onSubmit() {
  error.value = ''
  if (!clientId || !redirectUri) return
  loading.value = true
  try {
    const res = await fetch('/api/v1/admin/sso/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: username.value,
        password: password.value,
        clientId,
        redirectUri,
        state: state || undefined
      })
    })
    if (!res.ok) {
      error.value = '用户名或密码错误'
      return
    }
    const data = (await res.json()) as {
      authorizationCode?: string
      state?: string
      redirectUri?: string
    }
    const code = data.authorizationCode
    if (!code || !data.redirectUri) {
      error.value = '登录响应无效'
      return
    }
    const u = new URL(data.redirectUri)
    u.searchParams.set('code', code)
    if (data.state) u.searchParams.set('state', data.state)
    window.location.href = u.toString()
  } catch {
    error.value = '网络错误'
  } finally {
    loading.value = false
  }
}
</script>

<style>
.wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #60050a 0%, #d82028 100%);
  font-family: system-ui, sans-serif;
}
.card {
  background: #fff;
  padding: 2rem 2.5rem;
  border-radius: 12px;
  width: 100%;
  max-width: 380px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}
h1 {
  margin: 0 0 0.25rem;
  font-size: 1.35rem;
  color: #2f2f2f;
}
.sub {
  margin: 0 0 1.5rem;
  color: #666;
  font-size: 0.9rem;
}
label {
  display: block;
  margin: 0.75rem 0 0.35rem;
  font-size: 0.85rem;
  color: #444;
  font-weight: 600;
}
input {
  width: 100%;
  padding: 0.65rem 0.75rem;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  font-size: 1rem;
  box-sizing: border-box;
}
button {
  width: 100%;
  margin-top: 1.25rem;
  padding: 0.75rem;
  background: #c60c12;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
}
button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.err {
  color: #c60c12;
  font-size: 0.85rem;
  margin-top: 0.75rem;
}
</style>
