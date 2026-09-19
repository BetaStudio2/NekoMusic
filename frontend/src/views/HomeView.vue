<script setup>
/**
 * HomeView —— 首页
 * ------------------------------------------------------------
 * 完整重排：Hero + 快捷入口 + 歌单迁入 + 热门 + 最新。
 * 设计约定：黑偏青基调 + 圆角矩形语言（去胶囊）；不使用侧边高亮条与
 * 区块级动画渐变；层次由排版与留白建立，全页仅保留一处主强调。
 * 全局契约：播放经 hash #play / #playlist 驱动 GlobalPlayer（与列表页一致）。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NTag, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'

const toast = useToast()

const rankingList = ref([])
const latestList = ref([])
const rankingLoading = ref(true)
const latestLoading = ref(true)

const isLoggedIn = ref(false)
const syncLoginState = () => {
  const t = localStorage.getItem('userToken')
  isLoggedIn.value = t != null && t !== ''
}

/** 封面墙：热门取前 6，最新取前 12 */
const hotList = computed(() => rankingList.value.slice(0, 6))
const latestGrid = computed(() => latestList.value.slice(0, 12))
/** Hero 主视觉：热门第一首 */
const featured = computed(() => rankingList.value[0] || null)
const loadingGrids = computed(
  () => (rankingLoading.value || latestLoading.value) && !rankingList.value.length && !latestList.value.length
)

const quickLinks = computed(() => {
  const links = [
    { to: '/ranking', icon: 'trophy', title: '热门排行', desc: '按播放量排序' },
    { to: '/latest', icon: 'sparkles', title: '最新上架', desc: '刚刚入库的新歌' },
    { to: '/download#netease-migrate', icon: 'list-music', title: '歌单迁入', desc: '网易 / QQ / 酷狗' },
  ]
  links.push(
    isLoggedIn.value
      ? { to: '/upload', icon: 'upload', title: '上传音乐', desc: '分享你的作品' }
      : { to: '/favorites', icon: 'heart', title: '我的收藏', desc: '登录后同步' }
  )
  return links
})

