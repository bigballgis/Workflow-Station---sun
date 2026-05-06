<template>
  <div class="wrap">
    <div class="card">
      <h1>Workflow Platform</h1>
      <p class="sub">Unified Sign-In</p>
      <form @submit.prevent="onSubmit">
        <label>Username</label>
        <input v-model="username" type="text" autocomplete="username" required />
        <label>Password</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
        <p v-if="missingParams" class="err">Missing client_id or redirect_uri parameter.</p>
        <p v-else-if="error" class="err">{{ error }}</p>
        <button type="submit" :disabled="loading">{{ loading ? 'Signing in...' : 'Sign In' }}</button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useSsoParams } from '@/composables/useSsoParams'
import { useLogin } from '@/composables/useLogin'

const { clientId, redirectUri, state, missingParams } = useSsoParams()
const { username, password, loading, error, onSubmit } = useLogin(clientId, redirectUri, state)
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
