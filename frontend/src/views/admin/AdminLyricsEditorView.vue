<template>
  <div class="admin-layout">
    <AdminSidebar ref="sidebarRef" />

    <div class="admin-main-content">
      <div class="admin-header">
        <button class="menu-toggle-btn" @click="toggleSidebar" aria-label="打开菜单">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
          </svg>
        </button>
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button @click="logout" class="logout-button">退出登录</button>
        </div>
      </div>

      <div class="lyrics-workspace">
        <aside class="file-panel">
          <div class="panel-toolbar">
            <div>
              <h2>歌词编辑</h2>
              <span>{{ treeStats.totalFiles }} 个文件</span>
            </div>
            <button class="icon-button" @click="fetchTree" title="刷新">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h8V3l-3.35 3.35z"/>
              </svg>
            </button>
          </div>

          <div class="file-actions">
            <input
              v-model="searchQuery"
              class="search-input"
              type="text"
              placeholder="搜索歌词、ID、歌名或歌手"
            />
            <button v-if="canEditLyrics" class="new-button" @click="showNewFile = !showNewFile">
              新建
            </button>
          </div>

          <div v-if="showNewFile" class="new-file-box">
            <input
              v-model="newFilePath"
              class="new-file-input"
              type="text"
              placeholder="例如 123.lrc"
              @keydown.enter="createDraftFile"
            />
            <button class="small-primary-btn" @click="createDraftFile">确定</button>
          </div>

          <div v-if="isLoadingTree" class="tree-state">正在加载...</div>
          <div v-else-if="visibleNodes.length === 0" class="tree-state">暂无歌词文件</div>
          <div v-else class="file-tree">
            <button
              v-for="item in visibleNodes"
              :key="`${item.node.type}:${item.node.path}`"
              class="tree-row"
              :class="{
                active: selectedFile && selectedFile.path === item.node.path,
                directory: item.node.type === 'directory'
              }"
              :style="{ paddingLeft: `${12 + item.level * 18}px` }"
              @click="handleNodeClick(item.node)"
            >
              <span class="tree-icon">
                <template v-if="item.node.type === 'directory'">
                  {{ isExpanded(item.node.path) ? '▾' : '▸' }}
                </template>
                <template v-else>♪</template>
              </span>
              <span class="tree-label">{{ fileLabel(item.node) }}</span>
              <span v-if="item.node.type === 'file' && !item.node.existsInDb" class="orphan-dot" title="未匹配到曲库"></span>
            </button>
          </div>
        </aside>

        <main class="editor-panel">
          <div v-if="!selectedFile" class="empty-editor">
            <h3>选择一个歌词文件</h3>
          </div>

          <template v-else>
            <div class="editor-topbar">
              <div class="file-heading">
                <h3>{{ selectedFile.displayName || selectedFile.name }}</h3>
                <div class="file-meta">
                  <span>{{ selectedFile.path }}</span>
                  <span v-if="selectedFile.musicId">ID {{ selectedFile.musicId }}</span>
                  <span v-if="selectedFile.artist">{{ selectedFile.artist }}</span>
                  <span>{{ formatBytes(selectedFile.size || 0) }}</span>
                  <span v-if="hasUnsavedChanges" class="dirty-label">未保存</span>
                </div>
              </div>
              <div class="editor-actions">
                <button class="secondary-btn" @click="reloadSelectedFile" :disabled="isLoadingFile || isDraftFile">
                  重新加载
                </button>
                <button
                  v-if="canEditLyrics"
                  class="danger-btn"
                  @click="deleteSelectedFile"
                  :disabled="isDeleting || isDraftFile"
                >
                  删除
                </button>
                <button
                  v-if="canEditLyrics"
                  class="primary-btn"
                  @click="saveSelectedFile"
                  :disabled="isSaving || !hasUnsavedChanges"
                >
                  {{ isSaving ? '保存中...' : '保存' }}
                </button>
              </div>
            </div>

            <div class="editor-body">
              <div class="line-gutter" ref="lineGutterRef">
                <div v-for="line in lineNumbers" :key="line">{{ line }}</div>
              </div>
              <textarea
                ref="editorRef"
                v-model="lyricsContent"
                class="lyrics-editor"
                spellcheck="false"
                :readonly="!canEditLyrics || isLoadingFile"
                @scroll="syncEditorScroll"
              ></textarea>
            </div>
          </template>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'vue-toastification'
import AdminSidebar from '@/components/AdminSidebar.vue'
import API_CONFIG from '@/config/apiConfig.js'

const router = useRouter()
const toast = useToast()
const sidebarRef = ref(null)
const adminInfo = ref({})

