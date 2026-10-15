<script setup>
/**
 * PrivacyPolicyView —— 隐私政策
 * ------------------------------------------------------------
 * 长文静态页。保留全部文案；去除左侧高亮条，提示块改为图标 + 淡色表面。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { replaceUrlSafely } from '@/utils/routerHistory'
import { t } from '@/i18n'
import NIcon from '@/icons/NIcon.vue'
import { NCard, NTag } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

const toc = computed(() => [
  { id: 'collect', label: t('privacy.s1') },
  { id: 'permissions', label: t('privacy.s2') },
  { id: 'storage-tech', label: t('privacy.s3') },
  { id: 'third-party', label: t('privacy.s4') },
  { id: 'protect', label: t('privacy.s5') },
  { id: 'retain', label: t('privacy.s6') },
  { id: 'rights', label: t('privacy.s7') },
  { id: 'children', label: t('privacy.s8') },
  { id: 'updates', label: t('privacy.s9') },
  { id: 'contact', label: t('privacy.s10') },
])

/* ===== 目录：滚动高亮（scroll-spy）+ 点击平滑滚动 ===== */
const activeId = ref('')
/**
 * 与 .block 的 scroll-margin-top 对齐的停靠线：
 * 顶栏在手机上会变成两行并叠加安全区（约 100–150px），写死 80 会让目录
 * 高亮与实际章节错位。这里读取 App 注入到 #app 的实测高度。
 */
function headerHeight() {
  const raw = getComputedStyle(document.documentElement).getPropertyValue('--app-header-h')
  const appEl = document.getElementById('app')
  const v = appEl ? getComputedStyle(appEl).getPropertyValue('--app-header-h') : raw
  return parseFloat(v) || 64
}
/** 点击目录后的平滑滚动期间锁定高亮，避免被途经章节覆盖 */
let spyLocked = false
let unlockTimer = null

function unlockSpy() {
  spyLocked = false
  if (unlockTimer) {
    clearTimeout(unlockTimer)
    unlockTimer = null
  }
}

function scrollTo(id) {
  const el = document.getElementById(id)
  if (!el) return

  activeId.value = id
  spyLocked = true
  if (unlockTimer) clearTimeout(unlockTimer)
  unlockTimer = setTimeout(unlockSpy, 800) // 兜底：scrollend 不可靠时恢复跟随

  // scrollIntoView 会遵循 .block 的 scroll-margin-top，无需手算顶栏高度
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' })

  // 保留 vue-router 的 history.state，只把 hash 换掉
  replaceUrlSafely(`#${id}`)
}

function onScroll() {
  if (spyLocked || !toc.value.length) return
  let current = ''
  for (const item of toc.value) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= headerHeight() + 16) current = item.id
  }
  if (!current) current = toc.value[0].id
  activeId.value = current
}

