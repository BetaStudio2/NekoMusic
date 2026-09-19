import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import './design/tokens.css'
import './design/reset.css'
import VueToastification from 'vue-toastification'
import 'vue-toastification/dist/index.css'
import { installDevAdminBypass } from './config/devAdmin'

// 开发环境：注入模拟管理员，便于直接查看管理后台（生产构建会被剔除）
installDevAdminBypass()

const app = createApp(App)

/**
 * vue-toastification@2.0.0-rc.5 在 Vue 3.5 下的上游告警：
 * 其内部 VtToastContainer 的 data() 里已声明 positions（见库源码），
 * 但渲染代理仍会报 "Property positions was accessed during render"。
 * 这是该 rc 版（2021 年，已停止维护）与新版 Vue 的兼容性问题，功能正常。
 * 仅精确过滤这一条，其余警告照常输出，避免掩盖真实问题。
 */
app.config.warnHandler = (msg, instance, trace) => {
  if (msg.includes('Property "positions" was accessed during render')) return
  console.warn(msg, trace)
}

app.use(router)
app.use(VueToastification, {
  position: 'top-right',
  timeout: 3000,
  closeOnClick: true,
  pauseOnFocusLoss: true,
  pauseOnHover: true,
  draggable: true,
  showCloseButtonOnHover: false,
  hideProgressBar: false,
  icon: true,
  rtl: false
})

app.mount('#app')