const tree = ref(null)
const isLoadingTree = ref(false)
const isLoadingFile = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)
const searchQuery = ref('')
const selectedFile = ref(null)
const lyricsContent = ref('')
const originalContent = ref('')
const expandedPaths = ref(new Set(['']))
const showNewFile = ref(false)
const newFilePath = ref('')
const editorRef = ref(null)
const lineGutterRef = ref(null)

const canEditLyrics = computed(() => {
  const role = adminInfo.value.role || 'admin'
  return role === 'super_admin' || role === 'admin'
})

const hasUnsavedChanges = computed(() => lyricsContent.value !== originalContent.value)
const isDraftFile = computed(() => selectedFile.value && !findFileByPath(selectedFile.value.path))

const treeStats = computed(() => ({
  totalFiles: collectFiles(tree.value).length
}))

const visibleNodes = computed(() => {
  if (!tree.value) return []

  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    return collectFiles(tree.value)
      .filter(node => matchesQuery(node, query))
      .map(node => ({ node, level: 0 }))
  }

  return flattenChildren(sortedChildren(tree.value.children || []), 0)
})

const lineNumbers = computed(() => {
  const total = Math.max(1, lyricsContent.value.split('\n').length)
  return Array.from({ length: total }, (_, index) => index + 1)
})

onMounted(() => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')

  if (!storedToken || !storedAdminInfo) {
    router.push('/admin/login')
    return
  }

  try {
    adminInfo.value = JSON.parse(storedAdminInfo)
  } catch (e) {
    router.push('/admin/login')
    return
  }

  fetchTree()
})

const toggleSidebar = () => {
  sidebarRef.value?.toggleSidebar()
}

const fetchTree = async () => {
  isLoadingTree.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/lyrics-files/tree`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('adminToken')}`
      }
    })
    const data = await response.json()

    if (!response.ok || !data.success) {
      throw new Error(data.message || '获取歌词文件失败')
    }

    tree.value = data.data.tree
    if (selectedFile.value) {
      const refreshed = findFileByPath(selectedFile.value.path)
      if (refreshed) {
        selectedFile.value = refreshed
      }
    }
  } catch (error) {
    toast.error(error.message || '获取歌词文件失败')
  } finally {
    isLoadingTree.value = false
  }
}

const handleNodeClick = async (node) => {
  if (node.type === 'directory') {
    toggleDirectory(node.path)
    return
  }

  await selectFile(node)
}

const selectFile = async (file) => {
  if (hasUnsavedChanges.value && !confirm('当前歌词尚未保存，确定切换文件吗？')) {
    return
  }

  isLoadingFile.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/lyrics-files/file/${encodePath(file.path)}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('adminToken')}`
      }
    })
    const data = await response.json()

    if (!response.ok || !data.success) {
      throw new Error(data.message || '读取歌词失败')
    }

    selectedFile.value = data.data
    lyricsContent.value = data.data.content || ''
    originalContent.value = lyricsContent.value
  } catch (error) {
    toast.error(error.message || '读取歌词失败')
  } finally {
    isLoadingFile.value = false
  }
}

const reloadSelectedFile = async () => {
  if (!selectedFile.value || isDraftFile.value) return
  await selectFile(selectedFile.value)
}

const saveSelectedFile = async () => {
  if (!selectedFile.value || !canEditLyrics.value) return

  isSaving.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/lyrics-files/file/${encodePath(selectedFile.value.path)}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('adminToken')}`
      },
      body: JSON.stringify({ content: lyricsContent.value })
    })
    const data = await response.json()

    if (!response.ok || !data.success) {
      throw new Error(data.message || '保存歌词失败')
    }

    selectedFile.value = data.data
    originalContent.value = lyricsContent.value
    await fetchTree()
    toast.success('歌词已保存')
  } catch (error) {
    toast.error(error.message || '保存歌词失败')
  } finally {
    isSaving.value = false
  }
}

