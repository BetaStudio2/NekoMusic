<template>
  <div class="download-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <header class="topbar">
      <router-link to="/" class="topbar__back">
        <svg class="topbar__back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回 Web 播放器
      </router-link>
      <span class="topbar__tag">Download</span>
    </header>

    <main class="shell">
      <section class="hero" aria-labelledby="download-title">
        <div class="hero__copy">
          <p class="hero__eyebrow">Neko 云音乐</p>
          <h1 id="download-title" class="hero__title">把播放器装进口袋与桌面</h1>
          <p class="hero__lede">
            同一套免费体验：搜索、播放、收藏与歌单。选择你的平台，一键获取安装包。
          </p>
          <ul class="hero__facts">
            <li>完全免费</li>
            <li>开源透明</li>
            <li>多端一致</li>
          </ul>
        </div>
        <div class="hero__art">
          <div class="hero__frame">
            <img src="/favicon.ico" alt="" class="hero__logo" height="276" width="256" />
          </div>
          <p class="hero__art-caption">Android · Windows · Linux · macOS</p>
        </div>
      </section>

      <div v-if="loading" class="state state--loading">
        <div class="state__spinner" aria-hidden="true" />
        <p class="state__text">正在拉取最新版本信息…</p>
      </div>

      <div v-else-if="error" class="state state--error" role="alert">
        <svg class="state__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <circle cx="12" cy="12" r="10" stroke-width="2" />
          <path d="M12 8v4M12 16h.01" stroke-width="2" stroke-linecap="round" />
        </svg>
        <p class="state__text">{{ error }}</p>
      </div>

      <template v-else>
        <section class="android" aria-labelledby="android-heading">
          <div class="android__rail" />
          <div class="android__inner">
            <div class="android__icon-wrap" aria-hidden="true">
              <svg class="android__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                <path d="M17 2H7C5.34 2 4 3.34 4 5v14c0 1.66 1.34 3 3 3h10c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3Z" stroke-linejoin="round" />
                <path d="M12 18h.01" stroke-linecap="round" />
              </svg>
            </div>
            <div class="android__main">
              <div class="android__head">
                <h2 id="android-heading" class="android__title">Android</h2>
                <p class="android__sub">手机与平板 · APK 直链</p>
              </div>
              <dl class="android__meta">
                <div class="android__meta-row">
                  <dt>当前版本</dt>
                  <dd>{{ versionInfo.ver || '—' }}</dd>
                </div>
              </dl>
            </div>
            <a :href="versionInfo.updateUrl" class="android__cta" download>
              <span class="android__cta-shine" aria-hidden="true" />
              <span class="android__cta-label">
                <svg class="android__cta-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M12 16V4m0 12l-4-4m4 4l4-4M4 20h16" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                下载 APK
              </span>
            </a>
          </div>
        </section>

        <section class="desktop" aria-labelledby="desktop-heading">
          <div class="desktop__intro">
            <h2 id="desktop-heading" class="desktop__title">桌面客户端</h2>
            <p class="desktop__sub">离线能力更强 · 本机音频体验</p>
            <div class="desktop__version">
              <span class="desktop__version-label">最新版本</span>
              <span class="desktop__version-value">v{{ versionInfo.pc?.pc_ver || versionInfo.ver }}</span>
            </div>
          </div>

          <div class="desktop__grid">
            <a :href="windowsDownloadUrl" class="plat" download>
              <div class="plat__icon plat__icon--win" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="currentColor" class="plat__svg">
                  <path d="M0 3.45L9.75 2.1v9.45H0m10.95-9.6L24 0v11.4H10.95M0 12.6h9.75v9.45L0 20.7m10.95-8.1H24V24l-12.9-1.8" />
                </svg>
              </div>
              <div class="plat__body">
                <span class="plat__name">Windows</span>
                <span class="plat__fmt">可执行安装包 · .exe</span>
              </div>
              <span class="plat__action">获取</span>
            </a>

            <div class="plat plat--linux">
              <div class="plat__icon plat__icon--linux" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" class="plat__svg plat__svg--stroke">
                  <rect x="3" y="4" width="18" height="12" rx="2" />
                  <path d="M7 16v2M11 16v2M15 16v2M9 20h6" stroke-linecap="round" />
                  <path d="M8 9h2M14 9h2M10 12h4" stroke-linecap="round" />
                </svg>
              </div>
              <div class="plat__body">
                <span class="plat__name">Linux</span>
                <div class="plat__linux-tabs" role="tablist" aria-label="Linux 发行版">
                  <button
                    type="button"
                    class="plat__linux-tab"
                    :class="{ 'plat__linux-tab--active': linuxVariant === 'debian' }"
                    role="tab"
                    :aria-selected="linuxVariant === 'debian'"
                    @click="linuxVariant = 'debian'"
                  >
                    Debian 系
                  </button>
                  <button
                    type="button"
                    class="plat__linux-tab"
                    :class="{ 'plat__linux-tab--active': linuxVariant === 'arch' }"
                    role="tab"
                    :aria-selected="linuxVariant === 'arch'"
                    @click="linuxVariant = 'arch'"
                  >
                    Arch 系
                  </button>
                </div>
                <template v-if="linuxVariant === 'debian'">
                  <span class="plat__fmt">安装包 · .deb</span>
                  <a :href="linuxDownloadUrl" class="plat__linux-cta" download>下载 .deb</a>
                </template>
                <template v-else>
                  <span class="plat__fmt">AUR · yay / paru</span>
                  <div class="plat__cmd">
                    <code class="plat__cmd-text">{{ archInstallCommand }}</code>
                    <button
                      type="button"
                      class="plat__cmd-copy"
                      :aria-label="archCopied ? '已复制' : '复制安装命令'"
                      @click="copyArchCommand"
                    >
                      {{ archCopied ? '已复制' : '复制' }}
                    </button>
                  </div>
                </template>
              </div>
            </div>

            <a :href="macDownloadUrl" class="plat" download>
              <div class="plat__icon plat__icon--mac" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="currentColor" class="plat__svg">
                  <path
                    d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"
                  />
                </svg>
              </div>
              <div class="plat__body">
                <span class="plat__name">macOS</span>
                <span class="plat__fmt">安装包 · .pkg</span>
              </div>
              <span class="plat__action">获取</span>
            </a>
          </div>
        </section>
      </template>

      <footer class="foot">
        <p class="foot__line">安装如遇系统拦截，请在系统设置中允许来自开发者的应用。</p>
        <p class="foot__line foot__line--muted">© {{ year }} NekoMusic · 下载页</p>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'

