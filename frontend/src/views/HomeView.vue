<script setup>
/**
 * HomeView —— 首页
 * ------------------------------------------------------------
 * 排布参考主流音乐 App（ArchoeraMusic 首页范式）：
 *   页头（标题 + 问候） → Hero 横幅 → 动作卡 → 横向封面栏 ×2
 *
 * Hero 双态：
 *  - 已登录且取到每日推荐 → 展示「每日推荐」（2×2 封面拼图 + 播放推荐）
 *  - 否则 → 展示品牌文案（热门第一首 + 播放热门）
 *
 * 设计：黑偏青 + 圆角矩形；不使用侧边高亮条与区块级动画渐变。
 * 全局契约：播放经 usePlaybackBridge.playTrack / playTracks 统一驱动 GlobalPlayer。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'
import { playTracks, playTrackInList } from '@/composables/usePlaybackBridge'
import { coverSrcset } from '@/utils/coverImage'
import { getUser } from '@/utils/userStore.js'

const toast = useToast()

const rankingList = ref([])
const latestList = ref([])
const rankingLoading = ref(true)
const latestLoading = ref(true)

const isLoggedIn = ref(false)
const nickname = ref('')

/** 每日推荐（需登录） */
const dailyList = ref([])
const dailyDate = ref('')

const syncLoginState = () => {
  isLoggedIn.value = !!localStorage.getItem('userToken')
  nickname.value = getUser()?.nickname || ''
}

/** 按时段问候 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const hasDaily = computed(() => isLoggedIn.value && dailyList.value.length > 0)

/** Hero：优先每日推荐，否则热门第一首 */
const heroList = computed(() => (hasDaily.value ? dailyList.value : rankingList.value))
const heroFeature = computed(() => heroList.value[0] || null)
const heroBackdrop = computed(() => heroFeature.value?.coverUrl || '')
const dailyMosaic = computed(() => dailyList.value.slice(0, 4))

/** 横向封面栏取前 12 首 */
const hotList = computed(() => rankingList.value.slice(0, 12))
const latestGrid = computed(() => latestList.value.slice(0, 12))

const loadingAll = computed(
  () => (rankingLoading.value || latestLoading.value) && !rankingList.value.length && !latestList.value.length
)