const deleteSelectedFile = async () => {
  if (!selectedFile.value || !canEditLyrics.value || isDraftFile.value) return
  if (!confirm(`确定删除 ${selectedFile.value.path} 吗？`)) return

  isDeleting.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/lyrics-files/file/${encodePath(selectedFile.value.path)}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('adminToken')}`
      }
    })
    const data = await response.json()

    if (!response.ok || !data.success) {
      throw new Error(data.message || '删除歌词失败')
    }

    selectedFile.value = null
    lyricsContent.value = ''
    originalContent.value = ''
    await fetchTree()
    toast.success('歌词已删除')
  } catch (error) {
    toast.error(error.message || '删除歌词失败')
  } finally {
    isDeleting.value = false
  }
}

const createDraftFile = async () => {
  if (!canEditLyrics.value) return
  const path = normalizeNewFilePath(newFilePath.value)
  if (!path) {
    toast.error('请输入 .lrc 文件名')
    return
  }

  if (hasUnsavedChanges.value && !confirm('当前歌词尚未保存，确定新建文件吗？')) {
    return
  }

  const existing = findFileByPath(path)
  if (existing) {
    showNewFile.value = false
    newFilePath.value = ''
    await selectFile(existing)
    return
  }

  selectedFile.value = {
    type: 'file',
    name: path.split('/').pop(),
    path,
    size: 0,
    musicId: musicIdFromPath(path),
    title: null,
    artist: null,
    existsInDb: false,
    displayName: path
  }
  lyricsContent.value = ''
  originalContent.value = ''
  expandPathParents(path)
  showNewFile.value = false
  newFilePath.value = ''
}

const flattenChildren = (children, level) => {
  const rows = []
  for (const child of children) {
    rows.push({ node: child, level })
    if (child.type === 'directory' && isExpanded(child.path)) {
      rows.push(...flattenChildren(sortedChildren(child.children || []), level + 1))
    }
  }
  return rows
}

const sortedChildren = (children) => {
  return [...children].sort((a, b) => {
    if (a.type !== b.type) return a.type === 'directory' ? -1 : 1
    return a.name.localeCompare(b.name, 'zh-CN', { numeric: true })
  })
}

const collectFiles = (node) => {
  if (!node) return []
  if (node.type === 'file') return [node]
  return (node.children || []).flatMap(child => collectFiles(child))
}

const matchesQuery = (node, query) => {
  return [
    node.name,
    node.path,
    node.displayName,
    node.title,
    node.artist,
    node.musicId ? String(node.musicId) : ''
  ]
    .filter(Boolean)
    .some(value => String(value).toLowerCase().includes(query))
}

const findFileByPath = (path) => {
  return collectFiles(tree.value).find(file => file.path === path) || null
}

const toggleDirectory = (path) => {
  const next = new Set(expandedPaths.value)
  if (next.has(path)) {
    next.delete(path)
  } else {
    next.add(path)
  }
  expandedPaths.value = next
}

const isExpanded = (path) => expandedPaths.value.has(path)

const expandAll = () => {
  const next = new Set([''])
  collectDirectories(tree.value).forEach(dir => next.add(dir.path))
  expandedPaths.value = next
}

const collapseAll = () => {
  expandedPaths.value = new Set([''])
}

const collectDirectories = (node) => {
  if (!node || node.type !== 'directory') return []
  return [node, ...(node.children || []).flatMap(child => collectDirectories(child))]
}

const expandPathParents = (path) => {
  const parts = path.split('/')
  if (parts.length <= 1) return
  const next = new Set(expandedPaths.value)
  let current = ''
  for (let i = 0; i < parts.length - 1; i++) {
    current = current ? `${current}/${parts[i]}` : parts[i]
    next.add(current)
  }
  expandedPaths.value = next
}

const fileLabel = (node) => {
  if (node.type === 'directory') return node.name
  return node.displayName || node.name
}

const normalizeNewFilePath = (value) => {
  let path = value.trim().replace(/\\/g, '/').replace(/^\/+/, '')
  while (path.includes('//')) path = path.replace(/\/\//g, '/')
  if (!path) return ''
  if (!path.toLowerCase().endsWith('.lrc')) path += '.lrc'
  if (path.includes('..') || path.split('/').some(part => !part.trim())) return ''
  return path
}

const musicIdFromPath = (path) => {
  const match = path.split('/').pop().match(/^(\d+)\.lrc$/i)
  return match ? Number(match[1]) : null
}

const encodePath = (path) => path.split('/').map(segment => encodeURIComponent(segment)).join('/')

const formatBytes = (bytes) => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const syncEditorScroll = () => {
  if (lineGutterRef.value && editorRef.value) {
    lineGutterRef.value.scrollTop = editorRef.value.scrollTop
  }
}

const logout = () => {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('isAdminLoggedIn')
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #f4f7fb 0%, #e8eef6 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px;
  padding: 20px;
  min-width: 0;
}

.admin-header {
  padding: 18px 20px;
  background: rgba(255, 255, 255, 0.58);
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(35, 48, 80, 0.14);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.35);
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.menu-toggle-btn {
  display: none;
  background: none;
  border: none;
  color: #5d6680;
  cursor: pointer;
  padding: 5px;
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
  min-width: 0;
}

.logout-button,
.primary-btn,
.secondary-btn,
.danger-btn,
.new-button,
.small-primary-btn,
.icon-button {
  border: none;
  cursor: pointer;
  transition: all 0.18s ease;
  font-size: 0.9rem;
}

.logout-button {
  background: #d94b5f;
  color: white;
  border-radius: 18px;
  padding: 8px 16px;
}

.lyrics-workspace {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 18px;
  height: calc(100vh - 118px);
  min-height: 560px;
}

.file-panel,
.editor-panel {
  background: rgba(255, 255, 255, 0.64);
  border: 1px solid rgba(205, 215, 230, 0.85);
  border-radius: 8px;
  box-shadow: 0 12px 30px rgba(37, 51, 83, 0.12);
  overflow: hidden;
}

.file-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid rgba(205, 215, 230, 0.85);
}

.panel-toolbar h2 {
  color: #2f3650;
  margin: 0 0 4px;
  font-size: 1.15rem;
}

.panel-toolbar span {
  color: #6d7488;
  font-size: 0.82rem;
}

.icon-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(68, 91, 138, 0.1);
  color: #445b8a;
}

.icon-button svg {
  width: 20px;
  height: 20px;
}

.file-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 12px 14px;
}

.search-input,
.new-file-input {
  width: 100%;
  border: 1px solid rgba(184, 195, 215, 0.95);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
  color: #263044;
  padding: 10px 12px;
  outline: none;
}

.search-input:focus,
.new-file-input:focus {
  border-color: #6078b8;
  box-shadow: 0 0 0 3px rgba(96, 120, 184, 0.16);
}

.new-button,
.small-primary-btn,
.primary-btn {
  background: #546fb5;
  color: white;
  border-radius: 8px;
}

.new-button {
  padding: 0 14px;
}

.small-primary-btn {
  padding: 9px 12px;
}

.new-file-box {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 0 14px 12px;
}

.file-tree {
  flex: 1;
  overflow: auto;
  padding: 6px 0 12px;
}

.tree-row {
  width: 100%;
  min-height: 34px;
  border: 0;
  background: transparent;
  color: #2f3650;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 6px;
  padding-right: 10px;
  padding-bottom: 6px;
  text-align: left;
}

.tree-row:hover {
  background: rgba(84, 111, 181, 0.1);
}

.tree-row.active {
  background: rgba(84, 111, 181, 0.18);
  color: #243f86;
}

.tree-row.directory {
  font-weight: 600;
}

.tree-icon {
  flex: 0 0 16px;
  color: #66728e;
  text-align: center;
}

.tree-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
}

.orphan-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d98932;
  flex: 0 0 auto;
}

.tree-state {
  padding: 24px 16px;
  color: #6d7488;
  text-align: center;
}

.editor-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.empty-editor {
  flex: 1;
  display: grid;
  place-items: center;
  color: #6d7488;
}

.empty-editor h3 {
  font-size: 1.2rem;
  font-weight: 500;
}

.editor-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(205, 215, 230, 0.85);
}

.file-heading {
  min-width: 0;
}

.file-heading h3 {
  margin: 0 0 7px;
  color: #2f3650;
  font-size: 1.1rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #6d7488;
  font-size: 0.82rem;
}

.file-meta span {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dirty-label {
  color: #b45b20;
  font-weight: 700;
}

.editor-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.primary-btn,
.secondary-btn,
.danger-btn {
  padding: 9px 14px;
}

.secondary-btn {
  background: rgba(95, 107, 132, 0.12);
  color: #43506b;
  border-radius: 8px;
}

.danger-btn {
  background: rgba(207, 72, 88, 0.13);
  color: #b93748;
  border-radius: 8px;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.danger-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.editor-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  background: #f9fbff;
}

.line-gutter {
  overflow: hidden;
  padding: 14px 10px 14px 0;
  background: #eef3f9;
  border-right: 1px solid #d8e0ed;
  color: #8a94a8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.62;
  text-align: right;
  user-select: none;
}

.lyrics-editor {
  width: 100%;
  height: 100%;
  min-height: 0;
  resize: none;
  border: 0;
  outline: none;
  padding: 14px 16px;
  background: #fbfdff;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  line-height: 1.62;
  white-space: pre;
  overflow: auto;
}

.lyrics-editor[readonly] {
  color: #687386;
  background: #f5f7fb;
}

@media (max-width: 900px) {
  .admin-main-content {
    margin-left: 0;
    padding: 10px 10px 120px;
  }

  .menu-toggle-btn {
    display: block;
  }

  .lyrics-workspace {
    grid-template-columns: 1fr;
    height: auto;
  }

  .file-panel {
    height: 360px;
  }

  .editor-panel {
    height: 620px;
  }

  .editor-topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .editor-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