const versionInfo = ref({ ver: '', updateUrl: '' })
const loading = ref(true)
const error = ref('')
const linuxVariant = ref('debian')
const archCopied = ref(false)
const archInstallCommand = 'yay -S neko-cloud-music'

const year = new Date().getFullYear()

const fetchVersionInfo = async () => {
  try {
    const response = await axios.get('/version.json', {
      timeout: 5000
    })
    versionInfo.value = response.data
  } catch (err) {
    console.error('获取版本信息失败:', err)
    error.value = '获取下载信息失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const replaceVersion = (url) => {
  if (!url) return ''
  const pcVer = versionInfo.value.pc?.pc_ver || versionInfo.value.ver
  return url.replace('{pc_ver}', pcVer)
}

const windowsDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.windows || versionInfo.value.pc?.downloadUrl || versionInfo.value.updateUrl
  return replaceVersion(url)
})

const linuxDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.linux
  return replaceVersion(url)
})

const macDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.mac
  return replaceVersion(url)
})

let archCopyTimer = null
const copyArchCommand = async () => {
  try {
    await navigator.clipboard.writeText(archInstallCommand)
    archCopied.value = true
    if (archCopyTimer) clearTimeout(archCopyTimer)
    archCopyTimer = setTimeout(() => {
      archCopied.value = false
    }, 2000)
  } catch {
    /* 降级：部分环境无 clipboard API */
  }
}

onMounted(() => {
  fetchVersionInfo()
})
</script>

