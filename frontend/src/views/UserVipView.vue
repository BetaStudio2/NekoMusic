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
        <p v-if="user" class="vip-hint">
          选择下方套餐与支付方式可在线开通或续期；支付完成后返回站点即可（会员状态会自动刷新）。
        </p>
      </div>

      <div v-if="pricingLoading" class="pricing-block">加载价目中…</div>
      <div v-else-if="pricingError" class="pricing-block pricing-err">{{ pricingError }}</div>
      <div v-else-if="pricingRows.length" class="pricing-block">
        <h2 class="pricing-title">套餐与支付</h2>
        <p v-if="payError" class="pay-err">{{ payError }}</p>
        <ul class="pricing-list">
          <li v-for="row in pricingRows" :key="row.id" class="pricing-item">
            <div class="pricing-main">
              <span class="dur">{{ formatPlanDuration(row.months, row.days) }}</span>
              <span class="price">¥{{ formatYuan(row.priceYuan) }}</span>
            </div>
            <div class="pay-actions">
              <button
                type="button"
                class="pay-btn pay-btn--ali"
                :disabled="payBusyId === row.id"
                @click="startPay(row, 'alipay')"
              >
                {{ payBusyId === row.id ? '处理中…' : '支付宝' }}
              </button>
              <button
                type="button"
                class="pay-btn pay-btn--wx"
                :disabled="payBusyId === row.id"
                @click="startPay(row, 'wxpay')"
              >
                {{ payBusyId === row.id ? '处理中…' : '微信' }}
              </button>
            </div>
          </li>
        </ul>
        <p class="pricing-note">下单后以二维码为主；App 内请扫码支付。若支付未开通会提示联系管理员。</p>
      </div>

      <div class="vip-actions">
        <router-link to="/account" class="btn-secondary">个人中心</router-link>
        <router-link to="/" class="btn-primary">返回首页</router-link>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="payModal.open"
        class="pay-modal-backdrop"
        role="dialog"
        aria-modal="true"
        aria-labelledby="pay-modal-title"
        @click.self="closePayModal"
      >
        <div class="pay-modal">
          <h3 id="pay-modal-title" class="pay-modal-title">{{ payModal.title }}</h3>
          <p class="pay-modal-tip">
            App 内 WebView 常无法直接调起支付；请<strong>用系统相机或对应 App 扫下方二维码</strong>完成付款。若必须跳网页，可点「在浏览器打开」。
          </p>
          <div class="pay-qr-wrap">
            <img
              v-if="payModal.imageUrl"
              :src="payModal.imageUrl"
              class="pay-qr-img"
              alt="支付二维码"
              referrerpolicy="no-referrer"
            />
            <img
              v-else-if="payModal.qrDataUrl"
              :src="payModal.qrDataUrl"
              class="pay-qr-img"
              alt="支付二维码"
            />
          </div>
          <div class="pay-modal-links">
            <a
              v-if="payModal.browserUrl"
              class="pay-modal-link"
              :href="payModal.browserUrl"
              target="_blank"
              rel="noopener noreferrer"
            >在浏览器中打开支付页</a>
          </div>
          <div class="pay-modal-actions">
            <button type="button" class="pay-modal-btn pay-modal-btn--primary" @click="onPaidDone">我已完成支付</button>
            <button type="button" class="pay-modal-btn" @click="closePayModal">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { formatVipExpiresAt, syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'
import { fetchVipPricing, createVipPayOrder } from '@/api/vipPricing.js'

const router = useRouter()
const vipTick = ref(0)
const pricingRows = ref([])
const pricingLoading = ref(true)
const pricingError = ref('')
const payBusyId = ref(null)
const payError = ref('')
const payModal = ref({
  open: false,
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

const bump = () => {
  vipTick.value++
}

function formatPlanDuration(months, days) {
  const m = Number(months) || 0
  const d = Number(days) || 0
  const parts = []
  if (m > 0) parts.push(`${m} 个月`)
  if (d > 0) parts.push(`${d} 天`)
  return parts.length ? parts.join(' + ') : '—'
}

function formatYuan(n) {
  const x = Number(n)
  if (Number.isNaN(x)) return '—'
  return x.toFixed(2)
}

function closePayModal() {
  payModal.value = {
    open: false,
    title: '',
    imageUrl: '',
    qrDataUrl: '',
    browserUrl: ''
  }
}

async function onPaidDone() {
  await syncUserVipFromPlaylistsApi()
  bump()
  closePayModal()
}

/** 优先用 ZPay 返回的二维码图；否则用链接本地生成二维码；不在当前页整页跳转。 */
async function openPayModal(d, payLabel) {
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
        width: 260,
        margin: 2,
        errorCorrectionLevel: 'M',
        color: { dark: '#111111', light: '#ffffff' }
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
  payModal.value = {
    open: true,
    title: `请使用${payLabel}完成支付`,
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
    await openPayModal(d, label)
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
.vip-page {
  min-height: calc(100vh - 200px);
  padding: 24px 16px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.vip-card {
  width: 100%;
  max-width: 620px;
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
.pricing-block,
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

.pricing-block {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.35);
  text-align: left;
}

.pricing-title {
  margin: 0 0 12px;
  font-size: 1.05rem;
  font-weight: 700;
  color: #4a3f7a;
}

.pricing-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.pricing-item {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(106, 90, 205, 0.12);
}

.pricing-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex: 1 1 200px;
  gap: 12px;
}

.pricing-item .dur {
  font-size: 0.92rem;
  color: #444;
}

.pricing-item .price {
  font-weight: 800;
  color: #b45309;
  font-size: 1rem;
}

.pay-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pay-btn {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.15s ease;
}

.pay-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.pay-btn:not(:disabled):hover {
  transform: translateY(-1px);
}

.pay-btn--ali {
  background: linear-gradient(135deg, #1677ff, #4096ff);
  color: #fff;
}

.pay-btn--wx {
  background: linear-gradient(135deg, #07c160, #38d973);
  color: #fff;
}

.pay-err {
  margin: 0 0 10px;
  font-size: 0.88rem;
  color: #b91c1c;
  line-height: 1.4;
}

.pricing-note {
  margin: 12px 0 0;
  font-size: 0.8rem;
  color: #6b608e;
  line-height: 1.45;
}

.pricing-err {
  color: #b91c1c;
  font-size: 0.9rem;
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

.pay-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.pay-modal {
  width: 100%;
  max-width: 360px;
  padding: 22px 20px 20px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
  text-align: center;
}

.pay-modal-title {
  margin: 0 0 10px;
  font-size: 1.1rem;
  font-weight: 800;
  color: #312e81;
}

.pay-modal-tip {
  margin: 0 0 16px;
  font-size: 0.82rem;
  line-height: 1.55;
  color: #4b5563;
  text-align: left;
}

.pay-qr-wrap {
  margin: 0 auto 14px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.pay-qr-img {
  display: block;
  width: 260px;
  max-width: 100%;
  height: auto;
  margin: 0 auto;
}

.pay-modal-links {
  margin-bottom: 14px;
}

.pay-modal-link {
  font-size: 0.86rem;
  font-weight: 600;
  color: #4f46e5;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pay-modal-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pay-modal-btn {
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 0.92rem;
  font-weight: 600;
  border: 1px solid #cbd5e1;
  background: #f1f5f9;
  color: #334155;
  cursor: pointer;
}

.pay-modal-btn--primary {
  border: none;
  background: linear-gradient(135deg, #6a5acd, #8b5cf6);
  color: #fff;
}
</style>
