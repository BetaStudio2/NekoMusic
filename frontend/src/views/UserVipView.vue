<template>
  <AmbientBackdrop />

  <PageShell width="wide">
    <div class="vip">
      <!-- 左：用户 + 套餐 -->
      <div class="vip__main">
        <div v-if="user" class="vip__user">
          <div class="vip__avatar">
            <img
              v-if="!avatarBroken"
              :src="userAvatarUrl"
              alt="头像"
              referrerpolicy="no-referrer"
              @error="avatarBroken = true"
            />
            <span v-else class="vip__avatar-fallback" aria-hidden="true">{{ userInitial }}</span>
          </div>
          <div class="vip__usermeta">
            <div class="vip__userline">
              <span class="vip__nickname">{{ displayName }}</span>
              <span :class="['vip__badge', user.isVip ? 'vip__badge--on' : 'vip__badge--off']">
                {{ user.isVip ? '会员' : '未开通' }}
              </span>
            </div>
            <p class="vip__expires">
              <NIcon name="calendar" :size="14" />
              到期 {{ formatVipExpiresAt(user.vipExpiresAt) }} · UTC+8
            </p>
          </div>
        </div>

        <NCard pad="lg" class="vip__plans">
          <h1 class="vip__heading">会员套餐</h1>
          <p class="vip__tagline">开通会员，畅享高品质音乐与更多权益</p>

          <div v-if="pricingLoading" class="plans" aria-busy="true">
            <div class="plan plan--skel" />
            <div class="plan plan--skel" />
            <div class="plan plan--skel" />
          </div>

          <p v-else-if="pricingError" class="vip__err">{{ pricingError }}</p>

          <template v-else-if="pricingRows.length">
            <p v-if="payError" class="vip__err">{{ payError }}</p>

            <div class="plans">
              <button
                v-for="row in pricingRows"
                :key="row.id"
                type="button"
                class="plan"
                :class="{ 'plan--active': selectedPlanId === row.id }"
                @click="selectedPlanId = row.id"
              >
                <span class="plan__name">{{ formatPlanDuration(row.months, row.days) }}</span>
                <span class="plan__price"><span class="plan__yen">¥</span>{{ formatYuan(row.priceYuan) }}</span>
                <span class="plan__meta">{{ pricePerDayLine(row) || '所选时长权益' }}</span>
              </button>
            </div>

            <p class="vip__terms">
              <NIcon name="info" :size="14" />
              支付成功后会员时长将按套餐叠加；请在常用网络环境下完成支付。若有疑问请联系管理员。
            </p>
          </template>

          <div v-else class="vip__empty">
            <NIcon name="gem" :size="24" />
            <p>暂无在售套餐</p>
            <p class="vip__empty-sub">请稍后再试或联系管理员维护价目表。</p>
          </div>
        </NCard>

        <div class="vip__links">
          <RouterLink to="/account">个人中心</RouterLink>
          <span aria-hidden="true">·</span>
          <RouterLink to="/">返回首页</RouterLink>
        </div>
      </div>

      <!-- 右：结算 -->
      <aside class="vip__checkout" aria-label="结算与支付">
        <NCard pad="lg" class="checkout">
          <template v-if="selectedPlan && pricingRows.length && !pricingLoading">
            <p class="checkout__label">当前套餐</p>
            <p class="checkout__dur">{{ formatPlanDuration(selectedPlan.months, selectedPlan.days) }}</p>

            <p class="checkout__price">
              <span class="checkout__yen">¥</span>{{ formatYuan(selectedPlan.priceYuan) }}
            </p>

            <template v-if="!payInline.visible">
              <p class="checkout__hint">选择支付方式</p>
              <div class="checkout__pay">
                <NButton
                  variant="primary"
                  block
                  icon="qr-code"
                  :disabled="payBusyId === selectedPlan.id"
                  @click="startPay(selectedPlan, 'alipay')"
                >
                  {{ payBusyId === selectedPlan.id ? '请稍候…' : '支付宝' }}
                </NButton>
                <NButton
                  variant="secondary"
                  block
                  icon="message"
                  :disabled="payBusyId === selectedPlan.id"
                  @click="startPay(selectedPlan, 'wxpay')"
                >
                  {{ payBusyId === selectedPlan.id ? '请稍候…' : '微信' }}
                </NButton>
              </div>
            </template>

            <template v-else>
              <p class="checkout__qr-title">{{ payInline.title }}</p>
              <p class="checkout__qr-tip">请使用相机或对应 App 扫描完成支付</p>
              <div class="checkout__qr-frame">
                <img
                  v-if="payInline.imageUrl"
                  :src="payInline.imageUrl"
                  class="checkout__qr-img"
                  alt="支付二维码"
                  referrerpolicy="no-referrer"
                />
                <img
                  v-else-if="payInline.qrDataUrl"
                  :src="payInline.qrDataUrl"
                  class="checkout__qr-img"
                  alt="支付二维码"
                />
              </div>

              <a
                v-if="payInline.browserUrl"
                class="checkout__browser"
                :href="payInline.browserUrl"
                target="_blank"
                rel="noopener noreferrer"
              >
                <NIcon name="external-link" :size="14" />
                浏览器打开支付
              </a>

              <div class="checkout__done">
                <NButton variant="primary" block icon="circle-check" @click="onPaidDone">我已完成支付</NButton>
                <NButton variant="ghost" block @click="clearPayInline">更换支付方式</NButton>
              </div>
            </template>

            <p class="checkout__legal">支付即视为同意会员服务说明</p>
          </template>

          <div v-else-if="pricingLoading" class="checkout__placeholder">加载套餐中…</div>
          <div v-else-if="pricingError" class="checkout__placeholder checkout__placeholder--err">
            无法加载价目
          </div>
          <div v-else class="checkout__placeholder">暂无可售套餐</div>
        </NCard>
      </aside>
    </div>
  </PageShell>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