<style scoped>
.download-page {
  --bg0: #07060d;
  --bg1: #0f1020;
  --line: rgba(255, 255, 255, 0.08);
  --text: rgba(255, 255, 255, 0.92);
  --muted: rgba(255, 255, 255, 0.62);
  --faint: rgba(255, 255, 255, 0.42);
  --card: rgba(255, 255, 255, 0.06);
  --card2: rgba(255, 255, 255, 0.09);
  --accent: #8b5cf6;
  --accent2: #22d3ee;
  --accent3: #34d399;
  --radius: 18px;
  --radius-lg: 24px;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  --shadow: 0 24px 80px rgba(0, 0, 0, 0.45);

  position: relative;
  min-height: 100vh;
  padding-top: env(safe-area-inset-top, 0px);
  color: var(--text);
  background: radial-gradient(1200px 700px at 10% -10%, rgba(139, 92, 246, 0.35), transparent 55%),
    radial-gradient(900px 600px at 95% 10%, rgba(34, 211, 238, 0.18), transparent 50%),
    linear-gradient(180deg, var(--bg0), var(--bg1) 40%, #0a0a12 100%);
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
  opacity: 0.55;
  animation: blobFloat 22s var(--ease) infinite;
}

.ambient__blob--a {
  width: 420px;
  height: 420px;
  background: rgba(139, 92, 246, 0.45);
  top: -140px;
  left: -120px;
}

.ambient__blob--b {
  width: 360px;
  height: 360px;
  background: rgba(34, 211, 238, 0.28);
  bottom: -80px;
  right: -100px;
  animation-delay: -7s;
}

.ambient__blob--c {
  width: 280px;
  height: 280px;
  background: rgba(52, 211, 153, 0.2);
  top: 42%;
  left: 38%;
  animation-delay: -12s;
}

.ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 80% 60% at 50% 20%, black, transparent);
  animation: gridBreathe 10s ease-in-out infinite;
}

@keyframes gridBreathe {
  0%,
  100% {
    opacity: 0.28;
  }
  50% {
    opacity: 0.42;
  }
}

@keyframes blobFloat {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(24px, -18px) scale(1.05);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient__blob {
    animation: none;
  }
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px clamp(16px, 4vw, 32px);
  border-bottom: 1px solid var(--line);
  background: rgba(7, 6, 13, 0.72);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  animation: topbarSlide 0.65s var(--ease) both;
}

@keyframes topbarSlide {
  from {
    transform: translateY(-100%);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.topbar__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--muted);
  text-decoration: none;
  padding: 8px 12px;
  margin: -8px -12px;
  border-radius: 10px;
  transition: color 0.2s var(--ease), background 0.2s var(--ease);
}

.topbar__back:hover {
  color: var(--text);
  background: rgba(255, 255, 255, 0.06);
}

.topbar__back-icon {
  width: 18px;
  height: 18px;
  opacity: 0.85;
}

.topbar__tag {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--faint);
  text-transform: uppercase;
}

/* 小屏：系统返回手势/按钮已够用，顶栏占高且易与刘海重叠 */
@media (max-width: 768px) {
  .topbar {
    display: none;
  }
}

.shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  margin: 0 auto;
  padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 32px) 56px;
}

.hero {
  display: grid;
  grid-template-columns: 1fr;
  gap: clamp(28px, 5vw, 48px);
  align-items: center;
  margin-bottom: clamp(32px, 6vw, 56px);
  perspective: 1200px;
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translate3d(0, 28px, 0);
    filter: blur(8px);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    filter: blur(0);
  }
}

@keyframes riseInSoft {
  from {
    opacity: 0;
    transform: translate3d(0, 20px, 0) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}

@media (min-width: 900px) {
  .hero {
    grid-template-columns: 1.15fr 0.85fr;
  }
}

.hero__eyebrow {
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--accent2);
  margin-bottom: 12px;
  animation: riseIn 0.75s var(--ease) 0.06s both;
}

.hero__title {
  font-size: clamp(1.85rem, 4vw, 2.75rem);
  font-weight: 800;
  line-height: 1.12;
  letter-spacing: -0.02em;
  margin: 0 0 16px;
  animation: riseIn 0.78s var(--ease) 0.14s both;
}

.hero__lede {
  font-size: clamp(1rem, 2vw, 1.125rem);
  line-height: 1.65;
  color: var(--muted);
  max-width: 42ch;
  margin: 0 0 22px;
  animation: riseIn 0.8s var(--ease) 0.22s both;
}

.hero__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  list-style: none;
  padding: 0;
  margin: 0;
}

.hero__facts li {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text);
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.04);
  animation: riseInSoft 0.65s var(--ease) both;
  transition: transform 0.25s var(--ease), border-color 0.25s var(--ease), box-shadow 0.25s var(--ease),
    background 0.25s var(--ease);
}

.hero__facts li:nth-child(1) {
  animation-delay: 0.32s;
}

