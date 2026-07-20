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
        <div class="admin-dashboard">
          <div class="admin-header-section">
            <h2 class="admin-title">管理中心</h2>
            <p class="admin-subtitle">平台数据统计与管理</p>
          </div>
          

            <!-- 统计概览 -->
            <div v-if="activeTab === 'stats'" class="tab-panel">
              <div class="stats-summary">
                <div class="stat-card">
                  <div class="stat-icon">🎵</div>
                  <div class="stat-info">
                    <div class="stat-number">{{ stats.totalMusic }}</div>
                    <div class="stat-label">总音乐数</div>
                  </div>
                </div>
                
                <div class="stat-card">
                  <div class="stat-icon">👥</div>
                  <div class="stat-info">
                    <div class="stat-number">{{ stats.totalUsers }}</div>
                    <div class="stat-label">总用户数</div>
                  </div>
                </div>
              </div>
              
              <div class="chart-section">
                <h3>数据趋势图</h3>
                <div class="chart-container">
                  <canvas ref="trendChartCanvas"></canvas>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>

<script setup>
import { ref, onMounted, onActivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import AdminSidebar from '@/components/AdminSidebar.vue'
import {
  Chart as ChartJS,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'

// 注册 Chart.js 组件
ChartJS.register(
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Title,
  Tooltip,
  Legend,
  Filler
)

const router = useRouter()
const sidebarRef = ref(null)
const stats = ref({
  totalMusic: 0,
  totalUsers: 0
})

// 管理员信息
const adminInfo = ref({})
const activeTab = ref('stats')
const trendChartCanvas = ref(null)
let trendChart = null

// 切换侧边栏
const toggleSidebar = () => {
  if (sidebarRef.value) {
    sidebarRef.value.toggleSidebar()
  }
}

// 初始化管理员信息
onMounted(() => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  
  if (storedToken && storedAdminInfo) {
    try {
      const parsedInfo = JSON.parse(storedAdminInfo)
      adminInfo.value = parsedInfo
    } catch (e) {
      console.error('解析管理员信息失败:', e)
      router.push('/admin/login')
    }
    fetchStats()
  } else {
    // 如果没有存储的管理员信息，重定向到登录页面
    router.push('/admin/login')
  }
})

// 组件激活时（例如路由切换回来时）重新获取数据
onActivated(() => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  
  if (storedToken && storedAdminInfo) {
    fetchStats()
  }
})

// 在组件卸载时销毁图表
onUnmounted(() => {
  if (trendChart) {
    trendChart.destroy()
  }
})

const fetchStats = async () => {
  try {
    const storedToken = localStorage.getItem('adminToken')
    
    // 获取总体统计数据
    const statsResponse = await fetch(`${API_CONFIG.BASE_URL}/api/admin/stats`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${storedToken}` // 发送实际的会话令牌进行验证
      }
    })
    
    const statsData = await statsResponse.json()
    
    if (statsResponse.ok && statsData.success) {
      // 只更新后端返回的实际数据（totalMusic 和 totalUsers）
      stats.value = {
        totalMusic: statsData.data.totalMusic || 0,
        totalUsers: statsData.data.totalUsers || 0
      }
    } else {
      console.error('获取统计数据失败:', statsData.message)
      // 使用默认值
      stats.value = {
        totalMusic: 0,
        totalUsers: 0
      }
    }
    
    // 获取图表数据
    const chartResponse = await fetch(`${API_CONFIG.BASE_URL}/api/admin/chart-data`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${storedToken}` // 发送实际的会话令牌进行验证
      }
    })
    
    const chartData = await chartResponse.json()
    
    if (chartResponse.ok && chartData.success) {
      // 渲染趋势图表
      renderTrendChart(chartData.data)
    } else {
      console.error('获取图表数据失败:', chartData.message)
      renderTrendChart({})
    }
  } catch (error) {
    console.error('获取统计数据时发生错误:', error)
    // 使用默认值
    stats.value = {
      totalMusic: 0,
      totalUsers: 0
    }
    renderTrendChart({})
  }
}