onMounted(() => {
  window.scrollTo(0, 0)
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (unlockTimer) clearTimeout(unlockTimer)
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="xwide">
    <div class="doc">
      <!-- 目录：桌面端 sticky 侧栏，窄屏置顶 -->
      <aside class="doc__toc" :aria-label="$t('privacy.tocAria')">
        <p class="doc__toc-title">{{ $t('privacy.tocTitle') }}</p>
        <nav class="doc__toc-nav">
          <a
            v-for="item in toc"
            :key="item.id"
            :href="`#${item.id}`"
            class="doc__toc-link"
            :class="{ 'doc__toc-link--active': activeId === item.id }"
            @click.prevent="scrollTo(item.id)"
          >
            {{ item.label }}
          </a>
        </nav>
      </aside>

      <div class="doc__main">
    <!-- 头部 -->
    <NCard pad="lg" class="block hero">
      <p class="hero__eyebrow">{{ $t('privacy.eyebrow') }}</p>
      <h1 class="hero__title">{{ $t('privacy.title') }}</h1>
      <p class="hero__sub">{{ $t('privacy.sub') }}</p>
      <div class="hero__meta">
        <NTag size="sm">{{ $t('privacy.published') }}</NTag>
        <NTag size="sm">{{ $t('privacy.updated') }}</NTag>
        <NTag size="sm">{{ $t('privacy.effective') }}</NTag>
      </div>
    </NCard>

    <!-- 引言 -->
    <NCard pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.introTitle') }}</h2>
      <p class="para">{{ $t('privacy.introP1') }}</p>
      <p class="para">{{ $t('privacy.introP2') }}</p>
      <p class="note">
        <NIcon name="info" :size="16" class="note__icon" />
        <span>{{ $t('privacy.introNote') }}</span>
      </p>
    </NCard>

    <!-- 一 -->
    <NCard id="collect" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s1') }}</h2>
      <p class="para">{{ $t('privacy.c1P1') }}</p>
      <p class="note">
        <NIcon name="info" :size="16" class="note__icon" />
        <span>{{ $t('privacy.c1Note') }}</span>
      </p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>{{ $t('privacy.c1h1') }}</th>
              <th>{{ $t('privacy.c1h2') }}</th>
              <th>{{ $t('privacy.c1h3') }}</th>
              <th>{{ $t('privacy.c1h4') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ $t('privacy.c1r1c1') }}</td>
              <td>
                {{ $t('privacy.c1r1c2_1') }}<strong class="sensitive">{{ $t('privacy.c1r1c2_2') }}</strong>{{ $t('privacy.c1r1c2_3') }}<strong class="sensitive">{{ $t('privacy.c1r1c2_4') }}</strong>{{ $t('privacy.c1r1c2_5') }}
              </td>
              <td>{{ $t('privacy.c1r1c3') }}</td>
              <td>{{ $t('privacy.c1r1c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c1r2c1') }}</td>
              <td>
                {{ $t('privacy.c1r2c2_1') }}<strong class="sensitive">{{ $t('privacy.c1r2c2_2') }}</strong>{{ $t('privacy.c1r2c2_3') }}
              </td>
              <td>{{ $t('privacy.c1r2c3') }}</td>
              <td>{{ $t('privacy.c1r2c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c1r3c1') }}</td>
              <td>{{ $t('privacy.c1r3c2') }}</td>
              <td>{{ $t('privacy.c1r3c3') }}</td>
              <td>{{ $t('privacy.c1r3c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c1r4c1') }}</td>
              <td>
                {{ $t('privacy.c1r4c2_1') }}<strong class="sensitive">{{ $t('privacy.c1r4c2_2') }}</strong>{{ $t('privacy.c1r4c2_3') }}
              </td>
              <td>{{ $t('privacy.c1r4c3') }}</td>
              <td>{{ $t('privacy.c1r4c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c1r5c1') }}</td>
              <td>{{ $t('privacy.c1r5c2') }}</td>
              <td>{{ $t('privacy.c1r5c3') }}</td>
              <td>{{ $t('privacy.c1r5c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c1r6c1') }}</td>
              <td>
                <strong class="sensitive">{{ $t('privacy.c1r6c2_1') }}</strong>{{ $t('privacy.c1r6c2_2') }}
              </td>
              <td>{{ $t('privacy.c1r6c3') }}</td>
              <td>{{ $t('privacy.c1r6c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c1r7c1') }}</td>
              <td>{{ $t('privacy.c1r7c2') }}</td>
              <td>{{ $t('privacy.c1r7c3') }}</td>
              <td>{{ $t('privacy.c1r7c4') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3 class="block__sub">{{ $t('privacy.c1sub') }}</h3>
      <p class="para">{{ $t('privacy.c1subP') }}</p>
    </NCard>

    <!-- 二 -->
    <NCard id="permissions" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s2') }}</h2>
      <p class="para">{{ $t('privacy.c2P1') }}</p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>{{ $t('privacy.c2h1') }}</th>
              <th>{{ $t('privacy.c2h2') }}</th>
              <th>{{ $t('privacy.c2h3') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ $t('privacy.c2r1c1') }}</td>
              <td>{{ $t('privacy.c2r1c2') }}</td>
              <td>{{ $t('privacy.c2r1c3') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c2r2c1') }}</td>
              <td>{{ $t('privacy.c2r2c2') }}</td>
              <td>{{ $t('privacy.c2r2c3') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c2r3c1') }}</td>
              <td>{{ $t('privacy.c2r3c2') }}</td>
              <td>{{ $t('privacy.c2r3c3') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c2r4c1') }}</td>
              <td>{{ $t('privacy.c2r4c2') }}</td>
              <td>{{ $t('privacy.c2r4c3') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c2r5c1') }}</td>
              <td>{{ $t('privacy.c2r5c2') }}</td>
              <td>{{ $t('privacy.c2r5c3') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c2r6c1') }}</td>
              <td>{{ $t('privacy.c2r6c2') }}</td>
              <td>{{ $t('privacy.c2r6c3') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c2r7c1') }}</td>
              <td>{{ $t('privacy.c2r7c2') }}</td>
              <td>{{ $t('privacy.c2r7c3') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </NCard>

    <!-- 三 -->
    <NCard id="storage-tech" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s3') }}</h2>
      <p class="para">{{ $t('privacy.c3P1') }}</p>
      <p class="para">{{ $t('privacy.c3P2') }}</p>
    </NCard>

    <!-- 四 -->
    <NCard id="third-party" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s4') }}</h2>
      <p class="para">{{ $t('privacy.c4P1') }}</p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>{{ $t('privacy.c4h1') }}</th>
              <th>{{ $t('privacy.c4h2') }}</th>
              <th>{{ $t('privacy.c4h3') }}</th>
              <th>{{ $t('privacy.c4h4') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ $t('privacy.c4r1c1') }}</td>
              <td>{{ $t('privacy.c4r1c2') }}</td>
              <td>{{ $t('privacy.c4r1c3') }}</td>
              <td>{{ $t('privacy.c4r1c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c4r2c1') }}</td>
              <td>
                <strong class="sensitive">{{ $t('privacy.c4r2c2_1') }}</strong>{{ $t('privacy.c4r2c2_2') }}
              </td>
              <td>{{ $t('privacy.c4r2c3') }}</td>
              <td>{{ $t('privacy.c4r2c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c4r3c1') }}</td>
              <td>{{ $t('privacy.c4r3c2') }}</td>
              <td>{{ $t('privacy.c4r3c3') }}</td>
              <td>{{ $t('privacy.c4r3c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c4r4c1') }}</td>
              <td>{{ $t('privacy.c4r4c2') }}</td>
              <td>{{ $t('privacy.c4r4c3') }}</td>
              <td>{{ $t('privacy.c4r4c4') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c4r5c1') }}</td>
              <td>{{ $t('privacy.c4r5c2') }}</td>
              <td>{{ $t('privacy.c4r5c3') }}</td>
              <td>{{ $t('privacy.c4r5c4') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3 class="block__sub">{{ $t('privacy.c4sub') }}</h3>
      <p class="para">{{ $t('privacy.c4subP1') }}</p>
      <p class="para">{{ $t('privacy.c4subP2') }}</p>
    </NCard>

    <!-- 五 -->
    <NCard id="protect" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s5') }}</h2>
      <p class="para">{{ $t('privacy.c5P1') }}</p>
      <p class="para">{{ $t('privacy.c5P2') }}</p>
    </NCard>

    <!-- 六 -->
    <NCard id="retain" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s6') }}</h2>
      <p class="para">{{ $t('privacy.c6P1') }}</p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>{{ $t('privacy.c6h1') }}</th>
              <th>{{ $t('privacy.c6h2') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ $t('privacy.c6r1c1') }}</td>
              <td>{{ $t('privacy.c6r1c2') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c6r2c1') }}</td>
              <td>{{ $t('privacy.c6r2c2') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c6r3c1') }}</td>
              <td>{{ $t('privacy.c6r3c2') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c6r4c1') }}</td>
              <td>{{ $t('privacy.c6r4c2') }}</td>
            </tr>
            <tr>
              <td>{{ $t('privacy.c6r5c1') }}</td>
              <td>{{ $t('privacy.c6r5c2') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </NCard>

    <!-- 七 -->
    <NCard id="rights" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s7') }}</h2>
      <p class="para">{{ $t('privacy.c7P1') }}</p>
      <ul class="list">
        <li>{{ $t('privacy.c7li1') }}</li>
        <li>{{ $t('privacy.c7li2') }}</li>
        <li>{{ $t('privacy.c7li3') }}</li>
        <li>{{ $t('privacy.c7li4') }}</li>
      </ul>
      <p class="note">
        <NIcon name="info" :size="16" class="note__icon" />
        <span>{{ $t('privacy.c7Note') }}</span>
      </p>
    </NCard>

    <!-- 八 -->
    <NCard id="children" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s8') }}</h2>
      <p class="para">{{ $t('privacy.c8P1') }}</p>
      <p class="para">{{ $t('privacy.c8P2') }}</p>
    </NCard>

    <!-- 九 -->
    <NCard id="updates" pad="lg" class="block">
      <h2 class="block__title">{{ $t('privacy.s9') }}</h2>
      <p class="para">{{ $t('privacy.c9P1') }}</p>
      <p class="para">{{ $t('privacy.c9P2') }}</p>
    </NCard>

    <!-- 十 -->
    <NCard id="contact" pad="lg" class="block block--last">
      <h2 class="block__title">{{ $t('privacy.s10') }}</h2>
      <p class="para">{{ $t('privacy.c10P1') }}</p>
      <ul class="list">
        <li>{{ $t('privacy.c10li1') }}<a href="mailto:support@nekocore.cn">support@nekocore.cn</a></li>
        <li>{{ $t('privacy.c10li2') }}<a href="https://www.nekocore.cn/" target="_blank" rel="noopener noreferrer">https://www.nekocore.cn/</a></li>
        <li>{{ $t('privacy.c10li3') }}<a href="https://qm.qq.com/q/Q9HkDi6Ewk" target="_blank" rel="noopener noreferrer">{{ $t('privacy.c10GroupName') }}</a></li>
      </ul>
      <p class="para">{{ $t('privacy.c10P2') }}</p>
    </NCard>
      </div>
    </div>
  </PageShell>
</template>

<style scoped>
.block {
  margin-bottom: var(--n-space-4);
  /* 顶栏高度在手机上会变（两行布局 + 安全区），必须用 App 注入的实测值，
     否则锚点跳转后标题会被顶栏盖住 */
  scroll-margin-top: calc(var(--app-header-h, var(--n-header-height)) + var(--n-space-4));
}

.block--last {
  margin-bottom: 0;
}

.block__title {
  margin: 0 0 var(--n-space-4);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.01em;
}

.block__sub {
  margin: var(--n-space-6) 0 var(--n-space-3);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
}

.para {
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.para:last-child {
  margin-bottom: 0;
}

/* ===== 提示块（原紫色左边条 → 图标 + 淡色表面）===== */
.note {
  display: flex;
  gap: var(--n-space-3);
  margin: var(--n-space-4) 0 0;
  padding: var(--n-space-4);
  border: 1px solid var(--n-accent-line);
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-text);
  font-size: var(--n-text-base);
  line-height: var(--n-leading-normal);
}

.note__icon {
  flex: none;
  margin-top: 3px;
  color: var(--n-accent-strong);
}

/* ===== 列表（原青色左边条 → 中性圆点）===== */
.list {
  margin: var(--n-space-3) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3);
}

.list li {
  position: relative;
  padding-left: var(--n-space-5);
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

.list li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 0.62em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--n-accent);
  opacity: 0.8;
}

.list a {
  font-weight: var(--n-weight-semibold);
}

/* ===== 头部 ===== */
.hero__eyebrow {
  margin: 0 0 var(--n-space-3);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero__title {
  margin: 0 0 var(--n-space-3);
  font-size: clamp(1.9rem, 4.5vw, 2.8rem);
  font-weight: var(--n-weight-bold);
  line-height: 1.08;
  letter-spacing: -0.03em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero__sub {
  max-width: 760px;
  margin: 0;
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-2);
  margin-top: var(--n-space-5);
}

/* ===== 布局：正文 + 目录侧栏 ===== */
.doc {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-5);
}

/* 窄屏：目录置顶（无容器外观，仅标题 + 链接） */
.doc__toc {
  order: -1;
}

.doc__main {
  order: 1;
  min-width: 0;
}

.doc__toc-title {
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.doc__toc-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.doc__toc-link {
  display: block;
  padding: var(--n-space-2) var(--n-space-3);
  border-radius: var(--n-radius-xs);
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
  transition:
    color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .doc__toc-link:hover {
    color: var(--n-text-muted);
    background: var(--n-surface-soft);
  }
}

.doc__toc-link--active {
  color: var(--n-accent-strong);
  background: var(--n-accent-soft);
}

/* 桌面端：正文限宽在左，目录在右并随滚动常驻 */
@media (min-width: 1024px) {
  .doc {
    flex-direction: row;
    justify-content: center;
    align-items: flex-start;
    gap: clamp(32px, 4vw, 56px);
  }

  .doc__main {
    order: 1;
    flex: 1 1 auto;
    /* 长文限宽，避免每行过长难以阅读 */
    max-width: 1100px;
  }

  /* 目录必须在正文之后（order 2），否则会排到左侧 */
  .doc__toc {
    order: 2;
    flex: 0 0 240px;
    position: sticky;
    top: calc(var(--app-header-h, var(--n-header-height)) + var(--n-space-5));
    max-height: calc(100dvh - var(--app-header-h, var(--n-header-height)) - var(--n-space-10));
    overflow-y: auto;
  }
}

/* ===== 表格 ===== */
.table-wrap {
  width: 100%;
  margin: var(--n-space-5) 0;
  overflow-x: auto;
  /* 手机上 4 列表格要反复左右拖，见下方 ≤560 的卡片化改造 */
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius);
  background: var(--n-surface-sunken);
}

.info-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.info-table th,
.info-table td {
  border-bottom: 1px solid var(--n-line-subtle);
  padding: 13px 14px;
  text-align: left;
  vertical-align: top;
}

.info-table th {
  background: var(--n-accent-soft);
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
}

.info-table tr:last-child td {
  border-bottom: 0;
}

.info-table td:first-child {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

/* ===== 手机竖屏：表格卡片化 =====
   760px 最小宽度在 375px 屏上只能露出约四成，用户要反复左右拖。
   首列（「功能或场景」「权限或能力」）本就是该行的分类名，
   因此直接隐藏表头、把每行堆叠成一张卡，无需改动 HTML。 */
@media (max-width: 560px) {
  .table-wrap {
    overflow-x: visible;
  }

  .info-table {
    min-width: 0;
  }

  .info-table thead {
    display: none;
  }

  .info-table,
  .info-table tbody,
  .info-table tr,
  .info-table td {
    display: block;
    width: 100%;
  }

  .info-table tr {
    padding: var(--n-space-3) var(--n-space-4);
    border-bottom: 1px solid var(--n-line-subtle);
  }

  .info-table tr:last-child {
    border-bottom: 0;
  }

  .info-table td {
    border-bottom: 0;
    padding: 0 0 var(--n-space-2);
  }

  .info-table td:first-child {
    padding-bottom: var(--n-space-1);
    font-size: var(--n-text-base);
  }

  .info-table td:last-child {
    padding-bottom: 0;
  }

  /* 第 2 列起挂一条竖线，表示「从属于上方分类」 */
  .info-table td:not(:first-child) {
    margin-left: 2px;
    padding-left: var(--n-space-3);
    border-left: 2px solid var(--n-line);
  }
}

.sensitive {
  color: var(--n-warning);
  font-weight: var(--n-weight-semibold);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
