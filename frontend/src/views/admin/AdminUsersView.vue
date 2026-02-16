<template>
  <div class="admin-layout">
    <AdminSidebar ref="sidebarRef" />
    
    <div class="admin-main-content">
      <div class="admin-header">
        <button class="menu-toggle-btn" @click="toggleSidebar">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
          </svg>
        </button>
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button @click="logout" class="logout-button">退出登录</button>
        </div>
      </div>
      
      <div class="admin-content-wrapper">
        <div class="admin-subpage">
          <h2>用户管理</h2>
          <p>管理平台用户信息，包括查看用户列表、编辑用户权限等操作。</p>
          
          <div class="admin-controls">
            <div class="filter-section">
              <select v-model="accountType" class="filter-select">
                <option value="">所有账户</option>
                <option value="admin">管理员</option>
                <option value="user">用户</option>
              </select>
              <input 
                type="text" 
                v-model="searchQuery" 
                placeholder="搜索用户名或邮箱..." 
                class="search-input"
              />
              <button 
                              v-if="isSuperAdmin" 
                              class="create-btn"
                              @click="openCreateModal"
                            >
                              + 创建账号
                            </button>            </div>
          </div>
          
          <div class="users-list-section">
            <div class="table-container">
              <table class="users-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>用户名</th>
                    <th>邮箱</th>
                    <th>角色</th>
                    <th>注册时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="user in filteredUsers" :key="user.id">
                    <td>{{ user.id }}</td>
                    <td>{{ user.username }}</td>
                    <td>{{ user.email }}</td>
                    <td>{{ getRoleText(user.role) }}</td>
                    <td>{{ formatDate(user.registerTime) }}</td>
                    <td>
                      <button 
                        class="action-btn edit-btn" 
                        @click="editUser(user)"
                        v-if="canEditUser(user)"
                      >
                        编辑
                      </button>
                      <button 
                        class="action-btn delete-btn" 
                        @click="deleteUser(user.id)"
                        v-if="canDeleteUser(user)"
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          
          <div class="pagination">
            <button 
              :disabled="currentPage === 1" 
              @click="currentPage--"
              class="page-btn"
            >
              上一页
            </button>
            <span class="page-info">第 {{ currentPage }} 页，共 {{ totalPages }} 页</span>
            <button 
              :disabled="currentPage === totalPages" 
              @click="currentPage++"
              class="page-btn"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 创建账号模态框 -->
    <Transition name="modal">
      <div v-if="creatingUser" class="edit-modal-overlay" @click="closeCreateModal">
        <div class="edit-modal" @click.stop>
          <div class="modal-header">
            <h3>创建账号</h3>
            <button class="close-btn" @click="closeCreateModal">&times;</button>
          </div>
          <div class="modal-content">
            <div v-if="createFormData.accountType === 'admin'" class="form-group">
              <label>角色</label>
              <select v-model="createFormData.role" class="form-select">
<!--                <option value="super_admin">超级管理员</option>-->
                <option value="admin">管理员</option>
                <option value="auditor">审核员</option>
              </select>
            </div>
            <div class="form-group">
              <label>用户名</label>
              <input type="text" v-model="createFormData.username" placeholder="请输入用户名" />
            </div>
            <div class="form-group">
              <label>邮箱</label>
              <input type="email" v-model="createFormData.email" placeholder="请输入邮箱" />
            </div>
            <div class="form-group">
              <label>密码</label>
              <input type="password" v-model="createFormData.password" placeholder="请输入密码" />
            </div>
            <div class="form-group">
              <label>确认密码</label>
              <input type="password" v-model="createFormData.confirmPassword" placeholder="请确认密码" />
            </div>
          </div>
          <div class="modal-actions">
            <button class="secondary-btn" @click="closeCreateModal">取消</button>
            <button class="primary-btn" @click="createUser">创建</button>
          </div>
        </div>
      </div>
    </Transition>
    
    <!-- 编辑用户模态框 -->
    <Transition name="modal">
      <div v-if="editingUser" class="edit-modal-overlay" @click="closeEditModal">
        <div class="edit-modal" @click.stop>
          <div class="modal-header">
            <h3>编辑用户</h3>
            <button class="close-btn" @click="closeEditModal">&times;</button>
          </div>
          <div class="modal-content">
            <div class="form-group">
              <label>用户名</label>
              <input type="text" v-model="editingUser.username" disabled class="disabled-input" />
            </div>
            <div class="form-group">
              <label>邮箱</label>
              <input type="email" v-model="editingUser.email" disabled class="disabled-input" />
            </div>
            <div class="form-group">
              <label>新密码</label>
              <input type="password" v-model="editingUser.newPassword" placeholder="如果不修改密码请留空" />
            </div>
            <div class="form-group">
              <label>确认新密码</label>
              <input type="password" v-model="editingUser.confirmPassword" placeholder="如果不修改密码请留空" />
            </div>
          </div>
          <div class="modal-actions">
            <button class="secondary-btn" @click="closeEditModal">取消</button>
            <button class="primary-btn" @click="saveUserEdit">保存</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import AdminSidebar from '@/components/AdminSidebar.vue'
