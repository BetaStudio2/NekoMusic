<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">客户端更新</h1>
        <p class="subpage__desc">
          保存版本号后，<strong>/version 仍对外返回旧版本 30 分钟</strong>，便于上传新安装包；上传目标文件名为待生效版本。
        </p>
      </div>
    </header>

    <NCard v-if="publishedAndroidVer || publishedPcVer || publishedWatchVer" pad="lg" class="card">
      <h2 class="card__title">当前对外（/version）</h2>
      <p class="line">Android：<code class="code">{{ publishedAndroidVer }}</code></p>
      <p class="line">手表：<code class="code">{{ publishedWatchVer }}</code></p>
      <p class="line">PC：<code class="code">{{ publishedPcVer }}</code></p>
    </NCard>

    <NCard pad="lg" class="card">
      <h2 class="card__title">待发布版本号</h2>
      <p v-if="pendingEffectiveAt" class="hint">
        保存后将于 <strong>{{ pendingEffectiveAt }}</strong> 起在 /version 生效
      </p>

      <div class="field">
        <label class="field__label" for="rel-android">Android 版本 (ver)</label>
        <NInput id="rel-android" v-model="androidVer" placeholder="如 20260207-36" />
      </div>
      <div class="field">
        <label class="field__label" for="rel-watch">手表版本 (watch_ver)</label>
        <NInput id="rel-watch" v-model="watchVer" placeholder="如 1.4.0-12" />
      </div>
      <div class="field">
        <label class="field__label" for="rel-pc">PC 版本 (pc_ver)</label>
        <NInput id="rel-pc" v-model="pcVer" placeholder="如 2026.207.6" />
      </div>

      <div class="toolbar">
        <NButton variant="secondary" icon="refresh" :disabled="loading" @click="loadData">重新加载</NButton>
        <NButton
          variant="primary"
          icon="check"
          :disabled="savingVersions || loading"
          :loading="savingVersions"
          @click="saveVersions"
        >
          保存版本号
        </NButton>
      </div>
    </NCard>

    <NCard pad="lg" class="card card--last">
      <h2 class="card__title">安装包</h2>

      <p v-if="loadError" class="err">
        <NIcon name="triangle-alert" :size="16" />
        {{ loadError }}
      </p>

      <div v-for="pkg in displayPackages" :key="pkg.platform" class="pkg">
        <div class="pkg__meta">
          <span class="pkg__platform">{{ platformLabel(pkg.platform) }}</span>
          <span class="pkg__name" :title="pkg.fileName">{{ pkg.fileName || '保存版本号后生成文件名' }}</span>
          <NTag :variant="pkg.uploaded ? 'success' : 'warning'" size="sm">
            {{ pkg.uploaded ? '已上传' : '未上传' }}
          </NTag>
          <span v-if="pkg.uploaded && pkg.size != null" class="pkg__size">{{ formatSize(pkg.size) }}</span>
        </div>

        <div class="pkg__actions">
          <NButton
            v-if="pkg.uploaded && pkg.downloadUrl"
            size="sm"
            variant="secondary"
            icon="external-link"
            :href="pkg.downloadUrl"
            target="_blank"
            rel="noopener"
          >
            直链
          </NButton>

          <label
            class="upload"
            :class="{ 'upload--busy': uploadingPlatform === pkg.platform }"
          >
            <input
              type="file"
              :accept="acceptForPlatform(pkg.platform)"
              class="upload__input"
              :disabled="uploadingPlatform === pkg.platform"
              @change="(e) => onFilePick(e, pkg)"
            />
            <NIcon name="upload" :size="14" />
            {{ uploadingPlatform === pkg.platform ? '上传中…' : '上传' }}
          </label>
        </div>

        <div v-if="uploadingPlatform === pkg.platform && uploadProgress >= 0" class="progress">
          <div class="progress__bar" :style="{ width: uploadProgress + '%' }" />
        </div>
      </div>

      <p class="hint">
        请先保存待发布版本号，再上传安装包；手表使用独立版本与独立 APK。落盘文件名与上表一致。
      </p>
    </NCard>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner, NTag } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import {
  fetchAdminClientReleases,
  saveAdminClientReleaseVersions,
  uploadAdminClientRelease
} from '@/api/clientReleases.js'

