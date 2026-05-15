<template>
  <div class="vip-page">
    <div class="vip-shell">
      <div class="vip-shell-inner">
        <!-- 左：参考 img — 用户信息 + 横向套餐卡 + 权益 -->
        <div class="vip-col vip-col--main">
          <div v-if="user" class="vip-userbar">
            <div class="vip-avatar">
              <img
                v-if="!avatarBroken"
                :src="userAvatarUrl"
                alt="头像"
                class="vip-avatar-img"
                referrerpolicy="no-referrer"
                @error="avatarBroken = true"
              />
              <span v-else class="vip-avatar-fallback" aria-hidden="true">{{ userInitial }}</span>
            </div>
            <div class="vip-usermeta">
              <div class="vip-userline">
                <span class="vip-nickname">{{ displayName }}</span>
                <span :class="['vip-badge', user.isVip ? 'vip-badge--on' : 'vip-badge--off']">
                  {{ user.isVip ? '会员' : '未开通' }}
                </span>
              </div>
              <p class="vip-expires-line">到期 {{ formatVipExpiresAt(user.vipExpiresAt) }} · UTC8</p>
            </div>
          </div>

          <p class="vip-tagline">开通会员，畅享高品质音乐与更多权益</p>

          <div class="vip-tier-tabs" role="tablist" aria-label="套餐类型">
            <span class="vip-tier-tab vip-tier-tab--active" role="tab" aria-selected="true">会员套餐</span>
          </div>

          <div v-if="pricingLoading" class="plan-strip plan-strip--skel" aria-busy="true">
            <div class="plan-card plan-card--skel" />
            <div class="plan-card plan-card--skel plan-card--skel-mid" />
            <div class="plan-card plan-card--skel" />
          </div>
          <div v-else-if="pricingError" class="vip-inline-err">{{ pricingError }}</div>
          <template v-else-if="pricingRows.length">
            <p v-if="payError" class="vip-inline-err">{{ payError }}</p>
            <div class="plan-strip-wrap">
              <div class="plan-strip">
                <button
                  v-for="row in pricingRows"
                  :key="row.id"
                  type="button"
                  class="plan-card"
                  :class="{ 'plan-card--active': selectedPlanId === row.id }"
                  @click="selectedPlanId = row.id"
                >
                  <span class="plan-card-name">{{ formatPlanDuration(row.months, row.days) }}</span>
                  <span class="plan-card-price">¥{{ formatYuan(row.priceYuan) }}</span>
                  <span v-if="pricePerDayLine(row)" class="plan-card-meta">{{ pricePerDayLine(row) }}</span>
                  <span v-else class="plan-card-meta">所选时长权益</span>
                </button>
              </div>
            </div>
            <p class="vip-terms">
              支付成功后会员时长将按套餐叠加；请在常用网络环境下完成支付。若有疑问请联系管理员。
            </p>