import QRCode from 'qrcode'
import { formatVipExpiresAt, syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'
import { avatarUrl, useAvatarVersion } from '@/utils/userAvatar.js'
import { fetchVipPricing, createVipPayOrder } from '@/api/vipPricing.js'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { openAuthDialog } from '@/composables/useAuthDialog'
import { useAuth } from '@/composables/useAuth'

const { token: authToken } = useAuth()
const avatarVersion = useAvatarVersion()
const vipTick = ref(0)
const avatarBroken = ref(false)
const pricingRows = ref([])
const pricingLoading = ref(true)
const pricingError = ref('')
const payBusyId = ref(null)
const payError = ref('')
const selectedPlanId = ref(null)
const payInline = ref({
  visible: false,
  title: '',
  imageUrl: '',
  qrDataUrl: '',
  browserUrl: ''
})

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

const displayName = computed(() => {
  const u = user.value
  if (!u) return ''
  if (u.nickname && String(u.nickname).trim()) return String(u.nickname).trim()
  if (u.email && String(u.email).trim()) return String(u.email).split('@')[0]
  return '用户'
})

const userInitial = computed(() => {
  const n = displayName.value
  if (!n) return '?'
  const ch = n.charAt(0).toUpperCase()
  return /[a-z0-9\u4e00-\u9fff]/i.test(ch) ? ch : '?'
})

const userAvatarUrl = computed(() => {
  const u = user.value
  const id = u?.id != null ? u.id : 'default'
  return avatarUrl(id, avatarVersion.value)
})

watch(
  () => user.value?.id,
  () => {
    avatarBroken.value = false
  }
)

// 换过头像后 URL 会变，重新给 img 一次加载机会
watch(avatarVersion, () => {
  avatarBroken.value = false
})

const selectedPlan = computed(() => {
  const rows = pricingRows.value
  if (!rows.length) return null
  const id = selectedPlanId.value
  if (id == null) return rows[0]
  return rows.find((r) => r.id === id) ?? rows[0]
})

watch(
  pricingRows,
  (rows) => {
    if (!rows.length) {
      selectedPlanId.value = null
      return
    }
    if (selectedPlanId.value == null || !rows.some((r) => r.id === selectedPlanId.value)) {
      selectedPlanId.value = rows[0].id
    }
  },
  { immediate: true }
)

watch(selectedPlanId, () => {
  if (payInline.value.visible) clearPayInline()
})

const bump = () => {
  vipTick.value++
}

function formatPlanDuration(months, days) {
  const m = Number(months) || 0
  const d = Number(days) || 0
  const parts = []
  if (m > 0) parts.push(`${m} 个月`)
  if (d > 0) parts.push(`${d} 天`)
  return parts.length ? parts.join(' · ') : '-'
}

function approxDays(months, days) {
  return (Number(months) || 0) * 30 + (Number(days) || 0)
}

function pricePerDayLine(row) {
  const d = approxDays(row.months, row.days)
  if (d <= 0) return ''
  const x = Number(row.priceYuan)
  if (Number.isNaN(x)) return ''
  return `约 ¥${(x / d).toFixed(2)} / 天`
}

function formatYuan(n) {
  const x = Number(n)
  if (Number.isNaN(x)) return '-'
  return x.toFixed(2)
}

function clearPayInline() {
  payInline.value = {
    visible: false,
    title: '',
    imageUrl: '',
    qrDataUrl: '',
    browserUrl: ''
  }
}

async function onPaidDone() {
  await syncUserVipFromPlaylistsApi()
  bump()
  clearPayInline()
}

async function showCheckoutQr(d, payLabel) {
  const imageUrl = (d.img && String(d.img).trim()) || ''
  const linkForEncode =
    (d.qrcode && String(d.qrcode).trim()) ||
    (d.payurl && String(d.payurl).trim()) ||
    (d.payurl2 && String(d.payurl2).trim()) ||
    ''
  let qrDataUrl = ''
  if (!imageUrl) {
    if (!linkForEncode) {
      payError.value = '未返回二维码图片或支付链接，请稍后再试。'
      return
    }
    try {
      qrDataUrl = await QRCode.toDataURL(linkForEncode, {
        width: 220,
        margin: 2,
        errorCorrectionLevel: 'M',
        color: { dark: '#1a1a1a', light: '#ffffff' }
      })
    } catch {
      payError.value = '二维码生成失败，请稍后再试。'
      return
    }
  }
  const browserUrl =
    (d.payurl2 && String(d.payurl2).trim()) ||
    (d.payurl && String(d.payurl).trim()) ||
    (d.qrcode && String(d.qrcode).trim()) ||
    ''
  payInline.value = {
    visible: true,
    title: `${payLabel}扫码支付`,
    imageUrl,
    qrDataUrl,
    browserUrl
  }
  payError.value = ''
}

async function startPay(row, payType) {
  if (payBusyId.value != null) return
  payError.value = ''
  payBusyId.value = row.id
  try {
    const d = await createVipPayOrder(row.id, payType)
    const label = payType === 'wxpay' ? '微信' : '支付宝'
    await showCheckoutQr(d, label)
  } catch (e) {
    payError.value = e?.message || '下单失败'
  } finally {
    payBusyId.value = null
  }
}

async function initVipPage() {
  window.addEventListener(USER_VIP_SYNC_EVENT, bump)
  await syncUserVipFromPlaylistsApi()
  bump()

  try {
    pricingRows.value = await fetchVipPricing()
  } catch (e) {
    pricingError.value = e?.message || '价目加载失败'
  } finally {
    pricingLoading.value = false
  }
}

onMounted(() => {
  if (!localStorage.getItem('userToken')) {
    openAuthDialog('login')
    return
  }
  initVipPage()
})

// 在弹窗里登录成功后接着把页面初始化起来
watch(authToken, (token) => {
  if (token && !pricingRows.value.length) initVipPage()
})

onUnmounted(() => {
  window.removeEventListener(USER_VIP_SYNC_EVENT, bump)
})
</script>

<style scoped>
.vip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 340px);
  gap: clamp(20px, 3vw, 32px);
  align-items: start;
}

