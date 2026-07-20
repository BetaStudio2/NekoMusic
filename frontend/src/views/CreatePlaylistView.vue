<template>
  <div class="cp-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__grid" />
    </div>

    <main class="shell">
      <header class="page-head">
        <button v-if="!isMobile" type="button" class="btn-back" @click="goBack">返回</button>
        <h1 class="page-title">创建歌单</h1>
        <p class="page-lede">填写名称与可选描述，创建后可在「我的歌单」中管理。</p>
      </header>

      <section class="panel form-panel">
        <form @submit.prevent="handleCreatePlaylist" class="create-form">
          <div class="form-group">
            <label for="pl-name">歌单名称 <span class="required">*</span></label>
            <input
              id="pl-name"
              v-model="playlistName"
              type="text"
              required
              maxlength="255"
              placeholder="请输入歌单名称"
              autocomplete="off"
            />
            <span class="char-count">{{ playlistName.length }}/255</span>
          </div>

          <div class="form-group">
            <label for="pl-desc">歌单描述</label>
            <textarea
              id="pl-desc"
              v-model="playlistDescription"
              maxlength="500"
              rows="4"
              placeholder="选填，简要介绍歌单"
            />
            <span class="char-count">{{ playlistDescription.length }}/500</span>
          </div>

          <div class="form-actions">
            <button type="button" class="btn-ghost" @click="goBack">取消</button>
            <button type="submit" class="btn-submit" :disabled="submitting">
              {{ submitting ? '创建中…' : '创建歌单' }}
            </button>
          </div>
        </form>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()
const router = useRouter()

const isMobile = computed(() => {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera
  return /android|ipad|iphone|ipod/i.test(userAgent)
})

const playlistName = ref('')
const playlistDescription = ref('')
const submitting = ref(false)

const getToken = () => {
  return localStorage.getItem('userToken')
}

const handleCreatePlaylist = async () => {
  if (submitting.value) return

  submitting.value = true

  try {
    const token = getToken()
    const requestData = {
      name: playlistName.value.trim()
    }

    if (playlistDescription.value.trim()) {
      requestData.description = playlistDescription.value.trim()
    }

    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/create`, {
      method: 'POST',
      headers: {
        Authorization: token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestData)
    })

    const data = await response.json()

    if (data.success) {
      toast.success('歌单创建成功')
      router.push('/playlists')
    } else {
      toast.error(data.message || '歌单创建失败')
    }
  } catch (error) {
    console.error('歌单创建失败:', error)
    toast.error('歌单创建失败')
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.cp-page {
  --text: rgba(255, 255, 255, 0.92);
  --muted: rgba(255, 255, 255, 0.62);
  --faint: rgba(255, 255, 255, 0.42);
  --line: rgba(255, 255, 255, 0.1);
  --accent: #69c8df;
  --accent2: #69c8df;
  --radius: 16px;
  --radius-lg: 22px;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  --shadow: 0 24px 80px rgba(0, 0, 0, 0.45);

  position: relative;
  min-height: 100vh;
  padding-top: env(safe-area-inset-top, 0px);
  color: var(--text);
  background: transparent;
}

.ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.ambient__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.48;
}

.ambient__blob--a {
  width: 380px;
  height: 380px;
  background: rgba(105, 200, 223, 0.38);
  top: -100px;
  right: -60px;
}

.ambient__blob--b {
  width: 300px;
  height: 300px;
  background: rgba(105, 200, 223, 0.2);
  bottom: 10%;
  left: -40px;
}

.ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.28;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 80% 55% at 50% 12%, black, transparent);
}

.shell {
  position: relative;
  z-index: 1;
  width: min(560px, 100%);
  margin: 0 auto;
  padding: clamp(16px, 3vw, 28px) clamp(14px, 3.5vw, 24px) 48px;
}

.page-head {
  margin-bottom: 22px;
}

.btn-back {
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  margin-bottom: 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.88);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s var(--ease), border-color 0.2s var(--ease);
}

.btn-back:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(105, 200, 223, 0.35);
}

.page-title {
  margin: 0 0 8px;
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: 800;
  letter-spacing: -0.03em;
}

.page-lede {
  margin: 0;
  font-size: 0.9rem;
  color: var(--muted);
  line-height: 1.5;
}

.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(105, 200, 223, 0.14), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.form-panel {
  padding: clamp(22px, 4vw, 32px);
}

.create-form {
  margin: 0;
}

.form-group {
  margin-bottom: 28px;
  position: relative;
}

.form-group label {
  display: block;
  font-weight: 600;
  font-size: 0.88rem;
  margin-bottom: 8px;
  color: rgba(255, 255, 255, 0.88);
}

.required {
  color: #fb7185;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  color: var(--text);
  font-size: 0.95rem;
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}

.form-group textarea {
  resize: vertical;
  min-height: 120px;
}

.form-group input::placeholder,
.form-group textarea::placeholder {
  color: var(--faint);
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: rgba(105, 200, 223, 0.45);
  box-shadow: 0 0 0 3px rgba(105, 200, 223, 0.12);
}

.char-count {
  display: block;
  margin-top: 6px;
  text-align: right;
  font-size: 0.78rem;
  color: var(--faint);
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 8px;
}

.btn-ghost {
  font-family: inherit;
  padding: 11px 22px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.88);
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.1);
}

.btn-submit {
  font-family: inherit;
  padding: 11px 22px;
  border-radius: 999px;
  border: none;
  font-weight: 700;
  font-size: 0.9rem;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, #9beaff, var(--accent2));
  box-shadow: 0 8px 24px rgba(105, 200, 223, 0.3);
}

.btn-submit:hover:not(:disabled) {
  filter: brightness(1.05);
}

.btn-submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 520px) {
  .form-actions {
    flex-direction: column-reverse;
  }

  .btn-ghost,
  .btn-submit {
    width: 100%;
    justify-content: center;
  }
}
</style>
