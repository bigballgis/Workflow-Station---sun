<template>
  <div class="wrap">
    <div class="card">
      <h1>{{ $t('login.title') }}</h1>
      <p class="sub">{{ $t('login.subtitle') }}</p>
      <form @submit.prevent="onSubmit">
        <label>{{ $t('login.username') }}</label>
        <input v-model="username" type="text" autocomplete="username" required />
        <label>{{ $t('login.password') }}</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
        <p v-if="missingParams" class="err">{{ $t('login.error.missingParams') }}</p>
        <p v-else-if="error" class="err">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? $t('login.submitting') : $t('login.submit') }}
        </button>
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