// 渲染趋势图表
const renderTrendChart = (data) => {
  // 销毁之前的图表实例（如果存在）
  if (trendChart) {
    trendChart.destroy()
  }
  
  // 确保canvas元素存在
  if (!trendChartCanvas.value) {
    console.error('Canvas element not found')
    return
  }
  
  // 获取canvas元素
  const ctx = trendChartCanvas.value.getContext('2d')
  
  // 准备趋势数据
  const today = new Date()
  const dates = []
  const userCounts = []
  const musicCounts = []
  const visitCounts = []
  
  // 生成最近7天的日期标签
  for (let i = 6; i >= 0; i--) {
    const date = new Date()
    date.setDate(today.getDate() - i)
    const formattedDate = date.toISOString().split('T')[0] // 格式化为 YYYY-MM-DD
    dates.push(formattedDate)
  }
  
  // 根据后端提供的数据填充数组
  dates.forEach(date => {
    // 用户注册数
    userCounts.push(data.userTrendData && data.userTrendData[date] ? data.userTrendData[date] : 0)
    
    // 音乐添加数
    musicCounts.push(data.musicTrendData && data.musicTrendData[date] ? data.musicTrendData[date] : 0)
    
    // 访问量
    visitCounts.push(data.visitTrendData && data.visitTrendData[date] ? data.visitTrendData[date] : 0)
  })
  
  // 计算y轴的最大值，至少为50
  const allDataValues = [...userCounts, ...musicCounts, ...visitCounts]
  const maxValue = Math.max(50, ...allDataValues)
  
  // 创建趋势图
  trendChart = new ChartJS(ctx, {
    type: 'line',
    data: {
      labels: dates,
      datasets: [
        {
          label: '用户注册数',
          data: userCounts,
          borderColor: '#69c8df',
          backgroundColor: 'rgba(105, 200, 223, 0.16)',
          tension: 0.28,
          pointBackgroundColor: '#9beaff',
          pointBorderColor: '#08131a'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false, // 允许图表调整大小
      plugins: {
        title: {
          display: true,
          text: '平台数据趋势',
          color: '#f2f8fb'
        },
        legend: {
          display: true,
          position: 'top',
          labels: {
            color: '#a6b7c4'
          }
        }
      },
      scales: {
        y: {
          beginAtZero: true,
          max: maxValue,
          grid: {
            color: 'rgba(143, 174, 198, 0.12)'
          },
          ticks: {
            color: '#a6b7c4',
            // 确保y轴只显示整数
            callback: function(value) {
              if (Number.isInteger(value)) {
                return value
              }
            },
            // 确保只显示整数刻度
            precision: 0
          }
        },
        x: {
          grid: {
            display: false // 隐藏x轴网格线以提高可读性
          },
          ticks: {
            color: '#a6b7c4'
          }
        }
      }
    }
  })
}

const goTo = (path) => {
  router.push(path)
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
  color: #69c8df;
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

.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.admin-header-section {
  margin-bottom: 30px;
}

.admin-title {
  color: #69c8df;
  margin: 0 0 10px 0;
  font-size: 1.8rem;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
  background: linear-gradient(45deg, #9beaff, #69c8df, #9beaff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
  z-index: 1;
}

.admin-subtitle {
  color: #887bb0;
  font-size: 1rem;
  margin: 0;
  position: relative;
  z-index: 1;
}

.stats-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  padding: 20px;
  display: flex;
  align-items: center;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: transform 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-5px);
}

.stat-icon {
  font-size: 2rem;
  margin-right: 15px;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 1.8rem;
  font-weight: bold;
  color: #69c8df;
  margin-bottom: 5px;
}

.stat-label {
  color: #887bb0;
  font-size: 0.9rem;
}

.chart-section {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 15px;
  padding: 25px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.chart-section h3 {
  color: #69c8df;
  margin: 0 0 20px 0;
  font-size: 1.3rem;
}

.chart-container {
  position: relative;
  height: 400px;
  width: 100%;
}

.chart-placeholder {
  text-align: center;
  padding: 40px;
  color: #887bb0;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
}

.section-content {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.content-placeholder {
  padding: 30px;
  color: #887bb0;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  text-align: center;
  margin-top: 10px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  text-decoration: none;
  display: inline-block;
}

.action-btn.primary {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.8), rgba(105, 200, 223, 0.8));
  color: white;
  box-shadow: 0 4px 10px rgba(105, 200, 223, 0.3);
}

.action-btn.primary:hover {
  background: linear-gradient(135deg, rgba(86, 70, 185, 0.9), rgba(118, 23, 206, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(105, 200, 223, 0.5);
}

/* dashboard skin override */
.admin-layout {
  background:
    radial-gradient(900px 520px at 12% -8%, rgba(105, 200, 223, 0.16), transparent 58%),
    linear-gradient(180deg, #070b10, #0b1118 48%, #06090d 100%);
  color: var(--neko-text);
}

.admin-main-content {
  min-height: 100dvh;
  padding: 22px 24px 40px;
}

.admin-header,
.stat-card,
.chart-section,
.chart-placeholder,
.content-placeholder {
  background: rgba(14, 22, 31, 0.82);
  border: 1px solid rgba(143, 174, 198, 0.14);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.24);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.admin-header {
  border-radius: 20px;
}

.menu-toggle-btn {
  color: var(--neko-muted);
}

.menu-toggle-btn:hover {
  color: var(--neko-accent-strong);
}

.admin-user-info {
  color: var(--neko-muted);
}

.logout-button {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(143, 174, 198, 0.14);
  color: var(--neko-text);
  box-shadow: none;
}

.logout-button:hover {
  background: rgba(105, 200, 223, 0.08);
  box-shadow: none;
}

.admin-title {
  color: var(--neko-text);
  background: none;
  -webkit-text-fill-color: currentColor;
  text-shadow: none;
}

.admin-subtitle,
.stat-label,
.chart-placeholder,
.content-placeholder {
  color: var(--neko-muted);
}

.stat-card {
  border-radius: 20px;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: rgba(105, 200, 223, 0.24);
}

.stat-number,
.chart-section h3 {
  color: var(--neko-accent-strong);
}

.chart-section {
  border-radius: 20px;
}

.action-btn.primary {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.22), rgba(105, 200, 223, 0.1));
  color: var(--neko-text);
  border: 1px solid rgba(105, 200, 223, 0.22);
  box-shadow: none;
}

.action-btn.primary:hover {
  background: rgba(105, 200, 223, 0.14);
  box-shadow: none;
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
  
  .stats-summary {
    grid-template-columns: 1fr;
  }
  
  .tab-button {
    padding: 10px 15px;
    font-size: 0.85rem;
  }
}
</style>