<!--            <div class="vip-perks">-->
<!--              <div class="vip-perks-title">会员权益</div>-->
<!--              <ul class="vip-perks-grid">-->
<!--                <li class="vip-perk"><span class="vip-perk-ic" aria-hidden="true">♪</span>高品质在线播放</li>-->
<!--                <li class="vip-perk"><span class="vip-perk-ic" aria-hidden="true">☁</span>云端歌单与同步</li>-->
<!--                <li class="vip-perk"><span class="vip-perk-ic" aria-hidden="true">✦</span>会员标识与优先体验</li>-->
<!--                <li class="vip-perk"><span class="vip-perk-ic" aria-hidden="true">∞</span>更多权益持续更新</li>-->
<!--              </ul>-->
<!--            </div>-->
          </template>
          <div v-else class="vip-inline-empty">
            <p>暂无在售套餐</p>
            <p class="vip-inline-empty-sub">请稍后再试或联系管理员维护价目表。</p>
          </div>

          <div class="vip-foot-links">
            <router-link to="/account" class="vip-foot-a">个人中心</router-link>
            <span class="vip-foot-dot">·</span>
            <router-link to="/" class="vip-foot-a">返回首页</router-link>
          </div>
        </div>

        <!-- 右：结算条 — 价格 + 支付 / 二维码（同 img 右栏） -->
        <aside class="vip-col vip-col--checkout" aria-label="结算与支付">
          <template v-if="selectedPlan && pricingRows.length && !pricingLoading">
            <div class="checkout-inner">
              <p class="checkout-label">当前套餐</p>
              <p class="checkout-dur">{{ formatPlanDuration(selectedPlan.months, selectedPlan.days) }}</p>

              <div class="checkout-price-block">
                <span class="checkout-price-yen">¥</span>
                <span class="checkout-price-num">{{ formatYuan(selectedPlan.priceYuan) }}</span>
              </div>

              <template v-if="!payInline.visible">
                <p class="checkout-pay-hint">选择支付方式</p>
                <div class="checkout-pay-row">
                  <button
                    type="button"
                    class="checkout-btn checkout-btn--ali"
                    :disabled="payBusyId === selectedPlan.id"
                    @click="startPay(selectedPlan, 'alipay')"
                  >
                    {{ payBusyId === selectedPlan.id ? '请稍候…' : '支付宝' }}
                  </button>
                  <button
                    type="button"
                    class="checkout-btn checkout-btn--wx"
                    :disabled="payBusyId === selectedPlan.id"
                    @click="startPay(selectedPlan, 'wxpay')"
                  >
                    {{ payBusyId === selectedPlan.id ? '请稍候…' : '微信' }}
                  </button>
                </div>
              </template>

              <template v-else>
                <p class="checkout-qr-title">{{ payInline.title }}</p>
                <p class="checkout-qr-tip">请使用相机或对应 App 扫描完成支付</p>
                <div class="checkout-qr-frame">
                  <div class="checkout-qr-scanline" aria-hidden="true" />
                  <img
                    v-if="payInline.imageUrl"
                    :src="payInline.imageUrl"
                    class="checkout-qr-img"
                    alt="支付二维码"
                    referrerpolicy="no-referrer"
                  />
                  <img
                    v-else-if="payInline.qrDataUrl"
                    :src="payInline.qrDataUrl"
                    class="checkout-qr-img"
                    alt="支付二维码"
                  />
                </div>
                <a
                  v-if="payInline.browserUrl"
                  class="checkout-browser-link"
                  :href="payInline.browserUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >浏览器打开支付</a>
                <div class="checkout-done-row">
                  <button type="button" class="checkout-done-btn" @click="onPaidDone">我已完成支付</button>
                  <button type="button" class="checkout-back-btn" @click="clearPayInline">更换支付方式</button>
                </div>
              </template>

              <p class="checkout-legal">
                <span>支付即视为同意会员服务说明</span>
              </p>
            </div>
          </template>
          <div v-else-if="pricingLoading" class="checkout-placeholder">加载套餐中…</div>
          <div v-else-if="pricingError" class="checkout-placeholder checkout-placeholder--err">无法加载价目</div>
          <div v-else class="checkout-placeholder">暂无可售套餐</div>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { formatVipExpiresAt, syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'
import { fetchVipPricing, createVipPayOrder } from '@/api/vipPricing.js'
import API_CONFIG from '@/config/apiConfig.js'

const router = useRouter()
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
  if (u.username && String(u.username).trim()) return String(u.username).trim()
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
  return `${API_CONFIG.BASE_URL}/api/user/avatar/${id}`
})

watch(
  () => user.value?.id,
  () => {
    avatarBroken.value = false
  }
)

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
  return parts.length ? parts.join(' · ') : '—'
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
  if (Number.isNaN(x)) return '—'
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

onMounted(async () => {
  if (!localStorage.getItem('userToken')) {
    router.replace('/login')
    return
  }
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
})

onUnmounted(() => {
  window.removeEventListener(USER_VIP_SYNC_EVENT, bump)
})
</script>

<style scoped>
/* 页面不铺底，由站点全局决定 */
.vip-page {
  --shell-bg: #fdf8f4;
  --shell-ink: #1a1a1a;
  --shell-muted: #8c8c8c;
  --shell-line: rgba(0, 0, 0, 0.06);
  --brand-red: #e60026;
  --brand-orange: #ff5c00;
  --checkout-bg: linear-gradient(165deg, #fff9f5 0%, #fff0e8 45%, #fffdfb 100%);
  min-height: calc(100vh - 180px);
  padding: 20px 14px 32px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: transparent;
}

.vip-shell {
  width: 100%;
  max-width: min(960px, calc(100vw - 28px));
  border-radius: 20px;
  background: var(--shell-bg);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.9) inset,
    0 24px 48px -28px rgba(0, 0, 0, 0.14);
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.vip-shell-inner {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

@media (min-width: 800px) {
  .vip-shell-inner {
    flex-direction: row;
    align-items: stretch;
    min-height: 420px;
  }
}

.vip-col {
  min-width: 0;
}

.vip-col--main {
  flex: 1 1 auto;
  padding: 20px 18px 22px;
  background: #fffef9;
}

@media (min-width: 800px) {
  .vip-col--main {
    padding: 22px 22px 24px;
    flex: 1 1 62%;
  }
}

.vip-userbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.vip-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe8dc, #ffd0bf);
  color: #c2410c;
  flex-shrink: 0;
  border: 2px solid #fff;
  box-shadow: 0 4px 12px rgba(230, 0, 38, 0.08);
  overflow: hidden;
  position: relative;
}

.vip-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.vip-avatar-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 1.1rem;
}