.hero__facts li:nth-child(2) {
  animation-delay: 0.4s;
}

.hero__facts li:nth-child(3) {
  animation-delay: 0.48s;
}

@media (hover: hover) {
  .hero__facts li:hover {
    transform: translateY(-4px) scale(1.03);
    border-color: rgba(139, 92, 246, 0.45);
    background: rgba(139, 92, 246, 0.12);
    box-shadow: 0 12px 32px rgba(139, 92, 246, 0.2);
  }
}

.hero__art {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
  animation: fadeBlurIn 0.88s var(--ease) 0.18s both;
}

@keyframes fadeBlurIn {
  from {
    opacity: 0;
    filter: blur(12px);
  }
  to {
    opacity: 1;
    filter: blur(0);
  }
}

.hero__art-caption {
  margin: 0;
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  color: var(--faint);
}

.hero__frame {
  position: relative;
  width: min(280px, 100%);
  aspect-ratio: 1;
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.02));
  border: 1px solid var(--line);
  box-shadow: var(--shadow);
  display: grid;
  place-items: center;
  overflow: hidden;
  transition: transform 0.45s var(--ease), box-shadow 0.45s var(--ease), border-color 0.35s var(--ease);
}

.hero__frame::after {
  content: '';
  position: absolute;
  inset: -40%;
  background: conic-gradient(from 200deg, rgba(139, 92, 246, 0.35), transparent, rgba(34, 211, 238, 0.25), transparent);
  opacity: 0.65;
  animation: spinSlow 18s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .hero__frame::after {
    animation: none;
  }
}

@keyframes spinSlow {
  to {
    transform: rotate(360deg);
  }
}

.hero__logo {
  position: relative;
  z-index: 1;
  width: min(160px, 52%);
  height: auto;
  filter: drop-shadow(0 16px 40px rgba(0, 0, 0, 0.45));
  animation: logoBob 5s ease-in-out infinite;
}

@keyframes logoBob {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-8px) scale(1.02);
  }
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 48px 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: var(--card);
  margin-bottom: 32px;
}

.state--loading {
  animation: statePulse 2.4s ease-in-out infinite;
}

@keyframes statePulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(34, 211, 238, 0);
  }
  50% {
    box-shadow: 0 0 40px 2px rgba(34, 211, 238, 0.08);
  }
}

.state__spinner {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.12);
  border-top-color: var(--accent2);
  animation: spin 0.85s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state--error .state__icon {
  width: 48px;
  height: 48px;
  color: #fb7185;
}

.state__text {
  margin: 0;
  text-align: center;
  color: var(--muted);
  max-width: 36ch;
}

.state--error .state__text {
  color: #fecdd3;
}

.android {
  position: relative;
  margin-bottom: clamp(24px, 4vw, 36px);
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.18), rgba(255, 255, 255, 0.03));
  box-shadow: var(--shadow);
  overflow: hidden;
  animation: sectionLift 0.85s var(--ease) 0.12s both;
}

@keyframes sectionLift {
  from {
    opacity: 0;
    transform: translate3d(0, 36px, 0) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}

@media (hover: hover) {
  .android:hover {
    border-color: rgba(139, 92, 246, 0.35);
    box-shadow: 0 28px 80px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(139, 92, 246, 0.15);
  }
}

.android__rail {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  background: linear-gradient(180deg, var(--accent), var(--accent2), var(--accent3));
  background-size: 100% 200%;
  animation: railFlow 3.5s linear infinite;
}

@keyframes railFlow {
  0% {
    background-position: 0% 0%;
  }
  100% {
    background-position: 0% 100%;
  }
}

.android__inner {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: clamp(16px, 3vw, 28px);
  align-items: center;
  padding: clamp(20px, 3.5vw, 28px) clamp(20px, 3.5vw, 32px) clamp(20px, 3.5vw, 28px) clamp(24px, 4vw, 36px);
}

@media (max-width: 720px) {
  .android__inner {
    grid-template-columns: 1fr;
    text-align: center;
    justify-items: center;
  }

  .android__main {
    align-items: center;
  }

  .android__meta {
    justify-content: center;
  }
}

.android__icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.12);
  transition: transform 0.35s var(--ease), box-shadow 0.35s var(--ease);
  animation: iconPop 0.55s var(--ease) 0.35s both;
}