.vip__main {
  min-width: 0;
}

/* ==================== 用户条 ==================== */
.vip__user {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-4) var(--n-space-5);
  margin-bottom: var(--n-space-5);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
}

.vip__avatar {
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius);
  overflow: hidden;
  border: 1px solid var(--n-line-strong);
  background: var(--n-surface-soft);
}

.vip__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.vip__avatar-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, var(--n-accent-strong), var(--n-accent));
  color: var(--n-text-inverse);
  font-size: var(--n-text-xl);
  font-weight: var(--n-weight-bold);
}

.vip__usermeta {
  min-width: 0;
}

.vip__userline {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
}

.vip__nickname {
  color: var(--n-text);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  overflow-wrap: anywhere;
}

.vip__badge {
  flex: none;
  padding: 3px 9px;
  border-radius: var(--n-radius-xs);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-semibold);
}

.vip__badge--on {
  background: linear-gradient(135deg, var(--n-accent-strong), var(--n-accent));
  color: var(--n-text-inverse);
}

.vip__badge--off {
  background: var(--n-surface-soft);
  border: 1px solid var(--n-line);
  color: var(--n-text-muted);
}

.vip__expires {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: var(--n-space-2) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

/* ==================== 套餐 ==================== */
.vip__heading {
  margin: 0 0 var(--n-space-1);
  font-size: var(--n-text-xl);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.02em;
}

.vip__tagline {
  margin: 0 0 var(--n-space-5);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.plans {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--n-space-3);
}

/* 手机竖屏：auto-fit 会挤成两列，套餐名/价格被压扁，直接单列 */
@media (max-width: 560px) {
  .plans {
    grid-template-columns: 1fr;
  }
}

.plan {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-1);
  padding: var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface-soft);
  text-align: left;
  transition:
    border-color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease),
    transform var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .plan:hover {
    border-color: var(--n-line-strong);
    background: var(--n-surface-hover);
    transform: translateY(-2px);
  }
}

