<template>
  <div class="home-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <main class="shell">
      <section class="intro" aria-labelledby="home-title">
        <div class="intro__text">
          <h1 id="home-title" class="intro__title">从这里开始听</h1>
          <p class="intro__lede">用顶栏搜索与播放；下载、上传与文档从下方进入。</p>
        </div>
        <nav class="intro__nav" aria-label="快捷入口">
          <router-link to="/download" class="intro__chip">下载客户端</router-link>
          <router-link v-if="isLoggedIn" to="/upload" class="intro__chip">上传音乐</router-link>
          <a
            href="https://github.com/NyaNyagulugulu/NekoMusicDocs"
            target="_blank"
            rel="noopener noreferrer"
            class="intro__chip"
          >开发者文档</a>
          <router-link to="/playlists" class="intro__chip">歌单</router-link>
        </nav>
      </section>

      <section class="browse" aria-labelledby="browse-heading">
        <div class="browse__rail" aria-hidden="true" />
        <div class="browse__inner">
          <header class="browse__head">
            <div>
              <h2 id="browse-heading" class="browse__title">热门与最新</h2>
              <p class="browse__sub">排行榜与最新上架封面预览</p>
            </div>
          </header>

          <div v-if="showDiscoverSkeleton" class="state state--loading">
            <div class="state__spinner" aria-hidden="true" />
            <p class="state__text">正在加载预览…</p>
          </div>

          <div v-else class="browse__tiles">
            <article
              class="b-tile b-tile--hot"
              tabindex="0"
              role="button"
              @click="goToRanking"
              @keydown.enter.prevent="goToRanking"
            >
              <div class="b-tile__cover b-tile__cover--hot">
                <div class="b-tile__mosaic">
                  <img
                    v-for="(item, index) in displayList.slice(0, 4)"
                    :key="'h-' + index"
                    :src="item.coverUrl"
                    :alt="item.title"
                    class="b-tile__img"
                    @error="handleImageError"
                  />
                </div>
                <span class="b-tile__badge">{{ rankingList.length }} 首</span>
              </div>
              <div class="b-tile__meta">
                <h3 class="b-tile__name">热门音乐</h3>
                <p class="b-tile__hint">进入排行榜</p>
              </div>
            </article>

            <article
              class="b-tile b-tile--latest"
              tabindex="0"
              role="button"
              @click="goToLatest"
              @keydown.enter.prevent="goToLatest"
            >
              <div class="b-tile__cover b-tile__cover--latest">
                <div class="b-tile__mosaic">
                  <img
                    v-for="(item, index) in displayLatestList.slice(0, 4)"
                    :key="'l-' + index"
                    :src="item.coverUrl"
                    :alt="item.title"
                    class="b-tile__img"
                    @error="handleImageError"
                  />
                </div>
                <span class="b-tile__badge">{{ latestList.length }} 首</span>
              </div>
              <div class="b-tile__meta">
                <h3 class="b-tile__name">最新音乐</h3>
                <p class="b-tile__hint">进入最新上架</p>
              </div>
            </article>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()
const router = useRouter()

const rankingList = ref([])
const latestList = ref([])
const rankingLoading = ref(true)
const latestLoading = ref(true)

const isLoggedIn = ref(false)
const syncLoginState = () => {
  const t = localStorage.getItem('userToken')
  isLoggedIn.value = t != null && t !== ''
}

const displayList = computed(() => rankingList.value.slice(0, 20))
const displayLatestList = computed(() => latestList.value.slice(0, 20))

const showDiscoverSkeleton = computed(
  () =>
    (rankingLoading.value || latestLoading.value) &&
    rankingList.value.length === 0 &&
    latestList.value.length === 0
)

const fetchRanking = async () => {
  rankingLoading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/ranking`)
    const data = await response.json()

    if (data.success && data.data) {
      rankingList.value = data.data.map((item) => ({
        ...item,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`
      }))
    } else {
      console.error('获取排行榜失败:', data.message)
    }
  } catch (error) {
    console.error('排行榜请求失败:', error)
    toast.error('加载排行榜失败')
  } finally {
    rankingLoading.value = false
  }
}

const fetchLatest = async () => {
  latestLoading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/latest?limit=300`)
    const data = await response.json()

    if (data.success && data.data) {
      latestList.value = data.data.map((item) => ({
        ...item,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`
      }))
    } else {
      console.error('获取最新音乐失败:', data.message)
    }
  } catch (error) {
    console.error('最新音乐请求失败:', error)
    toast.error('加载最新音乐失败')
  } finally {
    latestLoading.value = false
  }
}

const goToRanking = () => {
  router.push('/ranking')
}

const goToLatest = () => {
  router.push('/latest')
}

const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

onMounted(() => {
  syncLoginState()
  window.addEventListener('storage', syncLoginState)
  fetchRanking()
  fetchLatest()
})

onUnmounted(() => {
  window.removeEventListener('storage', syncLoginState)
})
</script>