@keyframes iconPop {
  from {
    transform: scale(0.6) rotate(-8deg);
    opacity: 0;
  }
  to {
    transform: scale(1) rotate(0);
    opacity: 1;
  }
}

@media (hover: hover) {
  .android:hover .android__icon-wrap {
    transform: scale(1.06) rotate(-3deg);
    box-shadow: 0 12px 28px rgba(139, 92, 246, 0.35);
  }
}

.android__icon {
  width: 32px;
  height: 32px;
  color: #e9d5ff;
}

.android__main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  align-items: flex-start;
}

.android__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.android__title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.android__sub {
  margin: 0;
  font-size: 0.9rem;
  color: var(--muted);
}

.android__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin: 0;
}

.android__meta-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 0.88rem;
}

.android__meta-row dt {
  margin: 0;
  color: var(--faint);
  font-weight: 600;
}

.android__meta-row dd {
  margin: 0;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--text);
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.android__cta {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 14px 22px;
  border-radius: 14px;
  font-weight: 800;
  font-size: 0.95rem;
  text-decoration: none;
  color: #0b0b10;
  background: linear-gradient(135deg, #f5f3ff, #e9d5ff);
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 14px 40px rgba(139, 92, 246, 0.35);
  transition: transform 0.22s var(--ease), box-shadow 0.22s var(--ease);
  white-space: nowrap;
  overflow: hidden;
}

.android__cta-shine {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(
    110deg,
    transparent 0%,
    transparent 38%,
    rgba(255, 255, 255, 0.55) 50%,
    transparent 62%,
    transparent 100%
  );
  transform: translateX(-120%);
  animation: ctaShine 2.6s ease-in-out infinite;
}

@keyframes ctaShine {
  0%,
  100% {
    transform: translateX(-120%);
  }
  45%,
  55% {
    transform: translateX(120%);
  }
}

.android__cta:hover {
  transform: translateY(-3px) scale(1.03);
  box-shadow: 0 20px 56px rgba(139, 92, 246, 0.5);
}

.android__cta:active {
  transform: translateY(0) scale(0.98);
}

.android__cta-label {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.android__cta-icon {
  width: 20px;
  height: 20px;
  transition: transform 0.35s var(--ease);
}

.android__cta:hover .android__cta-icon {
  transform: translateY(3px);
}

.desktop {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.04);
  padding: clamp(22px, 3.5vw, 32px);
  margin-bottom: 40px;
  animation: sectionLift 0.88s var(--ease) 0.22s both;
}

@media (hover: hover) {
  .desktop:hover {
    border-color: rgba(34, 211, 238, 0.22);
    box-shadow: 0 24px 70px rgba(0, 0, 0, 0.38);
  }
}

.desktop__intro {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: clamp(20px, 3vw, 28px);
  padding-bottom: clamp(18px, 3vw, 24px);
  border-bottom: 1px solid var(--line);
}

.desktop__title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.desktop__sub {
  margin: 0;
  font-size: 0.95rem;
  color: var(--muted);
}

.desktop__version {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.desktop__version-label {
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--faint);
}

.desktop__version-value {
  font-size: 0.95rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid rgba(34, 211, 238, 0.35);
  background: rgba(34, 211, 238, 0.1);
  color: #a5f3fc;
}

.desktop__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  perspective: 1100px;
}

@media (min-width: 720px) {
  .desktop__grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
}

.plat {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 18px;
  border-radius: var(--radius);
  text-decoration: none;
  color: inherit;
  border: 1px solid var(--line);
  background: var(--card);
  transform-style: preserve-3d;
  transition: transform 0.28s var(--ease), border-color 0.28s var(--ease), background 0.28s var(--ease),
    box-shadow 0.28s var(--ease);
  min-height: 168px;
  opacity: 0;
  animation: platEnter 0.62s var(--ease) forwards;
}

.plat--linux {
  text-decoration: none;
  cursor: default;
}

.plat--linux:hover {
  transform: none;
  border-color: var(--line);
  background: var(--card);
  box-shadow: none;
}

@media (hover: hover) {
  .plat--linux:hover .plat__icon {
    transform: none;
  }
}

.plat:nth-child(1) {
  animation-delay: 0.38s;
}

.plat:nth-child(2) {
  animation-delay: 0.52s;
}

