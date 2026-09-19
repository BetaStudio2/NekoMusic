<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">用户管理</h1>
        <p class="subpage__desc">管理平台用户信息，包括查看用户列表、编辑用户权限等操作。</p>
      </div>
    </header>

    <!-- 工具条 -->
    <div class="toolbar">
      <select v-model="accountType" class="select" aria-label="账户类型">
        <option value="">所有账户</option>
        <option value="admin">管理员</option>
        <option value="user">用户</option>
      </select>

      <NInput
        v-model="searchQuery"
        icon="search"
        placeholder="搜索用户名或邮箱…"
        clearable
        class="toolbar__search"
      />

      <NButton v-if="isSuperAdmin" variant="primary" icon="user-plus" @click="openCreateModal">
        创建账号
      </NButton>
    </div>

    <!-- 表格 -->
    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>注册时间</th>
            <th>会员</th>
            <th aria-label="操作" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in filteredUsers" :key="user.id">
            <td class="cell-dim">{{ user.id }}</td>
            <td class="cell-strong">{{ user.username }}</td>
            <td class="cell-dim">{{ user.email }}</td>
            <td>{{ getRoleText(user.role) }}</td>
            <td class="cell-dim">{{ formatDate(user.registerTime) }}</td>
            <td>
              <template v-if="user.accountType === 'user'">
                <NTag v-if="user.vip" variant="accent" size="sm">VIP</NTag>
                <span v-else class="cell-dim">-</span>
                <div v-if="user.vipExpiresAt" class="cell-sub">{{ formatVipExpiresAt(user.vipExpiresAt) }}</div>
              </template>
              <span v-else class="cell-dim">-</span>
            </td>
            <td class="cell-actions">
              <NButton
                v-if="canEditUser(user)"
                size="sm"
                variant="secondary"
                icon="pencil"
                @click="editUser(user)"
              >
                编辑
              </NButton>
              <NButton
                v-if="canDeleteUser(user)"
                size="sm"
                variant="danger"
                icon="trash-2"
                @click="deleteUser(user.id)"
              >
                删除
              </NButton>
            </td>
          </tr>
          <tr v-if="!filteredUsers.length">
            <td colspan="7" class="cell-empty">没有匹配的用户</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pager">
      <NButton size="sm" variant="secondary" icon="chevron-left" :disabled="currentPage === 1" @click="currentPage--">
        上一页
      </NButton>
      <span class="pager__info">第 {{ currentPage }} / {{ totalPages }} 页</span>
      <NButton
        size="sm"
        variant="secondary"
        icon-after="chevron-right"
        :disabled="currentPage === totalPages"
        @click="currentPage++"
      >
        下一页
      </NButton>
    </div>

    <!-- 创建账号 -->
    <NModal v-model="creatingUser" title="创建账号" @close="closeCreateModal">
      <div v-if="createFormData.accountType === 'admin'" class="field">
        <label class="field__label" for="cu-role">角色</label>
        <select id="cu-role" v-model="createFormData.role" class="select select--block">
          <option value="admin">管理员</option>
          <option value="auditor">审核员</option>
        </select>
      </div>
      <div class="field">
        <label class="field__label" for="cu-username">用户名</label>
        <NInput id="cu-username" v-model="createFormData.username" placeholder="请输入用户名" />
      </div>
      <div class="field">
        <label class="field__label" for="cu-email">邮箱</label>
        <NInput id="cu-email" v-model="createFormData.email" type="email" placeholder="请输入邮箱" />
      </div>
      <div class="field">
        <label class="field__label" for="cu-password">密码</label>
        <NInput id="cu-password" v-model="createFormData.password" type="password" placeholder="请输入密码" />
      </div>
      <div class="field field--last">
        <label class="field__label" for="cu-confirm">确认密码</label>
        <NInput id="cu-confirm" v-model="createFormData.confirmPassword" type="password" placeholder="请确认密码" />
      </div>
      <template #footer>
        <NButton variant="ghost" @click="closeCreateModal">取消</NButton>
        <NButton variant="primary" @click="createUser">创建</NButton>
      </template>
    </NModal>

    <!-- 编辑用户 -->
    <NModal
      :model-value="!!editingUser"
      title="编辑用户"
      @close="closeEditModal"
    >
      <template v-if="editingUser">
        <div class="field">
          <label class="field__label" for="eu-username">用户名</label>
          <NInput id="eu-username" v-model="editingUser.username" disabled />
        </div>
        <div class="field">
          <label class="field__label" for="eu-email">邮箱</label>
          <NInput id="eu-email" v-model="editingUser.email" type="email" disabled />
        </div>
        <div class="field">
          <label class="field__label" for="eu-pwd">新密码</label>
          <NInput id="eu-pwd" v-model="editingUser.newPassword" type="password" placeholder="不修改请留空" />
        </div>
        <div class="field">
          <label class="field__label" for="eu-pwd2">确认新密码</label>
          <NInput id="eu-pwd2" v-model="editingUser.confirmPassword" type="password" placeholder="不修改请留空" />
        </div>

        <template v-if="editingUser.accountType === 'user'">
          <div class="field field--last">
            <label class="field__label" for="eu-vip">会员到期（上海时间 UTC+8）</label>
            <input
              id="eu-vip"
              v-model="editingUser.vipLocal"
              type="datetime-local"
              class="native-input"
              :disabled="editingUser.vipClear"
            />
            <label class="check">
              <input v-model="editingUser.vipClear" type="checkbox" />
              清除会员
            </label>
          </div>
        </template>
      </template>

      <template #footer>
        <NButton variant="ghost" @click="closeEditModal">取消</NButton>
        <NButton variant="primary" @click="saveUserEdit">保存</NButton>
      </template>
    </NModal>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner, NTag } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import API_CONFIG from '@/config/apiConfig.js'
