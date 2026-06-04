import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import './styles/glassShell.css'
import VueToastification from 'vue-toastification'
import 'vue-toastification/dist/index.css'

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
