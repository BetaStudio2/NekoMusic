<script setup>
/**
 * UserAuthView —— 登录 / 注册（同一张卡片）
 * ------------------------------------------------------------
 * /login 与 /register 共用本组件：同一实例内切换面板，
 * 因此切换时只更新 URL，不会重挂，过渡动画得以完整播放。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { PageShell, AmbientBackdrop } from '@/layouts'
import AuthPanel from '@/layouts/AuthPanel.vue'

const route = useRoute()
const initialTab = computed(() => (route.name === 'register' ? 'register' : 'login'))
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <AuthPanel :initial-tab="initialTab" />
  </PageShell>
</template>
