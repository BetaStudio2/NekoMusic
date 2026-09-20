<template>
  <div class="workspace">
    <!-- 左：文件树 -->
    <aside class="files">
      <header class="files__head">
        <div>
          <h1 class="files__title">歌词编辑</h1>
          <p class="files__count">{{ treeStats.totalFiles }} 个文件</p>
        </div>
        <NButton size="sm" variant="ghost" icon="refresh" title="刷新" @click="fetchTree" />
      </header>

      <div class="files__tools">
        <NInput v-model="searchQuery" icon="search" placeholder="搜索歌词、ID、歌名或歌手" clearable />
        <NButton v-if="canEditLyrics" size="sm" variant="secondary" icon="plus" @click="showNewFile = !showNewFile">
          新建
        </NButton>
      </div>

      <div v-if="showNewFile" class="files__new">
        <NInput
          v-model="newFilePath"
          placeholder="例如 123.lrc"
          @keydown.enter="createDraftFile"
        />
        <NButton size="sm" variant="primary" @click="createDraftFile">确定</NButton>
      </div>

      <div v-if="isLoadingTree" class="files__state">正在加载…</div>
      <div v-else-if="visibleNodes.length === 0" class="files__state">暂无歌词文件</div>
      <nav v-else class="tree" aria-label="歌词文件树">
        <button
          v-for="item in visibleNodes"
          :key="`${item.node.type}:${item.node.path}`"
          type="button"
          class="tree__row"
          :class="{
            'tree__row--active': selectedFile && selectedFile.path === item.node.path,
            'tree__row--dir': item.node.type === 'directory',
          }"
          :style="{ paddingLeft: `${12 + item.level * 18}px` }"
          @click="handleNodeClick(item.node)"
        >
          <NIcon
            v-if="item.node.type === 'directory'"
            :name="isExpanded(item.node.path) ? 'chevron-down' : 'chevron-right'"
            :size="14"
            class="tree__icon"
          />
          <NIcon v-else name="file-text" :size="14" class="tree__icon" />
          <span class="tree__label">{{ fileLabel(item.node) }}</span>
          <NIcon
            v-if="item.node.type === 'file' && !item.node.existsInDb"
            name="circle-alert"
            :size="13"
            class="tree__orphan"
            title="未匹配到曲库"
          />
        </button>
      </nav>
    </aside>

    <!-- 右：编辑器 -->
    <main class="editor">
      <div v-if="!selectedFile" class="editor__empty">
        <NIcon name="file-text" :size="30" />
        <p>从左侧选择一个歌词文件</p>
      </div>

      <template v-else>
        <header class="editor__head">
          <div class="editor__heading">
            <h2 class="editor__name">{{ selectedFile.displayName || selectedFile.name }}</h2>
            <div class="editor__meta">
              <span>{{ selectedFile.path }}</span>
              <span v-if="selectedFile.musicId">ID {{ selectedFile.musicId }}</span>
              <span v-if="selectedFile.artist">{{ selectedFile.artist }}</span>
              <span>{{ formatBytes(selectedFile.size || 0) }}</span>
              <NTag v-if="hasUnsavedChanges" variant="warning" size="sm">未保存</NTag>
            </div>
          </div>

          <div class="editor__actions">
            <NButton
              size="sm"
              variant="secondary"
              icon="refresh"
              :disabled="isLoadingFile || isDraftFile"
              @click="reloadSelectedFile"
            >
              重新加载
            </NButton>
            <NButton
              v-if="canEditLyrics"
              size="sm"
              variant="danger"
              icon="trash-2"
              :disabled="isDeleting || isDraftFile"
              @click="deleteSelectedFile"
            >
              删除
            </NButton>
            <NButton
              v-if="canEditLyrics"
              size="sm"
              variant="primary"
              icon="check"
              :disabled="isSaving || !hasUnsavedChanges"
              :loading="isSaving"
              @click="saveSelectedFile"
            >
              保存
            </NButton>
          </div>
        </header>

        <div class="editor__body">
          <div ref="lineGutterRef" class="gutter">
            <div v-for="line in lineNumbers" :key="line">{{ line }}</div>
          </div>
          <textarea
            ref="editorRef"
            v-model="lyricsContent"
            class="code"
            spellcheck="false"
            :readonly="!canEditLyrics || isLoadingFile"
            @scroll="syncEditorScroll"
          />
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner, NTag } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import API_CONFIG from '@/config/apiConfig.js'

const router = useRouter()
const toast = useToast()
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

</script>

<style scoped>
.workspace {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: var(--n-space-5);
  align-items: stretch;
  min-height: calc(100dvh - 200px);
}

/* ==================== 左侧文件树 ==================== */
.files {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
}

.files__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-4);
}

.files__title {
  margin: 0;
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.files__count {
  margin: 2px 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.files__tools {
  display: flex;
  gap: var(--n-space-2);
  margin-bottom: var(--n-space-3);
}

.files__tools :deep(.n-input) {
  flex: 1;
  min-width: 0;
}

.files__new {
  display: flex;
  gap: var(--n-space-2);
  margin-bottom: var(--n-space-3);
}

.files__new :deep(.n-input) {
  flex: 1;
  min-width: 0;
}

.files__state {
  padding: var(--n-space-8) 0;
  text-align: center;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

/* ==================== 文件树 ==================== */
.tree {
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
  min-height: 0;
  flex: 1;
}

.tree__row {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  width: 100%;
  padding: var(--n-space-2) var(--n-space-3);
  border-radius: var(--n-radius-xs);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  text-align: left;
  transition: background var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .tree__row:hover {
    background: var(--n-surface-soft);
    color: var(--n-text);
  }
}

.tree__row--active {
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
  font-weight: var(--n-weight-medium);
}

.tree__row--dir {
  color: var(--n-text-faint);
}

.tree__icon {
  flex: none;
}

.tree__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree__orphan {
  flex: none;
  color: var(--n-warning);
}

/* ==================== 右侧编辑器 ==================== */
.editor {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
  overflow: hidden;
}

.editor__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-3);
  flex: 1;
  min-height: 320px;
  color: var(--n-text-faint);
}

.editor__empty p {
  margin: 0;
  font-size: var(--n-text-sm);
}

.editor__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-4);
  padding: var(--n-space-4) var(--n-space-5);
  border-bottom: 1px solid var(--n-line-subtle);
}

.editor__heading {
  min-width: 0;
}

.editor__name {
  margin: 0;
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
  overflow-wrap: anywhere;
}

.editor__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--n-space-2) var(--n-space-4);
  margin-top: var(--n-space-1);
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.editor__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-2);
  flex: none;
}

.editor__body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 320px;
  overflow: hidden;
}

.gutter {
  flex: none;
  width: 56px;
  padding: var(--n-space-4) var(--n-space-3) var(--n-space-4) 0;
  border-right: 1px solid var(--n-line-subtle);
  background: var(--n-surface-sunken);
  color: var(--n-text-faint);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-xs);
  line-height: var(--n-leading-loose);
  text-align: right;
  overflow: hidden;
  user-select: none;
}

.code {
  flex: 1;
  min-width: 0;
  padding: var(--n-space-4) var(--n-space-5);
  border: none;
  outline: none;
  background: transparent;
  color: var(--n-text);
  caret-color: var(--n-accent-strong);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-loose);
  resize: none;
  white-space: pre;
  overflow: auto;
}

.code:read-only {
  color: var(--n-text-muted);
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .files {
    max-height: 360px;
  }

  .editor__body {
    min-height: 420px;
  }
}
</style>