const withCover = (item) => ({
  ...item,
  coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`,
})

const fetchRanking = async () => {
  rankingLoading.value = true
  try {
    const res = await fetch(`${API_CONFIG.BASE_URL}/api/music/ranking`)
    const data = await res.json()
    if (data.success && data.data) rankingList.value = data.data.map(withCover)
    else console.error('获取排行榜失败:', data.message)
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
    const res = await fetch(`${API_CONFIG.BASE_URL}/api/music/latest?limit=300`)
    const data = await res.json()
    if (data.success && data.data) latestList.value = data.data.map(withCover)
    else console.error('获取最新音乐失败:', data.message)
  } catch (error) {
    console.error('最新音乐请求失败:', error)
    toast.error('加载最新音乐失败')
  } finally {
    latestLoading.value = false
  }
}

const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

const toTrack = (m) => ({
  id: m.id,
  title: m.title,
  artist: m.artist,
  album: m.album,
  duration: m.duration,
})

const playMusic = (m) => {
  window.location.hash = `#play=${encodeURIComponent(JSON.stringify(toTrack(m)))}`
  toast.success(`开始播放：${m.title}`)
}

const playList = (list) => {
  if (!list.length) return
  const payload = encodeURIComponent(JSON.stringify(list.map(toTrack)))
  window.location.hash = `#playlist=${payload}&index=0`
  toast.success(`开始播放热门 ${list.length} 首`)
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

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <!-- ==================== Hero ==================== -->
    <section class="hero">
      <div class="hero__copy">
        <span class="hero__eyebrow">
          <NIcon name="sparkles" :size="14" />
          开源 · 免费 · 无广告
        </span>
        <h1 class="hero__title">从这里开始听</h1>
        <p class="hero__lede">
          搜索、播放、收藏全站音乐；在客户端还能从
          <strong class="hero__strong">网易、QQ、酷狗</strong>
          一键迁入歌单。开源免费，无绑架式社交。
        </p>
        <div class="hero__actions">
          <NButton
            v-if="hotList.length"
            variant="primary"
            icon="play"
            @click="playList(rankingList)"
          >
            播放热门
          </NButton>
          <NButton variant="secondary" icon="list-music" to="/download#netease-migrate">
            歌单迁入
          </NButton>
          <NButton variant="ghost" icon="download" to="/download">下载客户端</NButton>
        </div>
      </div>

      <div class="hero__art" :class="{ 'hero__art--empty': !featured }">
        <div
          v-if="featured"
          class="hero__glow"
          :style="{ backgroundImage: `url(${featured.coverUrl})` }"
          aria-hidden="true"
        />
        <div class="hero__frame">
          <img
            v-if="featured"
            :src="featured.coverUrl"
            :alt="featured.title"
            decoding="async"
            @error="handleImageError"
          />
          <div v-else class="hero__placeholder">
            <NIcon name="music" :size="44" />
          </div>

          <button
            v-if="featured"
            type="button"
            class="hero__play"
            :aria-label="`播放 ${featured.title}`"
            @click="playMusic(featured)"
          >
            <NIcon name="play" :size="24" />
          </button>

          <NTag v-if="featured" class="hero__tag" variant="accent" size="sm">
            <NIcon name="flame" :size="12" />
            热度第 1
          </NTag>
        </div>
      </div>
    </section>

    <!-- ==================== 快捷入口 ==================== -->
    <section class="quick" aria-label="快捷入口">
      <NCard
        v-for="q in quickLinks"
        :key="q.to"
        as="router-link"
        :to="q.to"
        hoverable
        pad="md"
        class="quick__card"
      >
        <span class="quick__icon"><NIcon :name="q.icon" :size="20" /></span>
        <span class="quick__body">
          <span class="quick__title">{{ q.title }}</span>
          <span class="quick__desc">{{ q.desc }}</span>
        </span>
        <NIcon name="arrow-right" :size="16" class="quick__arrow" />
      </NCard>
    </section>

    <!-- ==================== 歌单迁入 ==================== -->
    <NCard pad="lg" class="migrate">
      <span class="migrate__icon"><NIcon name="list-music" :size="24" /></span>
      <div class="migrate__body">
        <NTag variant="accent" size="sm">歌单迁入</NTag>
        <h2 class="migrate__title">从网易、QQ、酷狗过来？</h2>
        <p class="migrate__text">
          在 Android / PC 客户端粘贴歌单链接或 ID，自动拉取曲目并在站内曲库中匹配后导入你的歌单。
        </p>
      </div>
      <NButton
        class="migrate__cta"
        variant="outline"
        icon-after="arrow-right"
        to="/download#netease-migrate"
      >
        查看怎么操作
      </NButton>
    </NCard>

    <!-- ==================== 加载态 ==================== -->
    <div v-if="loadingGrids" class="loading">
      <NSpinner :size="28" />
      <p>正在加载音乐…</p>
    </div>

    <template v-else>
      <!-- ==================== 热门音乐 ==================== -->
      <section v-if="hotList.length" class="section">
        <header class="section__head">
          <div class="section__heading">
            <h2 class="section__title">热门音乐</h2>
            <p class="section__sub">按播放量排序的热门曲目</p>
          </div>
          <NButton variant="ghost" size="sm" icon-after="arrow-right" to="/ranking">
            查看全部
          </NButton>
        </header>

        <div class="grid">
          <article
            v-for="(m, i) in hotList"
            :key="m.id"
            class="cover-card"
            tabindex="0"
            role="button"
            :aria-label="`播放 ${m.title}`"
            @click="playMusic(m)"
            @keydown.enter.prevent="playMusic(m)"
          >
            <div class="cover-card__art">
              <img
                :src="m.coverUrl"
                :alt="m.title"
                loading="lazy"
                decoding="async"
                @error="handleImageError"
              />
              <span class="cover-card__rank">{{ i + 1 }}</span>
              <span class="cover-card__play"><NIcon name="play" :size="16" /></span>
            </div>
            <h3 class="cover-card__title">{{ m.title }}</h3>
            <p class="cover-card__artist">{{ m.artist }}</p>
          </article>
        </div>
      </section>

      <!-- ==================== 最新上架 ==================== -->
      <section v-if="latestGrid.length" class="section">
        <header class="section__head">
          <div class="section__heading">
            <h2 class="section__title">最新上架</h2>
            <p class="section__sub">刚刚入库的新歌</p>
          </div>
          <NButton variant="ghost" size="sm" icon-after="arrow-right" to="/latest">
            查看全部
          </NButton>
        </header>

        <div class="grid">
          <article
            v-for="m in latestGrid"
            :key="m.id"
            class="cover-card"
            tabindex="0"
            role="button"
            :aria-label="`播放 ${m.title}`"
            @click="playMusic(m)"
            @keydown.enter.prevent="playMusic(m)"
          >
            <div class="cover-card__art">
              <img
                :src="m.coverUrl"
                :alt="m.title"
                loading="lazy"
                decoding="async"
                @error="handleImageError"
              />
              <span class="cover-card__play"><NIcon name="play" :size="16" /></span>
            </div>
            <h3 class="cover-card__title">{{ m.title }}</h3>
            <p class="cover-card__artist">{{ m.artist }}</p>
          </article>
        </div>
      </section>
    </template>
  </PageShell>
</template>

<style scoped>
/* ==================== Hero ==================== */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  align-items: center;
  gap: clamp(24px, 5vw, 56px);
  padding: clamp(12px, 3vw, 32px) 0 clamp(32px, 5vw, 56px);
}

.hero__copy {
  min-width: 0;
}

.hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  padding: 5px 11px;
  border-radius: var(--n-radius-xs);
  background: var(--n-accent-soft);
  border: 1px solid var(--n-accent-line);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-semibold);
  letter-spacing: 0.02em;
}

