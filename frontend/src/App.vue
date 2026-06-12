<template>
  <MainLayout v-if="isLoggedIn" @logout="handleLogout" />
  <AuthView v-else @login-success="handleLoginSuccess" />
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import AuthView from './views/AuthView.vue'
import MainLayout from './views/MainLayout.vue'

const isLoggedIn = ref(Boolean(localStorage.getItem('token')))

function handleLoginSuccess() {
  isLoggedIn.value = true
}

function handleLogout() {
  isLoggedIn.value = false
}

function handleAuthExpired() {
  isLoggedIn.value = false
}

onMounted(() => {
  window.addEventListener('auth-expired', handleAuthExpired)
})

onUnmounted(() => {
  window.removeEventListener('auth-expired', handleAuthExpired)
})
</script>
