<template>
  <div class="admin-layout">
    <AdminSidebar ref="sidebarRef" />

    <div class="admin-main-content">
      <div class="admin-header">
        <button type="button" class="menu-toggle-btn" @click="toggleSidebar">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z" />
          </svg>
        </button>
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button type="button" class="logout-button" @click="logout">退出登录</button>
        </div>
      </div>

      <div class="admin-content-wrapper">
        <div class="admin-subpage">
          <h2>客户端更新</h2>
          <p>
            保存版本号后，<strong>version.json 仍对外返回旧版本 30 分钟</strong>，便于上传新安装包；上传目标文件名为待生效版本。
          </p>

          <section v-if="publishedAndroidVer || publishedPcVer" class="card card-muted">
            <h3>当前对外（version.json）</h3>
            <p class="published-line">Android：<code>{{ publishedAndroidVer }}</code></p>
            <p class="published-line">PC：<code>{{ publishedPcVer }}</code></p>
          </section>

          <section class="card">
            <h3>待发布版本号</h3>
            <p v-if="pendingEffectiveAt" class="schedule-hint">
              保存后将于 <strong>{{ pendingEffectiveAt }}</strong> 起在 version.json 生效
            </p>
            <div class="form-row">
              <label>Android 版本 (ver)</label>
              <input v-model="androidVer" type="text" class="inp" placeholder="如 20260207-36" />
            </div>
            <div class="form-row">
              <label>PC 版本 (pc_ver)</label>
              <input v-model="pcVer" type="text" class="inp" placeholder="如 2026.207.6" />
            </div>
            <div class="toolbar">
              <button type="button" class="btn-ghost" :disabled="loading" @click="loadData">重新加载</button>
              <button type="button" class="btn-primary" :disabled="savingVersions || loading" @click="saveVersions">
                {{ savingVersions ? '保存中…' : '保存版本号' }}
              </button>
            </div>
          </section>

          <section class="card">
            <h3>安装包</h3>
            <p v-if="loadError" class="err">{{ loadError }}</p>

            <div v-for="pkg in packages" :key="pkg.platform" class="pkg-row">
              <div class="pkg-meta">
                <span class="pkg-platform">{{ platformLabel(pkg.platform) }}</span>
                <span class="pkg-name" :title="pkg.fileName">{{ pkg.fileName }}</span>
                <span v-if="pkg.uploaded" class="badge ok">已上传</span>
                <span v-else class="badge warn">未上传</span>
                <span v-if="pkg.uploaded && pkg.size != null" class="pkg-size">{{ formatSize(pkg.size) }}</span>
              </div>
              <div class="pkg-actions">
                <a
                  v-if="pkg.uploaded && pkg.downloadUrl"
                  :href="pkg.downloadUrl"
                  class="btn-link"
                  target="_blank"
                  rel="noopener"
                >直链</a>
                <label class="btn-upload">
                  <input
                    type="file"
                    :accept="acceptForPlatform(pkg.platform)"
                    class="file-inp"
                    :disabled="uploadingPlatform === pkg.platform"
                    @change="(e) => onFilePick(e, pkg)"
                  />
                  {{ uploadingPlatform === pkg.platform ? '上传中…' : '上传' }}
                </label>
                <div v-if="uploadingPlatform === pkg.platform && uploadProgress >= 0" class="progress-wrap">
                  <div class="progress-bar" :style="{ width: uploadProgress + '%' }" />
                </div>
              </div>
            </div>

            <p class="hint">请先保存待发布版本号，再上传安装包；仅校验文件类型，落盘文件名与上表一致。</p>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'vue-toastification'
import AdminSidebar from '@/components/AdminSidebar.vue'
import {
  fetchAdminClientReleases,
  saveAdminClientReleaseVersions,
  uploadAdminClientRelease
} from '@/api/clientReleases.js'

const router = useRouter()
const toast = useToast()
const sidebarRef = ref(null)
const adminInfo = ref({})
const androidVer = ref('')
const pcVer = ref('')
const publishedAndroidVer = ref('')
const publishedPcVer = ref('')
const pendingEffectiveAt = ref('')
const packages = ref([])
const loading = ref(false)
const savingVersions = ref(false)
const loadError = ref('')
const uploadingPlatform = ref('')
const uploadProgress = ref(-1)

const platformLabel = (p) => {
  const map = { android: 'Android', windows: 'Windows', linux: 'Linux', mac: 'macOS' }
  return map[p] || p
}