.vip-usermeta {
  flex: 1;
  min-width: 0;
}

.vip-userline {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.vip-nickname {
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--shell-ink);
  letter-spacing: -0.02em;
}

.vip-badge {
  font-size: 0.68rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.02em;
}

.vip-badge--on {
  background: linear-gradient(90deg, #ffe4e6, #fff1f2);
  color: #be123c;
  border: 1px solid #fecdd3;
}

.vip-badge--off {
  background: #f4f4f5;
  color: var(--shell-muted);
  border: 1px solid #e4e4e7;
}

.vip-expires-line {
  margin: 4px 0 0;
  font-size: 0.78rem;
  color: var(--shell-muted);
}

.vip-tagline {
  margin: 0 0 14px;
  font-size: 0.8rem;
  line-height: 1.5;
  color: #666;
}

.vip-tier-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--shell-line);
  margin-bottom: 14px;
}

.vip-tier-tab {
  padding: 10px 18px 12px;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--shell-muted);
  border: none;
  background: transparent;
  border-radius: 12px 12px 0 0;
  cursor: default;
}

.vip-tier-tab--active {
  color: var(--shell-ink);
  background: #fff;
  box-shadow: 0 -1px 0 #fff;
  margin-bottom: -1px;
  border: 1px solid var(--shell-line);
  border-bottom-color: #fff;
}

.plan-strip-wrap {
  margin: 0 -4px;
}