const quickLinks = computed(() => {
  const links = [
    { to: '/ranking', icon: 'trophy', title: '热门排行', desc: '按播放量排序' },
    { to: '/latest', icon: 'sparkles', title: '最新上架', desc: '刚刚入库的新歌' },
    { to: '/download', icon: 'list-music', title: '歌单迁入', desc: '网易 / QQ / 酷狗' },
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

/**
 * 每日推荐：失败/未登录时静默回退到品牌 Hero，不打扰用户。
 * 契约：Authorization 传裸 userToken（与其它用户接口一致）。
 */
const fetchDaily = async () => {
  const token = localStorage.getItem('userToken')
  if (!token) {
    dailyList.value = []
    return
  }
  try {
    const res = await fetch(`${API_CONFIG.BASE_URL}/api/user/recommendations/daily`, {
      headers: { Authorization: token },
    })
    if (!res.ok) {
      dailyList.value = []
      return
    }
    const data = await res.json()
    if (data.success && Array.isArray(data.data)) {
      dailyList.value = data.data.map((it) => ({
        ...it,
        id: it.musicId,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${it.musicId}`,
      }))
      dailyDate.value = data.date || ''
    } else {
      dailyList.value = []
    }
  } catch (error) {
    console.error('每日推荐请求失败:', error)
    dailyList.value = []
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

/** 点单曲：以所在列表为播放队列，保证能接着听「下一首」 */
const playMusic = (m, list) => {
  playTrackInList(toTrack(m), (Array.isArray(list) ? list : []).map(toTrack))
  toast.success(`开始播放：${m.title}`)
}

const playList = (list, label) => {
  if (!list.length) return
  playTracks(list.map(toTrack), 0)
  toast.success(`开始播放${label ? '：' + label : ''} ${list.length} 首`)
}

const playHero = () => {
  if (heroFeature.value) playMusic(heroFeature.value, heroList.value)
}

const playHeroList = () => {
  playList(heroList.value, hasDaily.value ? '每日推荐' : '热门')
}

function onStorage() {
  const wasLoggedIn = isLoggedIn.value
  syncLoginState()
  if (isLoggedIn.value && !wasLoggedIn) fetchDaily()
  if (!isLoggedIn.value) dailyList.value = []
}

onMounted(() => {
  syncLoginState()
  window.addEventListener('storage', onStorage)
  fetchRanking()
  fetchLatest()
  fetchDaily()
})

onUnmounted(() => {
  window.removeEventListener('storage', onStorage)
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <!-- ==================== 页头 ==================== -->
    <header class="home-head">
      <h1 class="home-head__title">首页</h1>
      <p class="home-head__greeting">
        {{ greeting }}<template v-if="isLoggedIn && nickname">，{{ nickname }}</template>，欢迎来到 Neko歌姬计划
      </p>
    </header>

    <!-- ==================== Hero 横幅 ==================== -->
    <section class="hero">
      <div
        v-if="heroBackdrop"
        class="hero__bg"
        :style="{ backgroundImage: `url(${heroBackdrop})` }"
        aria-hidden="true"
      />
      <div class="hero__scrim" aria-hidden="true" />

      <div class="hero__copy">
        <span class="hero__eyebrow">
          <NIcon name="sparkles" :size="14" />
          {{ hasDaily ? '每日推荐' : '开源 · 免费 · 无广告' }}
        </span>

        <h2 class="hero__title">{{ hasDaily ? '今日为你推荐' : '从这里开始听' }}</h2>

        <p v-if="hasDaily" class="hero__lede">
          根据你的收藏与口味生成，每天 00:00 更新<template v-if="dailyList.length">，共 {{ dailyList.length }} 首</template>。
        </p>
        <p v-else class="hero__lede">
          搜索、播放、收藏全站音乐；在客户端还能从
          <strong>网易、QQ、酷狗</strong>
          一键迁入歌单。
        </p>

        <div class="hero__actions">
          <NButton
            v-if="hasDaily"
            variant="primary"
            icon="play"
            @click="playHeroList"
          >
            播放推荐
          </NButton>
          <NButton
            v-else-if="rankingList.length"
            variant="primary"
            icon="play"
            @click="playHeroList"
          >
            播放热门
          </NButton>
          <NButton variant="secondary" icon="list-music" to="/download">
            歌单迁入
          </NButton>
        </div>
      </div>

      <button
        v-if="heroFeature"
        type="button"
        class="hero__feature"
        :class="{ 'hero__feature--mosaic': hasDaily }"
        @click="playHero"
      >
        <!-- 可见文本已含歌名/歌手，用视觉隐藏的「播放」补足动作语义：
             可访问名由内容计算，避免 aria-label 与可见文本不一致（WCAG 2.5.3） -->
        <span class="n-visually-hidden">播放</span>
        <span v-if="hasDaily" class="hero__mosaic">
          <img
            v-for="m in dailyMosaic"
            :key="m.id"
            :src="m.coverUrl"
            :srcset="coverSrcset(m.coverUrl)"
            sizes="110px"
            width="110"
            height="110"
            :alt="m.title"
            decoding="async"
            @error="handleImageError"
          />
        </span>
        <img
          v-else
          :src="heroFeature.coverUrl"
          :srcset="coverSrcset(heroFeature.coverUrl)"
          sizes="220px"
          width="220"
          height="220"
          :alt="heroFeature.title"
          decoding="async"
          @error="handleImageError"
        />

        <span class="hero__badge">
          <NIcon name="sparkles" :size="12" />
          {{ hasDaily ? '每日推荐' : '热度第 1' }}
        </span>
        <span class="hero__play"><NIcon name="play" :size="22" /></span>
        <span class="hero__feature-meta">
          <span class="hero__feature-title">{{ heroFeature.title }}</span>
          <span class="hero__feature-artist">{{ heroFeature.artist }}</span>
        </span>
      </button>
    </section>

    <!-- ==================== 动作卡 ==================== -->
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
        <NIcon name="chevron-right" :size="16" class="quick__arrow" />
      </NCard>
    </section>

    <!-- ==================== 加载态 ==================== -->
    <div v-if="loadingAll" class="loading">
      <NSpinner :size="28" />
      <p>正在加载音乐…</p>
    </div>

    <template v-else>
      <!-- ==================== 热门音乐（横向栏） ==================== -->
      <section v-if="hotList.length" class="section">
        <header class="section__head">
          <div class="section__heading">
            <h2 class="section__title">热门音乐</h2>
            <p class="section__sub">按播放量排序</p>
          </div>
          <NButton variant="ghost" size="sm" icon-after="chevron-right" to="/ranking">更多</NButton>
        </header>

        <div class="rail">
          <article
            v-for="(m, i) in hotList"
            :key="m.id"
            class="cover-card"
          >
            <div class="cover-card__art">
              <img
                :src="m.coverUrl"
                :srcset="coverSrcset(m.coverUrl)"
                sizes="152px"
                width="152"
                height="152"
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
            <button
              type="button"
              class="cover-card__hit"
              :aria-label="`播放 ${m.title}`"
              @click="playMusic(m, hotList)"
            ></button>
          </article>
        </div>
      </section>

      <!-- ==================== 最新上架（横向栏） ==================== -->
      <section v-if="latestGrid.length" class="section">
        <header class="section__head">
          <div class="section__heading">
            <h2 class="section__title">最新上架</h2>
            <p class="section__sub">刚刚入库的新歌</p>
          </div>
          <NButton variant="ghost" size="sm" icon-after="chevron-right" to="/latest">更多</NButton>
        </header>

        <div class="rail">
          <article
            v-for="m in latestGrid"
            :key="m.id"
            class="cover-card"
          >
            <div class="cover-card__art">
              <img
                :src="m.coverUrl"
                :srcset="coverSrcset(m.coverUrl)"
                sizes="152px"
                width="152"
                height="152"
                :alt="m.title"
                loading="lazy"
                decoding="async"
                @error="handleImageError"
              />
              <span class="cover-card__play"><NIcon name="play" :size="16" /></span>
            </div>
            <h3 class="cover-card__title">{{ m.title }}</h3>
            <p class="cover-card__artist">{{ m.artist }}</p>
            <button
              type="button"
              class="cover-card__hit"
              :aria-label="`播放 ${m.title}`"
              @click="playMusic(m, latestGrid)"
            ></button>
          </article>
        </div>
      </section>
    </template>
  </PageShell>
</template>

<style scoped>
/* ==================== 页头 ==================== */
.home-head {
  margin-bottom: var(--n-space-6);
}

.home-head__title {
  font-size: clamp(1.5rem, 3vw, 1.9rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
}

.home-head__greeting {
  margin-top: var(--n-space-1);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== Hero 横幅 ==================== */
.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: clamp(20px, 4vw, 44px);
  min-height: 208px;
  padding: clamp(20px, 3.5vw, 32px);
  margin-bottom: clamp(24px, 3.5vw, 36px);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  overflow: hidden;
  background: var(--n-surface);
}

/* 模糊封面底 */
.hero__bg {
  position: absolute;
  inset: -20%;
  background-size: cover;
  background-position: center;
  filter: blur(64px) saturate(1.3);
  opacity: 0.28;
  transform: scale(1.1);
}

.hero__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(100deg, rgba(4, 9, 11, 0.94) 22%, rgba(4, 9, 11, 0.6) 62%, rgba(4, 9, 11, 0.32) 100%);
}

.hero__copy {
  position: relative;
  z-index: var(--n-z-content);
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
  margin: var(--n-space-4) 0 var(--n-space-2);
  font-size: clamp(1.7rem, 4vw, 2.6rem);
  font-weight: var(--n-weight-bold);
  line-height: 1.1;
  letter-spacing: -0.03em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero__lede {
  max-width: 42ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-base);
  line-height: var(--n-leading-normal);
}

.hero__lede strong {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-top: var(--n-space-5);
}

/* ==================== Hero 右侧视觉 ==================== */
.hero__feature {
  position: relative;
  z-index: var(--n-z-content);
  width: clamp(150px, 22vw, 200px);
  aspect-ratio: 1;
  border-radius: var(--n-radius-lg);
  overflow: hidden;
  border: 1px solid var(--n-line-strong);
  background: var(--n-surface-soft);
  box-shadow: var(--n-shadow-lg);
  cursor: pointer;
  padding: 0;
}

.hero__feature > img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--n-duration-slow) var(--n-ease);
}

/* 每日推荐：2×2 拼图 */
.hero__mosaic {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 2px;
  width: 100%;
  height: 100%;
}

.hero__mosaic img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--n-duration-slow) var(--n-ease);
}

@media (hover: hover) {
  .hero__feature:hover > img,
  .hero__feature:hover .hero__mosaic img {
    transform: scale(1.05);
  }
}

.hero__badge {
  position: absolute;
  top: var(--n-space-3);
  left: var(--n-space-3);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--n-radius-xs);
  background: rgba(4, 9, 11, 0.72);
  backdrop-filter: var(--n-blur-sm);
  -webkit-backdrop-filter: var(--n-blur-sm);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-semibold);
}

.hero__play {
  position: absolute;
  right: var(--n-space-3);
  top: var(--n-space-3);
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: var(--n-radius-control);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.4);
  transition: transform var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .hero__feature:hover .hero__play {
    transform: scale(1.08);
    background: var(--n-accent-strong);
  }
}

.hero__feature-meta {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 26px var(--n-space-3) var(--n-space-3);
  text-align: left;
  background: linear-gradient(to top, rgba(4, 9, 11, 0.9), transparent);
}

.hero__feature-title {
  color: var(--n-text);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hero__feature-artist {
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 动作卡 ==================== */
.quick {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
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
  margin-bottom: clamp(28px, 4vw, 44px);
}

.section:last-child {
  margin-bottom: 0;
}

.section__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-4);
}

.section__heading {
  min-width: 0;
}

.section__title {
  font-size: 1.1rem;
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.02em;
}

.section__sub {
  margin-top: 2px;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

/* ==================== 横向封面栏 ==================== */
.rail {
  display: flex;
  gap: var(--n-space-4);
  overflow-x: auto;
  scroll-snap-type: x proximity;
  scroll-padding-inline: var(--n-space-1);
  padding: var(--n-space-1) var(--n-space-1) var(--n-space-4);
  -webkit-overflow-scrolling: touch;
}

.rail::-webkit-scrollbar {
  height: 6px;
}

.rail::-webkit-scrollbar-track {
  background: transparent;
}

.rail::-webkit-scrollbar-thumb {
  background: rgba(95, 208, 224, 0.28);
  border-radius: var(--n-radius-pill);
}

@media (hover: hover) {
  .rail::-webkit-scrollbar-thumb:hover {
    background: rgba(95, 208, 224, 0.45);
  }
}

.cover-card {
  position: relative;
  flex: 0 0 auto;
  width: 152px;
  scroll-snap-align: start;
  min-width: 0;
}

/* 整卡点击：用真实 button 覆盖，避免把 role="button" 加在 article 上（ARIA 角色不匹配） */
.cover-card__hit {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  border-radius: var(--n-radius-sm);
  cursor: pointer;
  z-index: 1;
}

.cover-card__hit:focus-visible {
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

@media (hover: none) {
  .cover-card__play {
    opacity: 1;
    transform: none;
  }
}

.cover-card__title {
  margin-top: var(--n-space-3);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cover-card__artist {
  margin-top: 1px;
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    gap: var(--n-space-6);
  }

  .hero__feature {
    width: 100%;
    max-width: 220px;
    justify-self: start;
  }

  .hero__actions :deep(.n-btn) {
    flex: 1 1 auto;
    justify-content: center;
  }
}

/* ==================== 手机竖屏：横向卡片栏 ====================
   原来卡片是 4px 内边距 + 152px 固定宽，在 375px 屏上只能露出 2 张，
   既看不出「还能往右滑」，卡片又没贴到屏幕边缘、与上方标题差 4px。
   改为手机上通行的「出血滚动」：负外边距抵消页面留白，让卡片能滑到
   屏幕边缘，再靠 padding-inline + scroll-padding-inline 把首尾两张
   对齐回正文左边界。 */
@media (max-width: 560px) {
  .rail {
    gap: var(--n-space-3);
    /* 抵消 PageShell 的内容留白（.section 本身无内边距，故可直接出血） */
    margin-inline: calc(-1 * var(--n-content-gutter));
    padding-inline: var(--n-content-gutter);
    /* 吸附位置也按留白计算，滑停时首张与标题对齐 */
    scroll-padding-inline: var(--n-content-gutter);
    /* 触摸端不用横向滚动条：占高度、且手指本来也拖不到它 */
    scrollbar-width: none;
  }

  .rail::-webkit-scrollbar {
    display: none;
  }

  /* 128px 可露出约 2.5 张 —— 露出的半张本身就是「可横滑」的提示 */
  .cover-card {
    width: 128px;
  }

  .cover-card__play {
    width: 40px;
    height: 40px;
  }

  .cover-card__title {
    margin-top: var(--n-space-2);
  }

  .quick__card {
    gap: var(--n-space-3);
  }
}
</style>