const acceptForPlatform = (platform) => {
  const map = {
    android: '.apk,application/vnd.android.package-archive',
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

const toggleSidebar = () => sidebarRef.value?.toggleSidebar()

const logout = () => {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminInfo')
  localStorage.removeItem('isAdminLoggedIn')
  router.push('/admin/login')
}

const applyReleaseData = (data) => {
  publishedAndroidVer.value = data.publishedAndroidVer || data.androidVer || ''
  publishedPcVer.value = data.publishedPcVer || data.pcVer || ''
  pendingEffectiveAt.value = data.pendingEffectiveAt || ''
  androidVer.value = data.pendingAndroidVer || data.androidVer || publishedAndroidVer.value
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
  const pv = pcVer.value.trim()
  if (!av || !pv) {
    toast.error('请填写 Android 与 PC 版本号')
    return
  }
  savingVersions.value = true
  try {
    const data = await saveAdminClientReleaseVersions({ androidVer: av, pcVer: pv })
    applyReleaseData(data)
    toast.success(data.pendingEffectiveAt
      ? `已排期，version.json 将于 ${data.pendingEffectiveAt} 生效`
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

  if (!androidVer.value.trim() || !pcVer.value.trim()) {
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
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px;
  padding: 20px;
  min-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.admin-header {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.menu-toggle-btn {
  display: none;
  background: none;
  border: none;
  color: #887bb0;
  cursor: pointer;
}

.menu-toggle-btn svg {
  width: 28px;
  height: 28px;
}

.admin-user-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex: 1;
}

.logout-button {
  background: linear-gradient(135deg, rgba(220, 20, 60, 0.8), rgba(105, 200, 223, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 8px 16px;
  cursor: pointer;
}

.admin-content-wrapper {
  flex: 1;
  padding: 0 20px;
  overflow: auto;
}

.admin-subpage h2 {
  color: #69c8df;
  margin: 0 0 8px;
}

.admin-subpage > p {
  color: #887bb0;
  margin: 0 0 20px;
}

.card {
  background: rgba(255, 255, 255, 0.35);
  border-radius: 15px;
  padding: 20px;
  margin-bottom: 20px;
  border: 1px solid rgba(255, 255, 255, 0.4);
}

.card h3 {
  margin: 0 0 16px;
  color: #69c8df;
  font-size: 1.1rem;
}

.card-muted {
  background: rgba(105, 200, 223, 0.08);
}

.published-line {
  margin: 0 0 8px;
  color: #555;
  font-size: 0.95rem;
}

.published-line code {
  color: #69c8df;
}

.schedule-hint {
  margin: 0 0 14px;
  padding: 10px 12px;
  background: rgba(105, 200, 223, 0.15);
  border-radius: 8px;
  color: #6d5a00;
  font-size: 0.9rem;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.form-row label {
  font-size: 0.9rem;
  color: #887bb0;
}

.inp {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(105, 200, 223, 0.25);
  max-width: 360px;
}

.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.btn-primary,
.btn-ghost,
.btn-upload {
  padding: 8px 16px;
  border-radius: 20px;
  border: none;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-primary {
  background: linear-gradient(135deg, #69c8df, #4aa9c0);
  color: #fff;
}

.btn-ghost {
  background: rgba(255, 255, 255, 0.6);
  color: #69c8df;
}

.btn-primary:disabled,
.btn-ghost:disabled,
.btn-upload:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.pkg-row {
  padding: 14px 0;
  border-bottom: 1px solid rgba(105, 200, 223, 0.12);
}

.pkg-row:last-of-type {
  border-bottom: none;
}

.pkg-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.pkg-platform {
  font-weight: 600;
  color: #69c8df;
  min-width: 72px;
}

.pkg-name {
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
  color: #555;
  word-break: break-all;
}

.badge {
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 10px;
}

.badge.ok {
  background: rgba(76, 175, 80, 0.2);
  color: #2e7d32;
}

.badge.warn {
  background: rgba(255, 152, 0, 0.2);
  color: #e65100;
}

.pkg-size {
  font-size: 0.8rem;
  color: #888;
}

.pkg-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.btn-link {
  color: #69c8df;
  font-size: 0.9rem;
}

.btn-upload {
  background: rgba(105, 200, 223, 0.15);
  color: #69c8df;
  position: relative;
  overflow: hidden;
}

.file-inp {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.progress-wrap {
  flex: 1;
  min-width: 120px;
  height: 6px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: #69c8df;
  transition: width 0.15s ease;
}

.hint {
  margin: 16px 0 0;
  font-size: 0.85rem;
  color: #887bb0;
}

.err {
  color: #c62828;
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .admin-main-content {
    margin-left: 0;
  }
  .menu-toggle-btn {
    display: block;
  }
}
</style>