import {
  isoToDatetimeLocalValue,
  datetimeLocalShanghaiToIso,
  formatVipExpiresAt
} from '@/utils/userVip.js'

const toast = useToast()

const router = useRouter()

// 管理员信息
const adminInfo = ref({})

// 切换侧边栏
const searchQuery = ref('')
const accountType = ref('')
const currentPage = ref(1)
const usersPerPage = 10

// 编辑用户
const editingUser = ref(null)

// 创建账号
const creatingUser = ref(false)
const createFormData = ref({
  accountType: 'admin',
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  role: 'admin'
})

// 检查管理员登录状态
onMounted(() => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  
  if (storedToken && storedAdminInfo) {
    try {
      const parsedInfo = JSON.parse(storedAdminInfo)
      adminInfo.value = parsedInfo
      
      // 临时修复：如果没有role字段，从后端重新获取管理员信息
      if (!parsedInfo.role) {
        console.log('没有role字段，重新获取管理员信息')
        fetchCurrentAdminInfo()
      }
      
      // 加载用户数据
      loadAllUsers()
    } catch (e) {
      console.error('解析管理员信息失败:', e)
      router.push('/admin/login')
    }
  } else {
    router.push('/admin/login')
  }
})

// 临时方法：获取当前管理员信息
const fetchCurrentAdminInfo = async () => {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/current`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      }
    })
    const data = await response.json()
    if (data.success && data.admin) {
      adminInfo.value = data.admin
      localStorage.setItem('adminInfo', JSON.stringify(data.admin))
      console.log('已更新管理员信息:', adminInfo.value)
    }
  } catch (error) {
    console.error('获取管理员信息失败:', error)
  }
}

// 权限检查方法
const canEditUser = (user) => {
  if (!adminInfo.value) return false
  const role = adminInfo.value.role || 'admin'
  
  // 审核员只能编辑自己的账号
  if (role === 'auditor') {
    return user.id === adminInfo.value.id && user.accountType === 'admin'
  }
  
  // 管理员可以编辑普通用户，但不能编辑其他管理员（只能编辑自己）
  if (role === 'admin') {
    return user.accountType === 'user' || (user.accountType === 'admin' && user.id === adminInfo.value.id)
  }
  
  // 超级管理员可以编辑所有用户
  return true
}

const canDeleteUser = (user) => {
  if (!adminInfo.value) return false
  const role = adminInfo.value.role || 'admin'
  
  // 审核员不能删除用户（包括自己）
  if (role === 'auditor') return false
  
  // 管理员可以删除普通用户，但不能删除其他管理员
  if (role === 'admin') {
    return user.accountType === 'user'
  }
  
  // 超级管理员拥有所有权限，可以删除任何用户
  return role === 'super_admin';
}

// 检查是否显示该用户（审核员只能看到自己的账号）
const shouldShowUser = (user) => {
  if (!adminInfo.value) return false
  const role = adminInfo.value.role || 'admin'
  
  // 审核员只能看到自己的账号
  if (role === 'auditor') {
    return user.id === adminInfo.value.id && user.accountType === 'admin'
  }
  
  // 超管和管理员可以看到所有用户
  return true
}

// 检查是否为超级管理员（临时解决方案）
const isSuperAdmin = computed(() => {
  if (!adminInfo.value) return false
  const role = adminInfo.value.role
  if (role === 'super_admin') return true
  
  // 临时解决方案：如果没有role字段或role不是super_admin，根据id判断（第一个管理员是超管）
  if (!role || role !== 'super_admin') {
    return adminInfo.value.id === 1
  }
  
  return false
})

// 用户数据
const adminUsers = ref([])
const regularUsers = ref([])

// 获取管理员用户列表
const fetchAdminUsers = async () => {
  const role = adminInfo.value?.role || 'admin'
  console.log('fetchAdminUsers - 当前角色:', role)
  console.log('fetchAdminUsers - adminInfo.value:', adminInfo.value)
  
  // 审核员只显示自己的账号
  if (role === 'auditor') {
    adminUsers.value = [{
      id: adminInfo.value.id,
      username: adminInfo.value.username,
      email: adminInfo.value.email,
      role: adminInfo.value.role,
      registerTime: new Date().toISOString()
    }]
    return
  }
  
  // 超管和管理员从后端获取完整列表
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/users`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      }
    })
    const data = await response.json()
    console.log('管理员用户数据:', data)
    if (data.success) {
      adminUsers.value = data.data.map(user => ({
        ...user,
        accountType: 'admin'
      }))
      console.log('处理后的管理员用户:', adminUsers.value)
    } else {
      console.error('获取管理员用户失败:', data.message)
    }
  } catch (error) {
    console.error('获取管理员用户失败:', error)
  }
}