.hero__title {
  margin: var(--n-space-5) 0 var(--n-space-3);
  font-size: clamp(2rem, 5vw, 3.2rem);
  font-weight: var(--n-weight-bold);
  line-height: 1.08;
  letter-spacing: -0.03em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero__lede {
  max-width: 44ch;
  color: var(--n-text-muted);
  font-size: clamp(0.95rem, 1.6vw, 1.05rem);
  line-height: var(--n-leading-normal);
}

.hero__strong {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-top: var(--n-space-6);
}

/* Hero 主视觉：热门第一首封面 */
.hero__art {
  position: relative;
  justify-self: center;
  width: min(320px, 70vw);
  aspect-ratio: 1;
}

.hero__glow {
  position: absolute;
  inset: 8%;
  border-radius: var(--n-radius-xl);
  background-size: cover;
  background-position: center;
  filter: blur(42px) saturate(1.25);
  opacity: 0.38;
  transform: scale(0.9);
}

.hero__frame {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: var(--n-radius-xl);
  overflow: hidden;
  border: 1px solid var(--n-line-strong);
  background: var(--n-surface-soft);
  box-shadow: var(--n-shadow-lg);
}

.hero__frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hero__placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--n-text-faint);
}

.hero__play {
  position: absolute;
  right: var(--n-space-4);
  bottom: var(--n-space-4);
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: var(--n-radius-lg);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.42);
  transition: transform var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .hero__play:hover {
    transform: translateY(-2px);
    background: var(--n-accent-strong);
  }
}

.hero__tag {
  position: absolute;
  top: var(--n-space-4);
  left: var(--n-space-4);
  background: rgba(4, 9, 11, 0.72);
  backdrop-filter: var(--n-blur-sm);
  -webkit-backdrop-filter: var(--n-blur-sm);
}

/* ==================== 快捷入口 ==================== */
.quick {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: var(--n-space-4);
  margin-bottom: clamp(28px, 4vw, 44px);
}

.quick__card {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  text-decoration: none;
  color: inherit;
}