.plan-strip {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 6px 4px 14px;
  scroll-snap-type: x proximity;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.plan-strip::-webkit-scrollbar {
  display: none;
}

.plan-card {
  flex: 0 0 auto;
  scroll-snap-align: start;
  width: 148px;
  min-height: 128px;
  padding: 12px 12px 10px;
  border-radius: 14px;
  border: 1.5px solid #e8e8e8;
  background: #fff;
  cursor: pointer;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.15s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.plan-card:hover {
  border-color: #ffc9a8;
}

.plan-card--active {
  border-color: var(--brand-orange);
  background: linear-gradient(180deg, #fff 0%, #fff8f4 100%);
  box-shadow:
    0 0 0 1px var(--brand-orange),
    0 10px 24px rgba(255, 92, 0, 0.12);
  transform: translateY(-1px);
}

.plan-card-name {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--shell-ink);
  line-height: 1.35;
}

.plan-card-price {
  font-size: 1.35rem;
  font-weight: 800;
  color: var(--brand-red);
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.plan-card-meta {
  margin-top: auto;
  font-size: 0.7rem;
  color: var(--shell-muted);
}

.plan-strip--skel {
  pointer-events: none;
}

.plan-card--skel {
  border-color: #eee;
  background: linear-gradient(90deg, #f3f3f3 0%, #fafafa 50%, #f3f3f3 100%);
  background-size: 200% 100%;
  animation: skel 1s ease-in-out infinite;
  min-height: 128px;
}

.plan-card--skel-mid {
  width: 160px;
}

@keyframes skel {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

.vip-terms {
  margin: 0 0 18px;
  font-size: 0.68rem;
  line-height: 1.55;
  color: #999;
}

.vip-inline-err {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  color: #b91c1c;
  font-size: 0.82rem;
}

.vip-inline-empty {
  padding: 20px;
  text-align: center;
  border-radius: 14px;
  background: #fafafa;
  border: 1px dashed #ddd;
  color: #555;
  font-size: 0.9rem;
}

.vip-inline-empty-sub {
  margin: 6px 0 0;
  font-size: 0.78rem;
  color: #999;
}

.vip-perks {
  margin-bottom: 8px;
}

.vip-perks-title {
  font-size: 0.82rem;
  font-weight: 800;
  color: var(--shell-ink);
  margin-bottom: 10px;
}

.vip-perks-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 10px;
}

@media (min-width: 520px) {
  .vip-perks-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.vip-perk {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  color: #555;
  padding: 8px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.vip-perk-ic {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: #fff5f0;
  color: var(--brand-orange);
  font-size: 0.65rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.vip-foot-links {
  margin-top: 16px;
  font-size: 0.78rem;
  color: var(--shell-muted);
}

.vip-foot-a {
  color: #666;
  text-decoration: none;
  font-weight: 600;
}

.vip-foot-a:hover {
  color: var(--brand-red);
}

.vip-foot-dot {
  margin: 0 6px;
  opacity: 0.45;
}

/* 右栏结算 */
.vip-col--checkout {
  flex: 1 1 auto;
  background: var(--checkout-bg);
  border-top: 1px solid var(--shell-line);
  padding: 20px 18px 22px;
  display: flex;
  flex-direction: column;
}

@media (min-width: 800px) {
  .vip-col--checkout {
    flex: 0 0 300px;
    max-width: 320px;
    border-top: none;
    border-left: 1px solid var(--shell-line);
    padding: 22px 18px 20px;
  }
}

.checkout-inner {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  height: 100%;
}

.checkout-label {
  margin: 0 0 4px;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--shell-muted);
}

.checkout-dur {
  margin: 0 0 12px;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--shell-ink);
  line-height: 1.35;
}

.checkout-price-block {
  margin-bottom: 16px;
  line-height: 1;
}

.checkout-price-yen {
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--brand-red);
  vertical-align: super;
  margin-right: 2px;
}

.checkout-price-num {
  font-size: 2.35rem;
  font-weight: 800;
  color: var(--brand-red);
  letter-spacing: -0.04em;
}

.checkout-pay-hint {
  margin: 0 0 10px;
  font-size: 0.78rem;
  color: #888;
}

.checkout-pay-row {
  display: flex;
  gap: 10px;
  margin-bottom: auto;
}

.checkout-btn {
  flex: 1;
  padding: 11px 8px;
  border-radius: 999px;
  border: none;
  font-size: 0.82rem;
  font-weight: 800;
  cursor: pointer;
  color: #fff;
  transition: transform 0.12s, opacity 0.2s;
}

.checkout-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.checkout-btn:not(:disabled):active {
  transform: scale(0.98);
}

.checkout-btn--ali {
  background: linear-gradient(135deg, #1677ff, #0958d9);
  box-shadow: 0 6px 14px rgba(22, 119, 255, 0.28);
}

.checkout-btn--wx {
  background: linear-gradient(135deg, #07c160, #06ae56);
  box-shadow: 0 6px 14px rgba(7, 193, 96, 0.25);
}

.checkout-qr-title {
  margin: 0 0 6px;
  font-size: 0.88rem;
  font-weight: 800;
  color: var(--shell-ink);
  text-align: center;
}

.checkout-qr-tip {
  margin: 0 0 12px;
  font-size: 0.72rem;
  color: #888;
  text-align: center;
  line-height: 1.45;
}

.checkout-qr-frame {
  position: relative;
  margin: 0 auto 12px;
  padding: 12px;
  max-width: 220px;
  border-radius: 14px;
  background: #fff;
  border: 2px solid rgba(255, 92, 0, 0.45);
  box-shadow: 0 8px 24px rgba(255, 92, 0, 0.1);
  overflow: hidden;
}

.checkout-qr-scanline {
  position: absolute;
  left: 8%;
  right: 8%;
  top: 18%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--brand-red), transparent);
  opacity: 0.55;
  animation: scanmove 2.2s ease-in-out infinite;
  pointer-events: none;
  z-index: 1;
}

@keyframes scanmove {
  0%,
  100% {
    top: 14%;
    opacity: 0.3;
  }
  50% {
    top: 78%;
    opacity: 0.75;
  }
}

.checkout-qr-img {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 8px;
  position: relative;
  z-index: 0;
}

.checkout-browser-link {
  display: block;
  text-align: center;
  margin-bottom: 12px;
  font-size: 0.76rem;
  font-weight: 600;
  color: #2563eb;
  text-decoration: none;
}

.checkout-browser-link:hover {
  text-decoration: underline;
}

.checkout-done-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.checkout-done-btn {
  padding: 11px 14px;
  border-radius: 999px;
  border: none;
  background: var(--brand-red);
  color: #fff;
  font-size: 0.84rem;
  font-weight: 800;
  cursor: pointer;
}

.checkout-done-btn:hover {
  filter: brightness(1.05);
}

.checkout-back-btn {
  padding: 9px 14px;
  border-radius: 999px;
  border: 1px solid #e5e5e5;
  background: #fff;
  color: #666;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.checkout-legal {
  margin: 0;
  margin-top: 8px;
  font-size: 0.65rem;
  line-height: 1.5;
  color: #aaa;
  text-align: center;
}

.checkout-placeholder {
  margin: auto 0;
  text-align: center;
  font-size: 0.86rem;
  color: #999;
  padding: 24px 8px;
}

.checkout-placeholder--err {
  color: #b91c1c;
}
</style>