const router = useRouter()
const toast = useToast()
const adminInfo = ref({})
const androidVer = ref('')
const watchVer = ref('')
const pcVer = ref('')
const publishedAndroidVer = ref('')
const publishedWatchVer = ref('')
const publishedPcVer = ref('')
const pendingEffectiveAt = ref('')
const packages = ref([])
const loading = ref(false)
const savingVersions = ref(false)
const loadError = ref('')
const uploadingPlatform = ref('')
const uploadProgress = ref(-1)

const WATCH_PLACEHOLDER = Object.freeze({
  platform: 'watch',
  fileName: null,
  downloadUrl: null,
  uploaded: false,
  size: null
})

/** 手表上传入口固定在本地渲染，不依赖后端当前是否已返回 watch 包信息。 */
const displayPackages = computed(() => {
  const list = Array.isArray(packages.value) ? [...packages.value] : []
  if (list.some((pkg) => pkg.platform === 'watch')) return list
  const androidIndex = list.findIndex((pkg) => pkg.platform === 'android')
  list.splice(androidIndex >= 0 ? androidIndex + 1 : 0, 0, WATCH_PLACEHOLDER)
  return list
})

const platformLabel = (p) => {
  const map = { android: 'Android', watch: '手表', windows: 'Windows', linux: 'Linux', mac: 'macOS' }
  return map[p] || p
}

const acceptForPlatform = (platform) => {
  const map = {
    android: '.apk,application/vnd.android.package-archive',
    watch: '.apk,application/vnd.android.package-archive',
    windows: '.exe,application/vnd.microsoft.portable-executable',
    linux: '.deb,application/vnd.debian.binary-package',
    mac: '.pkg'
  }
  return map[platform] || '.apk,.exe,.deb,.pkg'
}