.quick__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: var(--n-radius-sm);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.quick__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.quick__title {
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.quick__desc {
  font-size: var(--n-text-sm);
  color: var(--n-text-muted);
}

.quick__arrow {
  flex: none;
  color: var(--n-text-faint);
  transition: transform var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .quick__card:hover .quick__arrow {
    transform: translateX(3px);
    color: var(--n-accent);
  }
}

/* ==================== 歌单迁入 ==================== */
.migrate {
  display: flex;
  align-items: center;
  gap: var(--n-space-6);
  margin-bottom: clamp(32px, 5vw, 56px);
}

.migrate__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius);
  background: var(--n-surface-soft);
  border: 1px solid var(--n-line);
  color: var(--n-accent-strong);
}

.migrate__body {
  flex: 1;
  min-width: 0;
}

.migrate__title {
  margin: var(--n-space-2) 0 var(--n-space-1);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.01em;
}

.migrate__text {
  color: var(--n-text-muted);
  font-size: var(--n-text-base);
  max-width: 64ch;
}

.migrate__cta {
  flex: none;
}

/* ==================== 加载态 ==================== */
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-16) 0;
  color: var(--n-text-muted);
}

/* ==================== 区块 ==================== */
.section {
  margin-bottom: clamp(32px, 5vw, 56px);
}

.section:last-child {
  margin-bottom: 0;
}

.section__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-6);
}

.section__heading {
  min-width: 0;
}

.section__title {
  font-size: clamp(1.2rem, 2.4vw, 1.5rem);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.02em;
}

.section__sub {
  margin-top: var(--n-space-1);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 封面墙 ==================== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: var(--n-space-5) var(--n-space-4);
}

.cover-card {
  cursor: pointer;
  min-width: 0;
}

.cover-card:focus-visible {
  outline: var(--n-focus-ring);
  outline-offset: 3px;
  border-radius: var(--n-radius-sm);
}

.cover-card__art {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--n-radius);
  overflow: hidden;
  border: 1px solid var(--n-line);
  background: var(--n-surface-soft);
  transition: border-color var(--n-duration-fast) var(--n-ease);
}

.cover-card__art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--n-duration-slow) var(--n-ease);
}

@media (hover: hover) {
  .cover-card:hover .cover-card__art {
    border-color: var(--n-line-strong);
  }

  .cover-card:hover .cover-card__art img {
    transform: scale(1.05);
  }
}

.cover-card__rank {
  position: absolute;
  top: var(--n-space-2);
  left: var(--n-space-2);
  display: grid;
  place-items: center;
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  border-radius: var(--n-radius-xs);
  background: rgba(4, 9, 11, 0.72);
  backdrop-filter: var(--n-blur-sm);
  -webkit-backdrop-filter: var(--n-blur-sm);
  color: var(--n-text);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
  font-variant-numeric: tabular-nums;
}

.cover-card__play {
  position: absolute;
  right: var(--n-space-2);
  bottom: var(--n-space-2);
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--n-radius-sm);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  opacity: 0;
  transform: translateY(6px);
  transition: opacity var(--n-duration-fast) var(--n-ease), transform var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .cover-card:hover .cover-card__play {
    opacity: 1;
    transform: none;
  }
}

/* 触屏设备常显播放键 */
@media (hover: none) {
  .cover-card__play {
    opacity: 1;
    transform: none;
  }
}

.cover-card__title {
  margin-top: var(--n-space-3);
  font-size: var(--n-text-base);
  font-weight: var(--n-weight-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cover-card__artist {
  margin-top: 2px;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    gap: var(--n-space-8);
  }

  .hero__art {
    justify-self: start;
    width: min(260px, 60vw);
    order: -1;
  }

  .migrate {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--n-space-4);
  }

  .migrate__cta {
    align-self: stretch;
    justify-content: center;
  }
}

@media (max-width: 560px) {
  .hero__actions :deep(.n-btn) {
    flex: 1 1 auto;
    justify-content: center;
  }

  .grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
}
</style>