import { useToast } from 'vue-toastification'
import API_CONFIG from '@/config/apiConfig.js'

const toast = useToast()

const router = useRouter()
const sidebarRef = ref(null)

// 管理员信息
const adminInfo = ref({})

// 切换侧边栏
const toggleSidebar = () => {
  if (sidebarRef.value) {
    sidebarRef.value.toggleSidebar()
  }
}
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
    const timestamp = Date.now()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/users?t=${timestamp}`, {
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

// 格式化日期
const formatDate = (date) => {
  if (!date) return '无'
  return new Date(date).toLocaleDateString('zh-CN')
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
    confirmPassword: ''
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

// 保存用户编辑
const saveUserEdit = async () => {
  if (!editingUser.value.newPassword && !editingUser.value.confirmPassword) {
    toast.info('密码未修改')
    closeEditModal()
    return
  }
  
  if (editingUser.value.newPassword !== editingUser.value.confirmPassword) {
    toast.error('两次输入的密码不一致')
    return
  }
  
  if (editingUser.value.newPassword.length < 6) {
    toast.error('密码长度不能少于6位')
    return
  }
  
  try {
    let endpoint
    let requestBody
    
    if (editingUser.value.accountType === 'admin') {
      // 管理员账户使用专用 API
      endpoint = `${API_CONFIG.BASE_URL}/api/admin/users/${editingUser.value.id}/edit`
      requestBody = {
        password: editingUser.value.newPassword
      }
    } else {
      // 普通用户使用专用 API
      endpoint = `${API_CONFIG.BASE_URL}/api/users/${editingUser.value.id}/edit`
      requestBody = {
        password: editingUser.value.newPassword
      }
    }
    
    const response = await fetch(endpoint, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      },
      body: JSON.stringify(requestBody)
    })
    
    const data = await response.json()
    if (data.success) {
      toast.success('密码修改成功')
      closeEditModal()
    } else {
      toast.error(data.message || '密码修改失败')
    }
  } catch (error) {
    console.error('密码修改失败:', error)
    toast.error('密码修改失败')
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

const logout = () => {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminInfo')
  localStorage.removeItem('isAdminLoggedIn')
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px; /* 侧边栏宽度 */
  padding: 20px;
  transition: margin-left 0.3s ease;
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
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  margin-bottom: 20px;
  flex-shrink: 0; /* 防止头部被压缩 */
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
  padding: 5px;
  transition: color 0.3s ease;
}

.menu-toggle-btn:hover {
  color: #6a5acd;
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
  background: linear-gradient(135deg, rgba(220, 20, 60, 0.8), rgba(255, 99, 71, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(220, 20, 60, 0.3);
}

.logout-button:hover {
  background: linear-gradient(135deg, rgba(190, 10, 50, 0.9), rgba(235, 79, 51, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(220, 20, 60, 0.5);
}

.admin-content-wrapper {
  flex: 1; /* 让内容区域占据剩余空间 */
  padding: 0 20px;
  min-height: 0; /* 允许内容区域收缩 */
  overflow: auto; /* 如果内容过多，允许滚动 */
}

.admin-subpage {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.admin-subpage h2 {
  color: #6a5acd;
  margin: 0 0 20px 0;
  font-size: 1.5rem;
}

.admin-controls {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.filter-section {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-select, .search-input {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.create-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #6a5acd, #7c6bfa);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 1rem;
  transition: all 0.3s ease;
  margin-left: auto;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(106, 90, 205, 0.4);
}

.create-btn:active {
  transform: translateY(0);
}

.filter-select:focus, .search-input:focus {
  outline: none;
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 0 0 2px rgba(106, 90, 205, 0.2);
  background: rgba(255, 255, 255, 0.35);
}

.users-list-section {
  margin-top: 20px;
}

.table-container {
  overflow-x: auto;
}

.users-table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  overflow: hidden;
}

.users-table th,
.users-table td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.users-table th {
  background: rgba(106, 90, 205, 0.3);
  color: #6a5acd;
  font-weight: 600;
}

.users-table tr:last-child td {
  border-bottom: none;
}

.users-table tr:hover {
  background: rgba(106, 90, 205, 0.1);
}

.action-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.8rem;
  margin-right: 5px;
  transition: all 0.3s ease;
}

.edit-btn {
  background: rgba(52, 152, 219, 0.2);
  color: #3498db;
}

.edit-btn:hover {
  background: rgba(52, 152, 219, 0.3);
}

.delete-btn {
  background: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
}

.delete-btn:hover {
  background: rgba(231, 76, 60, 0.3);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 15px;
}

.page-btn {
  padding: 8px 15px;
  border: none;
  border-radius: 8px;
  background: rgba(106, 90, 205, 0.2);
  color: #6a5acd;
  cursor: pointer;
  transition: all 0.3s ease;
}

.page-btn:hover:not(:disabled) {
  background: rgba(106, 90, 205, 0.3);
  transform: translateY(-2px);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.page-info {
  color: #887bb0;
  font-size: 0.9rem;
}

/* 编辑模态框样式 */
.edit-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.edit-modal {
  background: white;
  border-radius: 15px;
  width: 90%;
  max-width: 500px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
  animation: modalSlideIn 0.3s ease;
}

@keyframes modalSlideIn {
  from {
    transform: translateY(-50px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  padding: 20px;
  border-bottom: 1px solid rgba(106, 90, 205, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  margin: 0;
  color: #6a5acd;
  font-size: 1.3rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #999;
  cursor: pointer;
  transition: color 0.3s ease;
}

.close-btn:hover {
  color: #e74c3c;
}

.modal-content {
  padding: 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #6a5acd;
  font-weight: 600;
}

.form-group input {
  width: 100%;
  padding: 10px 15px;
  border: 1px solid rgba(106, 90, 205, 0.3);
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.form-group input:focus {
  outline: none;
  border-color: #6a5acd;
  box-shadow: 0 0 0 3px rgba(106, 90, 205, 0.1);
}

.form-select {
  width: 100%;
  padding: 10px 15px;
  border: 1px solid rgba(106, 90, 205, 0.3);
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.3s ease;
  background: white;
}

.form-select:focus {
  outline: none;
  border-color: #6a5acd;
  box-shadow: 0 0 0 3px rgba(106, 90, 205, 0.1);
}

.disabled-input {
  background: rgba(0, 0, 0, 0.05);
  cursor: not-allowed;
}

.modal-actions {
  padding: 20px;
  border-top: 1px solid rgba(106, 90, 205, 0.1);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.primary-btn, .secondary-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.3s ease;
}

.primary-btn {
  background: linear-gradient(135deg, #6a5acd, #8a2be2);
  color: white;
}

.primary-btn:hover {
  background: linear-gradient(135deg, #5a4ab3, #7a2ad2);
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(106, 90, 205, 0.3);
}

.secondary-btn {
  background: rgba(106, 90, 205, 0.1);
  color: #6a5acd;
}

.secondary-btn:hover {
  background: rgba(106, 90, 205, 0.2);
}

/* 模态框过渡动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-main-content {
    margin-left: 0;
    padding: 10px 10px 130px 10px;
  }
  
  .admin-layout {
    flex-direction: column;
  }
  
  .menu-toggle-btn {
    display: block;
  }
  
  .admin-header {
    padding: 15px;
  }
  
  .admin-user-info span {
    font-size: 0.9rem;
  }
}
</style>