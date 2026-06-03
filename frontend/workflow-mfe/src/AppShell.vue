<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// Bridge: host uses createWebHistory (pushState doesn't fire hashchange).
// Listen for hashchange events so MFE internal routing responds to host-driven hash changes.
function onHashChange() {
  const target = window.location.hash?.replace(/^#/, '') || '/tasks'
  if (target && route.path !== target) {
    router.replace(target)
  }
}

onMounted(() => {
  window.addEventListener('hashchange', onHashChange)
  // Also sync on initial mount
  const hash = window.location.hash?.replace(/^#/, '')
  if (hash && route.path !== hash) {
    router.replace(hash)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', onHashChange)
})
</script>
