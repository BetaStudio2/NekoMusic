<template>
  <div class="subpage">
    <header class="subpage__head">
      <h1 class="subpage__title">管理中心</h1>
      <p class="subpage__desc">平台数据统计与管理</p>
    </header>

    <div v-if="activeTab === 'stats'" class="stats">
      <div class="stats__grid">
        <NCard pad="lg" class="stat">
          <span class="stat__icon"><NIcon name="music" :size="22" /></span>
          <span class="stat__info">
            <span class="stat__number">{{ stats.totalMusic }}</span>
            <span class="stat__label">总音乐数</span>
          </span>
        </NCard>

        <NCard pad="lg" class="stat">
          <span class="stat__icon"><NIcon name="users" :size="22" /></span>
          <span class="stat__info">
            <span class="stat__number">{{ stats.totalUsers }}</span>
            <span class="stat__label">总用户数</span>
          </span>
        </NCard>
      </div>

      <NCard pad="lg" class="chart">
        <h2 class="chart__title">数据趋势图</h2>
        <div class="chart__canvas">
          <canvas ref="trendChartCanvas"></canvas>
        </div>
      </NCard>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
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

</script>

<style scoped>
.subpage {
  width: 100%;
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
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 统计卡 ==================== */
.stats__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--n-space-5);
  margin-bottom: var(--n-space-5);
}

.stat {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
}

.stat__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.stat__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat__number {
  color: var(--n-text);
  font-size: var(--n-text-2xl);
  font-weight: var(--n-weight-bold);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.stat__label {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 图表 ==================== */
.chart__title {
  margin: 0 0 var(--n-space-5);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.chart__canvas {
  position: relative;
  width: 100%;
  min-height: 280px;
}
</style>