// 获取普通用户列表
const fetchRegularUsers = async () => {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/users`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      }
    })
    const data = await response.json()
    if (data.success) {
      regularUsers.value = data.data.map(user => ({
        ...user,
        accountType: 'user'
      }))
    }
  } catch (error) {
    console.error('获取普通用户失败:', error)
  }
}

// 合并所有用户
const allUsers = computed(() => {
  return [...adminUsers.value, ...regularUsers.value]
})

// 计算过滤后的用户列表
const filteredUsers = computed(() => {
  let result = allUsers.value
  
  // 先应用权限过滤
  result = result.filter(user => shouldShowUser(user))
  
  // 按账户类型过滤
  if (accountType.value) {
    result = result.filter(user => user.accountType === accountType.value)
  }
  
  // 按搜索查询过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(user => 
      user.username.toLowerCase().includes(query) || 
      user.email.toLowerCase().includes(query)
    )
  }
  
  // 计算分页
  const startIndex = (currentPage.value - 1) * usersPerPage
  const endIndex = startIndex + usersPerPage
  return result.slice(startIndex, endIndex)
})

// 计算总页数
const totalPages = computed(() => {
  let result = allUsers.value
  
  // 先应用权限过滤
  result = result.filter(user => shouldShowUser(user))
  
  // 应用账户类型过滤
  if (accountType.value) {
    result = result.filter(user => user.accountType === accountType.value)
  }
  
  // 应用搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(user => 
      user.accountType === (accountType.value || user.accountType) &&
      (user.username.toLowerCase().includes(query) || user.email.toLowerCase().includes(query))
    )
  }
  
  return Math.max(1, Math.ceil(result.length / usersPerPage))
})

// 格式化日期（统一东八区）
const formatDate = (date) => {
  if (!date) return '无'
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return '无'
  return d.toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai',
    dateStyle: 'medium'
  })
}

// 获取角色文本
const getRoleText = (role) => {
  const roleMap = {
    'super_admin': '超级管理员',
    'admin': '管理员',
    'auditor': '审核员'
  }
  return roleMap[role] || role
}

// 编辑用户
const editUser = (user) => {
  editingUser.value = {
    ...user,
    newPassword: '',
    confirmPassword: '',
    vipClear: false,
    vipLocal: user.accountType === 'user' ? isoToDatetimeLocalValue(user.vipExpiresAt) : ''
  }
}

// 关闭编辑模态框
const closeEditModal = () => {
  editingUser.value = null
}

// 打开创建模态框
const openCreateModal = () => {
  creatingUser.value = true
  createFormData.value = {
    accountType: 'admin',
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: 'admin'
  }
}

// 关闭创建模态框
const closeCreateModal = () => {
  creatingUser.value = false
  createFormData.value = {
    accountType: 'admin',
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: 'admin'
  }
}

// 创建账号
const createUser = async () => {
  // 验证表单
  if (!createFormData.value.username || !createFormData.value.username.trim()) {
    toast.error('用户名不能为空')
    return
  }
  
  if (!createFormData.value.email || !createFormData.value.email.trim()) {
    toast.error('邮箱不能为空')
    return
  }
  
  if (!createFormData.value.password || createFormData.value.password.length < 6) {
    toast.error('密码长度不能少于6位')
    return
  }
  
  if (createFormData.value.password !== createFormData.value.confirmPassword) {
    toast.error('两次输入的密码不一致')
    return
  }
  
  if (createFormData.value.accountType === 'admin' && !createFormData.value.role) {
    toast.error('请选择管理员角色')
    return
  }
  
  try {
    const token = localStorage.getItem('adminToken')
    
    let endpoint, requestData
    
    if (createFormData.value.accountType === 'admin') {
      // 创建管理员账号
      endpoint = `${API_CONFIG.BASE_URL}/api/admin/create`
      requestData = {
        username: createFormData.value.username,
        email: createFormData.value.email,
        password: createFormData.value.password,
        role: createFormData.value.role
      }
    } else {
      // 创建普通用户账号
      endpoint = `${API_CONFIG.BASE_URL}/api/admin/create-user`
      requestData = {
        username: createFormData.value.username,
        email: createFormData.value.email,
        password: createFormData.value.password
      }
    }
    
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(requestData)
    })
    
    const data = await response.json()
    
    if (data.success) {
      toast.success('账号创建成功')
      closeCreateModal()
      // 重新加载用户列表
      loadAllUsers()
    } else {
      toast.error(data.message || '账号创建失败')
    }
  } catch (error) {
    console.error('创建账号失败:', error)
    toast.error('账号创建失败')
  }
}

// 保存用户编辑（普通用户：密码可选；会员到期按东八区提交）
const saveUserEdit = async () => {
  const e = editingUser.value
  if (!e) return

  const pwd = (e.newPassword || '').trim()
  const confirm = (e.confirmPassword || '').trim()
  const wantPwd = pwd.length > 0 || confirm.length > 0
  if (wantPwd) {
    if (pwd !== confirm) {
      toast.error('两次输入的密码不一致')
      return
    }
    if (pwd.length < 6) {
      toast.error('密码长度不能少于6位')
      return
    }
  }

  if (e.accountType === 'admin') {
    if (!wantPwd) {
      toast.info('未填写新密码，未做修改')
      closeEditModal()
      return
    }
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/users/${e.id}/edit`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('adminToken')}`
        },
        body: JSON.stringify({ password: pwd })
      })
      const data = await response.json()
      if (data.success) {
        toast.success('密码修改成功')
        closeEditModal()
        await fetchAdminUsers()
      } else {
        toast.error(data.message || '密码修改失败')
      }
    } catch (err) {
      console.error('密码修改失败:', err)
      toast.error('密码修改失败')
    }
    return
  }

  let vipExpiresAt
  if (e.vipClear) {
    vipExpiresAt = null
  } else {
    const iso = datetimeLocalShanghaiToIso(e.vipLocal)
    if (!iso) {
      toast.error('请填写有效的会员到期时间（东八区），或勾选「清除会员」')
      return
    }
    vipExpiresAt = iso
  }

  const requestBody = { vipExpiresAt }
  if (wantPwd) requestBody.password = pwd

  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/users/${e.id}/edit`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('adminToken')}`
      },
      body: JSON.stringify(requestBody)
    })
    const data = await response.json()
    if (data.success) {
      toast.success('保存成功')
      closeEditModal()
      await loadAllUsers()
    } else {
      toast.error(data.message || '保存失败')
    }
  } catch (err) {
    console.error('保存失败:', err)
    toast.error('保存失败')
  }
}

// 删除用户
const deleteUser = async (userId) => {
  const user = allUsers.value.find(u => u.id === userId)
  if (user && confirm(`确定要删除用户 ${user.username} 吗？此操作不可撤销。`)) {
    try {
      const endpoint = user.accountType === 'admin' 
        ? `${API_CONFIG.BASE_URL}/api/admin/users/${userId}`
        : `${API_CONFIG.BASE_URL}/api/users/${userId}`
      
      const response = await fetch(endpoint, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
        }
      })
      
      const data = await response.json()
      if (data.success) {
        toast.success('删除用户成功')
        // 重新加载数据
        if (user.accountType === 'admin') {
          await fetchAdminUsers()
        } else {
          await fetchRegularUsers()
        }
        // 如果当前页为空且不是第一页，则转到上一页
        if (filteredUsers.value.length === 0 && currentPage.value > 1) {
          currentPage.value--
        }
      } else {
        toast.error(data.message || '删除用户失败')
      }
    } catch (error) {
      console.error('删除用户失败:', error)
      toast.error('删除用户失败')
    }
  }
}

// 加载所有用户数据
const loadAllUsers = async () => {
  await Promise.all([
    fetchAdminUsers(),
    fetchRegularUsers()
  ])
}

// 监听页码变化，确保不会超出范围
watch(currentPage, (newPage) => {
  if (newPage < 1) {
    currentPage.value = 1
  } else if (newPage > totalPages.value && totalPages.value > 0) {
    currentPage.value = totalPages.value
  }
})

</script>

<style scoped>
.subpage {
  width: 100%;
}

.subpage__head {
  margin-bottom: var(--n-space-5);
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
}

/* ==================== 工具条 ==================== */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-5);
}

.toolbar__search {
  flex: 1 1 240px;
  min-width: 0;
}

.select {
  height: 40px;
  padding: 0 var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  color: var(--n-text);
  font-size: var(--n-text-base);
  outline: none;
  transition: border-color var(--n-duration-fast) var(--n-ease), box-shadow var(--n-duration-fast) var(--n-ease);
}

.select:focus {
  border-color: var(--n-accent-line);
  box-shadow: var(--n-shadow-glow);
}

.select option {
  background: var(--n-bg-elevated);
  color: var(--n-text);
}

.select--block {
  width: 100%;
}

/* ==================== 表格 ==================== */
.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
}

.table {
  width: 100%;
  border-collapse: collapse;
  color: var(--n-text);
  font-size: var(--n-text-sm);
}

.table th,
.table td {
  padding: var(--n-space-3) var(--n-space-4);
  text-align: left;
  border-bottom: 1px solid var(--n-line-subtle);
  vertical-align: middle;
  white-space: nowrap;
}

.table th {
  background: var(--n-accent-soft);
  font-weight: var(--n-weight-semibold);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

@media (hover: hover) {
  .table tbody tr:hover td {
    background: var(--n-surface-soft);
  }
}

.cell-dim {
  color: var(--n-text-muted);
}

.cell-strong {
  color: var(--n-text);
  font-weight: var(--n-weight-medium);
}

.cell-sub {
  margin-top: 2px;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.cell-actions {
  display: flex;
  gap: var(--n-space-2);
}

.cell-empty {
  padding: var(--n-space-10) var(--n-space-4) !important;
  text-align: center;
  color: var(--n-text-faint);
}

/* ==================== 分页 ==================== */
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--n-space-4);
  margin-top: var(--n-space-4);
}

.pager__info {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-variant-numeric: tabular-nums;
}

/* ==================== 弹窗字段 ==================== */
.field {
  margin-bottom: var(--n-space-4);
}

.field--last {
  margin-bottom: 0;
}

.field__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-medium);
}

.native-input {
  width: 100%;
  height: 40px;
  padding: 0 var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  color: var(--n-text);
  font-size: var(--n-text-base);
  outline: none;
}

.native-input:focus {
  border-color: var(--n-accent-line);
  box-shadow: var(--n-shadow-glow);
}

.native-input:disabled {
  opacity: 0.55;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  margin-top: var(--n-space-3);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  cursor: pointer;
}
</style>