.plan--active {
  border-color: var(--n-accent-line);
  background: var(--n-accent-soft);
  box-shadow: 0 0 0 1px var(--n-accent-line);
}

.plan__name {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
}

.plan__price {
  color: var(--n-text);
  font-size: var(--n-text-2xl);
  font-weight: var(--n-weight-bold);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.plan--active .plan__price {
  color: var(--n-accent-strong);
}

.plan__yen {
  margin-right: 2px;
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
}

.plan__meta {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.plan--skel {
  min-height: 104px;
  border-radius: var(--n-radius-lg);
  background: linear-gradient(100deg, var(--n-surface-soft) 30%, var(--n-surface-hover) 50%, var(--n-surface-soft) 70%);
  background-size: 200% 100%;
  animation: planSkel 1.4s linear infinite;
}

@keyframes planSkel {
  to { background-position: -200% 0; }
}

.vip__terms {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-2);
  margin: var(--n-space-5) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  line-height: var(--n-leading-normal);
}

.vip__terms :deep(.n-icon) {
  flex: none;
  margin-top: 2px;
}

.vip__err {
  margin: var(--n-space-3) 0;
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid rgba(255, 107, 107, 0.28);
  border-radius: var(--n-radius-control);
  background: var(--n-danger-soft);
  color: #ffb3b3;
  font-size: var(--n-text-sm);
}

.vip__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-2);
  padding: var(--n-space-10) var(--n-space-4);
  text-align: center;
  color: var(--n-text-muted);
}

.vip__empty :deep(.n-icon) {
  color: var(--n-text-faint);
}

.vip__empty p {
  margin: 0;
}

.vip__empty-sub {
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

.vip__links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-3);
  margin-top: var(--n-space-5);
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

/* ==================== 结算 ==================== */
.vip__checkout {
  position: sticky;
  /* 用实测顶栏高度，避免 900–1024 区间两行顶栏与结算卡重叠 */
  top: calc(var(--app-header-h, var(--n-header-height)) + var(--n-space-5));
}

.checkout__label {
  margin: 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.checkout__dur {
  margin: var(--n-space-1) 0 var(--n-space-4);
  color: var(--n-text);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
}

.checkout__price {
  display: flex;
  align-items: baseline;
  margin: 0 0 var(--n-space-5);
  color: var(--n-accent-strong);
  font-size: var(--n-text-3xl);
  font-weight: var(--n-weight-bold);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
}

.checkout__yen {
  margin-right: 4px;
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
}

.checkout__hint {
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.checkout__pay {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3);
}

.checkout__qr-title {
  margin: 0 0 var(--n-space-1);
  color: var(--n-text);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  text-align: center;
}

.checkout__qr-tip {
  margin: 0 0 var(--n-space-4);
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  text-align: center;
}

.checkout__qr-frame {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  max-width: 240px;
  margin: 0 auto var(--n-space-4);
  padding: var(--n-space-3);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: #ffffff;
}

.checkout__qr-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.checkout__browser {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-2);
  width: 100%;
  margin-bottom: var(--n-space-4);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
}

.checkout__done {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-2);
}

.checkout__legal {
  margin: var(--n-space-5) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  text-align: center;
}

.checkout__placeholder {
  padding: var(--n-space-10) 0;
  text-align: center;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.checkout__placeholder--err {
  color: var(--n-danger);
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .vip {
    grid-template-columns: 1fr;
  }

  .vip__checkout {
    position: static;
  }
}

@media (prefers-reduced-motion: reduce) {
  .plan--skel {
    animation: none;
  }
}
</style>
