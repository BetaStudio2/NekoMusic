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