.plat:nth-child(3) {
  animation-delay: 0.64s;
}

@keyframes platEnter {
  from {
    opacity: 0;
    transform: translate3d(0, 28px, 0) rotateX(8deg) scale(0.94);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) rotateX(0) scale(1);
  }
}

@media (hover: hover) {
  .plat:hover {
    transform: translate3d(0, -10px, 8px) scale(1.03) rotateX(4deg);
    border-color: rgba(139, 92, 246, 0.5);
    background: var(--card2);
    box-shadow: 0 24px 56px rgba(0, 0, 0, 0.42), 0 0 40px rgba(139, 92, 246, 0.12);
  }

  .plat:hover .plat__icon {
    transform: scale(1.12) rotate(-4deg);
  }

  .plat:hover .plat__action {
    letter-spacing: 0.14em;
    color: #67e8f9;
  }
}

.plat__icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  transition: transform 0.35s var(--ease);
}

.plat__icon--win {
  background: rgba(59, 130, 246, 0.18);
  color: #93c5fd;
}

.plat__icon--linux {
  background: rgba(52, 211, 153, 0.16);
  color: #6ee7b7;
}

.plat__icon--mac {
  background: rgba(244, 244, 245, 0.12);
  color: #fafafa;
}

.plat__svg {
  width: 26px;
  height: 26px;
}

.plat__svg--stroke {
  stroke: currentColor;
  fill: none;
}

.plat__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.plat__name {
  font-size: 1.1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.plat__fmt {
  font-size: 0.82rem;
  color: var(--muted);
  line-height: 1.45;
}

.plat__action {
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent2);
  transition: letter-spacing 0.35s var(--ease), color 0.25s var(--ease);
}

.plat__linux-tabs {
  display: flex;
  gap: 6px;
  margin: 4px 0 2px;
}

.plat__linux-tab {
  flex: 1;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: rgba(0, 0, 0, 0.2);
  color: var(--muted);
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
  transition: color 0.2s var(--ease), border-color 0.2s var(--ease), background 0.2s var(--ease);
}

.plat__linux-tab:hover {
  color: var(--text);
  border-color: rgba(52, 211, 153, 0.35);
}

.plat__linux-tab--active {
  color: #6ee7b7;
  border-color: rgba(52, 211, 153, 0.5);
  background: rgba(52, 211, 153, 0.12);
}

.plat__linux-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  text-decoration: none;
  color: #0b0b10;
  background: linear-gradient(135deg, #d1fae5, #6ee7b7);
  border: 1px solid rgba(255, 255, 255, 0.25);
  transition: transform 0.22s var(--ease), box-shadow 0.22s var(--ease);
}

.plat__linux-cta:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(52, 211, 153, 0.35);
}

.plat__cmd {
  display: flex;
  align-items: stretch;
  gap: 8px;
  margin-top: 8px;
  padding: 10px 10px 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: rgba(0, 0, 0, 0.28);
}

.plat__cmd-text {
  flex: 1;
  min-width: 0;
  font-size: 0.78rem;
  font-family: ui-monospace, 'Cascadia Code', 'SF Mono', Menlo, monospace;
  color: #a7f3d0;
  word-break: break-all;
  line-height: 1.45;
}

.plat__cmd-copy {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid rgba(52, 211, 153, 0.35);
  background: rgba(52, 211, 153, 0.12);
  color: #6ee7b7;
  font-size: 0.72rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s var(--ease), color 0.2s var(--ease);
}

.plat__cmd-copy:hover {
  background: rgba(52, 211, 153, 0.22);
  color: #ecfdf5;
}

.plat__linux-tab:focus-visible,
.plat__linux-cta:focus-visible,
.plat__cmd-copy:focus-visible {
  outline: 2px solid var(--accent2);
  outline-offset: 2px;
}

.foot {
  padding-top: 8px;
  border-top: 1px solid var(--line);
}

.foot__line {
  margin: 0 0 8px;
  font-size: 0.82rem;
  color: var(--muted);
  line-height: 1.55;
  max-width: 70ch;
}

.foot__line--muted {
  color: var(--faint);
  margin-bottom: 0;
}

.topbar__back:focus-visible,
.android__cta:focus-visible,
.plat:focus-visible,
.plat__linux-cta:focus-visible {
  outline: 2px solid var(--accent2);
  outline-offset: 3px;
}
</style>