<style scoped>
/* —— 与下载页同一套页面基底（深色、光斑、网格）—— */
.home-page {
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
  /* 底图由 #app.app--home 统一提供，避免顶栏下沿与主内容背景不一致 */
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

  .ambient__grid {
    animation: none;
  }
}

.shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  margin: 0 auto;
  padding: clamp(18px, 3.5vw, 36px) clamp(16px, 4vw, 32px) 56px;
}

.intro {
  margin-bottom: clamp(22px, 3.5vw, 32px);
  padding-bottom: clamp(16px, 2.8vw, 24px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.intro__text {
  max-width: 38rem;
}

.intro__title {
  margin: 0 0 8px;
  font-size: clamp(1.55rem, 3.5vw, 2.1rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--text);
}

.intro__lede {
  margin: 0;
  font-size: clamp(0.88rem, 1.75vw, 0.97rem);
  line-height: 1.5;
  color: var(--muted);
  max-width: 40ch;
}

.intro__nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: clamp(14px, 2.5vw, 20px);
}

.intro__chip {
  display: inline-flex;
  align-items: center;
  padding: 9px 15px;
  font-size: 0.84rem;
  font-weight: 600;
  text-decoration: none;
  color: rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  transition: background 0.2s var(--ease), border-color 0.2s var(--ease), transform 0.2s var(--ease);
}

.intro__chip:focus-visible {
  outline: 2px solid var(--accent2);
  outline-offset: 3px;
}

@media (hover: hover) {
  .intro__chip:hover {
    background: rgba(255, 255, 255, 0.11);
    border-color: rgba(139, 92, 246, 0.35);
    transform: translateY(-1px);
  }
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

/* —— 浏览区：左侧彩条 + 与 Android 区块同气质 —— */
.browse {
  position: relative;
  margin-bottom: clamp(24px, 4vw, 36px);
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.18), rgba(255, 255, 255, 0.03));
  box-shadow: var(--shadow);
  overflow: hidden;
  animation: sectionLift 0.85s var(--ease) 0.2s both;
}

@media (prefers-reduced-motion: reduce) {
  .browse {
    animation: none;
  }
}

@media (hover: hover) {
  .browse:hover {
    border-color: rgba(139, 92, 246, 0.35);
    box-shadow: 0 28px 80px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(139, 92, 246, 0.15);
  }
}

.browse__rail {
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

@media (prefers-reduced-motion: reduce) {
  .browse__rail {
    animation: none;
  }
}

.browse__inner {
  padding: clamp(20px, 3.5vw, 28px) clamp(20px, 3.5vw, 32px) clamp(20px, 3.5vw, 28px) clamp(24px, 4vw, 36px);
}

.browse__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.browse__title {
  margin: 0 0 4px;
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.browse__sub {
  margin: 0;
  font-size: 0.86rem;
  color: var(--muted);
  line-height: 1.4;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 40px 24px;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  background: var(--card);
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

@media (prefers-reduced-motion: reduce) {
  .state--loading {
    animation: none;
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

@media (prefers-reduced-motion: reduce) {
  .state__spinner {
    animation: none;
    border-color: rgba(34, 211, 238, 0.35);
  }
}

.state__text {
  margin: 0;
  text-align: center;
  color: var(--muted);
  max-width: 36ch;
}

.browse__tiles {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
  scroll-snap-type: x mandatory;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
}

.browse__tiles::-webkit-scrollbar {
  height: 6px;
}

.browse__tiles::-webkit-scrollbar-thumb {
  background: rgba(139, 92, 246, 0.45);
  border-radius: 999px;
}

.b-tile {
  flex: 0 0 auto;
  width: min(200px, 78vw);
  scroll-snap-align: start;
  cursor: pointer;
  transition: transform 0.25s var(--ease);
}

.b-tile:focus-visible {
  outline: 2px solid var(--accent2);
  outline-offset: 4px;
}

@media (hover: hover) {
  .b-tile:hover {
    transform: translateY(-6px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .b-tile {
    transition: none;
  }

  .b-tile:hover {
    transform: none;
  }
}

.b-tile__cover {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius);
  overflow: hidden;
  margin-bottom: 10px;
  border: 1px solid var(--line);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

.b-tile__cover--hot {
  background: linear-gradient(135deg, #fb7185, #fb923c);
  padding: 4px;
}

.b-tile__cover--latest {
  background: linear-gradient(135deg, #6366f1, #7c3aed);
  padding: 4px;
}

.b-tile__mosaic {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 4px;
  width: 100%;
  height: 100%;
  border-radius: 14px;
  overflow: hidden;
}

.b-tile__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.b-tile__badge {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18px 10px 8px;
  font-size: 0.78rem;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
}

.b-tile__meta {
  padding: 0 2px;
}

.b-tile__name {
  margin: 0 0 4px;
  font-size: 0.95rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.b-tile__hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--muted);
}

</style>
