<template>
  <div class="vip-page">
    <div class="vip-card">
      <div class="vip-card-header">
        <svg class="vip-header-icon" viewBox="0 0 24 24" width="44" height="44" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M4 10l2-6 4 4 4-8 4 8 4-4 2 6" />
          <path d="M4 10v10h16V10" />
        </svg>
        <h1 class="vip-title">会员中心</h1>
        <p class="vip-sub">会员状态与到期时间（UTC+8）</p>
      </div>

      <div v-if="user" class="vip-body">
        <div class="vip-row">
          <span class="vip-label">当前状态</span>
          <span :class="['vip-value', user.isVip ? 'vip-value--on' : 'vip-value--off']">
            {{ user.isVip ? '会员' : '非会员' }}
          </span>
        </div>
        <div class="vip-row">
          <span class="vip-label">到期时间</span>
          <span class="vip-value">{{ formatVipExpiresAt(user.vipExpiresAt) }}</span>
        </div>
        <p v-if="!user.isVip" class="vip-hint">如需开通或续期会员，请联系平台管理员。</p>
      </div>

      <div class="vip-actions">
        <router-link to="/account" class="btn-secondary">个人中心</router-link>
        <router-link to="/" class="btn-primary">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatVipExpiresAt, syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'

const router = useRouter()
const vipTick = ref(0)

const user = computed(() => {
  vipTick.value
  const raw = localStorage.getItem('user')
  if (!raw || raw === 'undefined' || raw === 'null') return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
})

const bump = () => {
  vipTick.value++
}

onMounted(async () => {
  if (!localStorage.getItem('userToken')) {
    router.replace('/login')
    return
  }
  window.addEventListener(USER_VIP_SYNC_EVENT, bump)
  await syncUserVipFromPlaylistsApi()
  bump()
})

onUnmounted(() => {
  window.removeEventListener(USER_VIP_SYNC_EVENT, bump)
})
</script>

<style scoped>
.vip-page {
  min-height: calc(100vh - 200px);
  padding: 24px 16px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.vip-card {
  width: 100%;
  max-width: 520px;
  padding: 32px 28px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.32);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 12px 40px rgba(31, 38, 135, 0.28);
  position: relative;
  overflow: hidden;
}

.vip-card::before {
  content: '';
  position: absolute;
  inset: -20%;
  background: linear-gradient(120deg, rgba(255, 224, 130, 0.35), rgba(106, 90, 205, 0.25), rgba(20, 184, 166, 0.2));
  z-index: 0;
  opacity: 0.85;
  animation: vip-bg 12s ease-in-out infinite alternate;
}

@keyframes vip-bg {
  from {
    transform: translate(-4%, -2%) rotate(0deg);
  }
  to {
    transform: translate(4%, 2%) rotate(2deg);
  }
}

.vip-card-header,
.vip-body,
.vip-actions {
  position: relative;
  z-index: 1;
}

.vip-card-header {
  text-align: center;
  margin-bottom: 28px;
}

.vip-header-icon {
  display: block;
  margin: 0 auto 10px;
  color: #ca8a04;
  filter: drop-shadow(0 2px 6px rgba(234, 179, 8, 0.35));
}

.vip-title {
  margin: 0;
  font-size: 1.65rem;
  font-weight: 800;
  color: #4a3f7a;
  letter-spacing: 0.04em;
}

.vip-sub {
  margin: 10px 0 0;
  font-size: 0.9rem;
  color: #6b608e;
}

.vip-body {
  padding: 20px 0 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.35);
  border-bottom: 1px solid rgba(255, 255, 255, 0.25);
}

.vip-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  padding: 12px 0;
}

.vip-label {
  font-size: 0.95rem;
  font-weight: 600;
  color: #5c4b7b;
}

.vip-value {
  font-size: 0.95rem;
  color: #333;
  text-align: right;
}

.vip-value--on {
  color: #b45309;
  font-weight: 700;
}

.vip-value--off {
  color: #6b7280;
  font-weight: 600;
}

.vip-hint {
  margin: 16px 0 0;
  font-size: 0.88rem;
  line-height: 1.5;
  color: #6b608e;
}

.vip-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 22px;
  border-radius: 999px;
  font-size: 0.92rem;
  font-weight: 600;
  text-decoration: none;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.btn-primary {
  background: linear-gradient(135deg, #6a5acd, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 14px rgba(106, 90, 205, 0.45);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(106, 90, 205, 0.55);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.55);
  color: #5c4b7b;
  border: 1px solid rgba(106, 90, 205, 0.35);
}

.btn-secondary:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.75);
}
</style>