const formatSize = (bytes) => {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`
}



const applyReleaseData = (data) => {
  publishedAndroidVer.value = data.publishedAndroidVer || data.androidVer || ''
  publishedWatchVer.value = data.publishedWatchVer || data.watchVer || ''
  publishedPcVer.value = data.publishedPcVer || data.pcVer || ''
  pendingEffectiveAt.value = data.pendingEffectiveAt || ''
  androidVer.value = data.pendingAndroidVer || data.androidVer || publishedAndroidVer.value
  watchVer.value = data.pendingWatchVer || data.watchVer || publishedWatchVer.value
  pcVer.value = data.pendingPcVer || data.pcVer || publishedPcVer.value
  packages.value = Array.isArray(data.packages) ? data.packages : []
}

const loadData = async () => {
  loadError.value = ''
  loading.value = true
  try {
    const data = await fetchAdminClientReleases()
    applyReleaseData(data)
  } catch (e) {
    loadError.value = e.message || '加载失败'
    toast.error(loadError.value)
  } finally {
    loading.value = false
  }
}

const saveVersions = async () => {
  const av = androidVer.value.trim()
  const wv = watchVer.value.trim()
  const pv = pcVer.value.trim()
  if (!av || !wv || !pv) {
    toast.error('请填写 Android、手表与 PC 版本号')
    return
  }
  savingVersions.value = true
  try {
    const data = await saveAdminClientReleaseVersions({ androidVer: av, pcVer: pv, watchVer: wv })
    applyReleaseData(data)
    toast.success(data.pendingEffectiveAt
      ? `已排期，/version 将于 ${data.pendingEffectiveAt} 生效`
      : '版本号已保存并立即对外生效')
  } catch (e) {
    toast.error(e.message || '保存失败')
  } finally {
    savingVersions.value = false
  }
}

const onFilePick = async (event, pkg) => {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!androidVer.value.trim() || !watchVer.value.trim() || !pcVer.value.trim()) {
    toast.error('请先保存版本号再上传安装包')
    return
  }

  uploadingPlatform.value = pkg.platform
  uploadProgress.value = 0
  try {
    await uploadAdminClientRelease(file, pkg.platform, (loaded, total) => {
      uploadProgress.value = total > 0 ? Math.round((loaded / total) * 100) : 0
    })
    toast.success(`${platformLabel(pkg.platform)} 安装包上传成功`)
    await loadData()
  } catch (e) {
    toast.error(e.message || '上传失败')
  } finally {
    uploadingPlatform.value = ''
    uploadProgress.value = -1
  }
}

onMounted(() => {
  const stored = localStorage.getItem('adminInfo')
  if (stored) {
    try {
      adminInfo.value = JSON.parse(stored)
      const role = adminInfo.value.role || 'admin'
      if (role === 'auditor') {
        toast.error('无权限访问')
        router.replace('/admin')
        return
      }
    } catch {
      router.push('/admin/login')
      return
    }
  } else {
    router.push('/admin/login')
    return
  }
  loadData()
})
</script>

<style scoped>
.subpage {
  width: 100%;
  max-width: 900px;
}

.subpage__head {
  margin-bottom: var(--n-space-6);
}

.subpage__title {
  margin: 0 0 var(--n-space-1);
  font-size: clamp(1.25rem, 2.6vw, 1.6rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.02em;
  color: var(--n-text);
}

.subpage__desc {
  margin: 0;
  max-width: 72ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.subpage__desc strong {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

/* ==================== 卡片 ==================== */
.card {
  margin-bottom: var(--n-space-5);
}

.card--last {
  margin-bottom: 0;
}

.card__title {
  margin: 0 0 var(--n-space-4);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.line {
  margin: 0 0 var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.line:last-child {
  margin-bottom: 0;
}

.code {
  padding: 2px 8px;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xs);
  background: var(--n-surface-sunken);
  color: var(--n-accent-strong);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-sm);
}

.hint {
  margin: var(--n-space-3) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  line-height: var(--n-leading-normal);
}

.hint strong {
  color: var(--n-accent-strong);
}

.err {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0 0 var(--n-space-4);
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid rgba(255, 107, 107, 0.28);
  border-radius: var(--n-radius-control);
  background: var(--n-danger-soft);
  color: #ffb3b3;
  font-size: var(--n-text-sm);
}

/* ==================== 表单 ==================== */
.field {
  margin-bottom: var(--n-space-4);
}

.field__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-medium);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-top: var(--n-space-5);
}

/* ==================== 安装包行 ==================== */
.pkg {
  padding: var(--n-space-4) 0;
  border-bottom: 1px solid var(--n-line-subtle);
}

.pkg:last-of-type {
  border-bottom: none;
}

.pkg__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--n-space-3);
}

.pkg__platform {
  color: var(--n-text);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
}

.pkg__name {
  color: var(--n-text-muted);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-xs);
  overflow-wrap: anywhere;
}

.pkg__size {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  font-variant-numeric: tabular-nums;
}

.pkg__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--n-space-3);
  margin-top: var(--n-space-3);
}

/* 上传按钮（label 包隐藏 file input，保证点击可用） */
.upload {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  height: 30px;
  padding: 0 var(--n-space-3);
  border: 1px solid var(--n-accent-line);
  border-radius: var(--n-radius-control);
  background: var(--n-gradient-accent);
  color: var(--n-accent-strong);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
  cursor: pointer;
  transition: background var(--n-duration-fast) var(--n-ease), border-color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .upload:not(.upload--busy):hover {
    background: var(--n-accent-soft);
    border-color: var(--n-accent);
  }
}

.upload--busy {
  opacity: 0.6;
  cursor: progress;
}

.upload__input {
  display: none;
}

/* ==================== 进度 ==================== */
.progress {
  height: 6px;
  margin-top: var(--n-space-3);
  border-radius: var(--n-radius-pill);
  background: var(--n-surface-soft);
  overflow: hidden;
}

.progress__bar {
  height: 100%;
  border-radius: var(--n-radius-pill);
  background: var(--n-accent);
  transition: width 0.15s var(--n-ease);
}
</style>
